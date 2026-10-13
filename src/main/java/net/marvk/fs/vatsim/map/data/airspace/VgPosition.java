package net.marvk.fs.vatsim.map.data.airspace;

import javafx.scene.paint.Color;

import java.util.List;

/**
 * A controller position from the VATGlasses data.
 */
public class VgPosition {
    static final int ANY_FREQUENCY = 0;

    private final String key;
    private final String callsign;
    private final List<String> prefixes;
    private final String type;
    private final int frequencyKhz;
    private final List<ColourRule> colours;

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
