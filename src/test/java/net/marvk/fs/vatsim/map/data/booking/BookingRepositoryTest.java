package net.marvk.fs.vatsim.map.data.booking;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

class BookingRepositoryTest {
    private static final String JSON = """
            [
              {
                "id": 2,
                "cid": 1469847,
                "type": "training",
                "callsign": "EDDF_N_APP",
                "start": "2026-10-13 07:00:00",
                "end": "2026-10-13 09:30:00",
                "division": "EUD",
                "subdivision": "GER"
              },
              {
                "id": 1,
                "cid": "1868127",
                "type": "booking",
                "callsign": " omdb_1_gnd ",
                "start": "2026-10-13 00:00:00",
                "end": "2026-10-13 12:00:34",
                "division": "MENA",
                "subdivision": null
              },
              {"id": 3, "cid": 1, "type": "something new", "callsign": "LON_CTR", "start": "2026-10-14 00:00:00", "end": "2026-10-14 01:00:00"},
              {"id": 4, "cid": 1, "type": "booking", "callsign": "NO_TIMES_TWR"},
              {"id": 5, "cid": 1, "type": "booking", "callsign": null, "start": "2026-10-14 00:00:00", "end": "2026-10-14 01:00:00"},
              {"id": 6, "cid": 1, "type": "exam", "callsign": "EGKK_TWR", "start": "2026-10-13 18:00:00", "end": "2026-10-13 15:00:00"},
              {"id": 7, "cid": 1, "type": "event", "callsign": "EGKK_GND", "start": "2026-10-13T18:00:00Z", "end": "2026-10-13T19:00:00Z"}
            ]
            """;

    private final List<Booking> bookings = BookingRepository.parse(new StringReader(JSON));

    @Test
    void skipsMalformedBookingsAndSortsByStart() {
        Assertions.assertEquals(List.of(1, 2, 3), bookings.stream().map(Booking::getId).collect(Collectors.toList()));
    }

    @Test
    void parsesFields() {
        final Booking booking = bookings.get(1);

        Assertions.assertEquals("1469847", booking.getCid());
        Assertions.assertEquals(BookingType.TRAINING, booking.getType());
        Assertions.assertEquals("EDDF_N_APP", booking.getCallsign());
        Assertions.assertEquals("EUD", booking.getDivision());
        Assertions.assertEquals("GER", booking.getSubdivision());
        Assertions.assertNull(booking.getFrequency());
    }

    @Test
    void timesAreUtc() {
        final Booking booking = bookings.get(0);

        Assertions.assertEquals(ZonedDateTime.of(2026, 10, 13, 0, 0, 0, 0, ZoneOffset.UTC), booking.getStart());
        Assertions.assertEquals(ZonedDateTime.of(2026, 10, 13, 12, 0, 34, 0, ZoneOffset.UTC), booking.getEnd());
    }

    @Test
    void normalizesCallsignsAndUnknownTypes() {
        Assertions.assertEquals("OMDB_1_GND", bookings.get(0).getCallsign());
        Assertions.assertNull(bookings.get(0).getSubdivision());
        Assertions.assertEquals(BookingType.BOOKING, bookings.get(2).getType());
    }
}
