package net.marvk.fs.vatsim.map.data.booking;

import net.marvk.fs.vatsim.map.data.Airport;
import net.marvk.fs.vatsim.map.data.CallsignParser;
import net.marvk.fs.vatsim.map.data.ControllerType;
import net.marvk.fs.vatsim.map.data.FlightInformationRegionBoundary;
import net.marvk.fs.vatsim.map.data.UpperInformationRegion;
import net.marvk.fs.vatsim.map.data.airspace.AtcStation;

import java.time.ZonedDateTime;

/**
 * A booked controller position from the VATSIM ATC Bookings API.
 */
public class Booking implements AtcStation {
    private final int id;
    private final String cid;
    private final BookingType type;
    private final String callsign;
    private final ZonedDateTime start;
    private final ZonedDateTime end;
    private final String division;
    private final String subdivision;

    private CallsignParser.Result location = CallsignParser.Result.EMPTY;

    public Booking(
            final int id,
            final String cid,
            final BookingType type,
            final String callsign,
            final ZonedDateTime start,
            final ZonedDateTime end,
            final String division,
            final String subdivision
    ) {
        this.id = id;
        this.cid = cid;
        this.type = type;
        this.callsign = callsign;
        this.start = start;
        this.end = end;
        this.division = division;
        this.subdivision = subdivision;
    }

    public int getId() {
        return id;
    }

    public String getCid() {
        return cid;
    }

    public BookingType getType() {
        return type;
    }

    @Override
    public String getCallsign() {
        return callsign;
    }

    /**
     * @return always {@code null}, the frequency of a booked position is unknown
     */
    @Override
    public String getFrequency() {
        return null;
    }

    public ZonedDateTime getStart() {
        return start;
    }

    public ZonedDateTime getEnd() {
        return end;
    }

    public String getDivision() {
        return division;
    }

    public String getSubdivision() {
        return subdivision;
    }

    public ControllerType getControllerType() {
        return location.getControllerType();
    }

    /**
     * @return the airport of the booked position, {@code null} if it is none or unknown
     */
    public Airport getAirport() {
        return location.getAirport();
    }

    public FlightInformationRegionBoundary getFlightInformationRegionBoundary() {
        return location.getFlightInformationRegionBoundary();
    }

    public UpperInformationRegion getUpperInformationRegion() {
        return location.getUpperInformationRegion();
    }

    void setLocation(final CallsignParser.Result location) {
        this.location = location;
    }

    @Override
    public String toString() {
        return "%s (%d)".formatted(callsign, id);
    }
}
