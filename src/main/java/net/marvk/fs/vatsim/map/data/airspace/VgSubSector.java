package net.marvk.fs.vatsim.map.data.airspace;

import net.marvk.fs.vatsim.map.data.Polygon;

import java.util.List;

/**
 * A block of airspace with the same lateral boundary at all of its flight levels.
 */
public class VgSubSector {
    static final int NO_MIN = Integer.MIN_VALUE;
    static final int NO_MAX = Integer.MAX_VALUE;

    private final VgSector sector;
    private final int min;
    private final int max;
    private final List<VgSector.RunwayCondition> runways;
    private final Polygon polygon;

    VgSubSector(final VgSector sector, final int min, final int max, final List<VgSector.RunwayCondition> runways, final Polygon polygon) {
        this.sector = sector;
        this.min = min;
        this.max = max;
        this.runways = List.copyOf(runways);
        this.polygon = polygon;
    }

    public VgSector getSector() {
        return sector;
    }

    public Polygon getPolygon() {
        return polygon;
    }

    /**
     * @param level flight level, i.e. altitude in hundreds of feet
     */
    public boolean containsLevel(final int level) {
        return min <= level && level <= max;
    }

    public double distance(final javafx.geometry.Point2D point) {
        return AirspacePolygons.distance(polygon, point);
    }

    List<VgSector.RunwayCondition> getRunways() {
        return runways;
    }
}
