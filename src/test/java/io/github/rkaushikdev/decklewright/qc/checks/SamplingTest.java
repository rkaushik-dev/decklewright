package io.github.rkaushikdev.decklewright.qc.checks;

import static io.github.rkaushikdev.decklewright.qc.Check.runCheck;
import static io.github.rkaushikdev.decklewright.qc.checks.SamplingAndSkew.SAMPLING_LADDER;
import static io.github.rkaushikdev.decklewright.qc.checks.SamplingAndSkew.SKEW_LADDER;
import static io.github.rkaushikdev.decklewright.qc.checks.SamplingAndSkew.samplingCheck;
import static io.github.rkaushikdev.decklewright.qc.checks.SamplingAndSkew.samplingFloorCheck;
import static io.github.rkaushikdev.decklewright.qc.checks.SamplingAndSkew.skewAgreementCheck;
import static io.github.rkaushikdev.decklewright.qc.checks.SamplingAndSkew.skewCheck;
import static io.github.rkaushikdev.decklewright.qc.checks.TestContexts.LIMITS;
import static io.github.rkaushikdev.decklewright.qc.checks.TestContexts.skewLimit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.geom.Angle;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.qc.Finding;
import io.github.rkaushikdev.decklewright.qc.Need;
import io.github.rkaushikdev.decklewright.qc.QcContext;
import io.github.rkaushikdev.decklewright.qc.Rung;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SamplingTest {

    /** A square sheet at exactly the 600 ppi asked for, read with the three skew measures close. */
    static QcContext context() {
        return QcContext.empty()
                .withSampling(Sampling.of(600))
                .withSkew(Angle.degrees(0.05))
                .withSpread(0.1)
                .withLimits(LIMITS);
    }

    static QcContext at(double ppi) {
        return context().withSampling(Sampling.of(ppi));
    }

    static QcContext skewed(double degrees) {
        return context().withSkew(Angle.degrees(degrees));
    }

    @Nested
    class SamplingCheck {
        @Test
        void givesFourStarsForAnExactMatch() {
            Finding out = runCheck(samplingCheck(), context());
            assertThat(out.stars()).isEqualTo(4);
            assertThat(out.passed()).isTrue();
            assertThat(out.note()).containsPattern("600 ppi against 600, off by 0.00 percent");
        }

        @Test
        void dropsAStarAsTheCaptureDrifts() {
            assertThat(runCheck(samplingCheck(), at(604)).stars()).isEqualTo(3);
            assertThat(runCheck(samplingCheck(), at(597)).stars()).isEqualTo(4);
        }

        @Test
        void givesNoStarsOnceTheProfileToleranceIsBroken() {
            Finding out = runCheck(samplingCheck(), at(660));
            assertThat(out.stars()).isEqualTo(0);
            assertThat(out.passed()).isFalse();
        }

        @Test
        void isSymmetricSinceTooFineIsAsWrongAsTooCoarseAgainstATarget() {
            int high = runCheck(samplingCheck(), at(606)).stars();
            int low = runCheck(samplingCheck(), at(594)).stars();
            assertThat(high).isEqualTo(low);
        }

        @Test
        void saysItCouldNotRunWhenThereIsNoFrequency() {
            Finding out = runCheck(samplingCheck(), context().without(Need.SAMPLING));
            assertThat(out.ran()).isFalse();
            assertThat(out.note()).containsPattern("no sampling");
        }
    }

    @Nested
    class SamplingFloorCheck {
        @Test
        void givesFullStarsForAnythingAtOrAboveTheTarget() {
            assertThat(runCheck(samplingFloorCheck(), context()).stars()).isEqualTo(4);
            assertThat(runCheck(samplingFloorCheck(), at(1200)).stars()).isEqualTo(4);
        }

        @Test
        void dropsStarsOnlyWhenTheCaptureIsShort() {
            assertThat(runCheck(samplingFloorCheck(), at(594)).stars()).isEqualTo(3);
            assertThat(runCheck(samplingFloorCheck(), at(300)).stars()).isEqualTo(0);
        }

        @Test
        void saysWhichWayItWasWrong() {
            assertThat(runCheck(samplingFloorCheck(), context()).note()).containsPattern("at or above");
            assertThat(runCheck(samplingFloorCheck(), at(500)).note()).containsPattern("short of 600");
        }
    }

    @Nested
    class SkewCheck {
        @Test
        void givesFourStarsForASheetThatIsSquare() {
            Finding out = runCheck(skewCheck(), context());
            assertThat(out.stars()).isEqualTo(4);
            assertThat(out.note()).containsPattern("0.050 deg off square against a limit of 0.3");
        }

        @Test
        void readsTheSizeOfTheTurnNotItsDirection() {
            int left = runCheck(skewCheck(), skewed(-0.2)).stars();
            int right = runCheck(skewCheck(), skewed(0.2)).stars();
            assertThat(left).isEqualTo(right);
            assertThat(left).isEqualTo(3);
        }

        @Test
        void givesNoStarsPastTheProfileLimit() {
            assertThat(runCheck(skewCheck(), skewed(0.4)).stars()).isEqualTo(0);
        }

        @Test
        void takesALooserLimitFromALooserProfile() {
            QcContext loose = skewed(0.4).withLimits(skewLimit(0.6));
            assertThat(runCheck(skewCheck(), loose).stars()).isEqualTo(2);
        }
    }

    @Nested
    class SkewAgreementCheck {
        @Test
        void gradesHowSureTheDetectorWas() {
            assertThat(runCheck(skewAgreementCheck(), context().withSpread(0.05))
                            .stars())
                    .isEqualTo(4);
            assertThat(runCheck(skewAgreementCheck(), context().withSpread(0.4)).stars())
                    .isEqualTo(3);
            assertThat(runCheck(skewAgreementCheck(), context().withSpread(5)).stars())
                    .isEqualTo(0);
        }

        @Test
        void needsOnlyTheSpread() {
            assertThat(runCheck(skewAgreementCheck(), QcContext.empty().withSpread(0.1))
                            .ran())
                    .isTrue();
        }
    }

    @Nested
    class TheLadders {
        @Test
        void areFrozenAndRunBestFirst() {
            for (List<Rung> ladder : List.of(SAMPLING_LADDER, SKEW_LADDER)) {
                assertThatThrownBy(() -> ladder.add(new Rung(0, 9))).isInstanceOf(UnsupportedOperationException.class);
                List<Integer> stars = ladder.stream().map(Rung::stars).toList();
                assertThat(stars.stream().sorted(Comparator.reverseOrder()).toList())
                        .isEqualTo(stars);
                List<Double> limits = ladder.stream().map(Rung::limit).toList();
                assertThat(limits.stream().sorted().toList()).isEqualTo(limits);
            }
        }
    }
}
