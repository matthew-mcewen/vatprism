package net.marvk.fs.vatsim.map.view.statusbar;

import com.google.inject.Inject;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import net.marvk.fs.vatsim.map.view.BindingsUtil;
import net.marvk.fs.vatsim.map.view.preferences.PreferencesView;

import java.util.StringJoiner;

public class StatusBarView implements FxmlView<StatusBarViewModel> {
    @FXML
    private Label playersOnline;
    @FXML
    private Label mousePosition;
    @FXML
    private Label information;
    @FXML
    private Label vatglasses;
    @FXML
    private Label vatglassesSetCid;
    @InjectViewModel
    private StatusBarViewModel viewModel;

    private final PreferencesView preferencesView;

    @Inject
    public StatusBarView(final PreferencesView preferencesView) {
        this.preferencesView = preferencesView;
    }

    public void initialize() {
        mousePosition.textProperty().bind(BindingsUtil.position(viewModel.mouseWorldPositionProperty()));

        information.textProperty().bind(viewModel.informationProperty());
        vatglasses.textProperty().bind(viewModel.vatglassesProperty());
        vatglassesSetCid.visibleProperty().bind(viewModel.vatglassesCidMissingProperty());
        vatglassesSetCid.managedProperty().bind(viewModel.vatglassesCidMissingProperty());
        vatglassesSetCid.setTooltip(new Tooltip("Set your CID to follow the altitude of your own flight"));

        viewModel.playerStatsProperty().addListener((observable, oldValue, newValue) -> setPlayerStats());
        setPlayerStats();

        bindTextToTooltip(playersOnline);
        bindTextToTooltip(information);
        vatglasses.setTooltip(new Tooltip("Flight level of the displayed Vatglasses sectors and the flight it follows"));
        bindTextToTooltip(mousePosition);
    }

    @FXML
    private void openVatglassesSettings() {
        preferencesView.showVatglasses();
    }

    private void setPlayerStats() {
        final PlayerStats playerStats = viewModel.getPlayerStats();
        Platform.runLater(() -> {
            final StringJoiner sj = new StringJoiner(", ");

            sj.add(playerStats.getPilots() + " Pilots");
            sj.add(playerStats.getControllers() + " Controllers");
            sj.add(playerStats.getObservers() + " Observers");

            playersOnline.setText(sj.toString());
        });
    }

    private static void bindTextToTooltip(final Label label) {
        final Tooltip value = new Tooltip();
        value.textProperty().bind(label.textProperty());
        label.setTooltip(value);
    }
}
