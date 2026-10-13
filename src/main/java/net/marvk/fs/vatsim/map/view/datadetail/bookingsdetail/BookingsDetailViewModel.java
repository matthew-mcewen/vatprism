package net.marvk.fs.vatsim.map.view.datadetail.bookingsdetail;

import com.google.inject.Inject;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.scene.paint.Color;
import net.marvk.fs.vatsim.map.data.Data;
import net.marvk.fs.vatsim.map.data.Preferences;
import net.marvk.fs.vatsim.map.data.booking.Booking;
import net.marvk.fs.vatsim.map.data.booking.BookingService;
import net.marvk.fs.vatsim.map.view.datadetail.detailsubview.DetailSubViewModel;

import java.util.Collections;
import java.util.List;

public class BookingsDetailViewModel extends DetailSubViewModel<Data> {
    private static final Color DEFAULT_BOOKED_COLOR = Color.web("606060");
    private static final Color DEFAULT_LABEL_COLOR = Color.WHITE.darker();

    private final BookingService bookingService;
    private final Preferences preferences;

    @Inject
    public BookingsDetailViewModel(final BookingService bookingService, final Preferences preferences) {
        this.bookingService = bookingService;
        this.preferences = preferences;
    }

    /**
     * @return the bookings at the airport or in the airspace which are displayed according to the settings of the user
     */
    public List<Booking> bookings() {
        return getData() == null ? Collections.emptyList() : bookingService.bookingsAt(getData());
    }

    public ReadOnlyIntegerProperty revisionProperty() {
        return bookingService.revisionProperty();
    }

    public Color getBookedColor() {
        return colorOrDefault("airports.booked_color", DEFAULT_BOOKED_COLOR);
    }

    public Color getLabelColor() {
        return colorOrDefault("airports.controller_label_color", DEFAULT_LABEL_COLOR);
    }

    private Color colorOrDefault(final String key, final Color defaultColor) {
        final Color color = preferences.colorProperty(key).get();

        return color == null ? defaultColor : color;
    }
}
