package net.marvk.fs.vatsim.map.data.event;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

class EventServiceTest {
    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 10, 13, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final Set<EventType> DEFAULT_TYPES = EnumSet.of(EventType.EVENT, EventType.CONTROLLER_EXAMINATION);

    private static Event event(final int id, final EventType type, final Duration startsIn, final Duration length) {
        final ZonedDateTime start = NOW.plus(startsIn);

        return new Event(id, type, "Event " + id, null, start, start.plus(length), "", null, List.of());
    }

    private static List<Integer> visibleIds(final int horizonHours, final Set<EventType> types, final Event... events) {
        return EventService.visible(List.of(events), NOW, horizonHours, types).stream().map(Event::getId).collect(Collectors.toList());
    }

    @Test
    void showsRunningAndUpcomingEvents() {
        Assertions.assertEquals(List.of(1, 2), visibleIds(
                48,
                DEFAULT_TYPES,
                event(1, EventType.EVENT, Duration.ofHours(-1), Duration.ofHours(3)),
                event(2, EventType.EVENT, Duration.ofHours(47), Duration.ofHours(3))
        ));
    }

    @Test
    void hidesEndedEvents() {
        Assertions.assertEquals(List.of(), visibleIds(
                48,
                DEFAULT_TYPES,
                event(1, EventType.EVENT, Duration.ofHours(-4), Duration.ofHours(3)),
                event(2, EventType.EVENT, Duration.ofHours(-3), Duration.ofHours(3))
        ));
    }

    @Test
    void hidesEventsBeyondTheHorizon() {
        final Event[] events = {
                event(1, EventType.EVENT, Duration.ofHours(47).plusMinutes(59), Duration.ofHours(3)),
                event(2, EventType.EVENT, Duration.ofHours(48), Duration.ofHours(3)),
                event(3, EventType.EVENT, Duration.ofHours(72), Duration.ofHours(3))
        };

        Assertions.assertEquals(List.of(1), visibleIds(48, DEFAULT_TYPES, events));
        Assertions.assertEquals(List.of(1, 2, 3), visibleIds(96, DEFAULT_TYPES, events));
        Assertions.assertEquals(List.of(), visibleIds(24, DEFAULT_TYPES, events));
    }

    @Test
    void filtersByType() {
        final Event[] events = {
                event(1, EventType.EVENT, Duration.ofHours(1), Duration.ofHours(3)),
                event(2, EventType.CONTROLLER_EXAMINATION, Duration.ofHours(2), Duration.ofHours(3)),
                event(3, EventType.VASOPS, Duration.ofHours(3), Duration.ofHours(3))
        };

        Assertions.assertEquals(List.of(1, 2), visibleIds(48, DEFAULT_TYPES, events));
        Assertions.assertEquals(List.of(3), visibleIds(48, EnumSet.of(EventType.VASOPS), events));
        Assertions.assertEquals(List.of(), visibleIds(48, EnumSet.noneOf(EventType.class), events));
    }

    @Test
    void clampsTheHorizon() {
        final Event event = event(1, EventType.EVENT, Duration.ofMinutes(30), Duration.ofHours(3));

        Assertions.assertEquals(List.of(1), visibleIds(0, DEFAULT_TYPES, event));
        Assertions.assertEquals(List.of(1), visibleIds(-5, DEFAULT_TYPES, event));
    }

    @Test
    void reminderDelay() {
        final ZonedDateTime start = NOW.plusHours(2);

        Assertions.assertEquals(Duration.ofMinutes(90), EventReminderService.delay(start, 30, NOW));
        Assertions.assertEquals(Duration.ofHours(2), EventReminderService.delay(start, 0, NOW));
        Assertions.assertEquals(Duration.ZERO, EventReminderService.delay(start, 120, NOW));
        Assertions.assertEquals(Duration.ZERO, EventReminderService.delay(start, 600, NOW));
        Assertions.assertEquals(Duration.ofHours(2), EventReminderService.delay(start, -10, NOW));
    }
}
