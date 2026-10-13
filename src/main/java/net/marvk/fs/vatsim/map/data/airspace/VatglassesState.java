package net.marvk.fs.vatsim.map.data.airspace;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import javafx.beans.property.*;
import net.marvk.fs.vatsim.map.data.Pilot;
import net.marvk.fs.vatsim.map.data.Preferences;

import java.util.Collection;

/**
 * Determines the flight level the VATGlasses sectors are displayed at. The level follows, in descending priority, a
 * pilot which was explicitly chosen, the pilot connected with the CID of the user or a manually set level.
 * <p>
 * VATGlasses sectors and TRACON boundaries serve the same purpose, so at most one of them is enabled at a time.
 * TRACON boundaries return when the sectors are disabled, unless the user disabled the boundaries themselves.
 */
@Singleton
public class VatglassesState {
    public static final String ENABLED_KEY = "vatglasses.enabled";
    public static final String TRACON_ENABLED_KEY = "tracon.enabled";
    public static final String TRACON_PREFERRED_KEY = "tracon.preferred";
    public static final String CID_KEY = "vatglasses.cid";
    public static final String AUTOMATIC_KEY = "vatglasses.automatic";
    public static final String AUTO_SWITCH_KEY = "vatglasses.auto_switch";
    public static final String MANUAL_LEVEL_KEY = "vatglasses.manual_level";

    public static final int MIN_LEVEL = 0;
    public static final int MAX_LEVEL = 660;

    private static final int NO_CID = -1;

    private final BooleanProperty enabled;
    private final BooleanProperty traconEnabled;
    // Whether the user wants TRACON boundaries while VATGlasses sectors are disabled
    private final BooleanProperty traconPreferred;

    // Set while the TRACON boundaries are changed because of the VATGlasses sectors, as opposed to by the user
    private boolean adjustingTracon = false;
    private final StringProperty cid;
    private final BooleanProperty automatic;
    private final BooleanProperty autoSwitch;
    private final IntegerProperty manualLevel;

    // Whether the pilot of the user was online when the sectors were last switched automatically, null if they never were
    private Boolean ownPilotOnline = null;

    private final ObjectProperty<Pilot> overridePilot = new SimpleObjectProperty<>();

    private final ReadOnlyIntegerWrapper level = new ReadOnlyIntegerWrapper();
    private final ReadOnlyObjectWrapper<Pilot> trackedPilot = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<Source> source = new ReadOnlyObjectWrapper<>(Source.MANUAL);

    @Inject
    public VatglassesState(final Preferences preferences) {
        this.enabled = preferences.booleanProperty(ENABLED_KEY, false);
        this.traconEnabled = preferences.booleanProperty(TRACON_ENABLED_KEY, true);

        this.traconPreferred = preferences.booleanProperty(TRACON_PREFERRED_KEY, true);

        if (enabled.get() && traconEnabled.get()) {
            enabled.set(false);
        }

        enabled.addListener((observable, oldValue, newValue) -> {
            adjustingTracon = true;
            try {
                traconEnabled.set(!newValue && traconPreferred.get());
            } finally {
                adjustingTracon = false;
            }
        });
        traconEnabled.addListener((observable, oldValue, newValue) -> {
            if (adjustingTracon) {
                return;
            }

            traconPreferred.set(newValue);

            if (newValue) {
                enabled.set(false);
            }
        });
        this.cid = preferences.stringProperty(CID_KEY, "");
        this.automatic = preferences.booleanProperty(AUTOMATIC_KEY, true);
        this.autoSwitch = preferences.booleanProperty(AUTO_SWITCH_KEY, false);
        this.manualLevel = preferences.integerProperty(MANUAL_LEVEL_KEY, 200);

        this.level.set(clampedManualLevel());
    }

    /**
     * Reevaluates the tracked pilot and level.
     *
     * @param pilots the pilots which are currently online
     */
    public void update(final Collection<Pilot> pilots) {
        final Pilot ownPilot = findByCid(pilots, parseCid(cid.get()));

        applyAutoSwitch(ownPilot != null);

        final Pilot override = overridePilot.get();

        if (override != null && !pilots.contains(override)) {
            overridePilot.set(null);
        }

        if (overridePilot.get() != null) {
            track(overridePilot.get(), Source.OVERRIDE);
            return;
        }

        final Pilot own = automatic.get() ? ownPilot : null;

        if (own != null) {
            track(own, Source.CID);
            return;
        }

        trackedPilot.set(null);
        source.set(Source.MANUAL);
        level.set(clampedManualLevel());
    }

    /**
     * Enables the sectors when the pilot of the user comes online and disables them when it goes offline. Only acts
     * when that state changes, so that the user can still toggle the sectors manually in between.
     */
    void applyAutoSwitch(final boolean online) {
        if (!autoSwitch.get() || !isValidCid(cid.get())) {
            ownPilotOnline = null;
            return;
        }

        if (ownPilotOnline != null && ownPilotOnline == online) {
            return;
        }

        ownPilotOnline = online;

        if (online) {
            overridePilot.set(null);
            automatic.set(true);
        }

        enabled.set(online);
    }

    public static boolean isValidCid(final String cid) {
        return parseCid(cid) != NO_CID;
    }

    private void track(final Pilot pilot, final Source source) {
        this.trackedPilot.set(pilot);
        this.source.set(source);
        this.level.set(level(pilot.getAltitude()));
    }

    static int level(final double altitudeFeet) {
        return Math.max(MIN_LEVEL, (int) Math.floor(altitudeFeet / 100.0));
    }

    private int clampedManualLevel() {
        return Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, manualLevel.get()));
    }

    private static Pilot findByCid(final Collection<Pilot> pilots, final int cid) {
        if (cid == NO_CID) {
            return null;
        }

        for (final Pilot pilot : pilots) {
            if (pilot.getCid() == cid) {
                return pilot;
            }
        }

        return null;
    }

    static int parseCid(final String cid) {
        if (cid == null || cid.isBlank()) {
            return NO_CID;
        }

        try {
            return Integer.parseInt(cid.strip());
        } catch (final NumberFormatException e) {
            return NO_CID;
        }
    }

    public BooleanProperty enabledProperty() {
        return enabled;
    }

    public BooleanProperty traconEnabledProperty() {
        return traconEnabled;
    }

    public StringProperty cidProperty() {
        return cid;
    }

    public BooleanProperty automaticProperty() {
        return automatic;
    }

    public BooleanProperty autoSwitchProperty() {
        return autoSwitch;
    }

    public IntegerProperty manualLevelProperty() {
        return manualLevel;
    }

    public Pilot getOverridePilot() {
        return overridePilot.get();
    }

    /**
     * @param pilot the pilot to follow regardless of the CID of the user, {@code null} to stop following
     */
    public void setOverridePilot(final Pilot pilot) {
        overridePilot.set(pilot);
    }

    public ObjectProperty<Pilot> overridePilotProperty() {
        return overridePilot;
    }

    public int getLevel() {
        return level.get();
    }

    public ReadOnlyIntegerProperty levelProperty() {
        return level.getReadOnlyProperty();
    }

    public Pilot getTrackedPilot() {
        return trackedPilot.get();
    }

    public ReadOnlyObjectProperty<Pilot> trackedPilotProperty() {
        return trackedPilot.getReadOnlyProperty();
    }

    public Source getSource() {
        return source.get();
    }

    public ReadOnlyObjectProperty<Source> sourceProperty() {
        return source.getReadOnlyProperty();
    }

    public enum Source {
        OVERRIDE, CID, MANUAL
    }
}
