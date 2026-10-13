package net.marvk.fs.vatsim.map.data.airspace;

import javafx.geometry.Point2D;
import net.marvk.fs.vatsim.map.data.Polygon;

import java.util.List;

/**
 * A boundary from the SimAware TRACON Project.
 */
public class TraconBoundary {
    private final String id;
    private final String name;
    private final List<String> prefixes;
    private final String suffix;
    private final List<Polygon> polygons;

    private Point2D label;

    public TraconBoundary(final String id, final String name, final List<String> prefixes, final String suffix, final List<Polygon> polygons, final Point2D label) {
        this.id = id;
        this.name = name;
        this.prefixes = List.copyOf(prefixes);
        this.suffix = suffix;
        this.polygons = List.copyOf(polygons);
        this.label = label;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<String> getPrefixes() {
        return prefixes;
    }

    /**
     * @return the callsign suffix this boundary is restricted to, {@code null} if it applies to approach and departure
     */
    public String getSuffix() {
        return suffix;
    }

    public List<Polygon> getPolygons() {
        return polygons;
    }

    public Point2D getLabel() {
        if (label == null) {
            label = largestPolygon().getExteriorRing().getPolyLabel();
        }

        return label;
    }

    private Polygon largestPolygon() {
        Polygon result = polygons.get(0);

        for (final Polygon polygon : polygons) {
            if (area(polygon) > area(result)) {
                result = polygon;
            }
        }

        return result;
    }

    private static double area(final Polygon polygon) {
        return polygon.boundary().getWidth() * polygon.boundary().getHeight();
    }
}
