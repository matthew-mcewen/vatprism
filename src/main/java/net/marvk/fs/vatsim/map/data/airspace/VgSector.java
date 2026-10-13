package net.marvk.fs.vatsim.map.data.airspace;

import javafx.scene.paint.Color;

import java.util.List;

/**
 * An ATC sector from the VATGlasses data, made up of {@link VgSubSector}s which each cover one block of altitudes.
 */
public class VgSector {
    private final String id;
    private final Color groupColour;
    private final List<String> ownerKeys;
    private final List<RunwayCondition> runways;
    private final List<TimeWindow> timeWindows;

    private List<VgSubSector> subSectors = List.of();

    VgSector(final String id, final Color groupColour, final List<String> ownerKeys, final List<RunwayCondition> runways, final List<TimeWindow> timeWindows) {
        this.id = id;
        this.groupColour = groupColour;
        this.ownerKeys = List.copyOf(ownerKeys);
        this.runways = List.copyOf(runways);
        this.timeWindows = List.copyOf(timeWindows);
    }

    public String getId() {
        return id;
    }

    public List<VgSubSector> getSubSectors() {
        return subSectors;
    }

    void setSubSectors(final List<VgSubSector> subSectors) {
        this.subSectors = List.copyOf(subSectors);
    }

    Color getGroupColour() {
        return groupColour;
    }

    /**
     * @return keys of the owning positions in descending priority
     */
    List<String> getOwnerKeys() {
        return ownerKeys;
    }

    List<RunwayCondition> getRunways() {
        return runways;
    }

    /**
     * @return the times this sector exists at, always if empty
     */
    List<TimeWindow> getTimeWindows() {
        return timeWindows;
    }

    static class RunwayCondition {
        private final String icao;
        private final List<String> configurations;

        RunwayCondition(final String icao, final List<String> configurations) {
            this.icao = icao;
            this.configurations = List.copyOf(configurations);
        }

        String getIcao() {
            return icao;
        }

        List<String> getConfigurations() {
            return configurations;
        }
    }

    /**
     * A weekly or daily recurring window in UTC.
     */
    static class TimeWindow {
        static final int MINUTES_PER_DAY = 24 * 60;
        static final int MINUTES_PER_WEEK = 7 * MINUTES_PER_DAY;

        private final boolean daily;
        private final int start;
        private final int end;

        /**
         * @param daily whether start and end are minutes of the day instead of minutes of the week
         */
        TimeWindow(final boolean daily, final int start, final int end) {
            this.daily = daily;
            this.start = start;
            this.end = end;
        }

        /**
         * @param minuteOfWeek minutes since monday 00:00 UTC
         */
        boolean contains(final int minuteOfWeek) {
            final int t = daily ? minuteOfWeek % MINUTES_PER_DAY : minuteOfWeek;

            if (start <= end) {
                return start <= t && t < end;
            }

            return t >= start || t < end;
        }
    }
}
