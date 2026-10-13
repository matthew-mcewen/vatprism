package net.marvk.fs.vatsim.map.data.event;

import com.google.inject.Singleton;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import lombok.extern.log4j.Log4j2;

import java.awt.Image;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Sends notifications through the notification system of the operating system. Java has no way to ask for the
 * permission to do so, the operating system asks the user when the first notification is sent, if it asks at all.
 * Falls back to a dialog inside the application if the operating system offers no notifications.
 */
@Log4j2
@Singleton
public class OsNotifier {
    private static final String ICON = "/net/marvk/fs/vatsim/map/icon-32.png";
    private static final int TRAY_ICON_SECONDS = 30;
    private static final int PROCESS_TIMEOUT_SECONDS = 10;

    private final String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "os-notifier");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Sends the notification in the background.
     */
    public void send(final String title, final String message) {
        executor.submit(() -> {
            try {
                if (!sendToOs(title, message)) {
                    log.warn("No notifications available on this system, showing a dialog instead");
                    showDialog(title, message);
                }
            } catch (final RuntimeException e) {
                log.error("Failed to send notification", e);
            }
        });
    }

    private boolean sendToOs(final String title, final String message) {
        if (os.contains("mac")) {
            return run("osascript", "-e", "display notification \"%s\" with title \"%s\"".formatted(
                    appleScriptString(message),
                    appleScriptString(title)
            ));
        }

        if (os.contains("win")) {
            return sendToTray(title, message);
        }

        return run("notify-send", "--app-name=VATprism", title, message) || sendToTray(title, message);
    }

    private boolean sendToTray(final String title, final String message) {
        try {
            if (!SystemTray.isSupported()) {
                return false;
            }

            final URL icon = OsNotifier.class.getResource(ICON);
            final Image image = Toolkit.getDefaultToolkit().getImage(icon);

            final TrayIcon trayIcon = new TrayIcon(image, "VATprism");
            trayIcon.setImageAutoSize(true);

            final SystemTray tray = SystemTray.getSystemTray();
            tray.add(trayIcon);
            trayIcon.displayMessage(title, message, TrayIcon.MessageType.NONE);

            // The notification disappears with its icon, which is only needed to send it
            executor.schedule(() -> tray.remove(trayIcon), TRAY_ICON_SECONDS, TimeUnit.SECONDS);

            return true;
        } catch (final Exception | LinkageError e) {
            log.warn("Failed to send notification through the system tray", e);
            return false;
        }
    }

    private static boolean run(final String... command) {
        try {
            final Process process = new ProcessBuilder(List.of(command))
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();

            if (!process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }

            return process.exitValue() == 0;
        } catch (final IOException e) {
            log.debug("Failed to run %s: %s".formatted(command[0], e.getMessage()));
            return false;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String appleScriptString(final String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }

    private static void showDialog(final String title, final String message) {
        Platform.runLater(() -> {
            final Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("VATprism");
            alert.setHeaderText(title);
            alert.setContentText(message);
            alert.show();
        });
    }
}
