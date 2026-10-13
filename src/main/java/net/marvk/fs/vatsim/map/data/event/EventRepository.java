package net.marvk.fs.vatsim.map.data.event;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.log4j.Log4j2;

import java.io.Reader;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Parses the response of the VATSIM Events API.
 */
@Log4j2
public final class EventRepository {
    private EventRepository() {
        throw new AssertionError("No instances of utility class " + EventRepository.class);
    }

    /**
     * @return the events sorted by their start time, malformed events are skipped
     */
    public static List<Event> parse(final Reader json) {
        final JsonObject root = JsonParser.parseReader(json).getAsJsonObject();
        final JsonArray data = root.getAsJsonArray("data");

        final List<Event> result = new ArrayList<>(data.size());

        for (final JsonElement event : data) {
            try {
                result.add(parseEvent(event.getAsJsonObject()));
            } catch (final RuntimeException e) {
                log.warn("Skipping malformed event: %s".formatted(e.getMessage()));
            }
        }

        result.sort(Comparator.comparing(Event::getStart));

        log.info("Loaded %d events".formatted(result.size()));

        return result;
    }

    private static Event parseEvent(final JsonObject event) {
        final int id = event.get("id").getAsInt();
        final ZonedDateTime start = time(event, "start_time");
        final ZonedDateTime end = time(event, "end_time");

        if (end.isBefore(start)) {
            throw new IllegalArgumentException("Event %d ends before it starts".formatted(id));
        }

        final List<String> airports = new ArrayList<>();

        if (isPresent(event, "airports")) {
            for (final JsonElement airport : event.getAsJsonArray("airports")) {
                final String icao = string(airport.getAsJsonObject(), "icao");

                if (icao != null && !icao.isBlank() && !airports.contains(icao.strip().toUpperCase(Locale.ROOT))) {
                    airports.add(icao.strip().toUpperCase(Locale.ROOT));
                }
            }
        }

        final String name = string(event, "name");

        return new Event(
                id,
                EventType.ofApiName(string(event, "type")),
                name == null || name.isBlank() ? "Event " + id : name.strip(),
                blankToNull(string(event, "link")),
                start,
                end,
                plainText(string(event, "short_description")),
                blankToNull(string(event, "banner")),
                airports
        );
    }

    private static ZonedDateTime time(final JsonObject object, final String key) {
        return ZonedDateTime.parse(object.get(key).getAsString()).withZoneSameInstant(ZoneOffset.UTC);
    }

    private static String string(final JsonObject object, final String key) {
        return isPresent(object, key) ? object.get(key).getAsString() : null;
    }

    private static boolean isPresent(final JsonObject object, final String key) {
        return object.has(key) && !object.get(key).isJsonNull();
    }

    private static String blankToNull(final String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    // Descriptions are Markdown which may contain HTML, they are displayed as plain text
    static String plainText(final String s) {
        if (s == null) {
            return "";
        }

        return s.replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("<[^>]+>", "")
                .replace("**", "")
                .replace("\r\n", "\n")
                .strip();
    }
}
