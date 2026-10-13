package net.marvk.fs.vatsim.map.data.airspace;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.collections.ObservableList;
import javafx.scene.paint.Color;
import lombok.extern.log4j.Log4j2;
import net.marvk.fs.vatsim.map.data.ClientRepository;
import net.marvk.fs.vatsim.map.data.Controller;

import java.io.IOException;
import java.io.InputStream;
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
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Matches online controllers to TRACON boundaries and VATGlasses sectors. All matching happens when clients are
 * reloaded or a setting changes, painters only ever read the precomputed results.
 */
@Log4j2
@Singleton
public class AirspaceService {
    private static final String TRACON_FILE_NAME = "TRACONBoundaries.geojson";
    private static final String TRACON_URL = "https://github.com/vatsimnetwork/simaware-tracon-project/releases/latest/download/TRACONBoundaries.geojson";
    private static final String VATGLASSES_FILE_NAME = "vatglasses-data.zip";
    private static final String VATGLASSES_URL = "https://codeload.github.com/lennycolton/vatglasses-data/zip/refs/heads/main";
    private static final Duration CHECK_INTERVAL = Duration.ofHours(24);

    private static final ActiveSubSector[] NO_SUB_SECTORS = new ActiveSubSector[0];

    private final AirspaceDataFetcher fetcher;
    private final ClientRepository clientRepository;
    private final VatglassesState vatglassesState;

    private final BooleanProperty traconEnabled;

