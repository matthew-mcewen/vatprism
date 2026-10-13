package net.marvk.fs.vatsim.map.data.booking;

import java.util.Locale;

public enum BookingType {
    BOOKING("Booking"),
    TRAINING("Training"),
    EVENT("Event"),
    EXAM("Exam");

    private final String displayName;

    BookingType(final String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * @return the type with the passed name in the API, {@link #BOOKING} if it is unknown
     */
    public static BookingType ofApiName(final String name) {
        if (name == null) {
            return BOOKING;
        }

        return switch (name.strip().toLowerCase(Locale.ROOT)) {
            case "training" -> TRAINING;
            case "event" -> EVENT;
            case "exam" -> EXAM;
            default -> BOOKING;
        };
    }
}
