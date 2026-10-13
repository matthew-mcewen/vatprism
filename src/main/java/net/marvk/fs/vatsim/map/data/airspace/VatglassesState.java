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
 */
@Singleton
public class VatglassesState {
    public static final String ENABLED_KEY = "vatglasses.enabled";
    public static final String CID_KEY = "vatglasses.cid";
    public static final String AUTOMATIC_KEY = "vatglasses.automatic";
    public static final String MANUAL_LEVEL_KEY = "vatglasses.manual_level";

    public static final int MIN_LEVEL = 0;
    public static final int MAX_LEVEL = 660;

    private static final int NO_CID = -1;

    private final BooleanProperty enabled;
    private final StringProperty cid;
    private final BooleanProperty automatic;
    private final IntegerProperty manualLevel;

    private final ObjectProperty<Pilot> overridePilot = new SimpleObjectProperty<>();

    private final ReadOnlyIntegerWrapper level = new ReadOnlyIntegerWrapper();
    private final ReadOnlyObjectWrapper<Pilot> trackedPilot = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<Source> source = new ReadOnlyObjectWrapper<>(Source.MANUAL);

    @Inject
    public VatglassesState(final Preferences preferences) {
        this.enabled = preferences.booleanProperty(ENABLED_KEY, true);
        this.cid = preferences.stringProperty(CID_KEY, "");
        this.automatic = preferences.booleanProperty(AUTOMATIC_KEY, true);
        this.manualLevel = preferences.integerProperty(MANUAL_LEVEL_KEY, 200);

        this.level.set(clampedManualLevel());
    }

    /**
     * Reevaluates the tracked pilot and level.
     *
     * @param pilots the pilots which are currently online
     */
    public void update(final Collection<Pilot> pilots) {
        final Pilot override = overridePilot.get();

        if (override != null && !pilots.contains(override)) {
            overridePilot.set(null);
        }

        if (overridePilot.get() != null) {
            track(overridePilot.get(), Source.OVERRIDE);
            return;
        }

        final Pilot own = automatic.get() ? findByCid(pilots, parseCid(cid.get())) : null;

        if (own != null) {
            track(own, Source.CID);
            return;
        }

        trackedPilot.set(null);
        source.set(Source.MANUAL);
        level.set(clampedManualLevel());
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

    public StringProperty cidProperty() {
        return cid;
    }

    public BooleanProperty automaticProperty() {
        return automatic;
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
