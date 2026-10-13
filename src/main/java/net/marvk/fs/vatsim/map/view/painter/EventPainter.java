package net.marvk.fs.vatsim.map.view.painter;

import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import net.marvk.fs.vatsim.map.data.Airport;
import net.marvk.fs.vatsim.map.view.map.MapVariables;

import java.util.function.Supplier;

/**
 * Marks airports with an upcoming event with a pennon. The airport itself is painted as well, as airports without
 * controllers or traffic are usually not painted by the airport painter.
 */
public class EventPainter extends MapPainter<Airport> {
    // Height of the controller type symbols of the airport painter, the pennon is never taller than those
    private static final int PENNON_HEIGHT = 9;

    // The pole is tilted 30 degrees to the right
    private static final double POLE_X = Math.sin(Math.toRadians(30));
    private static final double POLE_Y = -Math.cos(Math.toRadians(30));

    // Portion of the pole which is covered by the flag, measured from the tip
    private static final double FLAG_LENGTH = 0.5;
    private static final double FLAG_WIDTH = 0.7;

    @Parameter("Pennon Color")
    private Color pennonColor = Color.web("760023");

    private final Supplier<Color> airportColor;

    private final double[] xs = new double[3];
    private final double[] ys = new double[3];

    public EventPainter(final MapVariables mapVariables, final Supplier<Color> airportColor) {
        super(mapVariables);
        this.airportColor = airportColor;
    }

    @Override
    public void paint(final GraphicsContext c, final Airport airport) {
        final Point2D position = airport.getPosition();
        final double xOffset = xOffset(position);

        if (!mapVariables.isContainedInExpandedWorldView(position.getX() + xOffset, position.getY())) {
            return;
        }

        final double x = (int) mapVariables.toCanvasX(position.getX() + xOffset);
        final double y = (int) mapVariables.toCanvasY(position.getY());

        final Color color = airportColor.get();
        c.setFill(color == null ? Color.WHITE : color);
        painterHelper.fillRect(c, x - 2, y, 5, 1);
        painterHelper.fillRect(c, x, y - 2, 1, 5);

        final double textScale = c.getFont().getSize() / 12.0;
        final double height = Math.ceil(textScale * PENNON_HEIGHT);
        final double poleLength = height / -POLE_Y;

        final double baseX = x + 0.5;
        final double baseY = y + 0.5;
        final double tipX = baseX + POLE_X * poleLength;
        final double tipY = baseY + POLE_Y * poleLength;

        c.setLineDashes(null);
        c.setLineWidth(1);
        c.setStroke(pennonColor);
        painterHelper.strokeLine(c, baseX, baseY, tipX, tipY);

        final double flagLength = poleLength * FLAG_LENGTH;
        final double flagWidth = poleLength * FLAG_WIDTH;

        // The flag flies to the right of the pole, perpendicular to it
        xs[0] = tipX;
        ys[0] = tipY;
        xs[1] = tipX - POLE_X * flagLength;
        ys[1] = tipY - POLE_Y * flagLength;
        xs[2] = tipX - POLE_X * flagLength / 2.0 - POLE_Y * flagWidth;
        ys[2] = tipY - POLE_Y * flagLength / 2.0 + POLE_X * flagWidth;

        c.setFill(pennonColor);
        c.fillPolygon(xs, ys, 3);
    }

    private double xOffset(final Point2D point) {
        final double centerX = mapVariables.toCanvasX(point.getX());

        if (centerX < 0) {
            return 360;
        } else if (centerX > mapVariables.getViewWidth()) {
            return -360;
        } else {
            return 0;
        }
    }
}
