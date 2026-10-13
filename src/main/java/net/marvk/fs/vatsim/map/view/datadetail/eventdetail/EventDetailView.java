package net.marvk.fs.vatsim.map.view.datadetail.eventdetail;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import net.marvk.fs.vatsim.map.data.Airport;
import net.marvk.fs.vatsim.map.data.event.Event;
import net.marvk.fs.vatsim.map.view.datadetail.DataDetailPane;
import net.marvk.fs.vatsim.map.view.datadetail.detailsubview.DataDetailSubView;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class EventDetailView extends DataDetailSubView<EventDetailViewModel, Event> {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("EEE dd MMM yyyy HH:mm'z'", Locale.ENGLISH);
    private static final String HYPERLINK_LABEL = "hyperlink-label";
    // Height of the route and remarks of a flight plan
    private static final double MAX_DESCRIPTION_HEIGHT = 100;

    @FXML
    private DataDetailPane status;
    @FXML
    private Label name;
    @FXML
    private Label type;
    @FXML
    private Label link;
    @FXML
    private Label start;
    @FXML
    private Label end;
    @FXML
    private Label duration;

    @FXML
    private FlowPane airports;

    @FXML
    private ScrollPane descriptionScroll;
    @FXML
    private Label description;

    @FXML
    private ToggleButton reminder;

    @Override
    public void initialize() {
        super.initialize();

        // Grows with the description and scrolls once it would become taller than the flight plan sections. Sizes
        // which depend on the result of a layout pass can only be applied after it, they are ignored during the pass
        description.heightProperty().addListener((observable, oldValue, newValue) -> Platform.runLater(() -> {
            final double height = Math.min(MAX_DESCRIPTION_HEIGHT, description.getHeight() + 2);
            descriptionScroll.setMinHeight(height);
            descriptionScroll.setPrefHeight(height);
        }));

        // Wraps the airports at the width of the pane instead of making the pane wider
        airports.widthProperty().addListener((observable, oldValue, newValue) -> Platform.runLater(
                () -> airports.setPrefWrapLength(airports.getWidth())
        ));

        final Tooltip reminderTooltip = new Tooltip();
        reminderTooltip.textProperty().bind(Bindings.createStringBinding(
                () -> "Notify me %d minutes before the event starts, while VATprism is running. Set the time in the settings"
                        .formatted(viewModel.reminderMinutesProperty().get()),
                viewModel.reminderMinutesProperty()
        ));
        reminder.setTooltip(reminderTooltip);

        viewModel.reminderIds().addListener((InvalidationListener) observable -> updateReminder());
    }

    @Override
    protected List<Label> labels() {
        return List.of(name, type, link, start, end, duration, description);
    }

    @Override
    protected List<StringProperty> stringProperties() {
        return List.of(status.headerTextProperty());
    }

    @Override
    protected void setData(final Event event) {
        status.setHeaderText(event.getName());
        name.setText(event.getName());
        type.setText(event.getType().getApiName());

        if (event.getLink() == null) {
            link.getStyleClass().remove(HYPERLINK_LABEL);
            link.setText(null);
        } else {
            if (!link.getStyleClass().contains(HYPERLINK_LABEL)) {
                link.getStyleClass().add(HYPERLINK_LABEL);
            }
            link.setText(event.getLink());
        }

        start.setText(TIME.format(event.getStart()));
        end.setText(TIME.format(event.getEnd()));
        duration.setText(Event.formatDuration(event.getDuration()));

        description.setText(event.getShortDescription().isEmpty() ? "No description" : event.getShortDescription());
        descriptionScroll.setVvalue(0);

        setAirports(event);
        updateReminder();
    }

    private void setAirports(final Event event) {
        airports.getChildren().clear();

        for (final String icao : event.getAirportIcaos()) {
            final Airport airport = airport(event, icao);

            if (airport == null) {
                final Label label = new Label(icao);
                label.getStyleClass().add("mono");
                airports.getChildren().add(label);
            } else {
                airports.getChildren().add(icaoLabel(airport));
            }
        }

        if (airports.getChildren().isEmpty()) {
            final Label label = new Label("No Airports");
            label.getStyleClass().add("mono");
            airports.getChildren().add(label);
        }
    }

    private static Airport airport(final Event event, final String icao) {
        for (final Airport airport : event.getAirports()) {
            if (icao.equals(airport.getIcao())) {
                return airport;
            }
        }

        return null;
    }

    private void updateReminder() {
        final Event event = viewModel.getData();

        reminder.setSelected(viewModel.isReminderSet());
        reminder.setDisable(event == null || !event.getStart().isAfter(ZonedDateTime.now(ZoneOffset.UTC)));
    }

    @Override
    protected void clear(final Event oldValue) {
        super.clear(oldValue);
        airports.getChildren().clear();
    }

    @FXML
    private void openLink() {
        viewModel.openLink();
    }

    @FXML
    private void addToCalendar() {
        viewModel.addToCalendar();
    }

    @FXML
    private void toggleReminder() {
        viewModel.setReminder(reminder.isSelected());
        updateReminder();
    }
}
