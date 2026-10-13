package net.marvk.fs.vatsim.map.data.airspace;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.paint.Color;
import net.marvk.fs.vatsim.map.data.Controller;
import net.marvk.fs.vatsim.map.data.Data;
import net.marvk.fs.vatsim.map.data.DataVisitor;

import java.util.List;

/**
 * A controller position from the VATGlasses data.
 */
public class VgPosition implements Data {
    static final int ANY_FREQUENCY = 0;

    private final String key;
    private final String callsign;
    private final List<String> prefixes;
    private final String type;
    private final int frequencyKhz;
    private final List<ColourRule> colours;

    private final ObservableList<Controller> controllers = FXCollections.observableArrayList();

    VgPosition(final String key, final String callsign, final List<String> prefixes, final String type, final int frequencyKhz, final List<ColourRule> colours) {
        this.key = key;
        this.callsign = callsign;
        this.prefixes = List.copyOf(prefixes);
        this.type = type;
        this.frequencyKhz = frequencyKhz;
        this.colours = List.copyOf(colours);
    }

    /**
     * @return globally unique key in the format {@code dataset/position}
     */
    public String getKey() {
        return key;
    }

    /**
     * @return the voice callsign, e.g. "London Control"
     */
    public String getCallsign() {
        return callsign;
    }

    /**
     * @return the online controllers working this position
     */
    public ObservableList<Controller> getControllers() {
        return controllers;
    }

    /**
     * @return the logon callsign of the controller working this position, the voice callsign if nobody is
     */
    public String getDisplayName() {
        if (!controllers.isEmpty()) {
            return controllers.get(0).getCallsign();
        }

        return callsign == null ? key : callsign;
    }

    @Override
    public <R> R visit(final DataVisitor<R> visitor) {
        return visitor.visit(this);
    }

    List<String> getPrefixes() {
        return prefixes;
    }

    String getType() {
        return type;
    }

    int getFrequencyKhz() {
        return frequencyKhz;
    }

    List<ColourRule> getColours() {
        return colours;
    }

    @Override
    public String toString() {
        return key;
    }

    static class ColourRule {
        private final Color colour;
        private final List<String> requiredOnlineKeys;

        ColourRule(final Color colour, final List<String> requiredOnlineKeys) {
            this.colour = colour;
            this.requiredOnlineKeys = List.copyOf(requiredOnlineKeys);
        }

        Color getColour() {
            return colour;
        }

        List<String> getRequiredOnlineKeys() {
            return requiredOnlineKeys;
        }
    }
}
