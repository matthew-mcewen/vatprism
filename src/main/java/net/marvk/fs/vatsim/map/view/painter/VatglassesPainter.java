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
 * Paints the VATGlasses sectors which are controlled at the current level. Which sectors those are is precomputed by
 * the airspace service, this painter only culls and draws. All sectors of the same colour are batched into a
 * single path, which keeps the number of draw calls proportional to the number of controllers in view and paints
 * overlapping blocks of the same controller with a uniform opacity.
 */
public class VatglassesPainter extends MapPainter<Void> {
    // Sectors smaller than this on the canvas are not worth a draw call
    private static final double MIN_CANVAS_SIZE = 3;

    @Parameter("Fill")
    private boolean fill = true;
    @Parameter(value = "Fill Opacity", min = 0, max = 1)
    private double fillOpacity = 0.15;
    @Parameter("Stroke")
    private boolean stroke = true;
    @Parameter(value = "Stroke Opacity", min = 0, max = 1)
    private double strokeOpacity = 0.6;
    @Parameter(value = "Stroke Width", min = 0, max = 10)
    private double lineWidth = 1;
    @Parameter("Label")
    private boolean label = true;
    @Parameter(value = "Label Opacity", min = 0, max = 1)
    private double labelOpacity = 0.9;

    private final Supplier<ActiveSubSector[]> visibleSubSectors;

    public VatglassesPainter(final MapVariables mapVariables, final Supplier<ActiveSubSector[]> visibleSubSectors) {
        super(mapVariables);
        this.visibleSubSectors = visibleSubSectors;
    }

    @Override
    public void paint(final GraphicsContext c, final Void unused) {
        final ActiveSubSector[] subSectors = visibleSubSectors.get();
        final int n = subSectors.length;

        if (n == 0) {
            return;
        }

        c.setLineWidth(lineWidth);
        c.setLineDashes(null);

        int start = 0;

        while (start < n) {
            final Color colour = subSectors[start].getColour();

            c.beginPath();

            boolean anyVisible = false;
            int end = start;

            while (end < n && subSectors[end].getColour().equals(colour)) {
                anyVisible |= painterHelper.appendPolygonsToPath(c, subSectors[end].getSubSector().getPolygon(), MIN_CANVAS_SIZE);
                end++;
            }

            if (anyVisible) {
                if (fill) {
                    c.setGlobalAlpha(fillOpacity);
                    c.setFill(colour);
                    painterHelper.fillPath(c);
                }

                if (stroke) {
                    c.setGlobalAlpha(strokeOpacity);
                    c.setStroke(colour);
                    painterHelper.strokePath(c);
                }

                if (label) {
                    c.setGlobalAlpha(labelOpacity);
                    c.setFill(colour);
                    paintLabels(c, subSectors, start, end);
                }
            }

            start = end;
        }

        c.setGlobalAlpha(1);
    }

    /**
     * Paints one label per owner, inside the largest of its sub sectors in view.
     */
    private void paintLabels(final GraphicsContext c, final ActiveSubSector[] subSectors, final int from, final int to) {
        c.setTextAlign(TextAlignment.CENTER);
        c.setTextBaseline(VPos.CENTER);

        int start = from;

        while (start < to) {
            final VgPosition owner = subSectors[start].getOwner();

            ActiveSubSector largest = null;
            double largestArea = 0;
            int end = start;

            while (end < to && subSectors[end].getOwner() == owner) {
                final double area = visibleArea(subSectors[end].getSubSector().getPolygon());

                if (area > largestArea) {
                    largest = subSectors[end];
                    largestArea = area;
                }

                end++;
            }

            if (largest != null) {
                final Point2D position = largest.getSubSector().getPolygon().getExteriorRing().getPolyLabel();

                if (position != null) {
                    painterHelper.fillText(
                            c,
                            largest.getOwnerCallsign(),
                            mapVariables.toCanvasX(position.getX()),
                            mapVariables.toCanvasY(position.getY())
                    );
                }
            }

            start = end;
        }
    }

    private double visibleArea(final Polygon polygon) {
        final Rectangle2D boundary = polygon.boundary();

        if (!mapVariables.isRectIntersectingWorldView(boundary.getMinX(), boundary.getMinY(), boundary.getWidth(), boundary.getHeight())) {
            return 0;
        }

        return boundary.getWidth() * boundary.getHeight();
    }
}
