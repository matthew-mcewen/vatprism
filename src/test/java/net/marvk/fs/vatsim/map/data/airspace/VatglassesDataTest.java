package net.marvk.fs.vatsim.map.data.airspace;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

class VatglassesDataTest {
    private static final String POINTS = "[[\"500000\", \"-0010000\"], [\"510000\", \"-0010000\"], [\"510000\", \"0010000\"], [\"500000\", \"0010000\"]]";

    private static final String STATIC = """
            {
              "airspace": [
                {
                  "id": "Upper", "group": "A", "owner": ["UP", "LOW", "xx/FAR"],
                  "sectors": [{"min": 245, "max": 660, "points": %1$s}]
                },
                {
                  "id": "Lower", "group": "A", "owner": ["LOW"],
                  "sectors": [
                    {"max": 244.0, "points": %1$s},
                    {"max": 100, "runways": [{"icao": "AAAA", "runway": "09"}], "points": %1$s},
                    {"max": 100, "runways": [{"icao": "AAAA", "runway": ["27", "33"]}], "points": %1$s}
                  ]
                },
                {
                  "id": "Night", "group": "B", "owner": ["LOW"],
                  "fua": [{"start": {"hour": 20, "minute": 0}, "end": {"hour": 7, "minute": 0}}],
                  "sectors": [{"points": %1$s}]
                },
                {
                  "id": "Weekend", "group": "B", "owner": ["LOW"],
                  "fua": [{"start": {"day": 6, "hour": 0, "minute": 0}, "end": {"day": 1, "hour": 0, "minute": 0}}],
                  "sectors": [{"points": %1$s}]
                },
                {
                  "id": "Unowned", "group": "A", "owner": ["MISSING"],
                  "sectors": [{"points": %1$s}]
                },
                {
                  "id": "Degenerate", "group": "A", "owner": ["LOW"],
                  "sectors": [{"points": [["500000", "0010000"]]}]
                }
              ],
              "groups": {
                "A": {"name": "Group A", "colour": "#112233"},
                "B": {"name": "Group B", "colour": "#445566"}
              },
              "positions": {
                "UP": {"pre": ["TST"], "type": "CTR", "frequency": "132.605", "callsign": "Test Upper",
                       "colours": [{"online": ["LOW"], "hex": "#ff0000"}, {"hex": "#00ff00"}]},
                "LOW": {"pre": ["TST", "TS"], "type": "CTR", "frequency": "118.3330", "callsign": "Test Lower"},
                "ANY": {"pre": ["ANY"], "type": "FSS", "callsign": "Any Radio"},
                "BROKEN": {"callsign": "No Prefix"}
              },
              "airports": {
                "AAAA": {"callsign": "Alpha", "runways": ["27", "09"]}
              }
            }
            """.formatted(POINTS);

    private static final String FAR = """
            {
              "airspace": [],
              "positions": {
                "FAR": {"pre": ["FAR"], "type": "CTR", "frequency": "120.000", "callsign": "Far Control"}
              }
            }
            """;

    private static final String DYNAMIC_AIRSPACE = """
            {
              "airspace": {
                "1": {"id": "One", "group": "Z", "sectors": [{"min": 0, "max": 100, "points": %1$s}]},
                "2": {"id": "Two", "group": "Z", "sectors": [{"min": 0, "max": 100, "points": %1$s}]},
                "3": {"id": "Three", "group": "Z", "sectors": [{"min": 0, "max": 100, "points": %1$s}]}
              },
              "groups": {"Z": {"name": "Group Z", "colour": "#abcdef"}},
              "airports": {}
            }
            """.formatted(POINTS);

    private static final String DYNAMIC_POSITIONS = """
            {"positions": {
              "P": {"pre": ["DYN"], "type": "CTR", "frequency": "127.000", "callsign": "Dynamic Primary"},
              "S": {"pre": ["DYN"], "type": "CTR", "frequency": "128.000", "callsign": "Dynamic Secondary"}
            }}
            """;

    private static final String DYNAMIC_OWNERSHIP = """
            {"name": "Default", "airspace": {"1": ["S", "P"], "2": "P"}}
            """;

    private static final ZonedDateTime WEDNESDAY_NOON = ZonedDateTime.of(2026, 10, 14, 12, 0, 0, 0, ZoneOffset.UTC);

    private final VatglassesData data = VatglassesData.parse(Map.of(
            "tst.json", STATIC,
            "xx.json", "﻿" + FAR,
            "nodata.json", STATIC,
            "broken.json", "{",
            "dyn/airspace.json", DYNAMIC_AIRSPACE,
            "dyn/positions.json", DYNAMIC_POSITIONS,
            "dyn/ownership/default.json", DYNAMIC_OWNERSHIP,
            "dyn/ownership/other.json", "{\"airspace\": {\"3\": [\"P\"]}}"
    ));

    private static AtcStation station(final String callsign, final String frequency) {
        return new AtcStation() {
            @Override
            public String getCallsign() {
                return callsign;
            }

            @Override
            public String getFrequency() {
                return frequency;
            }
        };
    }

