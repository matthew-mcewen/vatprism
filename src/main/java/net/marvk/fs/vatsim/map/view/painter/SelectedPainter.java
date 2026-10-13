package net.marvk.fs.vatsim.map.view.painter;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import net.marvk.fs.vatsim.map.data.*;
import net.marvk.fs.vatsim.map.data.airspace.ActiveSubSector;
import net.marvk.fs.vatsim.map.data.airspace.TraconBoundary;
import net.marvk.fs.vatsim.map.data.airspace.VgPosition;
import net.marvk.fs.vatsim.map.view.map.MapVariables;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SelectedPainter extends CompositeMapPainter<Data> {
    @MetaPainter("Pilot")
    private final PilotPainter pilotPainter;

    @MetaPainter("Airport")
    private final AirportPainter airportPainter;

    @MetaPainter("FIR")
    private final FirbPainter firbPainter;

    @MetaPainter("TRACON")
    private final TraconPainter traconPainter;

    @MetaPainter("Vatglasses")
    private final VatglassesPositionPainter vatglassesPainter;

    private final PainterVisitor painterVisitor;

    public SelectedPainter(final MapVariables mapVariables, final Supplier<ActiveSubSector[]> visibleSubSectors) {
        this(mapVariables, visibleSubSectors, Color.RED, false);
    }

    public SelectedPainter(final MapVariables mapVariables, final Supplier<ActiveSubSector[]> visibleSubSectors, final Color color, final boolean backgrounds) {
        this.traconPainter = new TraconPainter(mapVariables, color, 2.5);
        this.vatglassesPainter = new VatglassesPositionPainter(mapVariables, visibleSubSectors, color, 2.5);
        this.painterVisitor = new PainterVisitor();
        this.airportPainter = new AirportPainter(mapVariables, color, color, true, true, true);
        this.pilotPainter = new PilotPainter(mapVariables, color, backgrounds);
        this.firbPainter = new FirbPainter(mapVariables, color, 2.5, true, true, true);
    }

    @Override
    public void paint(final GraphicsContext context, final Data data) {
        painterVisitor.visit(data).accept(context);
    }

    @Override
    protected Collection<? extends Painter<?>> painters() {
        return List.of(firbPainter, traconPainter, vatglassesPainter);
    }

    private class PainterVisitor extends DefaultingDataVisitor<Consumer<GraphicsContext>> {
        public PainterVisitor() {
            super(c -> {
            });
        }

        @Override
        public Consumer<GraphicsContext> visit(final UpperInformationRegion upperInformationRegion) {
            return c -> firbPainter.paint(c, upperInformationRegion.getFlightInformationRegionBoundaries());
        }

        @Override
        public Consumer<GraphicsContext> visit(final Airport airport) {
            return c -> airportPainter.paint(c, airport);
        }

        @Override
        public Consumer<GraphicsContext> visit(final FlightInformationRegionBoundary flightInformationRegionBoundary) {
            return c -> firbPainter.paint(c, flightInformationRegionBoundary);
        }

        @Override
        public Consumer<GraphicsContext> visit(final Pilot pilot) {
            return c -> pilotPainter.paint(c, pilot);
        }

        @Override
        public Consumer<GraphicsContext> visit(final TraconBoundary traconBoundary) {
            return c -> traconPainter.paint(c, traconBoundary);
        }

        @Override
        public Consumer<GraphicsContext> visit(final VgPosition vatglassesPosition) {
            return c -> vatglassesPainter.paint(c, vatglassesPosition);
        }

        @Override
        public Consumer<GraphicsContext> visit(final Controller controller) {
            return c -> visit(controller.getWorkingLocation());
        }
    }
}
