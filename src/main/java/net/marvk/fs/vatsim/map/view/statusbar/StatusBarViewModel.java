package net.marvk.fs.vatsim.map.view.statusbar;

import com.google.inject.Inject;
import de.saxsys.mvvmfx.InjectScope;
import de.saxsys.mvvmfx.ViewModel;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import net.marvk.fs.vatsim.map.data.Client;
import net.marvk.fs.vatsim.map.data.ClientRepository;
import net.marvk.fs.vatsim.map.data.Data;
import net.marvk.fs.vatsim.map.data.IcaoVisitor;
import net.marvk.fs.vatsim.map.data.Pilot;
import net.marvk.fs.vatsim.map.data.airspace.VatglassesState;
import net.marvk.fs.vatsim.map.view.StatusScope;

import java.util.stream.Collectors;

public class StatusBarViewModel implements ViewModel {
    private final ObjectProperty<Point2D> mouseWorldPosition = new SimpleObjectProperty<>();
    private final ObservableList<Data> highlightedData = FXCollections.observableArrayList();

    private final ReadOnlyObjectWrapper<PlayerStats> playerStats = new ReadOnlyObjectWrapper<>(new PlayerStats());
    private final ClientRepository clientRepository;

    private final ReadOnlyStringWrapper information = new ReadOnlyStringWrapper();
    private final IcaoVisitor icaoVisitor = new IcaoVisitor("");

    private final VatglassesState vatglassesState;
    private final ReadOnlyStringWrapper vatglasses = new ReadOnlyStringWrapper();

    @InjectScope
    private StatusScope statusScope;

    @Inject
    public StatusBarViewModel(final ClientRepository clientRepository, final VatglassesState vatglassesState) {
        this.clientRepository = clientRepository;
        this.vatglassesState = vatglassesState;
    }

    public void initialize() {
        mouseWorldPosition.bind(statusScope.mouseWorldPositionProperty());

        Bindings.bindContent(highlightedData, statusScope.highlightedFirs());
        clientRepository.list().addListener((ListChangeListener<Client>) c -> updatePlayerStats());
        updatePlayerStats();
        vatglasses.bind(Bindings.createStringBinding(
                this::vatglassesString,
                vatglassesState.enabledProperty(),
                vatglassesState.levelProperty(),
                vatglassesState.trackedPilotProperty()
        ));
        information.bind(Bindings.createStringBinding(
                () -> highlightedData.stream().map(icaoVisitor::visit).collect(Collectors.joining(", ")),
                highlightedData
        ));
    }

    private String vatglassesString() {
        if (!vatglassesState.enabledProperty().get()) {
            return "";
        }

        final Pilot pilot = vatglassesState.getTrackedPilot();

        return "VG FL%03d%s".formatted(vatglassesState.getLevel(), pilot == null ? "" : " " + pilot.getCallsign());
    }

    private void updatePlayerStats() {
        playerStats.set(PlayerStats.read(clientRepository.list()));
    }

    public ObjectProperty<Point2D> mouseWorldPositionProperty() {
        return mouseWorldPosition;
    }

    public PlayerStats getPlayerStats() {
        return playerStats.get();
    }

    public ReadOnlyObjectProperty<PlayerStats> playerStatsProperty() {
        return playerStats.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty vatglassesProperty() {
        return vatglasses.getReadOnlyProperty();
    }

    public String getInformation() {
        return information.get();
    }

    public ReadOnlyStringProperty informationProperty() {
        return information.getReadOnlyProperty();
    }
}
