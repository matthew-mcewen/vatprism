package net.marvk.fs.vatsim.map.data.airspace;

import javafx.geometry.Point2D;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;
import java.util.stream.Collectors;

class TraconRepositoryTest {
    private static final String SQUARE = "[[[[-1, -1], [1, -1], [1, 1], [-1, 1], [-1, -1]]]]";

    private static final String GEO_JSON = """
            {
              "type": "FeatureCollection",
              "features": [
                %s,
                %s,
                %s,
                %s,
                %s,
                {"type": "Feature", "properties": {"id": "BROKEN"}, "geometry": null}
              ]
            }
            """.formatted(
            feature("SCT", "[\"LAX\"]", null, "MultiPolygon", SQUARE),
            feature("SCT_U", "[\"LAX_U\"]", null, "MultiPolygon", SQUARE),
            feature("SCT_DEP", "[\"LAX\"]", "DEP", "MultiPolygon", SQUARE),
            feature("AYGA", "[\"AYGA\"]", "TWR", "Polygon", "[[[-1, -1], [1, -1], [1, 1], [-1, 1], [-1, -1]]]"),
            feature("NFFN", "[\"NFFN\", \"NAN\"]", null, "MultiPolygon", "[[[[179, -1], [-179, -1], [-179, 1], [179, 1], [179, -1]]]]")
    );

    private final TraconRepository repository = TraconRepository.parse(new StringReader(GEO_JSON));

    private static String feature(final String id, final String prefix, final String suffix, final String type, final String coordinates) {
        return """
                {
                  "type": "Feature",
                  "properties": {"id": "%s", "prefix": %s, "name": "%s Approach"%s},
                  "geometry": {"type": "%s", "coordinates": %s}
                }
                """.formatted(id, prefix, id, suffix == null ? "" : ", \"suffix\": \"" + suffix + "\"", type, coordinates);
    }

    private List<String> match(final String callsign) {
        return repository.match(callsign).stream().map(TraconBoundary::getId).collect(Collectors.toList());
    }

    @Test
    void skipsMalformedFeatures() {
        Assertions.assertEquals(5, repository.list().size());
    }

    @Test
    void matchesApproachToBoundaryWithoutSuffix() {
        Assertions.assertEquals(List.of("SCT"), match("LAX_APP"));
    }

    @Test
    void prefersLongestPrefix() {
        Assertions.assertEquals(List.of("SCT_U"), match("LAX_U_APP"));
        Assertions.assertEquals(List.of("SCT"), match("LAX_X_APP"));
    }

    @Test
    void prefersBoundaryWithMatchingSuffix() {
        Assertions.assertEquals(List.of("SCT_DEP"), match("LAX_DEP"));
        Assertions.assertEquals(List.of("SCT_U"), match("LAX_U_DEP"));
    }

    @Test
    void matchesBoundaryWithoutSuffixOnlyToApproachAndDeparture() {
        Assertions.assertEquals(List.of(), match("LAX_TWR"));
        Assertions.assertEquals(List.of(), match("LAX_CTR"));
        Assertions.assertEquals(List.of("AYGA"), match("AYGA_TWR"));
        Assertions.assertEquals(List.of(), match("AYGA_APP"));
    }

    @Test
    void matchesAnyPrefix() {
        Assertions.assertEquals(List.of("NFFN"), match("NAN_APP"));
        Assertions.assertEquals(List.of("NFFN"), match("nffn_app"));
    }

    @Test
    void ignoresUnknownAndMalformedCallsigns() {
        Assertions.assertEquals(List.of(), match("EDDF_APP"));
        Assertions.assertEquals(List.of(), match("LAX"));
        Assertions.assertEquals(List.of(), match(null));
    }

    @Test
    void keepsPolygonsCrossingTheAntimeridianInOnePiece() {
        final TraconBoundary boundary = repository.match("NFFN_APP").get(0);

        Assertions.assertEquals(2, boundary.getPolygons().get(0).boundary().getWidth(), 1e-9);
    }

    @Test
    void measuresTheDistanceToABoundary() {
        final TraconBoundary boundary = repository.match("LAX_APP").get(0);

        Assertions.assertEquals(0, boundary.distance(new Point2D(0.5, -0.5)), 1e-9);
        Assertions.assertTrue(boundary.distance(new Point2D(2, 0)) >= 1);

        final TraconBoundary antimeridian = repository.match("NFFN_APP").get(0);

        Assertions.assertEquals(0, antimeridian.distance(new Point2D(179.5, 0)), 1e-9);
        Assertions.assertEquals(0, antimeridian.distance(new Point2D(-179.5, 0)), 1e-9);
        Assertions.assertTrue(antimeridian.distance(new Point2D(0, 0)) > 100);
    }

    @Test
    void parsesPolygonAndMultiPolygon() {
        Assertions.assertEquals(1, repository.match("AYGA_TWR").get(0).getPolygons().size());
        Assertions.assertEquals(1, repository.match("LAX_APP").get(0).getPolygons().size());
    }
}
