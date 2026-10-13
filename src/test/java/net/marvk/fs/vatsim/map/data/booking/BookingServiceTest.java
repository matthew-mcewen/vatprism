package net.marvk.fs.vatsim.map.data.booking;

import net.marvk.fs.vatsim.map.data.CallsignParser;
import net.marvk.fs.vatsim.map.data.ControllerType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

class BookingServiceTest {
    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 10, 13, 12, 0, 0, 0, ZoneOffset.UTC);

    private static Booking booking(final int id, final Duration startsIn, final Duration length) {
        final ZonedDateTime start = NOW.plus(startsIn);

        return new Booking(id, "1", BookingType.BOOKING, "EDDF_TWR", start, start.plus(length), null, null);
    }

    private static Booking booking(final ControllerType type) {
        final Booking result = booking(type.ordinal(), Duration.ofHours(1), Duration.ofHours(1));
        result.setLocation(new CallsignParser.Result(type, null, null, null, null));
        return result;
    }

    private static List<Integer> visibleIds(final int horizonHours, final Booking... bookings) {
        return BookingService.visible(List.of(bookings), NOW, horizonHours).stream().map(Booking::getId).collect(Collectors.toList());
    }

    private static List<ControllerType> offlineTypes(final Set<ControllerType> online, final ControllerType... booked) {
        return BookingService.offlineTypes(List.of(booked).stream().map(BookingServiceTest::booking).collect(Collectors.toList()), online);
    }

    @Test
    void showsActiveAndUpcomingBookings() {
        Assertions.assertEquals(List.of(1, 2), visibleIds(
                3,
                booking(1, Duration.ofHours(-1), Duration.ofHours(3)),
                booking(2, Duration.ofHours(2), Duration.ofHours(3))
        ));
    }

    @Test
    void hidesEndedBookings() {
        Assertions.assertEquals(List.of(), visibleIds(
                3,
                booking(1, Duration.ofHours(-4), Duration.ofHours(3)),
                booking(2, Duration.ofHours(-3), Duration.ofHours(3))
        ));
    }

    @Test
    void hidesBookingsBeyondTheHorizon() {
        final Booking[] bookings = {
                booking(1, Duration.ofHours(2).plusMinutes(59), Duration.ofHours(1)),
                booking(2, Duration.ofHours(3), Duration.ofHours(1)),
                booking(3, Duration.ofDays(6), Duration.ofHours(1))
        };

        Assertions.assertEquals(List.of(1), visibleIds(3, bookings));
        Assertions.assertEquals(List.of(1, 2), visibleIds(4, bookings));
        Assertions.assertEquals(List.of(1, 2, 3), visibleIds(BookingService.MAX_HORIZON_HOURS, bookings));
    }

    @Test
    void clampsTheHorizon() {
        final Booking soon = booking(1, Duration.ofMinutes(30), Duration.ofHours(1));
        final Booking late = booking(2, Duration.ofDays(8), Duration.ofHours(1));

        Assertions.assertEquals(List.of(1), visibleIds(0, soon, late));
        Assertions.assertEquals(List.of(1), visibleIds(-5, soon, late));
        Assertions.assertEquals(List.of(1), visibleIds(100_000, soon, late));
    }

    @Test
    void bookedTypesWhichAreOnlineAreLeftOut() {
        Assertions.assertEquals(
                List.of(ControllerType.DEL, ControllerType.TWR),
                offlineTypes(EnumSet.of(ControllerType.GND), ControllerType.TWR, ControllerType.GND, ControllerType.DEL, ControllerType.TWR)
        );
        Assertions.assertEquals(List.of(), offlineTypes(EnumSet.of(ControllerType.TWR), ControllerType.TWR));
    }

    @Test
    void approachAndDepartureAreTheSamePosition() {
        Assertions.assertEquals(List.of(ControllerType.APP), offlineTypes(EnumSet.noneOf(ControllerType.class), ControllerType.APP));
        Assertions.assertEquals(List.of(), offlineTypes(EnumSet.of(ControllerType.DEP), ControllerType.APP));
        Assertions.assertEquals(List.of(), offlineTypes(EnumSet.of(ControllerType.APP), ControllerType.DEP, ControllerType.APP));
    }

    @Test
    void positionsWithoutAnAirportBadgeAreLeftOut() {
        Assertions.assertEquals(
                List.of(),
                offlineTypes(EnumSet.noneOf(ControllerType.class), ControllerType.CTR, ControllerType.FSS, ControllerType.OBS, ControllerType.NONE)
        );
    }
}
