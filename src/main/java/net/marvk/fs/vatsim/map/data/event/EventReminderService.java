package net.marvk.fs.vatsim.map.data.event;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import lombok.extern.log4j.Log4j2;
import net.marvk.fs.vatsim.map.data.Preferences;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Notifies the user a configurable time before an event starts. Reminders are saved in the user config directory and
 * are only sent while the application is running. A reminder which became due while the application was closed is sent
 * on startup, as long as the event has not started yet. All methods must be called on the JavaFX application thread.
 */
@Log4j2
@Singleton
public class EventReminderService {
    public static final String REMINDER_MINUTES_KEY = "events.reminder_minutes";

    public static final int MIN_REMINDER_MINUTES = 0;
    public static final int MAX_REMINDER_MINUTES = 1440;

    private static final String FILE_NAME = "EventReminders.json";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmm'z'", Locale.ENGLISH);

    private final OsNotifier notifier;
    private final Path file;
    private final IntegerProperty reminderMinutes;

    private final Map<Integer, Reminder> reminders = new HashMap<>();
    private final Map<Integer, ScheduledFuture<?>> scheduled = new HashMap<>();
    private final ObservableSet<Integer> reminderIds = FXCollections.observableSet();

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "event-reminders");
        thread.setDaemon(true);
        return thread;
    });

    private boolean initialized = false;

    @Inject
    public EventReminderService(
            final OsNotifier notifier,
            final Preferences preferences,
            @Named("userConfigDir") final Path configPath
    ) {
        this.notifier = notifier;
        this.file = configPath.resolve(FILE_NAME);
        this.reminderMinutes = preferences.integerProperty(REMINDER_MINUTES_KEY, 30);
    }

    /**
     * Loads the saved reminders and schedules them.
     */
    public void initialize() {
        if (initialized) {
            return;
        }

        initialized = true;

        load();
        scheduleAll();

        reminderMinutes.addListener((observable, oldValue, newValue) -> scheduleAll());
    }

    /**
     * @return the ids of the events with a reminder
     */
    public ObservableSet<Integer> reminderIds() {
        return FXCollections.unmodifiableObservableSet(reminderIds);
    }

    public boolean isSet(final Event event) {
        return reminders.containsKey(event.getId());
    }

    public IntegerProperty reminderMinutesProperty() {
        return reminderMinutes;
    }

    /**
     * Sets a reminder for the event. A notification is sent right away, either to confirm the reminder or, if it is
     * already due, as the reminder itself. This first notification is also what makes the operating system ask the
     * user to allow notifications, where that is required.
     *
     * @return {@code false} if the event has already started
     */
    public boolean set(final Event event) {
        final ZonedDateTime now = now();

        if (!event.getStart().isAfter(now)) {
            return false;
        }

        final Reminder reminder = new Reminder(event.getId(), event.getName(), event.getStart());

        reminders.put(reminder.id, reminder);
        reminderIds.add(reminder.id);
        save();

        final Duration delay = delay(reminder.start, reminderMinutes.get(), now);

        if (!delay.isZero()) {
            notifier.send("Reminder set", "%s\nYou will be notified at %s, %s before the start".formatted(
                    reminder.name,
                    TIME.format(now.plus(delay)),
                    minutes(clampedMinutes(reminderMinutes.get()))
            ));
        }

        schedule(reminder);

        return true;
    }

    public void clear(final Event event) {
        remove(event.getId());
        save();
    }

    private void scheduleAll() {
        final ZonedDateTime now = now();
        boolean changed = false;

        for (final Reminder reminder : new ArrayList<>(reminders.values())) {
            if (reminder.start.isAfter(now)) {
                schedule(reminder);
            } else {
                remove(reminder.id);
                changed = true;
            }
        }

        if (changed) {
            save();
        }
    }

    private void schedule(final Reminder reminder) {
        cancel(reminder.id);

        final Duration delay = delay(reminder.start, reminderMinutes.get(), now());

        scheduled.put(reminder.id, executor.schedule(
                () -> Platform.runLater(() -> fire(reminder)),
                delay.toMillis(),
                TimeUnit.MILLISECONDS
        ));
    }

    private void fire(final Reminder reminder) {
        // The reminder may have been cleared or rescheduled in the meantime
        if (reminders.get(reminder.id) != reminder) {
            return;
        }

        remove(reminder.id);
        save();

        final long minutes = (long) Math.ceil(Duration.between(now(), reminder.start).toSeconds() / 60.0);

        if (minutes < 0) {
            return;
        }

        notifier.send(reminder.name, minutes == 0
                ? "Starts now, at %s".formatted(TIME.format(reminder.start))
                : "Starts in %s, at %s".formatted(minutes((int) minutes), TIME.format(reminder.start))
        );
    }

    private void remove(final int id) {
        cancel(id);
        reminders.remove(id);
        reminderIds.remove(id);
    }

    private void cancel(final int id) {
        final ScheduledFuture<?> future = scheduled.remove(id);

        if (future != null) {
            future.cancel(false);
        }
    }

    /**
     * @return the time to wait before reminding of an event, zero if the reminder is already due
     */
    static Duration delay(final ZonedDateTime start, final int reminderMinutes, final ZonedDateTime now) {
        final Duration result = Duration.between(now, start.minusMinutes(clampedMinutes(reminderMinutes)));

        return result.isNegative() ? Duration.ZERO : result;
    }

    private static int clampedMinutes(final int reminderMinutes) {
        return Math.max(MIN_REMINDER_MINUTES, Math.min(MAX_REMINDER_MINUTES, reminderMinutes));
    }

    private static String minutes(final int minutes) {
        return minutes == 1 ? "1 minute" : minutes + " minutes";
    }

    private static ZonedDateTime now() {
        return ZonedDateTime.now(ZoneOffset.UTC);
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }

        try {
            final JsonArray array = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonArray();

            for (final JsonElement element : array) {
                final JsonObject object = element.getAsJsonObject();
                final Reminder reminder = new Reminder(
                        object.get("id").getAsInt(),
                        object.get("name").getAsString(),
                        ZonedDateTime.parse(object.get("start").getAsString())
                );

                reminders.put(reminder.id, reminder);
                reminderIds.add(reminder.id);
            }

            log.info("Loaded %d event reminders".formatted(reminders.size()));
        } catch (final IOException | RuntimeException e) {
            log.error("Failed to load event reminders", e);
        }
    }

    private void save() {
        final JsonArray array = new JsonArray();

        for (final Reminder reminder : reminders.values()) {
            final JsonObject object = new JsonObject();
            object.addProperty("id", reminder.id);
            object.addProperty("name", reminder.name);
            object.addProperty("start", reminder.start.toString());
            array.add(object);
        }

        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, array.toString(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            log.error("Failed to save event reminders", e);
        }
    }

    private static final class Reminder {
        private final int id;
        private final String name;
        private final ZonedDateTime start;

        private Reminder(final int id, final String name, final ZonedDateTime start) {
            this.id = id;
            this.name = name;
            this.start = start;
        }
    }
}
