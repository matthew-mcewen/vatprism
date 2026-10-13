package net.marvk.fs.vatsim.map.data.event;

public enum EventType {
    EVENT("Event", "Event"),
    CONTROLLER_EXAMINATION("Controller Examination", "CPT"),
    VASOPS("VASOPS Event", "VASOPS");

    private final String apiName;
    private final String shortName;

    EventType(final String apiName, final String shortName) {
        this.apiName = apiName;
        this.shortName = shortName;
    }

    public String getApiName() {
        return apiName;
    }

    public String getShortName() {
        return shortName;
    }

    /**
     * @return the type with the name used by the VATSIM Events API, unknown types are treated as regular events
     */
    public static EventType ofApiName(final String apiName) {
        for (final EventType type : values()) {
            if (type.apiName.equalsIgnoreCase(apiName)) {
                return type;
            }
        }

        return EVENT;
    }
}
