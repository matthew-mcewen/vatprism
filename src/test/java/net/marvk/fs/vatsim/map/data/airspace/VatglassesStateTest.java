package net.marvk.fs.vatsim.map.data.airspace;

import javafx.beans.property.*;
import javafx.beans.value.ObservableValue;
import javafx.scene.paint.Color;
import net.marvk.fs.vatsim.map.data.Preferences;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

class VatglassesStateTest {
    private final InMemoryPreferences preferences = new InMemoryPreferences();

    @Test
    void traconBoundariesAreTheDefault() {
        final VatglassesState state = new VatglassesState(preferences);

        Assertions.assertFalse(state.enabledProperty().get());
        Assertions.assertTrue(state.traconEnabledProperty().get());
    }

    @Test
    void enablingOneDisablesTheOther() {
        final VatglassesState state = new VatglassesState(preferences);

        state.enabledProperty().set(true);
        Assertions.assertTrue(state.enabledProperty().get());
        Assertions.assertFalse(state.traconEnabledProperty().get());

        state.traconEnabledProperty().set(true);
        Assertions.assertFalse(state.enabledProperty().get());
        Assertions.assertTrue(state.traconEnabledProperty().get());
    }

    @Test
    void traconBoundariesReturnWhenVatglassesIsDisabled() {
        final VatglassesState state = new VatglassesState(preferences);

        state.enabledProperty().set(true);
        Assertions.assertFalse(state.traconEnabledProperty().get());

        state.enabledProperty().set(false);
        Assertions.assertTrue(state.traconEnabledProperty().get());
    }

    @Test
    void traconBoundariesStayDisabledIfTheUserDisabledThem() {
        final VatglassesState state = new VatglassesState(preferences);

        state.traconEnabledProperty().set(false);
        state.enabledProperty().set(true);
        state.enabledProperty().set(false);

        Assertions.assertFalse(state.traconEnabledProperty().get());
        Assertions.assertFalse(state.enabledProperty().get());
    }

    @Test
    void enablingTraconBoundariesWhileVatglassesIsEnabledRestoresThePreference() {
        final VatglassesState state = new VatglassesState(preferences);

        state.traconEnabledProperty().set(false);
        state.enabledProperty().set(true);
        state.traconEnabledProperty().set(true);

        Assertions.assertFalse(state.enabledProperty().get());
        Assertions.assertTrue(state.traconEnabledProperty().get());

        state.enabledProperty().set(true);
        state.enabledProperty().set(false);

        Assertions.assertTrue(state.traconEnabledProperty().get());
    }

    @Test
    void traconPreferenceSurvivesARestart() {
        final VatglassesState first = new VatglassesState(preferences);
        first.enabledProperty().set(true);

        final VatglassesState second = new VatglassesState(preferences);
        second.enabledProperty().set(false);

        Assertions.assertTrue(second.traconEnabledProperty().get());
    }

    @Test
    void autoSwitchFollowsTheOnlineStateOfTheUser() {
        final VatglassesState state = new VatglassesState(preferences);
        state.cidProperty().set("1234567");
        state.autoSwitchProperty().set(true);
        state.automaticProperty().set(false);

        state.applyAutoSwitch(true);
        Assertions.assertTrue(state.enabledProperty().get());
        Assertions.assertTrue(state.automaticProperty().get());
        Assertions.assertFalse(state.traconEnabledProperty().get());

        state.applyAutoSwitch(false);
        Assertions.assertFalse(state.enabledProperty().get());
        Assertions.assertTrue(state.traconEnabledProperty().get());
    }

    @Test
    void autoSwitchDisablesTheSectorsWhileTheUserIsOffline() {
        final VatglassesState state = new VatglassesState(preferences);
        state.cidProperty().set("1234567");
        state.enabledProperty().set(true);
        state.autoSwitchProperty().set(true);

        state.applyAutoSwitch(false);

        Assertions.assertFalse(state.enabledProperty().get());
    }

    @Test
    void autoSwitchKeepsManualTogglesUntilTheOnlineStateChanges() {
        final VatglassesState state = new VatglassesState(preferences);
        state.cidProperty().set("1234567");
        state.autoSwitchProperty().set(true);

        state.applyAutoSwitch(true);
        state.enabledProperty().set(false);
        state.applyAutoSwitch(true);
        Assertions.assertFalse(state.enabledProperty().get());

        state.applyAutoSwitch(false);
        state.enabledProperty().set(true);
        state.applyAutoSwitch(false);
        Assertions.assertTrue(state.enabledProperty().get());

        state.applyAutoSwitch(true);
        Assertions.assertTrue(state.enabledProperty().get());
    }

