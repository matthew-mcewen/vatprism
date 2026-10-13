package net.marvk.fs.vatsim.map.data.airspace;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.scene.paint.Color;
import lombok.extern.log4j.Log4j2;
import net.marvk.fs.vatsim.map.data.Polygon;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * The VATGlasses data set: three dimensional sectors and the positions which control them.
 */
@Log4j2
public class VatglassesData {
    public static final VatglassesData EMPTY = new VatglassesData(Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());

    private static final String DATA_DIRECTORY = "/data/";
    private static final String JSON_EXTENSION = ".json";
    // Fallback shapes for regions without data, these are already covered by the FIR boundaries
    private static final String NO_DATA_DATASET = "nodata";
    private static final String DEFAULT_OWNERSHIP = "ownership/default.json";

    // 8.33 kHz channels are published with varying precision, neighbouring channel names are at least 5 kHz apart
    private static final int FREQUENCY_TOLERANCE_KHZ = 5;

    private static final Color FALLBACK_COLOUR = Color.GRAY;

    private final List<VgSector> sectors;
    private final Map<String, VgPosition> positions;
    private final Map<String, String> defaultRunways;

    private final Map<VgSector, List<VgPosition>> owners = new HashMap<>();
    private final Map<String, List<VgPosition>> positionsByPrefixAndType = new HashMap<>();

    VatglassesData(final List<VgSector> sectors, final Map<String, VgPosition> positions, final Map<String, String> defaultRunways) {
        this.sectors = List.copyOf(sectors);
        this.positions = Map.copyOf(positions);
        this.defaultRunways = Map.copyOf(defaultRunways);

        for (final VgSector sector : sectors) {
            final List<VgPosition> sectorOwners = new ArrayList<>(sector.getOwnerKeys().size());
            for (final String ownerKey : sector.getOwnerKeys()) {
                final VgPosition owner = positions.get(ownerKey);
                if (owner != null) {
                    sectorOwners.add(owner);
                }
            }
            owners.put(sector, sectorOwners);
        }

        for (final VgPosition position : positions.values()) {
            for (final String prefix : position.getPrefixes()) {
                positionsByPrefixAndType
                        .computeIfAbsent(indexKey(prefix, position.getType()), e -> new ArrayList<>(1))
                        .add(position);
            }
        }
    }

    public List<VgSector> getSectors() {
        return sectors;
    }

    public VgPosition getPosition(final String key) {
        return positions.get(key);
    }

    public boolean isEmpty() {
        return sectors.isEmpty();
    }

    /**
     * Determines which sub sectors are controlled by the passed stations, at all levels.
     *
     * @return the controlled sub sectors, grouped by colour
     */
    public List<ActiveSubSector> resolve(final Collection<? extends AtcStation> stations, final ZonedDateTime now) {
        return resolve(stations, now, null);
    }

    /**
     * Determines which sub sectors are controlled by the passed stations, at all levels.
     *
     * @param fixedColour the colour of all sub sectors, {@code null} to use the colours of the positions
     *
     * @return the controlled sub sectors, grouped by colour
     */
    public List<ActiveSubSector> resolve(final Collection<? extends AtcStation> stations, final ZonedDateTime now, final Color fixedColour) {
        if (sectors.isEmpty()) {
            return Collections.emptyList();
        }

        final Map<VgPosition, String> onlineCallsigns = onlinePositions(stations);
        final Set<VgPosition> online = onlineCallsigns.keySet();

        if (online.isEmpty()) {
            return Collections.emptyList();
        }

        final int minuteOfWeek = minuteOfWeek(now);

        final Map<VgPosition, Color> colours = new HashMap<>();
        final List<ActiveSubSector> result = new ArrayList<>();

        for (final VgSector sector : sectors) {
            final VgPosition owner = firstOnline(owners.get(sector), online);

            if (owner == null || !isActiveAt(sector, minuteOfWeek) || !isRunwayConfigurationActive(sector.getRunways())) {
                continue;
            }

            final Color colour = fixedColour == null
                    ? colours.computeIfAbsent(owner, e -> colour(e, sector, online))
                    : fixedColour;

            for (final VgSubSector subSector : sector.getSubSectors()) {
                if (isRunwayConfigurationActive(subSector.getRunways())) {
                    result.add(new ActiveSubSector(subSector, owner, onlineCallsigns.get(owner), colour));
                }
            }
        }

        // Group by colour for batched painting, then by owner for labelling
        result.sort(Comparator
                .<ActiveSubSector>comparingInt(e -> e.getColour().hashCode())
                .thenComparing(e -> e.getOwner().getKey())
        );

        return result;
    }

