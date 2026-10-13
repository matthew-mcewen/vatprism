package net.marvk.fs.vatsim.map.view.datadetail.eventdetail;

import com.google.inject.Inject;
import javafx.application.HostServices;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.collections.ObservableSet;
import net.marvk.fs.vatsim.map.data.event.Event;
import net.marvk.fs.vatsim.map.data.event.EventCalendar;
import net.marvk.fs.vatsim.map.data.event.EventReminderService;
import net.marvk.fs.vatsim.map.view.datadetail.detailsubview.DataDetailSubViewModel;

public class EventDetailViewModel extends DataDetailSubViewModel<Event> {
    private final HostServices hostServices;
    private final EventCalendar eventCalendar;
    private final EventReminderService eventReminderService;

    @Inject
    public EventDetailViewModel(
            final HostServices hostServices,
            final EventCalendar eventCalendar,
            final EventReminderService eventReminderService
    ) {
        this.hostServices = hostServices;
        this.eventCalendar = eventCalendar;
        this.eventReminderService = eventReminderService;
    }

    public void openLink() {
        if (getData() != null && getData().getLink() != null) {
            hostServices.showDocument(getData().getLink());
        }
    }

    public void addToCalendar() {
        if (getData() != null) {
            eventCalendar.addToCalendar(getData());
        }
    }

    public boolean isReminderSet() {
        return getData() != null && eventReminderService.isSet(getData());
    }

    /**
     * @return whether the event has a reminder afterwards
     */
    public boolean setReminder(final boolean reminder) {
        if (getData() == null) {
            return false;
        }

        if (reminder) {
            return eventReminderService.set(getData());
        }

        eventReminderService.clear(getData());
        return false;
    }

    public ObservableSet<Integer> reminderIds() {
        return eventReminderService.reminderIds();
    }

    public ReadOnlyIntegerProperty reminderMinutesProperty() {
        return eventReminderService.reminderMinutesProperty();
    }
}
