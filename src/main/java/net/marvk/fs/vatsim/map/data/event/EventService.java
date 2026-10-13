package net.marvk.fs.vatsim.map.data.event;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import lombok.extern.log4j.Log4j2;
import net.marvk.fs.vatsim.map.data.Airport;
import net.marvk.fs.vatsim.map.data.AirportRepository;
import net.marvk.fs.vatsim.map.data.Preferences;
import net.marvk.fs.vatsim.map.data.airspace.AirspaceDataFetcher;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Loads the events from the VATSIM Events API and determines which of them are displayed. The API is contacted at most
 * once per hour, the response is cached in the user cache directory. Which events are visible is recalculated when
 * clients are reloaded or a setting changes, painters and views only ever read the precomputed results.
 */
@Log4j2
@Singleton
public class EventService {
    public static final String ENABLED_KEY = "events.enabled";
    public static final String SHOW_EVENTS_KEY = "events.show_events";
    public static final String SHOW_EXAMS_KEY = "events.show_exams";
    public static final String SHOW_VASOPS_KEY = "events.show_vasops";
    public static final String HORIZON_HOURS_KEY = "events.horizon_hours";

    public static final int MIN_HORIZON_HOURS = 1;
    public static final int MAX_HORIZON_HOURS = 720;

    private static final String FILE_NAME = "events.json";
    private static final String URL = "https://my.vatsim.net/api/v2/events/latest";
    private static final Duration CHECK_INTERVAL = Duration.ofHours(1);
    // Slightly longer than the check interval, so the cached file is never considered fresh when the timer fires
    private static final long RELOAD_PERIOD_MINUTES = CHECK_INTERVAL.toMinutes() + 1;

    private final AirspaceDataFetcher fetcher;
    private final AirportRepository airportRepository;

    private final BooleanProperty enabled;
    private final BooleanProperty showEvents;
    private final BooleanProperty showExams;
    private final BooleanProperty showVasops;
    private final IntegerProperty horizonHours;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "event-data-loader");
        thread.setDaemon(true);
        return thread;
    });

    private boolean reloadScheduled = false;
    // Read by the loader thread
    private volatile boolean loadingEnabled = false;

    private List<Event> events = Collections.emptyList();
    private Map<Airport, List<Event>> visibleEvents = Collections.emptyMap();

    private final ReadOnlyIntegerWrapper revision = new ReadOnlyIntegerWrapper();

    private Runnable onChange = () -> {
    };

    @Inject
    public EventService(
            final AirspaceDataFetcher fetcher,
            final AirportRepository airportRepository,
            final Preferences preferences
    ) {
        this.fetcher = fetcher;
        this.airportRepository = airportRepository;

        this.enabled = preferences.booleanProperty(ENABLED_KEY, false);
        this.showEvents = preferences.booleanProperty(SHOW_EVENTS_KEY, true);
        this.showExams = preferences.booleanProperty(SHOW_EXAMS_KEY, true);
        this.showVasops = preferences.booleanProperty(SHOW_VASOPS_KEY, false);
        this.horizonHours = preferences.integerProperty(HORIZON_HOURS_KEY, 48);
    }

    /**
     * Starts loading the events in the background if they are enabled. Must be called on the JavaFX application
     * thread.
     *
     * @param onChange called on the JavaFX application thread whenever the visible events may have changed outside of
     *                 {@link #refresh()}
     */
    public void initialize(final Runnable onChange) {
        this.onChange = onChange;

        final InvalidationListener refresh = observable -> {
            loadRequired();
            refresh();
            this.onChange.run();
        };

        enabled.addListener(refresh);
        showEvents.addListener(refresh);
        showExams.addListener(refresh);
        showVasops.addListener(refresh);
        horizonHours.addListener(refresh);

        loadRequired();
        refresh();
    }

    /**
     * Recalculates the visible events for the current time. Must be called on the JavaFX application thread.
     */
    public void refresh() {
        final Map<Airport, List<Event>> result = new LinkedHashMap<>();

        if (enabled.get()) {
            final List<Event> visible = visible(events, ZonedDateTime.now(ZoneOffset.UTC), horizonHours.get(), types());

            for (final Event event : visible) {
                for (final Airport airport : event.getAirports()) {
                    result.computeIfAbsent(airport, e -> new ArrayList<>(1)).add(event);
                }
            }
        }

        if (!result.equals(visibleEvents)) {
            visibleEvents = result;
            revision.set(revision.get() + 1);
        }
    }

    /**
     * @return the events of the given types which have not ended and start within the given number of hours, in their
     * original order
     */
    static List<Event> visible(final List<Event> events, final ZonedDateTime now, final int horizonHours, final Set<EventType> types) {
        final int clampedHours = Math.max(MIN_HORIZON_HOURS, Math.min(MAX_HORIZON_HOURS, horizonHours));
        final ZonedDateTime horizon = now.plusHours(clampedHours);

        final List<Event> result = new ArrayList<>();

        for (final Event event : events) {
            if (types.contains(event.getType()) && event.getEnd().isAfter(now) && event.getStart().isBefore(horizon)) {
                result.add(event);
            }
        }

        return result;
    }

    private Set<EventType> types() {
        final Set<EventType> result = EnumSet.noneOf(EventType.class);

        if (showEvents.get()) {
            result.add(EventType.EVENT);
        }

        if (showExams.get()) {
            result.add(EventType.CONTROLLER_EXAMINATION);
        }

        if (showVasops.get()) {
            result.add(EventType.VASOPS);
        }

        return result;
    }

    /**
     * @return the airports with at least one visible event
     */
    public Collection<Airport> visibleEventAirports() {
        return visibleEvents.keySet();
    }

    /**
     * @return the visible events at the airport, sorted by their start time
     */
    public List<Event> eventsAt(final Airport airport) {
        return visibleEvents.getOrDefault(airport, Collections.emptyList());
    }

    public boolean hasEvents(final Airport airport) {
        return visibleEvents.containsKey(airport);
    }

    /**
     * @return a counter which is incremented whenever the visible events change
     */
    public ReadOnlyIntegerProperty revisionProperty() {
        return revision.getReadOnlyProperty();
    }

    public BooleanProperty enabledProperty() {
        return enabled;
    }

    public BooleanProperty showEventsProperty() {
        return showEvents;
    }

    public BooleanProperty showExamsProperty() {
        return showExams;
    }

    public BooleanProperty showVasopsProperty() {
        return showVasops;
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
                log.warn("No events available");
                return;
            }

            try (final Reader reader = Files.newBufferedReader(path.get(), StandardCharsets.UTF_8)) {
                final List<Event> result = EventRepository.parse(reader);

                Platform.runLater(() -> {
                    setEvents(result);
                    refresh();
                    onChange.run();
                });
            }
        } catch (final IOException | RuntimeException e) {
            // Must not propagate, the executor would cancel all further reloads
            log.error("Failed to load events", e);
        }
    }

    private void setEvents(final List<Event> events) {
        for (final Event event : events) {
            final List<Airport> airports = new ArrayList<>(event.getAirportIcaos().size());

            for (final String icao : event.getAirportIcaos()) {
                final List<Airport> matches = airportRepository.getByIcao(icao);

                if (!matches.isEmpty()) {
                    airports.add(matches.get(0));
                }
            }

            event.setAirports(airports);
        }

        this.events = events;
    }
}
