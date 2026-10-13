package net.marvk.fs.vatsim.map.data.airspace;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import net.marvk.fs.vatsim.map.data.Controller;
import net.marvk.fs.vatsim.map.data.Data;
import net.marvk.fs.vatsim.map.data.DataVisitor;
import net.marvk.fs.vatsim.map.data.Polygon;

import java.util.List;

/**
 * A boundary from the SimAware TRACON Project.
 */
public class TraconBoundary implements Data {
    private final String id;
    private final String name;
    private final List<String> prefixes;
    private final String suffix;
    private final List<Polygon> polygons;

    private final ObservableList<Controller> controllers = FXCollections.observableArrayList();

    private Point2D label;
    private Rectangle2D bounds;

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

    /**
     * @return the online controllers working this boundary
     */
    public ObservableList<Controller> getControllers() {
        return controllers;
    }

    public Rectangle2D getBounds() {
        if (bounds == null) {
            bounds = AirspacePolygons.bounds(polygons);
        }

        return bounds;
    }

    /**
     * @return the distance to the boundary in degrees, {@code 0} if the point is inside
     */
    public double distance(final Point2D point) {
        double result = Double.MAX_VALUE;

        for (final Polygon polygon : polygons) {
            result = Math.min(result, AirspacePolygons.distance(polygon, point));
        }

        return result;
    }

    public double area() {
        return getBounds().getWidth() * getBounds().getHeight();
    }

    @Override
    public <R> R visit(final DataVisitor<R> visitor) {
        return visitor.visit(this);
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