    private List<ActiveSubSector> resolve(final ZonedDateTime time, final AtcStation... stations) {
        return data.resolve(Arrays.asList(stations), time);
    }

    private Set<String> sectorIds(final List<ActiveSubSector> subSectors) {
        return subSectors.stream().map(e -> e.getSubSector().getSector().getId()).collect(Collectors.toSet());
    }

    private Map<String, String> ownerBySectorId(final List<ActiveSubSector> subSectors) {
        return subSectors
                .stream()
                .collect(Collectors.toMap(e -> e.getSubSector().getSector().getId(), e -> e.getOwner().getKey(), (a, b) -> a));
    }

    @Test
    void parsesCoordinates() {
        Assertions.assertEquals(50.5, VatglassesData.parseCoordinate("503000"), 1e-9);
        Assertions.assertEquals(-80.74555555, VatglassesData.parseCoordinate("-0804444"), 1e-6);
        Assertions.assertEquals(-0.5, VatglassesData.parseCoordinate("-0003000"), 1e-9);
        Assertions.assertEquals(179.99972222, VatglassesData.parseCoordinate("1795959"), 1e-6);
    }

    @Test
    void parsesFrequencies() {
        Assertions.assertEquals(132605, VatglassesData.parseFrequency("132.605"));
        Assertions.assertEquals(118333, VatglassesData.parseFrequency("118.3330"));
        Assertions.assertEquals(VgPosition.ANY_FREQUENCY, VatglassesData.parseFrequency(null));
        Assertions.assertEquals(VgPosition.ANY_FREQUENCY, VatglassesData.parseFrequency("abc"));
    }

    @Test
    void skipsBrokenFilesSectorsAndPositions() {
        // 5 static sectors without the degenerate one, 2 dynamic sectors with owners and 1 without
        Assertions.assertEquals(8, data.getSectors().size());
        Assertions.assertNull(data.getPosition("tst/BROKEN"));
        Assertions.assertNotNull(data.getPosition("xx/FAR"));
        Assertions.assertNull(data.getPosition("nodata/UP"));
    }

    @Test
    void nothingIsActiveWithoutControllers() {
        Assertions.assertTrue(resolve(WEDNESDAY_NOON).isEmpty());
        Assertions.assertTrue(resolve(WEDNESDAY_NOON, station("EDDF_TWR", "118.000"), station("TST_OBS", "199.998")).isEmpty());
    }

    @Test
    void matchesPositionsByPrefixTypeAndFrequency() {
        Assertions.assertEquals(Set.of("tst/UP"), keys(station("TST_CTR", "132.605")));
        Assertions.assertEquals(Set.of("tst/UP"), keys(station("TST_N_CTR", "132.605")));
        Assertions.assertEquals(Set.of("tst/LOW"), keys(station("TS_CTR", "118.330")));
        Assertions.assertEquals(Set.of(), keys(station("TST_CTR", "121.000")));
        Assertions.assertEquals(Set.of(), keys(station("TST_APP", "132.605")));
        Assertions.assertEquals(Set.of("tst/ANY"), keys(station("ANY_1_FSS", "123.450")));
    }

    @Test
    void stationsWithoutFrequencyOnlyMatchUnambiguousPositions() {
        Assertions.assertEquals(Set.of("xx/FAR"), keys(station("FAR_CTR", null)));
        Assertions.assertEquals(Set.of("tst/LOW"), keys(station("TS_N_CTR", "")));
        // Two positions share the prefix and type
        Assertions.assertEquals(Set.of(), keys(station("TST_CTR", null)));
    }

    @Test
    void fixedColourReplacesThePositionColours() {
        final List<ActiveSubSector> subSectors = data.resolve(List.of(station("TST_CTR", "132.605")), WEDNESDAY_NOON, Color.GRAY);

        Assertions.assertFalse(subSectors.isEmpty());
        Assertions.assertTrue(subSectors.stream().allMatch(e -> e.getColour().equals(Color.GRAY)));
    }

    private Set<String> keys(final AtcStation... stations) {
        return data.onlinePositions(Arrays.asList(stations)).keySet().stream().map(VgPosition::getKey).collect(Collectors.toSet());
    }

    @Test
    void firstOnlineOwnerControlsTheSector() {
        final AtcStation up = station("TST_U_CTR", "132.605");
        final AtcStation low = station("TST_L_CTR", "118.330");
        final AtcStation far = station("FAR_CTR", "120.000");

        Assertions.assertEquals("tst/UP", ownerBySectorId(resolve(WEDNESDAY_NOON, up, low, far)).get("Upper"));
        Assertions.assertEquals("tst/LOW", ownerBySectorId(resolve(WEDNESDAY_NOON, low, far)).get("Upper"));
        Assertions.assertEquals("xx/FAR", ownerBySectorId(resolve(WEDNESDAY_NOON, far)).get("Upper"));
        Assertions.assertEquals(Set.of("Upper"), sectorIds(resolve(WEDNESDAY_NOON, far)));
        Assertions.assertEquals("TST_L_CTR", resolve(WEDNESDAY_NOON, low).get(0).getOwnerCallsign());
    }