    /**
     * @return the online positions with the callsign of the first station they were matched to
     */
    Map<VgPosition, String> onlinePositions(final Collection<? extends AtcStation> stations) {
        final Map<VgPosition, String> result = new HashMap<>();

        for (final AtcStation station : stations) {
            for (final VgPosition position : positionsOf(station)) {
                result.putIfAbsent(position, station.getCallsign());
            }
        }

        return result;
    }

    /**
     * @return the positions the station is working, matched by callsign prefix, callsign suffix and frequency. A
     * station without a frequency, e.g. a booked one, is only matched if there is no other candidate
     */
    public List<VgPosition> positionsOf(final AtcStation station) {
        final String callsign = station.getCallsign();

        if (callsign == null) {
            return Collections.emptyList();
        }

        final int firstSeparator = callsign.indexOf('_');

        if (firstSeparator <= 0) {
            return Collections.emptyList();
        }

        final String prefix = callsign.substring(0, firstSeparator);
        final String type = callsign.substring(callsign.lastIndexOf('_') + 1);

        final List<VgPosition> candidates = positionsByPrefixAndType.get(indexKey(prefix, type));

        if (candidates == null) {
            return Collections.emptyList();
        }

        final int frequency = parseFrequency(station.getFrequency());

        if (frequency == VgPosition.ANY_FREQUENCY && candidates.size() == 1) {
            return List.copyOf(candidates);
        }

        final List<VgPosition> result = new ArrayList<>(1);

        for (final VgPosition candidate : candidates) {
            final int candidateFrequency = candidate.getFrequencyKhz();
            if (candidateFrequency == VgPosition.ANY_FREQUENCY || Math.abs(candidateFrequency - frequency) < FREQUENCY_TOLERANCE_KHZ) {
                result.add(candidate);
            }
        }

        return result;
    }

    private static VgPosition firstOnline(final List<VgPosition> candidates, final Set<VgPosition> online) {
        for (final VgPosition candidate : candidates) {
            if (online.contains(candidate)) {
                return candidate;
            }
        }

        return null;
    }

    private static boolean isActiveAt(final VgSector sector, final int minuteOfWeek) {
        final List<VgSector.TimeWindow> windows = sector.getTimeWindows();

        if (windows.isEmpty()) {
            return true;
        }

        for (final VgSector.TimeWindow window : windows) {
            if (window.contains(minuteOfWeek)) {
                return true;
            }
        }

        return false;
    }

    /**
     * The active runways of an airport are unknown, so the first configuration listed for the airport is assumed.
     */
    private boolean isRunwayConfigurationActive(final List<VgSector.RunwayCondition> conditions) {
        for (final VgSector.RunwayCondition condition : conditions) {
            final String active = defaultRunways.get(condition.getIcao());

            if (active == null || !condition.getConfigurations().contains(active)) {
                return false;
            }
        }

        return true;
    }

    private Color colour(final VgPosition owner, final VgSector sector, final Set<VgPosition> online) {
        for (final VgPosition.ColourRule rule : owner.getColours()) {
            if (isApplicable(rule, online)) {
                return rule.getColour();
            }
        }

        return sector.getGroupColour() == null ? FALLBACK_COLOUR : sector.getGroupColour();
    }

    private boolean isApplicable(final VgPosition.ColourRule rule, final Set<VgPosition> online) {
        for (final String key : rule.getRequiredOnlineKeys()) {
            final VgPosition required = positions.get(key);

            if (required == null || !online.contains(required)) {
                return false;
            }
        }

        return true;
    }

    private static int minuteOfWeek(final ZonedDateTime now) {
        final ZonedDateTime utc = now.withZoneSameInstant(ZoneOffset.UTC);

        return (utc.getDayOfWeek().getValue() - 1) * VgSector.TimeWindow.MINUTES_PER_DAY + utc.getHour() * 60 + utc.getMinute();
    }

    private static String indexKey(final String prefix, final String type) {
        return prefix.toUpperCase(Locale.ROOT) + "|" + type.toUpperCase(Locale.ROOT);
    }

