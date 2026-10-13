package net.marvk.fs.vatsim.map.data.event;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

class EventCalendarTest {
    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 10, 13, 12, 0, 0, 0, ZoneOffset.UTC);

    private static Event event(final String name, final String link, final String description, final List<String> airports) {
        return new Event(
                21184,
                EventType.EVENT,
                name,
                link,
                ZonedDateTime.of(2026, 10, 13, 15, 0, 0, 0, ZoneOffset.UTC),
                ZonedDateTime.of(2026, 10, 14, 1, 30, 0, 0, ZoneOffset.UTC),
                description,
                null,
                airports
        );
    }

    @Test
    void writesAnEvent() {
        final String ics = EventCalendar.ics(
                event("Tuesdays Over Iraq", "https://my.vatsim.net/events/iraq", "Fly, with us; tonight\nSee you", List.of("ORBI", "ORER")),
                NOW
        );

        Assertions.assertEquals(String.join(
                "\r\n",
                "BEGIN:VCALENDAR",
                "VERSION:2.0",
                "PRODID:-//VATprism//Events//EN",
                "CALSCALE:GREGORIAN",
                "BEGIN:VEVENT",
                "UID:vatsim-event-21184@vatprism",
                "DTSTAMP:20261013T120000Z",
                "DTSTART:20261013T150000Z",
                "DTEND:20261014T013000Z",
                "SUMMARY:Tuesdays Over Iraq",
                "LOCATION:ORBI\\, ORER",
                "DESCRIPTION:Fly\\, with us\\; tonight\\nSee you\\n\\nhttps://my.vatsim.net/event",
                " s/iraq",
                "URL:https://my.vatsim.net/events/iraq",
                "END:VEVENT",
                "END:VCALENDAR",
                ""
        ), ics);
    }

    @Test
    void omitsMissingFields() {
        final String ics = EventCalendar.ics(event("Exam", null, "", List.of()), NOW);

        Assertions.assertFalse(ics.contains("LOCATION"));
        Assertions.assertFalse(ics.contains("DESCRIPTION"));
        Assertions.assertFalse(ics.contains("URL"));
    }

    @Test
    void foldsLongLines() {
        final String ics = EventCalendar.ics(event("ä".repeat(100), null, "", List.of()), NOW);

        for (final String line : ics.split("\r\n")) {
            Assertions.assertTrue(line.getBytes(StandardCharsets.UTF_8).length <= 75, line);
        }

        Assertions.assertTrue(ics.replace("\r\n ", "").contains("SUMMARY:" + "ä".repeat(100)));
    }
}