    @Test
    void coloursFollowTheFirstApplicableRuleThenTheGroup() {
        final AtcStation up = station("TST_CTR", "132.605");
        final AtcStation low = station("TST_CTR", "118.330");

        Assertions.assertEquals(Color.web("#00ff00"), colourOf("Upper", resolve(WEDNESDAY_NOON, up)));
        Assertions.assertEquals(Color.web("#ff0000"), colourOf("Upper", resolve(WEDNESDAY_NOON, up, low)));
        Assertions.assertEquals(Color.web("#112233"), colourOf("Lower", resolve(WEDNESDAY_NOON, low)));
    }

    private static Color colourOf(final String sectorId, final List<ActiveSubSector> subSectors) {
        return subSectors
                .stream()
                .filter(e -> e.getSubSector().getSector().getId().equals(sectorId))
                .findFirst()
                .orElseThrow()
                .getColour();
    }

    @Test
    void groupsSubSectorsByColour() {
        final List<Color> colours = resolve(WEDNESDAY_NOON, station("TST_CTR", "132.605"), station("TST_CTR", "118.330"), station("DYN_CTR", "127.000"))
                .stream()
                .map(ActiveSubSector::getColour)
                .collect(Collectors.toList());

        Assertions.assertEquals(colours.stream().distinct().count(), runs(colours));
    }

    private static long runs(final List<Color> colours) {
        long result = 0;

        for (int i = 0; i < colours.size(); i++) {
            if (i == 0 || !colours.get(i).equals(colours.get(i - 1))) {
                result++;
            }
        }

        return result;
    }

    @Test
    void assumesTheFirstRunwayConfigurationOfAnAirport() {
        final List<ActiveSubSector> lower = resolve(WEDNESDAY_NOON, station("TST_CTR", "118.330"))
                .stream()
                .filter(e -> e.getSubSector().getSector().getId().equals("Lower"))
                .collect(Collectors.toList());

        // The block without condition and the one for runway 27, but not the one for runway 09
        Assertions.assertEquals(2, lower.size());
    }

    @Test
    void levelsAreInclusive() {
        final List<ActiveSubSector> all = resolve(WEDNESDAY_NOON, station("TST_CTR", "118.330"));

        Assertions.assertEquals(Set.of("Lower"), sectorIds(atLevel(all, 0)));
        Assertions.assertEquals(Set.of("Lower"), sectorIds(atLevel(all, 244)));
        Assertions.assertEquals(Set.of("Upper"), sectorIds(atLevel(all, 245)));
        Assertions.assertEquals(Set.of("Upper"), sectorIds(atLevel(all, 660)));
        Assertions.assertEquals(Set.of(), sectorIds(atLevel(all, 661)));
    }

    private static List<ActiveSubSector> atLevel(final List<ActiveSubSector> subSectors, final int level) {
        return subSectors.stream().filter(e -> e.getSubSector().containsLevel(level)).collect(Collectors.toList());
    }

    @Test
    void timeRestrictedSectorsAreOnlyActiveInTheirWindows() {
        final AtcStation low = station("TST_CTR", "118.330");

        final ZonedDateTime wednesdayNight = WEDNESDAY_NOON.withHour(23);
        final ZonedDateTime thursdayEarlyMorning = WEDNESDAY_NOON.plusDays(1).withHour(6).withMinute(59);
        final ZonedDateTime saturdayNoon = WEDNESDAY_NOON.plusDays(3);
        final ZonedDateTime sundayNight = WEDNESDAY_NOON.plusDays(4).withHour(23).withMinute(59);
        final ZonedDateTime mondayMidnight = WEDNESDAY_NOON.plusDays(5).withHour(0);

        Assertions.assertEquals(Set.of("Upper", "Lower"), sectorIds(resolve(WEDNESDAY_NOON, low)));
        Assertions.assertEquals(Set.of("Upper", "Lower", "Night"), sectorIds(resolve(wednesdayNight, low)));
        Assertions.assertEquals(Set.of("Upper", "Lower", "Night"), sectorIds(resolve(thursdayEarlyMorning, low)));
        Assertions.assertEquals(Set.of("Upper", "Lower", "Weekend"), sectorIds(resolve(saturdayNoon, low)));
        Assertions.assertEquals(Set.of("Upper", "Lower", "Night", "Weekend"), sectorIds(resolve(sundayNight, low)));
        Assertions.assertEquals(Set.of("Upper", "Lower", "Night"), sectorIds(resolve(mondayMidnight, low)));
    }

    @Test
    void dynamicDataSetsUseTheDefaultOwnership() {
        final AtcStation primary = station("DYN_CTR", "127.000");
        final AtcStation secondary = station("DYN_CTR", "128.000");

        Assertions.assertEquals(Map.of("One", "dyn/P", "Two", "dyn/P"), ownerBySectorId(resolve(WEDNESDAY_NOON, primary)));
        Assertions.assertEquals(Map.of("One", "dyn/S"), ownerBySectorId(resolve(WEDNESDAY_NOON, secondary)));
        Assertions.assertEquals(Map.of("One", "dyn/S", "Two", "dyn/P"), ownerBySectorId(resolve(WEDNESDAY_NOON, primary, secondary)));
    }
}
