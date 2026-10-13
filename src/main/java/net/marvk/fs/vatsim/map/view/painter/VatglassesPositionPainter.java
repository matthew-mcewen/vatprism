package net.marvk.fs.vatsim.map.view.painter;

import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import net.marvk.fs.vatsim.map.data.Polygon;
import net.marvk.fs.vatsim.map.data.airspace.ActiveSubSector;
import net.marvk.fs.vatsim.map.data.airspace.VgPosition;
import net.marvk.fs.vatsim.map.view.map.MapVariables;

import java.util.function.Supplier;

/**
 * Paints the sectors a single VATGlasses position controls at the current level.
 */
public class VatglassesPositionPainter extends MapPainter<VgPosition> {
    @Parameter("Fill")
    private boolean fill = true;
    @Parameter("Fill Color")
    private Color fillColor;
    @Parameter("Stroke")
    private boolean stroke = true;
    @Parameter("Stroke Color")
    private Color strokeColor;
    @Parameter(value = "Stroke Width", min = 0, max = 10)
    private double lineWidth;
    @Parameter("Label")
    private boolean label = true;
    @Parameter("Label Color")
    private Color textColor;

    private final Supplier<ActiveSubSector[]> visibleSubSectors;

    public VatglassesPositionPainter(final MapVariables mapVariables, final Supplier<ActiveSubSector[]> visibleSubSectors, final Color color, final double lineWidth) {
        super(mapVariables);
        this.visibleSubSectors = visibleSubSectors;
        this.strokeColor = color;
        this.lineWidth = lineWidth;
        this.fillColor = color.deriveColor(0, 1, 1, 0.05);
        this.textColor = color;
    }

    @Override
    public void paint(final GraphicsContext c, final VgPosition position) {
        Polygon largest = null;
        double largestArea = 0;
        boolean anyVisible = false;

        c.beginPath();

        for (final ActiveSubSector subSector : visibleSubSectors.get()) {
            if (subSector.getOwner() != position) {
                continue;
            }

            final Polygon polygon = subSector.getSubSector().getPolygon();

            if (painterHelper.appendPolygonsToPath(c, polygon, 0)) {
                anyVisible = true;

                final Rectangle2D boundary = polygon.boundary();
                final double area = boundary.getWidth() * boundary.getHeight();

                if (area > largestArea) {
                    largest = polygon;
                    largestArea = area;
                }
            }
        }

        if (!anyVisible) {
            return;
        }

        if (fill) {
            c.setFill(fillColor);
            painterHelper.fillPath(c);
        }

        if (stroke) {
            c.setStroke(strokeColor);
            c.setLineWidth(lineWidth);
            c.setLineDashes(null);
            painterHelper.strokePath(c);
        }

        if (label && largest != null) {
            final Point2D labelPosition = largest.getExteriorRing().getPolyLabel();

            if (labelPosition != null) {
                c.setFill(textColor);
                c.setTextAlign(TextAlignment.CENTER);
                c.setTextBaseline(VPos.CENTER);
                painterHelper.fillText(
                        c,
                        position.getDisplayName(),
                        mapVariables.toCanvasX(labelPosition.getX()),
                        mapVariables.toCanvasY(labelPosition.getY())
                );
            }
        }
    }
}
