package net.marvk.fs.vatsim.map.view.datadetail.eventsdetail;

import com.google.inject.Inject;
import javafx.beans.property.ReadOnlyIntegerProperty;
import net.marvk.fs.vatsim.map.data.Airport;
import net.marvk.fs.vatsim.map.data.event.Event;
import net.marvk.fs.vatsim.map.data.event.EventService;
import net.marvk.fs.vatsim.map.view.datadetail.detailsubview.DetailSubViewModel;

import java.util.Collections;
import java.util.List;

public class EventsDetailViewModel extends DetailSubViewModel<Airport> {
    private final EventService eventService;

    @Inject
    public EventsDetailViewModel(final EventService eventService) {
        this.eventService = eventService;
    }

    /**
     * @return the events at the airport which are displayed according to the settings of the user
     */
    public List<Event> events() {
        return getData() == null ? Collections.emptyList() : eventService.eventsAt(getData());
    }

    public ReadOnlyIntegerProperty revisionProperty() {
        return eventService.revisionProperty();
    }
}
