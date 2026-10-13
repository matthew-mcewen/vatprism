package net.marvk.fs.vatsim.map.data.airspace;

import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import net.marvk.fs.vatsim.map.data.Polygon;

import java.util.Collection;
import java.util.List;

public final class AirspacePolygons {
    private AirspacePolygons() {
        throw new AssertionError("No instances of utility class " + AirspacePolygons.class);
    }

    /**
     * @param rings exterior ring followed by hole rings, each as {@code [xs, ys]}
     */
    static Polygon of(final List<double[][]> rings, final String name) {
        for (final double[][] ring : rings) {
            unwrapAntimeridian(ring[0]);
        }

        return new Polygon(rings, (ring, i) -> ring[0][i], (ring, i) -> ring[1][i], ring -> ring[0].length, name);
    }

    /**
     * @return the distance of the point to the polygon in degrees, {@code 0} if the point is inside
     */
    static double distance(final Polygon polygon, final Point2D point) {
        if (polygon.hasHoleRings()) {
            // Good enough for hit testing, holes are rare
            return polygon.boundary().contains(point.getX(), point.getY()) ? 0 : Double.MAX_VALUE;
        }

        return polygon.distance(point);
    }

    public static Rectangle2D bounds(final Collection<Polygon> polygons) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        for (final Polygon polygon : polygons) {
            final Rectangle2D boundary = polygon.boundary();
            minX = Math.min(minX, boundary.getMinX());
            minY = Math.min(minY, boundary.getMinY());
            maxX = Math.max(maxX, boundary.getMaxX());
            maxY = Math.max(maxY, boundary.getMaxY());
        }

        return new Rectangle2D(minX, minY, maxX - minX, maxY - minY);
    }

    /**
     * Shifts longitudes such that no edge spans more than half the world, which keeps rings crossing the antimeridian
     * in one piece.
     */
    static void unwrapAntimeridian(final double[] xs) {
        for (int i = 1; i < xs.length; i++) {
            while (xs[i] - xs[i - 1] > 180) {
                xs[i] -= 360;
            }

            while (xs[i] - xs[i - 1] < -180) {
                xs[i] += 360;
            }
        }
    }
}