    static int parseFrequency(final String frequency) {
        if (frequency == null || frequency.isBlank()) {
            return VgPosition.ANY_FREQUENCY;
        }

        try {
            return (int) Math.round(Double.parseDouble(frequency.strip()) * 1000);
        } catch (final NumberFormatException e) {
            return VgPosition.ANY_FREQUENCY;
        }
    }

    /**
     * @param coordinate latitude or longitude in the format {@code [-]dddmmss}
     *
     * @return decimal degrees
     */
    static double parseCoordinate(final String coordinate) {
        final String stripped = coordinate.strip();
        final double value = Math.abs(Double.parseDouble(stripped));

        final double degrees = Math.floor(value / 10000);
        final double minutes = Math.floor((value - degrees * 10000) / 100);
        final double seconds = value - degrees * 10000 - minutes * 100;

        final double result = degrees + minutes / 60 + seconds / 3600;

        return stripped.startsWith("-") ? -result : result;
    }

    /**
     * @param in a zip archive of the VATGlasses data repository
     */
    public static VatglassesData parseZip(final InputStream in) throws IOException {
        final Map<String, Map<String, JsonObject>> datasets = new TreeMap<>();

        try (final ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                final String name = entry.getName();
                final int dataIndex = name.indexOf(DATA_DIRECTORY);

                if (entry.isDirectory() || dataIndex < 0 || !name.endsWith(JSON_EXTENSION)) {
                    continue;
                }

                addFile(datasets, name.substring(dataIndex + DATA_DIRECTORY.length()), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }

        return build(datasets);
    }

    /**
     * @param files file contents by their path relative to the data directory, e.g. {@code eg.json} or
     *              {@code zab/airspace.json}
     */
    public static VatglassesData parse(final Map<String, String> files) {
        final Map<String, Map<String, JsonObject>> datasets = new TreeMap<>();

        for (final Map.Entry<String, String> file : files.entrySet()) {
            addFile(datasets, file.getKey(), file.getValue());
        }

        return build(datasets);
    }

    private static void addFile(final Map<String, Map<String, JsonObject>> datasets, final String path, final String content) {
        final int separator = path.indexOf('/');
        final boolean isStatic = separator < 0;

        final String dataset = isStatic ? path.substring(0, path.length() - JSON_EXTENSION.length()) : path.substring(0, separator);
        final String part = isStatic ? "" : path.substring(separator + 1);

        if (NO_DATA_DATASET.equals(dataset)) {
            return;
        }

        try {
            // Some files start with a byte order mark
            final JsonElement json = JsonParser.parseString(content.startsWith("\uFEFF") ? content.substring(1) : content);
            datasets.computeIfAbsent(dataset, e -> new HashMap<>()).put(part, json.getAsJsonObject());
        } catch (final RuntimeException e) {
            log.warn("Skipping malformed VATGlasses file %s: %s".formatted(path, e.getMessage()));
        }
    }

    private static VatglassesData build(final Map<String, Map<String, JsonObject>> datasets) {
        final Builder builder = new Builder();

        for (final Map.Entry<String, Map<String, JsonObject>> dataset : datasets.entrySet()) {
            try {
                builder.addDataset(dataset.getKey(), dataset.getValue());
            } catch (final RuntimeException e) {
                log.warn("Skipping malformed VATGlasses data set %s: %s".formatted(dataset.getKey(), e.getMessage()));
            }
        }

        log.info("Loaded %d VATGlasses sectors and %d positions from %d data sets"
                .formatted(builder.sectors.size(), builder.positions.size(), datasets.size()));

        return new VatglassesData(builder.sectors, builder.positions, builder.defaultRunways);
    }

    private static class Builder {
        private final List<VgSector> sectors = new ArrayList<>();
        private final Map<String, VgPosition> positions = new HashMap<>();
        private final Map<String, String> defaultRunways = new HashMap<>();
        private final Map<String, Color> colourCache = new HashMap<>();

        private void addDataset(final String dataset, final Map<String, JsonObject> parts) {
            final JsonObject staticData = parts.get("");
            final JsonObject airspaceData = staticData != null ? staticData : parts.get("airspace.json");
            final JsonObject positionData = staticData != null ? staticData : parts.get("positions.json");
            final JsonObject ownership = parts.get(DEFAULT_OWNERSHIP);

            if (positionData != null && isObject(positionData, "positions")) {
                for (final Map.Entry<String, JsonElement> e : positionData.getAsJsonObject("positions").entrySet()) {
                    addPosition(dataset, e.getKey(), e.getValue());
                }
            }

            if (airspaceData == null) {
                return;
            }

            if (isObject(airspaceData, "airports")) {
                for (final Map.Entry<String, JsonElement> e : airspaceData.getAsJsonObject("airports").entrySet()) {
                    addAirport(e.getKey(), e.getValue());
                }
            }

            final JsonObject groups = isObject(airspaceData, "groups") ? airspaceData.getAsJsonObject("groups") : new JsonObject();
            final JsonObject ownershipAirspace = ownership != null && isObject(ownership, "airspace")
                    ? ownership.getAsJsonObject("airspace")
                    : new JsonObject();

            final JsonElement airspace = airspaceData.get("airspace");

            if (airspace == null) {
                return;
            }

            if (airspace.isJsonArray()) {
                for (final JsonElement sector : airspace.getAsJsonArray()) {
                    addSector(dataset, sector, null, groups);
                }
            } else if (airspace.isJsonObject()) {
                for (final Map.Entry<String, JsonElement> e : airspace.getAsJsonObject().entrySet()) {
                    addSector(dataset, e.getValue(), ownershipAirspace.get(e.getKey()), groups);
                }
            }
        }

        private void addPosition(final String dataset, final String id, final JsonElement element) {
            try {
                final JsonObject position = element.getAsJsonObject();

                final List<String> prefixes = strings(position.get("pre"));
                final String type = string(position, "type");

                if (prefixes.isEmpty() || type == null) {
                    return;
                }

                final List<VgPosition.ColourRule> colours = new ArrayList<>();
                if (position.has("colours") && position.get("colours").isJsonArray()) {
                    for (final JsonElement e : position.getAsJsonArray("colours")) {
                        final JsonObject rule = e.getAsJsonObject();
                        final Color colour = colour(string(rule, "hex"));
                        if (colour != null) {
                            colours.add(new VgPosition.ColourRule(colour, keys(dataset, strings(rule.get("online")))));
                        }
                    }
                }

                final String key = key(dataset, id);

                positions.put(key, new VgPosition(
                        key,
                        string(position, "callsign"),
                        prefixes,
                        type,
                        parseFrequency(string(position, "frequency")),
                        colours
                ));
            } catch (final RuntimeException e) {
                log.debug("Skipping malformed VATGlasses position %s/%s: %s".formatted(dataset, id, e.getMessage()));
            }
        }

        private void addAirport(final String icao, final JsonElement element) {
            if (!element.isJsonObject()) {
                return;
            }

            final List<String> runways = strings(element.getAsJsonObject().get("runways"));

            if (!runways.isEmpty()) {
                defaultRunways.putIfAbsent(icao, runways.get(0));
            }
        }

        private void addSector(final String dataset, final JsonElement element, final JsonElement owner, final JsonObject groups) {
            try {
                final JsonObject json = element.getAsJsonObject();

                final String groupKey = string(json, "group");
                final Color groupColour = groupKey != null && isObject(groups, groupKey)
                        ? colour(string(groups.getAsJsonObject(groupKey), "colour"))
                        : null;

                final VgSector sector = new VgSector(
                        string(json, "id"),
                        groupColour,
                        keys(dataset, strings(owner != null ? owner : json.get("owner"))),
                        runwayConditions(json.get("runways")),
                        timeWindows(json.get("fua"))
                );

                final List<VgSubSector> subSectors = new ArrayList<>();

                for (final JsonElement e : json.getAsJsonArray("sectors")) {
                    final JsonObject subSector = e.getAsJsonObject();
                    final Polygon polygon = polygon(subSector.getAsJsonArray("points"), sector.getId());

                    if (polygon == null) {
                        continue;
                    }

                    subSectors.add(new VgSubSector(
                            sector,
                            level(subSector, "min", VgSubSector.NO_MIN),
                            level(subSector, "max", VgSubSector.NO_MAX),
                            runwayConditions(subSector.get("runways")),
                            polygon
                    ));
                }

                if (!subSectors.isEmpty()) {
                    sector.setSubSectors(subSectors);
                    sectors.add(sector);
                }
            } catch (final RuntimeException e) {
                log.debug("Skipping malformed VATGlasses sector in %s: %s".formatted(dataset, e.getMessage()));
            }
        }

        private static Polygon polygon(final JsonArray points, final String name) {
            final int n = points.size();

            if (n < 3) {
                return null;
            }

            final double[] xs = new double[n];
            final double[] ys = new double[n];

            for (int i = 0; i < n; i++) {
                final JsonArray point = points.get(i).getAsJsonArray();
                ys[i] = parseCoordinate(point.get(0).getAsString());
                xs[i] = parseCoordinate(point.get(1).getAsString());
            }

            return AirspacePolygons.of(Collections.singletonList(new double[][]{xs, ys}), name);
        }

        private static int level(final JsonObject subSector, final String key, final int defaultValue) {
            if (!subSector.has(key) || subSector.get(key).isJsonNull()) {
                return defaultValue;
            }

            return (int) Math.round(subSector.get(key).getAsDouble());
        }

        private static List<VgSector.RunwayCondition> runwayConditions(final JsonElement element) {
            if (element == null || !element.isJsonArray()) {
                return Collections.emptyList();
            }

            final List<VgSector.RunwayCondition> result = new ArrayList<>();

            for (final JsonElement e : element.getAsJsonArray()) {
                final JsonObject condition = e.getAsJsonObject();
                result.add(new VgSector.RunwayCondition(string(condition, "icao"), strings(condition.get("runway"))));
            }

            return result;
        }

        private static List<VgSector.TimeWindow> timeWindows(final JsonElement element) {
            if (element == null || !element.isJsonArray()) {
                return Collections.emptyList();
            }

            final List<VgSector.TimeWindow> result = new ArrayList<>();

            for (final JsonElement e : element.getAsJsonArray()) {
                final JsonObject start = e.getAsJsonObject().getAsJsonObject("start");
                final JsonObject end = e.getAsJsonObject().getAsJsonObject("end");

                final boolean daily = !start.has("day") || !end.has("day");

                result.add(new VgSector.TimeWindow(daily, minutes(start, daily), minutes(end, daily)));
            }

            return result;
        }

        private static int minutes(final JsonObject time, final boolean daily) {
            final int minuteOfDay = integer(time, "hour") * 60 + integer(time, "minute");

            if (daily) {
                return minuteOfDay;
            }

            // Days are numbered from monday = 1 to sunday = 7
            final int day = Math.floorMod(integer(time, "day") - 1, 7);

            return day * VgSector.TimeWindow.MINUTES_PER_DAY + minuteOfDay;
        }

        private static int integer(final JsonObject object, final String key) {
            return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsInt() : 0;
        }

        private Color colour(final String hex) {
            if (hex == null) {
                return null;
            }

            return colourCache.computeIfAbsent(hex.toLowerCase(Locale.ROOT), e -> {
                try {
                    return Color.web(e);
                } catch (final IllegalArgumentException exception) {
                    return FALLBACK_COLOUR;
                }
            });
        }

        private static boolean isObject(final JsonObject object, final String key) {
            return object.has(key) && object.get(key).isJsonObject();
        }

        private static String string(final JsonObject object, final String key) {
            final JsonElement element = object.get(key);

            return element == null || !element.isJsonPrimitive() ? null : element.getAsString();
        }

        /**
         * @return the strings of an element which is either a single string or an array of strings
         */
        private static List<String> strings(final JsonElement element) {
            if (element == null || element.isJsonNull()) {
                return Collections.emptyList();
            }

            if (element.isJsonPrimitive()) {
                return List.of(element.getAsString());
            }

            if (!element.isJsonArray()) {
                return Collections.emptyList();
            }

            final List<String> result = new ArrayList<>(element.getAsJsonArray().size());

            for (final JsonElement e : element.getAsJsonArray()) {
                if (e.isJsonPrimitive()) {
                    result.add(e.getAsString());
                }
            }

            return result;
        }

        private static List<String> keys(final String dataset, final List<String> ids) {
            final List<String> result = new ArrayList<>(ids.size());

            for (final String id : ids) {
                result.add(key(dataset, id));
            }

            return result;
        }

        /**
         * Positions of other data sets are referenced in the format {@code dataset/position}.
         */
        private static String key(final String dataset, final String id) {
            return id.indexOf('/') >= 0 ? id : dataset + "/" + id;
        }
    }
}