    private Set<VgPosition> onlinePositions = Collections.emptySet();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "airspace-data-loader");
        thread.setDaemon(true);
        return thread;
    });

    private boolean traconRequested = false;
    private boolean vatglassesRequested = false;

    private TraconRepository traconRepository = TraconRepository.EMPTY;
    private VatglassesData vatglassesData = VatglassesData.EMPTY;

    private List<TraconBoundary> activeTracons = Collections.emptyList();
    private List<ActiveSubSector> activeSubSectors = Collections.emptyList();
    private ActiveSubSector[] visibleSubSectors = NO_SUB_SECTORS;

    private Runnable onChange = () -> {
    };

    @Inject
    public AirspaceService(
            final AirspaceDataFetcher fetcher,
            final ClientRepository clientRepository,
            final VatglassesState vatglassesState
    ) {
        this.fetcher = fetcher;
        this.clientRepository = clientRepository;
        this.vatglassesState = vatglassesState;
        this.traconEnabled = vatglassesState.traconEnabledProperty();
    }

    /**
     * Starts loading the enabled data sets in the background. Must be called on the JavaFX application thread.
     *
     * @param onChange called on the JavaFX application thread whenever the active airspace changed outside of
     *                 {@link #refresh()}
     */
    public void initialize(final Runnable onChange) {
        this.onChange = onChange;

        final InvalidationListener refresh = observable -> {
            loadRequired();
            refresh();
            this.onChange.run();
        };

        traconEnabled.addListener(refresh);
        vatglassesState.enabledProperty().addListener(refresh);
        vatglassesState.cidProperty().addListener(refresh);
        vatglassesState.automaticProperty().addListener(refresh);
        vatglassesState.autoSwitchProperty().addListener(refresh);
        vatglassesState.manualLevelProperty().addListener(refresh);
        vatglassesState.overridePilotProperty().addListener(refresh);

        loadRequired();
        refresh();
    }

    /**
     * Recalculates the active airspace from the currently online clients. Must be called on the JavaFX application
     * thread.
     */
    public void refresh() {
        // May toggle the layers, so it has to come first
        vatglassesState.update(clientRepository.pilots());

        final List<Controller> controllers = clientRepository.controllers();

        final boolean tracon = traconEnabled.get();
        final boolean vatglasses = vatglassesState.enabledProperty().get();

        final Map<TraconBoundary, List<Controller>> tracons = new LinkedHashMap<>();
        final Map<VgPosition, List<Controller>> positions = new LinkedHashMap<>();

        for (final Controller controller : controllers) {
            final List<TraconBoundary> matches = tracon
                    ? traconRepository.match(controller.getCallsign())
                    : Collections.emptyList();

            for (final TraconBoundary match : matches) {
                tracons.computeIfAbsent(match, e -> new ArrayList<>(1)).add(controller);
            }

            controller.setAirspaceBoundaryMatched(!matches.isEmpty());

            if (vatglasses) {
                for (final VgPosition position : vatglassesData.positionsOf(controller)) {
                    positions.computeIfAbsent(position, e -> new ArrayList<>(1)).add(controller);
                }
            }
        }

        for (final TraconBoundary previous : activeTracons) {
            if (!tracons.containsKey(previous)) {
                previous.getControllers().clear();
            }
        }
        tracons.forEach((boundary, boundaryControllers) -> setIfChanged(boundary.getControllers(), boundaryControllers));
        activeTracons = new ArrayList<>(tracons.keySet());

        for (final VgPosition previous : onlinePositions) {
            if (!positions.containsKey(previous)) {
                previous.getControllers().clear();
            }
        }
        positions.forEach((position, positionControllers) -> setIfChanged(position.getControllers(), positionControllers));
        onlinePositions = positions.keySet();

        activeSubSectors = vatglasses
                ? vatglassesData.resolve(controllers, ZonedDateTime.now(ZoneOffset.UTC))
                : Collections.emptyList();

        // The sectors replace the FIR, UIR or approach circle of the controllers which own any
        final Set<VgPosition> owners = Collections.newSetFromMap(new IdentityHashMap<>());

        for (final ActiveSubSector activeSubSector : activeSubSectors) {
            if (owners.add(activeSubSector.getOwner())) {
                for (final Controller controller : positions.getOrDefault(activeSubSector.getOwner(), Collections.emptyList())) {
                    controller.setAirspaceBoundaryMatched(true);
                }
            }
        }

        final int level = vatglassesState.getLevel();
        final List<ActiveSubSector> visible = new ArrayList<>();

        for (final ActiveSubSector activeSubSector : activeSubSectors) {
            if (activeSubSector.getSubSector().containsLevel(level)) {
                visible.add(activeSubSector);
            }
        }

        visibleSubSectors = visible.toArray(NO_SUB_SECTORS);
    }

    private static void setIfChanged(final ObservableList<Controller> target, final List<Controller> controllers) {
        if (!target.equals(controllers)) {
            target.setAll(controllers);
        }
    }

    /**
     * @return the boundaries of the online approach and departure controllers
     */
    public List<TraconBoundary> activeTracons() {
        return activeTracons;
    }

    /**
     * @return the controlled sub sectors at the current level, grouped by colour
     */
    public ActiveSubSector[] visibleSubSectors() {
        return visibleSubSectors;
    }

    /**
     * @return the boundaries a station with the passed callsign would activate, none while the boundaries are disabled
     */
    public List<TraconBoundary> matchTracons(final String callsign) {
        return traconEnabled.get() ? traconRepository.match(callsign) : Collections.emptyList();
    }

    /**
     * @return the positions the station would work, none while VATGlasses is disabled
     */
    public List<VgPosition> matchVatglassesPositions(final AtcStation station) {
        return vatglassesState.enabledProperty().get() ? vatglassesData.positionsOf(station) : Collections.emptyList();
    }

    /**
     * Determines the sub sectors at the current level which the passed stations would control if they were online.
     * Sub sectors which are controlled by an online position are left out. Relies on the results of the last
     * {@link #refresh()}.
     *
     * @param colour the colour of all returned sub sectors
     */
    public ActiveSubSector[] bookedSubSectors(final Collection<? extends AtcStation> stations, final Color colour) {
        if (!vatglassesState.enabledProperty().get() || stations.isEmpty()) {
            return NO_SUB_SECTORS;
        }

        final Set<VgSubSector> controlled = Collections.newSetFromMap(new IdentityHashMap<>());

        for (final ActiveSubSector activeSubSector : activeSubSectors) {
            controlled.add(activeSubSector.getSubSector());
        }

        final int level = vatglassesState.getLevel();
        final List<ActiveSubSector> result = new ArrayList<>();

        for (final ActiveSubSector booked : vatglassesData.resolve(stations, ZonedDateTime.now(ZoneOffset.UTC), colour)) {
            if (booked.getSubSector().containsLevel(level) && !controlled.contains(booked.getSubSector())) {
                result.add(booked);
            }
        }

        return result.toArray(NO_SUB_SECTORS);
    }

    private void loadRequired() {
        if (traconEnabled.get() && !traconRequested) {
            traconRequested = true;
            executor.submit(this::loadTracons);
        }

        if (vatglassesState.enabledProperty().get() && !vatglassesRequested) {
            vatglassesRequested = true;
            executor.submit(this::loadVatglasses);
        }
    }

    private void loadTracons() {
        final Optional<Path> path = fetcher.fetch(TRACON_FILE_NAME, TRACON_URL, CHECK_INTERVAL);

        if (path.isEmpty()) {
            log.warn("No TRACON boundaries available");
            return;
        }

        try (final Reader reader = Files.newBufferedReader(path.get(), StandardCharsets.UTF_8)) {
            final TraconRepository result = TraconRepository.parse(reader);

            Platform.runLater(() -> {
                traconRepository = result;
                refresh();
                onChange.run();
            });
        } catch (final IOException | RuntimeException e) {
            log.error("Failed to load TRACON boundaries", e);
        }
    }

    private void loadVatglasses() {
        final Optional<Path> path = fetcher.fetch(VATGLASSES_FILE_NAME, VATGLASSES_URL, CHECK_INTERVAL);

        if (path.isEmpty()) {
            log.warn("No VATGlasses data available");
            return;
        }

        try (final InputStream in = Files.newInputStream(path.get())) {
            final VatglassesData result = VatglassesData.parseZip(in);

            Platform.runLater(() -> {
                vatglassesData = result;
                refresh();
                onChange.run();
            });
        } catch (final IOException | RuntimeException e) {
            log.error("Failed to load VATGlasses data", e);
        }
    }
}
