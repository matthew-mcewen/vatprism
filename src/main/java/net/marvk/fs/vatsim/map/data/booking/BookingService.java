package net.marvk.fs.vatsim.map.data.booking;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.scene.paint.Color;
import lombok.extern.log4j.Log4j2;
import net.marvk.fs.vatsim.map.data.Airport;
import net.marvk.fs.vatsim.map.data.CallsignParser;
import net.marvk.fs.vatsim.map.data.Controller;
import net.marvk.fs.vatsim.map.data.ControllerType;
import net.marvk.fs.vatsim.map.data.Data;
import net.marvk.fs.vatsim.map.data.FlightInformationRegionBoundary;
import net.marvk.fs.vatsim.map.data.Preferences;
import net.marvk.fs.vatsim.map.data.UpperInformationRegion;
import net.marvk.fs.vatsim.map.data.airspace.ActiveSubSector;
import net.marvk.fs.vatsim.map.data.airspace.AirspaceDataFetcher;
import net.marvk.fs.vatsim.map.data.airspace.AirspaceService;
import net.marvk.fs.vatsim.map.data.airspace.TraconBoundary;
import net.marvk.fs.vatsim.map.data.airspace.VgPosition;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Loads the bookings from the VATSIM ATC Bookings API and determines which positions they activate. The API is
 * contacted at most once per quarter of an hour, the response is cached in the user cache directory. Which bookings
 * are visible is recalculated when clients are reloaded or a setting changes, painters and views only ever read the
 * precomputed results.
 */
@Log4j2
@Singleton
public class BookingService {
    public static final String ENABLED_KEY = "bookings.enabled";
    public static final String HORIZON_HOURS_KEY = "bookings.horizon_hours";

    public static final int MIN_HORIZON_HOURS = 1;
    public static final int MAX_HORIZON_HOURS = 7 * 24;

    /**
     * The colour of booked Vatglasses sectors, their opacity is set by the painter.
     */
    public static final Color SUB_SECTOR_COLOUR = Color.GRAY;

    private static final String FILE_NAME = "bookings.json";
    private static final String URL = "https://atc-bookings.vatsim.net/api/booking";
    private static final Duration CHECK_INTERVAL = Duration.ofMinutes(15);
    // Slightly longer than the check interval, so the cached file is never considered fresh when the timer fires
    private static final long RELOAD_PERIOD_MINUTES = CHECK_INTERVAL.toMinutes() + 1;

    private static final ActiveSubSector[] NO_SUB_SECTORS = new ActiveSubSector[0];

    private final AirspaceDataFetcher fetcher;
    private final AirspaceService airspaceService;
    private final CallsignParser callsignParser;

