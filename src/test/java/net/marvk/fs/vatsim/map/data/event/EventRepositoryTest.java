package net.marvk.fs.vatsim.map.data.event;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

class EventRepositoryTest {
    private static final String JSON = """
            {
              "data": [
                {
                  "id": 2,
                  "type": "Controller Examination",
                  "name": "[EDDF_TWR] | S2",
                  "link": "https://my.vatsim.net/events/exam",
                  "organisers": [],
                  "airports": [{"icao": "EDDF"}],
                  "routes": [],
                  "start_time": "2026-10-14T18:00:00.000000Z",
                  "end_time": "2026-10-14T20:30:00.000000Z",
                  "short_description": "Exam",
                  "description": "Exam",
                  "banner": "https://example.com/banner.png"
                },
                {
                  "id": 1,
                  "type": "Event",
                  "name": " Tuesdays Over Iraq ",
                  "link": "https://my.vatsim.net/events/tuesdays-over-iraq",
                  "airports": [{"icao": "orbi"}, {"icao": "ORER"}, {"icao": "ORBI"}],
                  "start_time": "2026-10-13T15:00:00.000000Z",
                  "end_time": "2026-10-13T18:00:00.000000Z",
                  "short_description": "**Iraq** awaits!<br>Join <b>us</b>",
                  "banner": null
                },
                {
                  "id": 3,
                  "type": "VASOPS Event",
                  "name": null,
                  "link": null,
                  "airports": null,
                  "start_time": "2026-10-15T00:00:00.000000Z",
                  "end_time": "2026-10-20T04:05:00.000000Z",
                  "short_description": null
                },
                {"id": 4, "type": "Event", "name": "No times"},
                {
                  "id": 5,
                  "type": "Event",
                  "name": "Ends before it starts",
                  "start_time": "2026-10-13T18:00:00.000000Z",
                  "end_time": "2026-10-13T15:00:00.000000Z"
                }
              ]
            }
            """;

    private final List<Event> events = EventRepository.parse(new StringReader(JSON));

    @Test
    void skipsMalformedEventsAndSortsByStart() {
        Assertions.assertEquals(List.of(1, 2, 3), events.stream().map(Event::getId).collect(Collectors.toList()));
    }

    @Test
    void parsesFields() {
        final Event event = events.get(0);

        Assertions.assertEquals(EventType.EVENT, event.getType());
        Assertions.assertEquals("Tuesdays Over Iraq", event.getName());
        Assertions.assertEquals("https://my.vatsim.net/events/tuesdays-over-iraq", event.getLink());
        Assertions.assertEquals(ZonedDateTime.of(2026, 10, 13, 15, 0, 0, 0, ZoneOffset.UTC), event.getStart());
        Assertions.assertEquals(ZonedDateTime.of(2026, 10, 13, 18, 0, 0, 0, ZoneOffset.UTC), event.getEnd());
        Assertions.assertEquals(Duration.ofHours(3), event.getDuration());
        Assertions.assertNull(event.getBanner());
    }

    @Test
    void parsesTypes() {
        Assertions.assertEquals(EventType.CONTROLLER_EXAMINATION, events.get(1).getType());
        Assertions.assertEquals(EventType.VASOPS, events.get(2).getType());
        Assertions.assertEquals(EventType.EVENT, EventType.ofApiName("Something new"));
        Assertions.assertEquals(EventType.EVENT, EventType.ofApiName(null));
    }

    @Test
    void normalizesAirports() {
        Assertions.assertEquals(List.of("ORBI", "ORER"), events.get(0).getAirportIcaos());
        Assertions.assertEquals(List.of(), events.get(2).getAirportIcaos());
    }

    @Test
    void toleratesMissingFields() {
        final Event event = events.get(2);

        Assertions.assertEquals("Event 3", event.getName());
        Assertions.assertNull(event.getLink());
        Assertions.assertEquals("", event.getShortDescription());
    }

    @Test
    void descriptionsArePlainText() {
        Assertions.assertEquals("Iraq awaits!\nJoin us", events.get(0).getShortDescription());
    }

    @Test
    void formatsDurations() {
        Assertions.assertEquals("03:00", Event.formatDuration(events.get(0).getDuration()));
        Assertions.assertEquals("02:30", Event.formatDuration(events.get(1).getDuration()));
        Assertions.assertEquals("124:05", Event.formatDuration(events.get(2).getDuration()));
        Assertions.assertEquals("00:00", Event.formatDuration(Duration.ofMinutes(-5)));
    }
}
