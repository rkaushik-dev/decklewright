package io.github.rkaushikdev.decklewright.qc;

import static io.github.rkaushikdev.decklewright.qc.Levels.LEVEL_NAMES;
import static io.github.rkaushikdev.decklewright.qc.Levels.batchLevel;
import static io.github.rkaushikdev.decklewright.qc.Levels.levelName;
import static io.github.rkaushikdev.decklewright.qc.Levels.levelOf;
import static io.github.rkaushikdev.decklewright.qc.Levels.levelWithRequired;
import static io.github.rkaushikdev.decklewright.qc.Levels.meetsLevel;
import static io.github.rkaushikdev.decklewright.qc.Levels.shortfall;
import static io.github.rkaushikdev.decklewright.qc.Levels.tallyLevels;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LevelsTest {

    static Finding finding(String name, int stars) {
        return finding(name, stars, true);
    }

    static Finding finding(String name, int stars, boolean ran) {
        return new Finding(name, ran, stars >= 3, stars, stars, "", "3 stars");
    }

    /** Something graded at a level and nothing else, as a batch tally sees an item. */
    static Graded graded(int stars) {
        return () -> stars;
    }

    @Nested
    class LevelOf {
        @Test
        void isTheWorstCheckNotTheAverage() {
            Level out = levelOf(List.of(finding("a", 4), finding("b", 4), finding("c", 4), finding("d", 1)));
            assertThat(out.stars()).isEqualTo(1);
            assertThat(out.worst()).contains("d");
        }

        @Test
        void namesTheFirstCheckAtTheWorstLevel() {
            assertThat(levelOf(List.of(finding("a", 2), finding("b", 2))).worst())
                    .contains("a");
        }

        @Test
        void countsWhatRanAndWhatDidNot() {
            Level out = levelOf(List.of(finding("a", 4), finding("b", 0, false)));
            assertThat(out.ran()).isEqualTo(1);
            assertThat(out.skipped()).isEqualTo(1);
            assertThat(out.stars()).isEqualTo(4);
        }

        @Test
        void hasNoLevelWhenNothingRan() {
            Level out = levelOf(List.of(finding("a", 0, false)));
            assertThat(out.stars()).isEqualTo(0);
            assertThat(out.worst()).isEmpty();
        }

        @Test
        void hasNoLevelForAnEmptySet() {
            assertThat(levelOf(List.of()).stars()).isEqualTo(0);
        }
    }

    @Nested
    class LevelWithRequired {
        @Test
        void isThePlainLevelWhenEveryRequiredCheckRan() {
            RequiredLevel out =
                    levelWithRequired(List.of(finding("sampling", 4), finding("skew", 3)), List.of("sampling"));
            assertThat(out.stars()).isEqualTo(3);
            assertThat(out.missing()).isEqualTo(List.of());
        }

        @Test
        void dropsToNothingWhenARequiredCheckCouldNotRun() {
            RequiredLevel out = levelWithRequired(
                    List.of(finding("sampling", 4), finding("grayBalance", 0, false)),
                    List.of("sampling", "grayBalance"));
            assertThat(out.stars()).isEqualTo(0);
            assertThat(out.missing()).isEqualTo(List.of("grayBalance"));
            assertThat(out.worst()).contains("grayBalance");
        }

        @Test
        void dropsToNothingWhenARequiredCheckIsNotThereAtAll() {
            assertThat(levelWithRequired(List.of(finding("sampling", 4)), List.of("noise"))
                            .missing())
                    .isEqualTo(List.of("noise"));
        }

        @Test
        void ignoresACheckThatDidNotRunAndWasNotRequired() {
            RequiredLevel out = levelWithRequired(List.of(finding("a", 4), finding("b", 0, false)), List.of("a"));
            assertThat(out.stars()).isEqualTo(4);
        }
    }

    @Nested
    class LevelName {
        @Test
        void namesEveryLevel() {
            assertThat(levelName(4)).isEqualTo("four stars");
            assertThat(levelName(1)).isEqualTo("one star");
            assertThat(levelName(0)).isEqualTo("below the lowest level");
            assertThat(levelName(9)).isEqualTo("unknown");
            assertThatThrownBy(() -> LEVEL_NAMES.put(5, "five stars"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class MeetsLevel {
        @Test
        void comparesTheWorstCheckAgainstTheFloor() {
            List<Finding> findings = List.of(finding("a", 4), finding("b", 3));
            assertThat(meetsLevel(findings, 3)).isTrue();
            assertThat(meetsLevel(findings, 4)).isFalse();
        }
    }

    @Nested
    class ShortfallBehaviour {
        @Test
        void namesTheChecksHoldingTheLevelDownWorstFirst() {
            List<Shortfall> out = shortfall(List.of(finding("a", 4), finding("b", 1), finding("c", 2)), 3);
            assertThat(out.stream().map(Shortfall::name)).containsExactly("b", "c");
            assertThat(out.get(0).shortBy()).isEqualTo(2);
        }

        @Test
        void saysNothingWhenEverythingReachesTheLevel() {
            assertThat(shortfall(List.of(finding("a", 4)), 3)).isEqualTo(List.of());
        }

        @Test
        void leavesOutChecksThatDidNotRun() {
            assertThat(shortfall(List.of(finding("a", 0, false)), 3)).isEqualTo(List.of());
        }

        @Test
        void breaksATieByNameSoAReportIsStable() {
            List<Shortfall> out = shortfall(List.of(finding("z", 1), finding("a", 1)), 3);
            assertThat(out.stream().map(Shortfall::name)).containsExactly("a", "z");
            // localeCompare's order, not code point order: a capital does not jump the queue
            List<Shortfall> cased = shortfall(
                    List.of(
                            finding("Sharpness", 1),
                            finding("noise", 1),
                            finding("skewAgreement", 1),
                            finding("skew", 1)),
                    3);
            assertThat(cased.stream().map(Shortfall::name))
                    .containsExactly("noise", "Sharpness", "skew", "skewAgreement");
        }
    }

    @Nested
    class TallyLevels {
        @Test
        void countsABatchByLevel() {
            assertThat(tallyLevels(List.of(graded(4), graded(4), graded(2), graded(0))))
                    .isEqualTo(Map.of(0, 1, 1, 0, 2, 1, 3, 0, 4, 2));
        }

        @Test
        void holdsALevelOutsideTheRangeAtTheNearestEnd() {
            assertThat(tallyLevels(List.of(graded(9), graded(-1)))).isEqualTo(Map.of(0, 1, 1, 0, 2, 0, 3, 0, 4, 1));
        }

        @Test
        void countsNothingForAnEmptyBatch() {
            assertThat(tallyLevels(List.of())).isEqualTo(Map.of(0, 0, 1, 0, 2, 0, 3, 0, 4, 0));
        }
    }

    @Nested
    class BatchLevel {
        @Test
        void isTheWorstItemInTheBatch() {
            assertThat(batchLevel(List.of(graded(4), graded(3), graded(4)))).isEqualTo(3);
        }

        @Test
        void isNothingForAnEmptyBatch() {
            assertThat(batchLevel(List.of())).isEqualTo(0);
        }
    }
}