    private final BooleanProperty enabled;
    private final IntegerProperty horizonHours;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "booking-data-loader");
        thread.setDaemon(true);
        return thread;
    });

    private boolean reloadScheduled = false;
    // Read by the loader thread
    private volatile boolean loadingEnabled = false;

    private List<Booking> bookings = Collections.emptyList();

    // All visible bookings by the airport or airspace they belong to, online or not
    private Map<Data, List<Booking>> visibleBookings = Collections.emptyMap();

    // What the bookings add to the map, positions which are online are left out
    private Map<Airport, List<ControllerType>> bookedTypes = Collections.emptyMap();
    private Set<Airport> bookedApproachBoundaries = Collections.emptySet();
    private List<FlightInformationRegionBoundary> bookedFirbs = Collections.emptyList();
    private List<UpperInformationRegion> bookedUirs = Collections.emptyList();
    private List<TraconBoundary> bookedTracons = Collections.emptyList();
    private ActiveSubSector[] bookedSubSectors = NO_SUB_SECTORS;

    private final ReadOnlyIntegerWrapper revision = new ReadOnlyIntegerWrapper();

    private Runnable onChange = () -> {
    };

    @Inject
    public BookingService(
            final AirspaceDataFetcher fetcher,
            final AirspaceService airspaceService,
            final CallsignParser callsignParser,
            final Preferences preferences
    ) {
        this.fetcher = fetcher;
        this.airspaceService = airspaceService;
        this.callsignParser = callsignParser;

        this.enabled = preferences.booleanProperty(ENABLED_KEY, false);
        this.horizonHours = preferences.integerProperty(HORIZON_HOURS_KEY, 3);
    }

    /**
     * Starts loading the bookings in the background if they are enabled. Must be called on the JavaFX application
     * thread, after the airspace service was initialized.
     *
     * @param onChange called on the JavaFX application thread whenever the visible bookings may have changed outside
     *                 of {@link #refresh()}
     */
    public void initialize(final Runnable onChange) {
        this.onChange = onChange;

        final InvalidationListener refresh = observable -> {
            loadRequired();
            refresh();
            this.onChange.run();
        };

        enabled.addListener(refresh);
        horizonHours.addListener(refresh);

        loadRequired();
        refresh();
    }

    /**
     * Recalculates the visible bookings for the current time and the currently online controllers. Must be called on
     * the JavaFX application thread, after the airspace service was refreshed.
     */
    public void refresh() {
        final List<Booking> visible = enabled.get()
                ? visible(bookings, ZonedDateTime.now(ZoneOffset.UTC), horizonHours.get())
                : Collections.emptyList();

        final Map<Data, List<Booking>> result = new LinkedHashMap<>();
        final Map<Airport, List<Booking>> airports = new LinkedHashMap<>();
        final Set<FlightInformationRegionBoundary> firbs = new LinkedHashSet<>();
        final Set<UpperInformationRegion> uirs = new LinkedHashSet<>();
        final Set<TraconBoundary> tracons = new LinkedHashSet<>();
        final Set<Booking> approachWithoutBoundary = new HashSet<>();

        for (final Booking booking : visible) {
            if (booking.getAirport() != null) {
                add(result, booking.getAirport(), booking);
                airports.computeIfAbsent(booking.getAirport(), e -> new ArrayList<>(1)).add(booking);
            }

            // Vatglasses sectors replace the FIR, UIR or approach circle of the positions it knows
            final List<VgPosition> positions = airspaceService.matchVatglassesPositions(booking);

            if (booking.getFlightInformationRegionBoundary() != null) {
                add(result, booking.getFlightInformationRegionBoundary(), booking);
                if (positions.isEmpty()) {
                    firbs.add(booking.getFlightInformationRegionBoundary());
                }
            }

            if (booking.getUpperInformationRegion() != null) {
                add(result, booking.getUpperInformationRegion(), booking);
                if (positions.isEmpty()) {
                    uirs.add(booking.getUpperInformationRegion());
                }
            }

            final List<TraconBoundary> matches = airspaceService.matchTracons(booking.getCallsign());

            for (final TraconBoundary match : matches) {
                add(result, match, booking);
                tracons.add(match);
            }

            if (matches.isEmpty() && positions.isEmpty() && isApproach(booking.getControllerType())) {
                approachWithoutBoundary.add(booking);
            }

            for (final VgPosition position : positions) {
                add(result, position, booking);
            }
        }

        final Map<Airport, List<ControllerType>> types = new HashMap<>();
        final Set<Airport> approachBoundaries = new HashSet<>();

        airports.forEach((airport, airportBookings) -> {
            final List<ControllerType> offline = offlineTypes(airportBookings, onlineTypes(airport));

            if (!offline.isEmpty()) {
                types.put(airport, offline);

                if (airportBookings.stream().noneMatch(approachWithoutBoundary::contains)) {
                    approachBoundaries.add(airport);
                }
            }
        });

        bookedTypes = types;
        bookedApproachBoundaries = approachBoundaries;

        firbs.removeIf(FlightInformationRegionBoundary::hasFirControllers);
        bookedFirbs = new ArrayList<>(firbs);

        uirs.removeIf(uir -> !uir.getControllers().isEmpty());
        bookedUirs = new ArrayList<>(uirs);

        tracons.removeAll(airspaceService.activeTracons());
        bookedTracons = new ArrayList<>(tracons);

        bookedSubSectors = airspaceService.bookedSubSectors(visible, SUB_SECTOR_COLOUR);

        if (!result.equals(visibleBookings)) {
            visibleBookings = result;
            revision.set(revision.get() + 1);
        }
    }

    private static void add(final Map<Data, List<Booking>> result, final Data data, final Booking booking) {
        result.computeIfAbsent(data, e -> new ArrayList<>(1)).add(booking);
    }

    /**
     * @return the bookings which have not ended and start within the given number of hours, in their original order
     */
    static List<Booking> visible(final List<Booking> bookings, final ZonedDateTime now, final int horizonHours) {
        final int clampedHours = Math.max(MIN_HORIZON_HOURS, Math.min(MAX_HORIZON_HOURS, horizonHours));
        final ZonedDateTime horizon = now.plusHours(clampedHours);

        final List<Booking> result = new ArrayList<>();

        for (final Booking booking : bookings) {
            if (booking.getEnd().isAfter(now) && booking.getStart().isBefore(horizon)) {
                result.add(booking);
            }
        }

        return result;
    }

    /**
     * @return the distinct types of the bookings at an airport which are not online there, sorted. Approach and
     * departure count as the same position
     */
    static List<ControllerType> offlineTypes(final Collection<Booking> bookings, final Set<ControllerType> online) {
        final Set<ControllerType> result = EnumSet.noneOf(ControllerType.class);

        for (final Booking booking : bookings) {
            final ControllerType type = booking.getControllerType();

            if (isApproach(type)) {
                if (!online.contains(ControllerType.APP) && !online.contains(ControllerType.DEP)) {
                    result.add(type);
                }
            } else if (isAirportType(type) && !online.contains(type)) {
                result.add(type);
            }
        }

        return new ArrayList<>(result);
    }

    private static Set<ControllerType> onlineTypes(final Airport airport) {
        final Set<ControllerType> result = EnumSet.noneOf(ControllerType.class);

        for (final Controller controller : airport.getControllers()) {
            result.add(controller.getControllerType());
        }

        return result;
    }

    private static boolean isApproach(final ControllerType type) {
        return type == ControllerType.APP || type == ControllerType.DEP;
    }

    private static boolean isAirportType(final ControllerType type) {
        return type == ControllerType.ATIS
                || type == ControllerType.DEL
                || type == ControllerType.GND
                || type == ControllerType.TWR;
    }

    /**
     * @return the visible bookings at the airport or in the airspace, sorted by their start time. Includes the
     * bookings of positions which are online
     */
    public List<Booking> bookingsAt(final Data data) {
        return visibleBookings.getOrDefault(data, Collections.emptyList());
    }

    public boolean hasBookings(final Data data) {
        return visibleBookings.containsKey(data);
    }

    /**
     * @return the booked controller types at the airport which are not online, sorted
     */
    public List<ControllerType> bookedTypes(final Airport airport) {
        return bookedTypes.getOrDefault(airport, Collections.emptyList());
    }

    public boolean hasBookedTypes(final Airport airport) {
        return bookedTypes.containsKey(airport);
    }

    /**
     * @return whether every booked approach or departure position at the airport has a booked TRACON boundary
     */
    public boolean isApproachBoundaryBooked(final Airport airport) {
        return bookedApproachBoundaries.contains(airport);
    }

    /**
     * @return the booked FIRs without an online controller
     */
    public List<FlightInformationRegionBoundary> bookedFirbs() {
        return bookedFirbs;
    }

    /**
     * @return the booked UIRs without an online controller
     */
    public List<UpperInformationRegion> bookedUirs() {
        return bookedUirs;
    }

    /**
     * @return the booked TRACON boundaries without an online controller
     */
    public List<TraconBoundary> bookedTracons() {
        return bookedTracons;
    }

    /**
     * @return the booked Vatglasses sub sectors at the current level which are not controlled by an online position
     */
    public ActiveSubSector[] bookedSubSectors() {
        return bookedSubSectors;
    }

    /**
     * @return a counter which is incremented whenever the visible bookings change
     */
    public ReadOnlyIntegerProperty revisionProperty() {
        return revision.getReadOnlyProperty();
    }

    public BooleanProperty enabledProperty() {
        return enabled;
    }

    public IntegerProperty horizonHoursProperty() {
        return horizonHours;
    }

    private void loadRequired() {
        loadingEnabled = enabled.get();

        if (!loadingEnabled) {
            return;
        }

        if (reloadScheduled) {
            // Costs no request while the cached file is fresh
            executor.submit(this::load);
        } else {
            reloadScheduled = true;
            executor.scheduleWithFixedDelay(this::load, 0, RELOAD_PERIOD_MINUTES, TimeUnit.MINUTES);
        }
    }

    private void load() {
        if (!loadingEnabled) {
            return;
        }

        try {
            final Optional<Path> path = fetcher.fetch(FILE_NAME, URL, CHECK_INTERVAL);

            if (path.isEmpty()) {
                log.warn("No bookings available");
                return;
            }

            try (final Reader reader = Files.newBufferedReader(path.get(), StandardCharsets.UTF_8)) {
                final List<Booking> result = BookingRepository.parse(reader);

                Platform.runLater(() -> {
                    setBookings(result);
                    refresh();
                    onChange.run();
                });
            }
        } catch (final IOException | RuntimeException e) {
            // Must not propagate, the executor would cancel all further reloads
            log.error("Failed to load bookings", e);
        }
    }

    private void setBookings(final List<Booking> bookings) {
        for (final Booking booking : bookings) {
            booking.setLocation(callsignParser.parse(booking.getCallsign()));
        }

        this.bookings = bookings;
    }
}
