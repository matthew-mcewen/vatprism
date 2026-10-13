package net.marvk.fs.vatsim.map.view.painter;

import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import net.marvk.fs.vatsim.map.data.Polygon;
import net.marvk.fs.vatsim.map.data.airspace.TraconBoundary;
import net.marvk.fs.vatsim.map.view.map.MapVariables;

public class TraconPainter extends MapPainter<TraconBoundary> {
    private static final int MIN_LABEL_WIDTH = 40;

    @Parameter("Fill")
    private boolean fill = true;
    @Parameter("Fill Color")
    private Color fillColor = Color.web("00a0a0", 0.08);
    @Parameter("Stroke")
    private boolean stroke = true;
    @Parameter("Stroke Color")
    private Color strokeColor = Color.web("007a7a");
    @Parameter(value = "Stroke Width", min = 0, max = 10)
    private double lineWidth = 1;
    @Parameter("Label")
    private boolean label = true;
    @Parameter("Label Color")
    private Color textColor = Color.web("007a7a");

    public TraconPainter(final MapVariables mapVariables) {
        super(mapVariables);
    }

    public TraconPainter(final MapVariables mapVariables, final Color color, final double lineWidth) {
        super(mapVariables);
        this.strokeColor = color;
        this.lineWidth = lineWidth;
        this.fillColor = color.deriveColor(0, 1, 1, 0.05);
        this.textColor = color;
    }

    @Override
    public void paint(final GraphicsContext c, final TraconBoundary boundary) {
        double width = 0;

        for (final Polygon polygon : boundary.getPolygons()) {
            if (fill) {
                c.setFill(fillColor);
                painterHelper.fillPolygons(c, polygon);
            }

            if (stroke) {
                c.setStroke(strokeColor);
                c.setLineWidth(lineWidth);
                c.setLineDashes(null);
                painterHelper.strokePolygons(c, polygon);
            }

            final Rectangle2D bounds = polygon.boundary();
            width = Math.max(width, mapVariables.worldWidthToViewWidth(bounds.getWidth()));
        }

        if (label && width > MIN_LABEL_WIDTH) {
            final Point2D position = boundary.getLabel();

            if (position != null) {
                c.setFill(textColor);
                c.setTextAlign(TextAlignment.CENTER);
                c.setTextBaseline(VPos.CENTER);
                painterHelper.fillText(
                        c,
                        boundary.getId(),
                        mapVariables.toCanvasX(position.getX() + xOffset(position)),
                        mapVariables.toCanvasY(position.getY())
                );
            }
        }
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
