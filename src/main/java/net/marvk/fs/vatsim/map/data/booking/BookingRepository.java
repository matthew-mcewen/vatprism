package net.marvk.fs.vatsim.map.data.booking;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.log4j.Log4j2;

import java.io.Reader;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Parses the response of the VATSIM ATC Bookings API.
 */
@Log4j2
public final class BookingRepository {
    // The API returns UTC times without an offset
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    private BookingRepository() {
        throw new AssertionError("No instances of utility class " + BookingRepository.class);
    }

    /**
     * @return the bookings sorted by their start time, malformed bookings are skipped
     */
    public static List<Booking> parse(final Reader json) {
        final JsonArray data = JsonParser.parseReader(json).getAsJsonArray();

        final List<Booking> result = new ArrayList<>(data.size());

        for (final JsonElement booking : data) {
            try {
                result.add(parseBooking(booking.getAsJsonObject()));
            } catch (final RuntimeException e) {
                log.warn("Skipping malformed booking: %s".formatted(e.getMessage()));
            }
        }

        result.sort(Comparator.comparing(Booking::getStart));

        log.info("Loaded %d bookings".formatted(result.size()));

        return result;
    }

    private static Booking parseBooking(final JsonObject booking) {
        final int id = booking.get("id").getAsInt();
        final String callsign = string(booking, "callsign");

        if (callsign == null || callsign.isBlank()) {
            throw new IllegalArgumentException("Booking %d has no callsign".formatted(id));
        }

        final ZonedDateTime start = time(booking, "start");
        final ZonedDateTime end = time(booking, "end");

        if (end.isBefore(start)) {
            throw new IllegalArgumentException("Booking %d ends before it starts".formatted(id));
        }

        return new Booking(
                id,
                string(booking, "cid"),
                BookingType.ofApiName(string(booking, "type")),
                callsign.strip().toUpperCase(Locale.ROOT),
                start,
                end,
                string(booking, "division"),
                string(booking, "subdivision")
        );
    }

    private static ZonedDateTime time(final JsonObject object, final String key) {
        return LocalDateTime.parse(object.get(key).getAsString().strip(), TIME).atZone(ZoneOffset.UTC);
    }

    private static String string(final JsonObject object, final String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : null;
    }
}