    @Test
    void autoSwitchRequiresTheSettingAndACid() {
        final VatglassesState state = new VatglassesState(preferences);

        state.applyAutoSwitch(true);
        Assertions.assertFalse(state.enabledProperty().get());

        state.autoSwitchProperty().set(true);
        state.applyAutoSwitch(true);
        Assertions.assertFalse(state.enabledProperty().get());

        state.cidProperty().set("abc");
        state.applyAutoSwitch(true);
        Assertions.assertFalse(state.enabledProperty().get());

        state.cidProperty().set("1234567");
        state.applyAutoSwitch(true);
        Assertions.assertTrue(state.enabledProperty().get());
    }

    @Test
    void bothCanBeDisabled() {
        final VatglassesState state = new VatglassesState(preferences);

        state.traconEnabledProperty().set(false);

        Assertions.assertFalse(state.enabledProperty().get());
        Assertions.assertFalse(state.traconEnabledProperty().get());
    }

    @Test
    void traconBoundariesWinIfBothWereSavedAsEnabled() {
        preferences.booleanProperty(VatglassesState.ENABLED_KEY, true);
        preferences.booleanProperty(VatglassesState.TRACON_ENABLED_KEY, true);

        final VatglassesState state = new VatglassesState(preferences);

        Assertions.assertFalse(state.enabledProperty().get());
        Assertions.assertTrue(state.traconEnabledProperty().get());
    }

    @Test
    void keepsSavedVatglassesChoice() {
        preferences.booleanProperty(VatglassesState.ENABLED_KEY, true);
        preferences.booleanProperty(VatglassesState.TRACON_ENABLED_KEY, false);

        final VatglassesState state = new VatglassesState(preferences);

        Assertions.assertTrue(state.enabledProperty().get());
        Assertions.assertFalse(state.traconEnabledProperty().get());
    }

    @Test
    void usesTheManualLevelWithoutPilots() {
        final VatglassesState state = new VatglassesState(preferences);

        state.manualLevelProperty().set(350);
        state.update(Collections.emptyList());

        Assertions.assertEquals(350, state.getLevel());
        Assertions.assertEquals(VatglassesState.Source.MANUAL, state.getSource());
        Assertions.assertNull(state.getTrackedPilot());
    }

    @Test
    void convertsAltitudesToLevels() {
        Assertions.assertEquals(0, VatglassesState.level(-150));
        Assertions.assertEquals(0, VatglassesState.level(99));
        Assertions.assertEquals(123, VatglassesState.level(12345));
        Assertions.assertEquals(350, VatglassesState.level(35000));
    }

    @Test
    void parsesCids() {
        Assertions.assertEquals(1234567, VatglassesState.parseCid(" 1234567 "));
        Assertions.assertEquals(-1, VatglassesState.parseCid(""));
        Assertions.assertEquals(-1, VatglassesState.parseCid(null));
        Assertions.assertEquals(-1, VatglassesState.parseCid("abc"));
    }

    private static class InMemoryPreferences implements Preferences {
        private final Map<String, ObservableValue<?>> values = new HashMap<>();

        @SuppressWarnings("unchecked")
        private <T> T property(final String key, final Supplier<T> supplier) {
            return (T) values.computeIfAbsent(key, e -> (ObservableValue<?>) supplier.get());
        }

        @Override
        public BooleanProperty booleanProperty(final String key, final boolean initialValue) {
            return property(key, () -> new SimpleBooleanProperty(initialValue));
        }

        @Override
        public StringProperty stringProperty(final String key, final String defaultValue) {
            return property(key, () -> new SimpleStringProperty(defaultValue));
        }

        @Override
        public ObjectProperty<Color> colorProperty(final String key, final Color initialValue) {
            return property(key, () -> new SimpleObjectProperty<>(initialValue));
        }

        @Override
        public IntegerProperty integerProperty(final String key, final int defaultValue) {
            return property(key, () -> new SimpleIntegerProperty(defaultValue));
        }

        @Override
        public DoubleProperty doubleProperty(final String key, final double initialValue) {
            return property(key, () -> new SimpleDoubleProperty(initialValue));
        }

        @Override
        public Map<String, ObservableValue<?>> values() {
            return values;
        }
    }
}
