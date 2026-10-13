package net.marvk.fs.vatsim.map.data.airspace;

import net.marvk.fs.vatsim.map.data.Polygon;

import java.util.List;

final class AirspacePolygons {
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
