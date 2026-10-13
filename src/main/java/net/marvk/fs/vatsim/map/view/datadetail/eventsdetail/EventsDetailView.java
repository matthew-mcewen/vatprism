package net.marvk.fs.vatsim.map.view.datadetail.eventsdetail;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;
import net.marvk.fs.vatsim.map.data.Airport;
import net.marvk.fs.vatsim.map.data.event.Event;
import net.marvk.fs.vatsim.map.view.datadetail.DataDetailPane;
import net.marvk.fs.vatsim.map.view.datadetail.detailsubview.DetailSubView;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * The events at an airport. Hidden unless the user has events enabled and there is an event matching their settings.
 */
public class EventsDetailView extends DetailSubView<EventsDetailViewModel, Airport> {
    private static final DateTimeFormatter START = DateTimeFormatter.ofPattern("dd MMM HHmm'z'", Locale.ENGLISH);
    private static final DateTimeFormatter END = DateTimeFormatter.ofPattern("HHmm'z'", Locale.ENGLISH);

    @FXML
    private DataDetailPane root;
    @FXML
    private GridPane eventsGrid;

    @Override
    public void initialize() {
        super.initialize();
        viewModel.revisionProperty().addListener((observable, oldValue, newValue) -> update());
        update();
    }

    @Override
    protected void setData(final Airport airport) {
        update();
    }

    @Override
    protected void clear(final Airport oldValue) {
        super.clear(oldValue);
        update();
    }

    private void update() {
        final List<Event> events = viewModel.events();

        root.setVisible(!events.isEmpty());
        root.setManaged(!events.isEmpty());
        root.setHeaderText(events.size() < 2 ? "Events" : "Events (%s)".formatted(events.size()));

        eventsGrid.getChildren().clear();

        final ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);

        for (int i = 0; i < events.size(); i++) {
            final Event event = events.get(i);

            final Label type = new Label(event.getType().getShortName());
            type.getStyleClass().add("mono");
            type.setMinWidth(Label.USE_PREF_SIZE);
            eventsGrid.add(type, 0, i);

            final Label name = new Label(event.getName());
            name.getStyleClass().add("hyperlink-label");
            name.setMinWidth(0);
            name.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY) {
                    viewModel.setDataDetail(event);
                    e.consume();
                }
            });
            eventsGrid.add(name, 1, i);

            final Label time = new Label(event.getStart().isAfter(now)
                    ? START.format(event.getStart())
                    : "Until " + END.format(event.getEnd())
            );
            time.getStyleClass().add("mono");
            time.setMinWidth(Label.USE_PREF_SIZE);
            eventsGrid.add(time, 2, i);
        }
    }
}
