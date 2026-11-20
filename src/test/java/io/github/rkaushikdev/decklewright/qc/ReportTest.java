package io.github.rkaushikdev.decklewright.qc;

import static io.github.rkaushikdev.decklewright.qc.Reports.describeFinding;
import static io.github.rkaushikdev.decklewright.qc.Reports.measurements;
import static io.github.rkaushikdev.decklewright.qc.Reports.oneLine;
import static io.github.rkaushikdev.decklewright.qc.Reports.remedies;
import static io.github.rkaushikdev.decklewright.qc.Reports.report;
import static io.github.rkaushikdev.decklewright.qc.Reports.toJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;

import io.github.rkaushikdev.decklewright.util.Json;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ReportTest {

    private static final ReportOptions DEFAULTS = ReportOptions.defaults();

    static Finding finding(String name, int stars) {
        return finding(name, stars, true, "measured", stars);
    }

    static Finding finding(String name, int stars, String note) {
        return finding(name, stars, true, note, stars);
    }

    static Finding notRun(String name, String note) {
        return finding(name, 0, false, note, 0);
    }

    static Finding finding(String name, int stars, boolean ran, String note, double measured) {
        return new Finding(name, ran, stars >= 3, measured, stars, note, "3 stars");
    }

    private static final List<Finding> GOOD = List.of(
            finding("sampling", 4, "600 ppi against 600"),
            finding("skew", 4, "0.05 deg off square"),
            finding("noise", 4, "44 dB"));

    private static final List<Finding> MIXED = List.of(
            finding("sampling", 4, "600 ppi against 600"),
            finding("skew", 2, "0.5 deg off square"),
            notRun("grayBalance", "nothing to measure, no target"));

    @Nested
    class DescribeFinding {
        @Test
        void showsTheStarsAndTheNote() {
            assertThat(describeFinding(finding("skew", 3, "a bit off"))).isEqualTo("skew: ***. a bit off");
        }

        @Test
        void padsSoAColumnOfThemLinesUp() {
            assertThat(describeFinding(finding("a", 1, "x"))).isEqualTo("a: *... x");
            assertThat(describeFinding(finding("a", 4, "x"))).isEqualTo("a: **** x");
        }

        @Test
        void saysACheckWasNotMeasuredRatherThanPretendingItFailed() {
            String out = describeFinding(notRun("grayBalance", "no target"));
            assertThat(out).isEqualTo("grayBalance: not measured, no target");
        }
    }

    @Nested
    class Report {
        @Test
        void opensWithTheLevel() {
            assertThat(report(GOOD).get(0)).isEqualTo("four stars");
        }

        @Test
        void namesTheItemWhenItIsGivenOne() {
            assertThat(report(GOOD, DEFAULTS.withItem("q17")).get(0)).isEqualTo("q17: four stars");
        }

        @Test
        void saysWhatIsHoldingTheLevelDown() {
            assertThat(report(MIXED)).contains("asked for three stars, held down by skew");
        }

        @Test
        void saysNothingAboutTheLevelWhenItWasReached() {
            assertThat(String.join("\n", report(GOOD))).doesNotContainPattern("held down");
        }

        @Test
        void countsTheChecksThatCouldNotRun() {
            assertThat(report(MIXED)).contains("1 checks could not run");
        }

        @Test
        void listsEveryFindingIncludingTheOnesThatDidNotRun() {
            List<String> lines =
                    report(MIXED).stream().filter(line -> line.startsWith("  ")).toList();
            assertThat(lines).hasSize(3);
            assertThat(lines.get(2)).containsPattern("grayBalance: not measured");
        }

        @Test
        void saysSoWhenNothingCouldBeMeasured() {
            List<Finding> none = List.of(notRun("a", "no target"));
            assertThat(report(none)).contains("nothing could be measured");
        }
    }

    @Nested
    class OneLine {
        @Test
        void writesOneLineForABatchLog() {
            assertThat(oneLine(GOOD, DEFAULTS.withItem("q17"))).isEqualTo("q17, four stars");
        }

        @Test
        void namesTheCheckHoldingItDown() {
            assertThat(oneLine(MIXED, DEFAULTS.withItem("q18")))
                    .isEqualTo("q18, two stars, held down by skew, 1 not measured");
        }

        @Test
        void leavesTheItemOutWhenThereIsNone() {
            assertThat(oneLine(GOOD)).isEqualTo("four stars");
        }
    }

    @Nested
    class Remedies {
        @Test
        void saysWhatToChangeForEachCheckThatFellShort() {
            assertThat(remedies(MIXED)).isEqualTo(List.of("skew: square the sheet on the bed"));
        }

        @Test
        void saysNothingWhenEverythingReachedTheLevel() {
            assertThat(remedies(GOOD)).isEqualTo(List.of());
        }

        @Test
        void fallsBackForACheckItHasNoAdviceFor() {
            assertThat(remedies(List.of(finding("custom", 1)))).isEqualTo(List.of("custom: look at the capture"));
        }

        @Test
        void takesAnotherLevelToMeasureAgainst() {
            assertThat(remedies(GOOD, 4)).isEqualTo(List.of());
            assertThat(remedies(List.of(finding("noise", 3)), 4).get(0)).containsPattern("lower the gain");
        }
    }

    @Nested
    class Measurements {
        @Test
        void reducesTheFindingsToNumbers() {
            assertThat(measurements(GOOD).get("sampling"))
                    .isEqualTo(Map.of("measured", 4.0, "stars", 4.0, "ran", true));
        }

        @Test
        void writesNothingForAMeasurementThatIsNotANumber() {
            Map<String, Map<String, Object>> out =
                    measurements(List.of(finding("a", 0, false, "measured", Double.NaN)));
            assertThat(out.get("a")).containsEntry("measured", null);
            assertThat(out.get("a").get("ran")).isEqualTo(false);
        }
    }

    @Nested
    class ToJson {
        @Test
        void carriesTheLevelAndWhetherItPassed() {
            Map<String, Object> out = toJson(GOOD, DEFAULTS.withItem("q17"));
            assertThat(out.get("item")).isEqualTo("q17");
            assertThat(out.get("stars")).isEqualTo(4.0);
            assertThat(out.get("level")).isEqualTo("four stars");
            assertThat(out.get("passed")).isEqualTo(true);
            assertThat(out.get("worst")).isEqualTo("sampling");
            // the key order is what a sidecar and the command line print
            assertThat(out.keySet())
                    .containsExactly(
                            "item",
                            "stars",
                            "level",
                            "wanted",
                            "passed",
                            "worst",
                            "ran",
                            "skipped",
                            "measurements",
                            "remedies");
        }

        @Test
        void carriesTheCountsTheMeasurementsAndTheRemedies() {
            Map<String, Object> out = toJson(MIXED);
            assertThat(out.get("ran")).isEqualTo(2.0);
            assertThat(out.get("skipped")).isEqualTo(1.0);
            assertThat(out.get("passed")).isEqualTo(false);
            assertThat(out.get("measurements")).asInstanceOf(MAP).hasSize(3);
            assertThat(out.get("remedies")).isEqualTo(List.of("skew: square the sheet on the bed"));
            assertThat(out.get("item")).isNull();
            assertThat(Json.stringify(out))
                    .startsWith("{\"item\":null,\"stars\":2,\"level\":\"two stars\",\"wanted\":3,\"passed\":false,");
        }

        @Test
        void measuresAgainstTheLevelItWasToldToWant() {
            assertThat(toJson(GOOD, DEFAULTS.withWanted(4)).get("passed")).isEqualTo(true);
            assertThat(toJson(List.of(finding("a", 3)), DEFAULTS.withWanted(4)).get("passed"))
                    .isEqualTo(false);
        }
    }
}
