package io.github.rkaushikdev.decklewright.qc;

import static io.github.rkaushikdev.decklewright.qc.Ladders.LADDERED;
import static io.github.rkaushikdev.decklewright.qc.Ladders.unreachableLevels;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.decklewright.profile.Fields;
import io.github.rkaushikdev.decklewright.profile.Problem;
import io.github.rkaushikdev.decklewright.profile.Profile;
import io.github.rkaushikdev.decklewright.profile.ProfileRegistry;
import io.github.rkaushikdev.decklewright.profile.Severity;
import io.github.rkaushikdev.decklewright.qc.checks.Noise;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LaddersTest {

    private static Profile profile(Map<String, Object> qc) {
        return Fields.parseProfile(Map.of("name", "atlas", "qc", qc));
    }

    private static List<String> paths(List<Problem> problems) {
        return problems.stream().map(Problem::path).toList();
    }

    @Nested
    class UnreachableLevels {
        @Test
        void hasNothingToSayAboutTheHouseFigures() {
            assertThat(unreachableLevels(profile(Map.of()))).isEmpty();
        }

        @Test
        void notesEveryLimitLooserThanTheRungForTheLevelAskedFor() {
            List<Problem> problems = unreachableLevels(profile(Map.of("minimumStars", 4)));
            assertThat(paths(problems))
                    .containsExactly(
                            "qc.skewTolerance",
                            "qc.illuminationTolerance",
                            "qc.grayBalanceTolerance",
                            "qc.minimumSnr",
                            "qc.maximumClipping",
                            "qc.registrationTolerance");
            assertThat(problems).allMatch(problem -> problem.severity() == Severity.NOTE);
            assertThat(problems.get(3).message())
                    .isEqualTo(
                            "is looser than four stars allows, which is 40, so a capture can pass it and miss the level");
        }

        @Test
        void holdsTheRegistrationToleranceToItsLadder() {
            assertThat(paths(unreachableLevels(profile(Map.of("registrationTolerance", 0.9)))))
                    .containsExactly("qc.registrationTolerance");
            assertThat(profile(Map.of()).qc().registrationTolerance()).isEqualTo(0.5);
            assertThat(profile(Map.of()).qc().toJson()).containsEntry("registrationTolerance", 0.5);
        }

        @Test
        void readsAFloorTheOtherWayRound() {
            assertThat(paths(unreachableLevels(profile(Map.of("minimumSnr", 29)))))
                    .containsExactly("qc.minimumSnr");
            assertThat(unreachableLevels(profile(Map.of("minimumSnr", 45)))).isEmpty();
        }

        @Test
        void readsTheToneToleranceOnlyWhenTheWedgeIsAimed() {
            assertThat(unreachableLevels(profile(Map.of("toneTolerance", 9)))).isEmpty();
            assertThat(paths(unreachableLevels(profile(Map.of("toneTolerance", 9, "toneAims", List.of(40, 240))))))
                    .containsExactly("qc.toneTolerance");
        }

        @Test
        void leavesGrayBalanceAloneForAGrayCollection() {
            Profile microfilm = new ProfileRegistry().get("microfilm");
            assertThat(paths(unreachableLevels(microfilm)))
                    .doesNotContain("qc.grayBalanceTolerance", "qc.registrationTolerance");
            assertThat(paths(unreachableLevels(microfilm))).containsExactly("qc.minimumSnr");
        }

        @Test
        void catchesTheShippedQuadranglesProfileAskingForFourStarsOnThreeStarLimits() {
            assertThat(paths(unreachableLevels(new ProfileRegistry().get("quadrangles"))))
                    .contains("qc.skewTolerance", "qc.minimumSnr")
                    .doesNotContain("qc.maximumClipping");
        }
    }

    @Nested
    class DescribeLadders {
        @Test
        void givesALinePerLimitWithTheRungTheLevelSitsOn() {
            List<String> lines = Ladders.describeLadders(profile(Map.of()));
            assertThat(lines).hasSize(LADDERED.size());
            assertThat(lines.get(3)).isEqualTo("noise: qc.minimumSnr 30, rungs 40 30 24 18, three stars at 30, within");
        }

        @Test
        void saysWhichLimitsAreLooserThanTheLevel() {
            List<String> lines = Ladders.describeLadders(profile(Map.of("minimumStars", 2, "skewTolerance", 0.9)));
            assertThat(lines.get(0))
                    .isEqualTo("skew: qc.skewTolerance 0.9, rungs 0.1 0.3 0.6 1.5, two stars at 0.6, looser");
            assertThat(lines.get(1)).endsWith("two stars at 0.2, within");
        }
    }

    @Nested
    class TheTable {
        @Test
        void pairsEachLimitWithItsOwnChecksLadder() {
            Ladders.LadderedLimit noise = LADDERED.stream()
                    .filter(limit -> limit.check().equals("noise"))
                    .findFirst()
                    .orElseThrow();
            assertThat(noise.ladder()).isEqualTo(Noise.NOISE_LADDER);
            assertThat(noise.higherIsBetter()).isTrue();
            assertThat(noise.looser(29, 30)).isTrue();
            assertThat(noise.looser(31, 30)).isFalse();
        }

        @Test
        void namesEveryPathTheProfileCanWrite() {
            List<String> written = Fields.settingPaths();
            assertThat(LADDERED).allMatch(limit -> written.contains(limit.path()));
        }
    }

    @Nested
    class TheLimitAtALevel {
        @Test
        void isTheRungAwardingThoseStars() {
            assertThat(Check.limitAt(Noise.CLIPPING_LADDER, 4)).hasValue(0.001);
            assertThat(Check.limitAt(Noise.CLIPPING_LADDER, 0)).isEmpty();
        }

        @Test
        void holdsAReadingToTheProfileLimitBeforeTheLadder() {
            assertThat(Check.starsWithin(0.004, 0.005, Noise.CLIPPING_LADDER, false))
                    .isEqualTo(3);
            assertThat(Check.starsWithin(0.006, 0.005, Noise.CLIPPING_LADDER, false))
                    .isZero();
            assertThat(Check.starsWithin(35, 30, Noise.NOISE_LADDER, true)).isEqualTo(3);
            assertThat(Check.starsWithin(29, 30, Noise.NOISE_LADDER, true)).isZero();
        }
    }
}
