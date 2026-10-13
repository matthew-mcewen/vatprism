package net.marvk.fs.vatsim.map.data.airspace;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.geometry.Point2D;
import lombok.extern.log4j.Log4j2;
import net.marvk.fs.vatsim.map.data.Polygon;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Boundaries of the SimAware TRACON Project, indexed by callsign prefix and suffix.
 */
@Log4j2
public class TraconRepository {
    public static final TraconRepository EMPTY = new TraconRepository(Collections.emptyList());

    private static final String SEPARATOR = "_";
    private static final String NO_SUFFIX = "";

    private final List<TraconBoundary> boundaries;
    private final Map<String, List<TraconBoundary>> index = new HashMap<>();

    public TraconRepository(final List<TraconBoundary> boundaries) {
        this.boundaries = List.copyOf(boundaries);

        for (final TraconBoundary boundary : boundaries) {
            final String suffix = boundary.getSuffix() == null ? NO_SUFFIX : boundary.getSuffix();
            for (final String prefix : boundary.getPrefixes()) {
                index.computeIfAbsent(key(prefix, suffix), e -> new ArrayList<>(1)).add(boundary);
            }
        }
    }

    public List<TraconBoundary> list() {
        return boundaries;
    }

    /**
     * Finds the boundaries for a controller callsign. The longest matching prefix wins, e.g. {@code LAX_U_APP} prefers
     * a boundary for {@code LAX_U} over one for {@code LAX}. Boundaries without a suffix apply to approach and
     * departure only.
     */
    public List<TraconBoundary> match(final String callsign) {
        if (callsign == null || index.isEmpty()) {
            return Collections.emptyList();
        }

        final String[] parts = callsign.toUpperCase(Locale.ROOT).split(SEPARATOR);

        if (parts.length < 2) {
            return Collections.emptyList();
        }

        final String type = parts[parts.length - 1];
        final boolean approachOrDeparture = "APP".equals(type) || "DEP".equals(type);

        for (int prefixLength = parts.length - 1; prefixLength >= 1; prefixLength--) {
            final String prefix = join(parts, 0, prefixLength);

            final List<TraconBoundary> exactSuffix = index.get(key(prefix, join(parts, prefixLength, parts.length)));
            if (exactSuffix != null) {
                return exactSuffix;
            }

            final List<TraconBoundary> exactType = index.get(key(prefix, type));
            if (exactType != null) {
                return exactType;
            }

            if (approachOrDeparture) {
                final List<TraconBoundary> noSuffix = index.get(key(prefix, NO_SUFFIX));
                if (noSuffix != null) {
                    return noSuffix;
                }
            }
        }

        return Collections.emptyList();
    }

    private static String join(final String[] parts, final int from, final int to) {
        if (to - from == 1) {
            return parts[from];
        }

        return String.join(SEPARATOR, List.of(parts).subList(from, to));
    }

    private static String key(final String prefix, final String suffix) {
        return prefix + "|" + suffix;
    }

    public static TraconRepository parse(final Reader geoJson) {
        final JsonObject root = JsonParser.parseReader(geoJson).getAsJsonObject();
        final JsonArray features = root.getAsJsonArray("features");

        final List<TraconBoundary> result = new ArrayList<>(features.size());

        for (final JsonElement feature : features) {
            try {
                result.add(parseFeature(feature.getAsJsonObject()));
            } catch (final RuntimeException e) {
                log.warn("Skipping malformed TRACON boundary: %s".formatted(e.getMessage()));
            }
        }

        log.info("Loaded %d TRACON boundaries".formatted(result.size()));

        return new TraconRepository(result);
    }

    private static TraconBoundary parseFeature(final JsonObject feature) {
        final JsonObject properties = feature.getAsJsonObject("properties");
        final String id = properties.get("id").getAsString();

        final List<String> prefixes = new ArrayList<>();
        final JsonElement prefix = properties.get("prefix");
        if (prefix.isJsonArray()) {
            for (final JsonElement e : prefix.getAsJsonArray()) {
                prefixes.add(e.getAsString().toUpperCase(Locale.ROOT));
            }
        } else {
            prefixes.add(prefix.getAsString().toUpperCase(Locale.ROOT));
        }

        final String name = isPresent(properties, "name") ? properties.get("name").getAsString() : id;
        final String suffix = isPresent(properties, "suffix")
                ? properties.get("suffix").getAsString().toUpperCase(Locale.ROOT)
                : null;

        final Point2D label = isPresent(properties, "label_lon") && isPresent(properties, "label_lat")
                ? new Point2D(properties.get("label_lon").getAsDouble(), properties.get("label_lat").getAsDouble())
                : null;

        final JsonObject geometry = feature.getAsJsonObject("geometry");
        final JsonArray coordinates = geometry.getAsJsonArray("coordinates");

        final List<Polygon> polygons = new ArrayList<>();

        switch (geometry.get("type").getAsString()) {
            case "Polygon" -> polygons.add(parsePolygon(coordinates, id));
            case "MultiPolygon" -> {
                for (final JsonElement polygon : coordinates) {
                    polygons.add(parsePolygon(polygon.getAsJsonArray(), id));
                }
            }
            default -> throw new IllegalArgumentException("Unsupported geometry in " + id);
        }

        if (polygons.isEmpty()) {
            throw new IllegalArgumentException("No polygons in " + id);
        }

        return new TraconBoundary(id, name, prefixes, suffix, polygons, label);
    }

    private static boolean isPresent(final JsonObject object, final String key) {
        return object.has(key) && !object.get(key).isJsonNull();
    }

    private static Polygon parsePolygon(final JsonArray rings, final String name) {
        final List<double[][]> result = new ArrayList<>(rings.size());

        for (final JsonElement ringElement : rings) {
            final JsonArray ring = ringElement.getAsJsonArray();
            final int n = ring.size();

            final double[] xs = new double[n];
            final double[] ys = new double[n];

            for (int i = 0; i < n; i++) {
                final JsonArray point = ring.get(i).getAsJsonArray();
                xs[i] = point.get(0).getAsDouble();
                ys[i] = point.get(1).getAsDouble();
            }

            result.add(new double[][]{xs, ys});
        }

        return AirspacePolygons.of(result, name);
    }
}
