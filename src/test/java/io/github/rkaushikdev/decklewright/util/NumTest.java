package io.github.rkaushikdev.decklewright.util;

import static io.github.rkaushikdev.decklewright.util.Num.approxEqual;
import static io.github.rkaushikdev.decklewright.util.Num.ceilToMultiple;
import static io.github.rkaushikdev.decklewright.util.Num.clamp;
import static io.github.rkaushikdev.decklewright.util.Num.floorToMultiple;
import static io.github.rkaushikdev.decklewright.util.Num.gcd;
import static io.github.rkaushikdev.decklewright.util.Num.hypot;
import static io.github.rkaushikdev.decklewright.util.Num.isEven;
import static io.github.rkaushikdev.decklewright.util.Num.lerp;
import static io.github.rkaushikdev.decklewright.util.Num.mean;
import static io.github.rkaushikdev.decklewright.util.Num.median;
import static io.github.rkaushikdev.decklewright.util.Num.percentile;
import static io.github.rkaushikdev.decklewright.util.Num.roundHalfEven;
import static io.github.rkaushikdev.decklewright.util.Num.roundTo;
import static io.github.rkaushikdev.decklewright.util.Num.roundToMultiple;
import static io.github.rkaushikdev.decklewright.util.Num.stdDev;
import static io.github.rkaushikdev.decklewright.util.Num.sum;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NumTest {

    @Nested
    class Clamp {
        @Test
        void holdsTheValueInsideTheBand() {
            assertThat(clamp(5, 0, 10)).isEqualTo(5);
            assertThat(clamp(-2, 0, 10)).isEqualTo(0);
            assertThat(clamp(11, 0, 10)).isEqualTo(10);
        }
    }

    @Nested
    class Lerp {
        @Test
        void hitsBothEndsExactly() {
            assertThat(lerp(2, 8, 0)).isEqualTo(2);
            assertThat(lerp(2, 8, 1)).isEqualTo(8);
            assertThat(lerp(2, 8, 0.5)).isEqualTo(5);
        }

        @Test
        void extrapolatesOutsideTheUnitInterval() {
            assertThat(lerp(0, 10, 1.5)).isEqualTo(15);
        }
    }

    @Nested
    class ApproxEqual {
        @Test
        void usesTheDefaultEpsilonAndHonoursAnOverride() {
            assertThat(approxEqual(0.1 + 0.2, 0.3)).isTrue();
            assertThat(approxEqual(1, 1.001)).isFalse();
            assertThat(approxEqual(1, 1.001, 0.01)).isTrue();
        }
    }

    @Nested
    class RoundHalfEven {
        @Test
        void sendsExactHalvesToTheEvenNeighbour() {
            assertThat(roundHalfEven(0.5)).isEqualTo(0);
            assertThat(roundHalfEven(1.5)).isEqualTo(2);
            assertThat(roundHalfEven(2.5)).isEqualTo(2);
            assertThat(roundHalfEven(3.5)).isEqualTo(4);
        }

        @Test
        void roundsNormallyAwayFromTheHalves() {
            assertThat(roundHalfEven(2.4)).isEqualTo(2);
            assertThat(roundHalfEven(2.6)).isEqualTo(3);
        }

        @Test
        void handlesNegativesThroughTheFloor() {
            assertThat(roundHalfEven(-2.5)).isEqualTo(-2);
            assertThat(roundHalfEven(-2.6)).isEqualTo(-3);
        }
    }

    @Nested
    class TheMultipleHelpers {
        @Test
        void roundsToTheNearestStep() {
            assertThat(roundToMultiple(13, 4)).isEqualTo(12);
            assertThat(roundToMultiple(14, 4)).isEqualTo(16);
        }

        @Test
        void floorsAndCeilsToTheStep() {
            assertThat(floorToMultiple(13, 4)).isEqualTo(12);
            assertThat(ceilToMultiple(13, 4)).isEqualTo(16);
            assertThat(floorToMultiple(12, 4)).isEqualTo(12);
            assertThat(ceilToMultiple(12, 4)).isEqualTo(12);
        }

        @Test
        void refusesAStepOfZero() {
            assertThatThrownBy(() -> roundToMultiple(1, 0)).hasMessageFindingMatch("step must be greater than zero");
        }
    }

    @Nested
    class RoundTo {
        @Test
        void keepsTheRequestedNumberOfPlaces() {
            assertThat(roundTo(3.14159, 2)).isEqualTo(3.14);
            assertThat(roundTo(2.005, 2)).isCloseTo(2.0, byLessThan(0.000005));
            assertThat(roundTo(1234.5, -2)).isEqualTo(1200);
        }
    }

    @Nested
    class TheSummaryStatistics {
        private final List<Integer> values = List.of(4, 8, 15, 16, 23, 42);

        @Test
        void sumsAndAverages() {
            assertThat(sum(values)).isEqualTo(108);
            assertThat(mean(values)).isEqualTo(18);
        }

        @Test
        void interpolatesAPercentileBetweenSamples() {
            assertThat(percentile(values, 0)).isEqualTo(4);
            assertThat(percentile(values, 1)).isEqualTo(42);
            assertThat(percentile(values, 0.5)).isEqualTo(15.5);
            assertThat(median(values)).isEqualTo(15.5);
        }

        @Test
        void clampsAPercentileFractionThatRunsOffTheEnd() {
            assertThat(percentile(values, 2)).isEqualTo(42);
            assertThat(percentile(values, -1)).isEqualTo(4);
        }

        @Test
        void leavesTheInputUntouchedWhenSorting() {
            double[] input = {3, 1, 2};
            percentile(input, 0.5);
            assertThat(input).containsExactly(3, 1, 2);
        }

        @Test
        void reportsThePopulationDeviation() {
            assertThat(stdDev(2, 4, 4, 4, 5, 5, 7, 9)).isEqualTo(2);
            assertThat(stdDev(5)).isEqualTo(0);
        }

        @Test
        void answersNotANumberForAnEmptyRun() {
            assertThat(mean()).isNaN();
            assertThat(median()).isNaN();
            assertThat(stdDev()).isNaN();
            assertThat(sum()).isEqualTo(0);
        }
    }

    /** Covers the vector length helper, {@link Num#hypot(double, double)}. */
    @Nested
    class Hypot {
        @Test
        void measuresTheLengthOfAVectorWithoutOverflowing() {
            assertThat(hypot(3, 4)).isEqualTo(5);
            assertThat(hypot(-3, 4)).isEqualTo(5);
            assertThat(hypot(0, -7)).isEqualTo(7);
            assertThat(hypot(0, 0)).isEqualTo(0);
            assertThat(hypot(3 * 0x1p600, 4 * 0x1p600)).isEqualTo(5 * 0x1p600);
            assertThat(hypot(Double.NaN, Double.POSITIVE_INFINITY)).isEqualTo(Double.POSITIVE_INFINITY);
            assertThat(hypot(Double.NaN, 1)).isNaN();
        }

        @Test
        void roundsAsTheScaledSumDoesRatherThanAsStrictMath() {
            assertThat(hypot(1, 5)).isEqualTo(5.099019513592785);
            assertThat(StrictMath.hypot(1, 5)).isEqualTo(5.0990195135927845);
        }
    }

    /** Covers the half-up rounding helper, {@link Num#roundHalfUp(double)}. */
    @Nested
    class RoundHalfUp {
        @Test
        void sendsHalvesTowardsPositiveInfinity() {
            assertThat(Num.roundHalfUp(2.5)).isEqualTo(3);
            assertThat(Num.roundHalfUp(-2.5)).isEqualTo(-2);
            assertThat(Num.roundHalfUp(-2.6)).isEqualTo(-3);
            assertThat(Num.roundHalfUp(0.49999999999999994)).isEqualTo(0);
        }

        @Test
        void keepsTheSignOfAZeroAndLeavesHugeValuesAlone() {
            assertThat(Double.compare(Num.roundHalfUp(-0.25), -0.0)).isZero();
            assertThat(Double.compare(Num.roundHalfUp(0.25), 0.0)).isZero();
            assertThat(Num.roundHalfUp(1e300)).isEqualTo(1e300);
            assertThat(Num.roundHalfUp(Double.NaN)).isNaN();
            assertThat(Num.roundHalfUp(Double.NEGATIVE_INFINITY)).isEqualTo(Double.NEGATIVE_INFINITY);
        }
    }

    @Nested
    class GcdAndIsEven {
        @Test
        void reducesARatio() {
            assertThat(gcd(1200, 800)).isEqualTo(400);
            assertThat(gcd(0, 5)).isEqualTo(5);
            assertThat(gcd(-12, 18)).isEqualTo(6);
        }

        @Test
        void spotsEvenPixelCounts() {
            assertThat(isEven(4)).isTrue();
            assertThat(isEven(5)).isFalse();
            assertThat(isEven(-2)).isTrue();
        }
    }
}
