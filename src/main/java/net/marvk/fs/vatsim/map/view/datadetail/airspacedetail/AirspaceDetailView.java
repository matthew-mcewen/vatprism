package net.marvk.fs.vatsim.map.view.datadetail.airspacedetail;

import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import net.marvk.fs.vatsim.map.data.Data;
import net.marvk.fs.vatsim.map.data.airspace.TraconBoundary;
import net.marvk.fs.vatsim.map.data.airspace.VgPosition;
import net.marvk.fs.vatsim.map.view.datadetail.DataDetailPane;
import net.marvk.fs.vatsim.map.view.datadetail.bookingsdetail.BookingsDetailView;
import net.marvk.fs.vatsim.map.view.datadetail.controllersdetail.ControllersDetailView;
import net.marvk.fs.vatsim.map.view.datadetail.detailsubview.DataDetailSubView;
import net.marvk.fs.vatsim.map.view.datadetail.detailsubview.DataDetailSubViewModel;

import java.util.List;

/**
 * Details of a TRACON boundary or a VATGlasses position.
 */
public class AirspaceDetailView extends DataDetailSubView<DataDetailSubViewModel<Data>, Data> {
    @FXML
    private DataDetailPane status;
    @FXML
    private Label name;
    @FXML
    private Label type;
    @FXML
    private BookingsDetailView bookingsController;
    @FXML
    private ControllersDetailView controllersController;

    @Override
    protected List<Label> labels() {
        return List.of(name, type);
    }

    @Override
    protected List<StringProperty> stringProperties() {
        return List.of(status.headerTextProperty());
    }

    @Override
    protected void setData(final Data data) {
        bookingsController.getViewModel().setData(data);

        if (data instanceof TraconBoundary) {
            final TraconBoundary boundary = (TraconBoundary) data;

            status.setHeaderText(boundary.getId());
            name.setText(boundary.getName());
            type.setText("TRACON Boundary");
            controllersController.getViewModel().setData(boundary.getControllers());
        } else if (data instanceof VgPosition) {
            final VgPosition position = (VgPosition) data;

            status.setHeaderText(position.getDisplayName());
            name.setText(position.getCallsign());
            type.setText("Vatglasses Position");
            controllersController.getViewModel().setData(position.getControllers());
        }
    }

    @Override
    protected void clear(final Data oldValue) {
        super.clear(oldValue);
        bookingsController.getViewModel().setData(null);
        if (oldValue != null) {
            controllersController.getViewModel().setData(null);
        }
    }
}
