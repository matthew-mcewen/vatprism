package net.marvk.fs.vatsim.map.data.event;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import javafx.application.HostServices;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Hands events to the default calendar application of the operating system as iCalendar files.
 */
@Log4j2
@Singleton
public class EventCalendar {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final String LINE_BREAK = "\r\n";
    private static final int MAX_LINE_OCTETS = 75;

    private final HostServices hostServices;
    private final Path directory;

    @Inject
    public EventCalendar(final HostServices hostServices, @Named("userCacheDir") final Path cachePath) {
        this.hostServices = hostServices;
        this.directory = cachePath.resolve("events");
    }

    /**
     * Opens the event with the application registered for iCalendar files.
     *
     * @return whether the file could be written
     */
    public boolean addToCalendar(final Event event) {
        try {
            Files.createDirectories(directory);

            final Path file = directory.resolve("event-%d.ics".formatted(event.getId()));
            Files.writeString(file, ics(event, ZonedDateTime.now(ZoneOffset.UTC)), StandardCharsets.UTF_8);

            hostServices.showDocument(file.toUri().toString());

            return true;
        } catch (final IOException | RuntimeException e) {
            log.error("Failed to add event %d to the calendar".formatted(event.getId()), e);
            return false;
        }
    }

    static String ics(final Event event, final ZonedDateTime now) {
        final StringBuilder result = new StringBuilder();

        line(result, "BEGIN:VCALENDAR");
        line(result, "VERSION:2.0");
        line(result, "PRODID:-//VATprism//Events//EN");
        line(result, "CALSCALE:GREGORIAN");
        line(result, "BEGIN:VEVENT");
        line(result, "UID:vatsim-event-%d@vatprism".formatted(event.getId()));
        line(result, "DTSTAMP:" + timestamp(now));
        line(result, "DTSTART:" + timestamp(event.getStart()));
        line(result, "DTEND:" + timestamp(event.getEnd()));
        line(result, "SUMMARY:" + escape(event.getName()));

        if (!event.getAirportIcaos().isEmpty()) {
            line(result, "LOCATION:" + escape(String.join(", ", event.getAirportIcaos())));
        }

        final String description = description(event);

        if (!description.isEmpty()) {
            line(result, "DESCRIPTION:" + escape(description));
        }

        if (event.getLink() != null) {
            line(result, "URL:" + event.getLink());
        }

        line(result, "END:VEVENT");
        line(result, "END:VCALENDAR");

        return result.toString();
    }

    private static String description(final Event event) {
        if (event.getLink() == null) {
            return event.getShortDescription();
        }

        if (event.getShortDescription().isEmpty()) {
            return event.getLink();
        }

        return event.getShortDescription() + "\n\n" + event.getLink();
    }

    private static String timestamp(final ZonedDateTime time) {
        return TIMESTAMP.format(time.withZoneSameInstant(ZoneOffset.UTC));
    }

    private static String escape(final String text) {
        return text.replace("\\", "\\\\")
                   .replace(";", "\\;")
                   .replace(",", "\\,")
                   .replace("\r\n", "\\n")
                   .replace("\n", "\\n");
    }

    // Lines are limited to 75 octets, longer lines continue on the next line after a single space
    private static void line(final StringBuilder result, final String line) {
        int octets = 0;

        for (int i = 0; i < line.length(); ) {
            final int codePoint = line.codePointAt(i);
            final int size = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;

            if (octets + size > MAX_LINE_OCTETS) {
                result.append(LINE_BREAK).append(' ');
                octets = 1;
            }

            result.appendCodePoint(codePoint);
            octets += size;
            i += Character.charCount(codePoint);
        }

        result.append(LINE_BREAK);
    }
}
