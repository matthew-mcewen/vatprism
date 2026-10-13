package net.marvk.fs.vatsim.map.data.event;

import net.marvk.fs.vatsim.map.data.Airport;
import net.marvk.fs.vatsim.map.data.Data;
import net.marvk.fs.vatsim.map.data.DataVisitor;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

/**
 * An event from the VATSIM Events API.
 */
public class Event implements Data {
    private final int id;
    private final EventType type;
    private final String name;
    private final String link;
    private final ZonedDateTime start;
    private final ZonedDateTime end;
    private final String shortDescription;
    private final String banner;
    private final List<String> airportIcaos;

    private List<Airport> airports = Collections.emptyList();

    public Event(
            final int id,
            final EventType type,
            final String name,
            final String link,
            final ZonedDateTime start,
            final ZonedDateTime end,
            final String shortDescription,
            final String banner,
            final List<String> airportIcaos
    ) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.link = link;
        this.start = start;
        this.end = end;
        this.shortDescription = shortDescription;
        this.banner = banner;
        this.airportIcaos = List.copyOf(airportIcaos);
    }

    public int getId() {
        return id;
    }

    public EventType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    /**
     * @return the url of the event page, {@code null} if there is none
     */
    public String getLink() {
        return link;
    }

    public ZonedDateTime getStart() {
        return start;
    }

    public ZonedDateTime getEnd() {
        return end;
    }

    public Duration getDuration() {
        return Duration.between(start, end);
    }

    public String getShortDescription() {
        return shortDescription;
    }

    /**
     * @return the url of the banner image, {@code null} if there is none
     */
    public String getBanner() {
        return banner;
    }

    /**
     * @return the ICAO codes of all airports of the event, including those unknown to the airport repository
     */
    public List<String> getAirportIcaos() {
        return airportIcaos;
    }

    /**
     * @return the airports of the event which are known to the airport repository
     */
    public List<Airport> getAirports() {
        return airports;
    }

    void setAirports(final List<Airport> airports) {
        this.airports = List.copyOf(airports);
    }

    /**
     * @return the duration as hh:mm, with as many hour digits as required
     */
    public static String formatDuration(final Duration duration) {
        final Duration nonNegative = duration.isNegative() ? Duration.ZERO : duration;

        return "%02d:%02d".formatted(nonNegative.toHours(), nonNegative.toMinutesPart());
    }

    @Override
    public <R> R visit(final DataVisitor<R> visitor) {
        return visitor.visit(this);
    }
}
