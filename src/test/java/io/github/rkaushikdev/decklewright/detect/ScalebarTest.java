package io.github.rkaushikdev.decklewright.detect;

import static io.github.rkaushikdev.decklewright.detect.ScaleBars.checkAgainstClaim;
import static io.github.rkaushikdev.decklewright.detect.ScaleBars.countTicks;
import static io.github.rkaushikdev.decklewright.detect.ScaleBars.describeBar;
import static io.github.rkaushikdev.decklewright.detect.ScaleBars.findScaleBar;
import static io.github.rkaushikdev.decklewright.detect.ScaleBars.samplingFromBar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.DetectionError;
import io.github.rkaushikdev.decklewright.geom.Angle;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.measure.Unit;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ScalebarTest {

    private static final Rect BAR = Rect.of(20, 10, 120, 8);
    private static final Rect SHEET = Rect.of(30, 40, 120, 90);
    private static final ScaleBarOptions DEFAULTS = ScaleBarOptions.defaults();

    /** A 400 pixel bar, as a finding built by hand. */
    private static final ScaleBarFinding FOUR_HUNDRED = new ScaleBarFinding(BAR, 400, 20, Angle.zero());

    /** A 200 by 160 mask with each block set. */
    static Mask maskWith(Rect... blocks) {
        Mask mask = new Mask(200, 160);
        for (Rect rect : blocks) {
            for (int y = (int) rect.top(); y < rect.bottom(); y += 1) {
                for (int x = (int) rect.left(); x < rect.right(); x += 1) {
                    mask.set(x, y, 1);
                }
            }
        }
        return mask;
    }

    @Nested
    class FindScaleBar {
        @Test
        void findsTheLongThinBlockAndNotTheSheet() {
            ScaleBarFinding found = findScaleBar(maskWith(BAR, SHEET)).orElseThrow();
            assertThat(found.rect().approxEquals(BAR)).isTrue();
            assertThat(found.lengthPixels()).isEqualTo(120.0);
            assertThat(found.aspect()).isEqualTo(15.0);
        }

        @Test
        void readsAVerticalBarAsAQuarterTurn() {
            Rect upright = Rect.of(10, 20, 8, 120);
            ScaleBarFinding found = findScaleBar(maskWith(upright, SHEET)).orElseThrow();
            assertThat(found.angle().degrees()).isEqualTo(90.0);
            assertThat(found.lengthPixels()).isEqualTo(120.0);
        }

        @Test
        void findsNothingWhenThereIsNoBar() {
            assertThat(findScaleBar(maskWith(SHEET))).isEmpty();
        }

        @Test
        void ignoresABlockThatIsNotThinEnough() {
            Rect stubby = Rect.of(20, 10, 60, 30);
            assertThat(findScaleBar(maskWith(stubby))).isEmpty();
        }

        @Test
        void ignoresABarThatIsTooShort() {
            Rect tooShort = Rect.of(20, 10, 30, 3);
            assertThat(findScaleBar(maskWith(tooShort))).isEmpty();
            assertThat(findScaleBar(maskWith(tooShort), DEFAULTS.withMinimumLength(20))
                            .orElseThrow()
                            .lengthPixels())
                    .isEqualTo(30.0);
        }

        @Test
        void ignoresADiagonalStreakWhichDoesNotFillItsOwnBox() {
            Mask mask = new Mask(200, 160);
            for (int i = 0; i < 120; i += 1) {
                mask.set(20 + i, 10 + i, 1);
            }
            assertThat(findScaleBar(mask)).isEmpty();
        }

        @Test
        void takesTheLongestBarWhenThereAreTwo() {
            Rect shorter = Rect.of(20, 30, 60, 6);
            ScaleBarFinding found = findScaleBar(maskWith(BAR, shorter)).orElseThrow();
            assertThat(found.lengthPixels()).isEqualTo(120.0);
        }

        @Test
        void skipsComponentsItIsToldToIgnore() {
            Mask mask = maskWith(BAR, SHEET);
            assertThat(findScaleBar(mask, DEFAULTS.withIgnore(List.of(1)))).isEmpty();
        }
    }

    @Nested
    class SamplingFromBar {
        @Test
        void turnsAKnownLengthIntoAFrequency() {
            assertThat(samplingFromBar(FOUR_HUNDRED, 1, Unit.INCH).x()).isEqualTo(400.0);
            assertThat(samplingFromBar(FOUR_HUNDRED, 25.4, Unit.MILLIMETRE).x()).isCloseTo(400, byLessThan(0.5e-6));
        }

        @Test
        void refusesALengthOfZeroOrABarWithNone() {
            assertThatThrownBy(() -> samplingFromBar(FOUR_HUNDRED, 0, Unit.INCH))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("length must be greater");
            ScaleBarFinding empty = new ScaleBarFinding(BAR, 0, 20, Angle.zero());
            assertThatThrownBy(() -> samplingFromBar(empty, 1, Unit.INCH))
                    .isInstanceOf(DetectionError.class)
                    .hasMessageFindingMatch("no length");
        }

        @Test
        void refusesAUnitItDoesNotKnow() {
            // Unit is an enum, so the unknown label is refused where it is turned into one.
            assertThatThrownBy(() -> samplingFromBar(FOUR_HUNDRED, 1, Unit.parse("px")))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("unknown unit px");
        }
    }

    @Nested
    class CheckAgainstClaim {
        @Test
        void acceptsASmallDisagreement() {
            ClaimCheck out = checkAgainstClaim(Sampling.of(404), Sampling.of(400));
            assertThat(out.agrees()).isTrue();
            assertThat(out.error()).isCloseTo(0.01, byLessThan(0.5e-6));
        }

        @Test
        void rejectsALargeOneAndSignsIt() {
            ClaimCheck low = checkAgainstClaim(Sampling.of(360), Sampling.of(400));
            assertThat(low.agrees()).isFalse();
            assertThat(low.error()).isCloseTo(-0.1, byLessThan(0.5e-6));
            assertThat(low.measured()).isEqualTo(360.0);
            assertThat(low.claimed()).isEqualTo(400.0);
        }

        @Test
        void takesAnotherTolerance() {
            assertThat(checkAgainstClaim(Sampling.of(360), Sampling.of(400), 0.2)
                            .agrees())
                    .isTrue();
        }
    }

    @Nested
    class CountTicks {
        @Test
        void countsTheRunsOfInkAlongTheBar() {
            Mask mask = maskWith(BAR);
            Mask ink = new Mask(200, 160);
            for (int i = 0; i < 6; i += 1) {
                int x = (int) BAR.left() + 10 + i * 20;
                for (int y = (int) BAR.top(); y < BAR.bottom(); y += 1) {
                    ink.set(x, y, 1);
                }
            }
            ScaleBarFinding found = findScaleBar(mask).orElseThrow();
            assertThat(countTicks(mask, found, ink)).isEqualTo(6);
        }

        @Test
        void countsNothingOnABlankBar() {
            Mask mask = maskWith(BAR);
            ScaleBarFinding found = findScaleBar(mask).orElseThrow();
            assertThat(countTicks(mask, found, new Mask(200, 160))).isEqualTo(0);
        }

        @Test
        void walksAVerticalBarTheOtherWay() {
            Rect upright = Rect.of(10, 20, 8, 120);
            Mask mask = maskWith(upright);
            Mask ink = new Mask(200, 160);
            for (int i = 0; i < 4; i += 1) {
                int y = (int) upright.top() + 10 + i * 25;
                for (int x = (int) upright.left(); x < upright.right(); x += 1) {
                    ink.set(x, y, 1);
                }
            }
            ScaleBarFinding found = findScaleBar(mask).orElseThrow();
            assertThat(countTicks(mask, found, ink)).isEqualTo(4);
        }
    }

    @Nested
    class DescribeBar {
        @Test
        void writesOneLineForTheLog() {
            assertThat(describeBar(FOUR_HUNDRED, 1, Unit.INCH)).isEqualTo("400 px for 1 in, so 400 ppi");
        }
    }
}
