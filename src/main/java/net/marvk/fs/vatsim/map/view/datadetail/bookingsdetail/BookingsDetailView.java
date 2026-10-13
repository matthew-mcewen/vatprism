package net.marvk.fs.vatsim.map.view.datadetail.bookingsdetail;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import net.marvk.fs.vatsim.map.data.ControllerType;
import net.marvk.fs.vatsim.map.data.Data;
import net.marvk.fs.vatsim.map.data.booking.Booking;
import net.marvk.fs.vatsim.map.data.booking.BookingType;
import net.marvk.fs.vatsim.map.view.datadetail.DataDetailPane;
import net.marvk.fs.vatsim.map.view.datadetail.detailsubview.DetailSubView;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * The booked positions at an airport or in an airspace. Hidden unless the user has bookings enabled and there is a
 * booking within the time they want to look ahead.
 */
public class BookingsDetailView extends DetailSubView<BookingsDetailViewModel, Data> {
    private static final DateTimeFormatter START = DateTimeFormatter.ofPattern("dd MMM HHmm", Locale.ENGLISH);
    private static final DateTimeFormatter END = DateTimeFormatter.ofPattern("HHmm'z'", Locale.ENGLISH);

    @FXML
    private DataDetailPane root;
    @FXML
    private GridPane bookingsGrid;

    @Override
    public void initialize() {
        super.initialize();
        viewModel.revisionProperty().addListener((observable, oldValue, newValue) -> update());
        update();
    }

    @Override
    protected void setData(final Data data) {
        update();
    }

    @Override
    protected void clear(final Data oldValue) {
        super.clear(oldValue);
        update();
    }

    private void update() {
        final List<Booking> bookings = viewModel.bookings();

        root.setVisible(!bookings.isEmpty());
        root.setManaged(!bookings.isEmpty());
        root.setHeaderText(bookings.isEmpty() ? "Bookings" : "Bookings (%s)".formatted(bookings.size()));

        bookingsGrid.getChildren().clear();

        final ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);

        for (int i = 0; i < bookings.size(); i++) {
            final Booking booking = bookings.get(i);

            bookingsGrid.add(
                    controllerTypePane(typeLabelString(booking.getControllerType()), viewModel.getLabelColor(), viewModel.getBookedColor()),
                    0,
                    i
            );

            final Label callsign = new Label(booking.getCallsign());
            callsign.getStyleClass().add("mono");
            callsign.setMinWidth(Label.USE_PREF_SIZE);
            bookingsGrid.add(callsign, 1, i);

            final Label cid = new Label(booking.getCid());
            cid.getStyleClass().add("mono");
            cid.setMinWidth(Label.USE_PREF_SIZE);
            bookingsGrid.add(cid, 2, i);

            final Label type = new Label(booking.getType() == BookingType.BOOKING ? "" : booking.getType().getDisplayName());
            type.setMinWidth(0);
            bookingsGrid.add(type, 3, i);

            final Label time = new Label(booking.getStart().isAfter(now)
                    ? START.format(booking.getStart()) + "-" + END.format(booking.getEnd())
                    : "Until " + END.format(booking.getEnd())
            );
            time.getStyleClass().add("mono");
            time.setMinWidth(Label.USE_PREF_SIZE);
            bookingsGrid.add(time, 4, i);
        }
    }

    private static String typeLabelString(final ControllerType type) {
        final String name = type == ControllerType.NONE || type == ControllerType.OBS ? "" : type.name();

        return " ".repeat(4 - name.length()) + name;
    }
}
