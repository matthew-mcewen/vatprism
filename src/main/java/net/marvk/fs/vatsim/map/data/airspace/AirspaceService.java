package net.marvk.fs.vatsim.map.data.airspace;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import lombok.extern.log4j.Log4j2;
import net.marvk.fs.vatsim.map.data.ClientRepository;
import net.marvk.fs.vatsim.map.data.Controller;
import net.marvk.fs.vatsim.map.data.Preferences;

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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
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
    public static final String TRACON_ENABLED_KEY = "tracon.enabled";

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
            final VatglassesState vatglassesState,
            final Preferences preferences
    ) {
        this.fetcher = fetcher;
        this.clientRepository = clientRepository;
        this.vatglassesState = vatglassesState;
        this.traconEnabled = preferences.booleanProperty(TRACON_ENABLED_KEY, true);
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
        final List<Controller> controllers = clientRepository.controllers();

        final boolean tracon = traconEnabled.get();
        final Set<TraconBoundary> tracons = new LinkedHashSet<>();

        for (final Controller controller : controllers) {
            final List<TraconBoundary> matches = tracon
                    ? traconRepository.match(controller.getCallsign())
                    : Collections.emptyList();

            tracons.addAll(matches);
            controller.setAirspaceBoundaryMatched(!matches.isEmpty());
        }

        activeTracons = new ArrayList<>(tracons);

        activeSubSectors = vatglassesState.enabledProperty().get()
                ? vatglassesData.resolve(controllers, ZonedDateTime.now(ZoneOffset.UTC))
                : Collections.emptyList();

        vatglassesState.update(clientRepository.pilots());

        final int level = vatglassesState.getLevel();
        final List<ActiveSubSector> visible = new ArrayList<>();

        for (final ActiveSubSector activeSubSector : activeSubSectors) {
            if (activeSubSector.getSubSector().containsLevel(level)) {
                visible.add(activeSubSector);
            }
        }

        visibleSubSectors = visible.toArray(NO_SUB_SECTORS);
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
