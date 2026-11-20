package io.github.rkaushikdev.decklewright.geom;

import static io.github.rkaushikdev.decklewright.geom.Angle.meanAngle;
import static io.github.rkaushikdev.decklewright.geom.Angle.meanLineAngle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AngleTest {

    @Nested
    class BuildingAnAngle {
        @Test
        void storesDegreesAndIsFrozen() {
            Angle a = Angle.degrees(30);
            assertThat(a.degrees()).isEqualTo(30);
            // A record's components are final and this one is a primitive, so nothing can change it.
            assertThat(Angle.class.isRecord()).isTrue();
        }

        @Test
        void convertsFromAndToRadians() {
            assertThat(Angle.radians(Math.PI).degrees()).isCloseTo(180, byLessThan(5e-13));
            assertThat(Angle.degrees(90).radians()).isCloseTo(Math.PI / 2, byLessThan(5e-13));
            assertThat(Angle.zero().isZero()).isTrue();
        }

        @Test
        void measuresTheDirectionBetweenTwoPoints() {
            assertThat(Angle.between(Point.of(0, 0), Point.of(1, 0)).degrees()).isEqualTo(0);
            assertThat(Angle.between(Point.of(0, 0), Point.of(0, 1)).degrees()).isCloseTo(90, byLessThan(5e-13));
            assertThat(Angle.between(Point.of(0, 0), Point.of(-1, 0)).degrees()).isCloseTo(180, byLessThan(5e-13));
        }

        @Test
        void refusesTwoIdenticalPoints() {
            assertThatThrownBy(() -> Angle.between(Point.of(3, 3), Point.of(3, 3)))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("no direction");
        }
    }

    @Nested
    class Wrapped {
        @Test
        void foldsIntoTheHalfOpenTurn() {
            assertThat(Angle.degrees(370).wrapped().degrees()).isEqualTo(10);
            assertThat(Angle.degrees(-370).wrapped().degrees()).isEqualTo(-10);
            assertThat(Angle.degrees(180).wrapped().degrees()).isEqualTo(180);
            assertThat(Angle.degrees(-180).wrapped().degrees()).isEqualTo(180);
            assertThat(Angle.degrees(540).wrapped().degrees()).isEqualTo(180);
        }

        @Test
        void neverReportsANegativeZero() {
            // Double.compare, unlike ==, tells minus zero from zero.
            assertThat(Double.compare(Angle.degrees(-360).wrapped().degrees(), 0.0))
                    .isZero();
        }
    }

    @Nested
    class SplitBehaviour {
        @Test
        void leavesASmallAngleAlone() {
            Angle.Split split = Angle.degrees(1.4).split();
            assertThat(split.quarterTurns()).isEqualTo(0);
            assertThat(split.residual().degrees()).isCloseTo(1.4, byLessThan(5e-13));
        }

        @Test
        void pullsOutAQuarterTurn() {
            Angle.Split split = Angle.degrees(91.4).split();
            assertThat(split.quarterTurns()).isEqualTo(1);
            assertThat(split.residual().degrees()).isCloseTo(1.4, byLessThan(5e-13));
        }

        @Test
        void handlesANegativeAngleByTurningTheOtherWay() {
            Angle.Split split = Angle.degrees(-89).split();
            assertThat(split.quarterTurns()).isEqualTo(3);
            assertThat(split.residual().degrees()).isCloseTo(1, byLessThan(5e-13));
        }

        @Test
        void putsTheBoundaryAtPlusFortyFiveRatherThanMinus() {
            assertThat(Angle.degrees(45).split()).isEqualTo(new Angle.Split(0, Angle.degrees(45)));
            Angle.Split low = Angle.degrees(-45).split();
            assertThat(low.quarterTurns()).isEqualTo(3);
            assertThat(low.residual().degrees()).isCloseTo(45, byLessThan(5e-13));
        }

        @Test
        void reportsQuarterTurnsInTheRangeZeroToThree() {
            for (double d : new double[] {-720, -181, -90, 0, 90, 181, 359, 720}) {
                int quarterTurns = Angle.degrees(d).split().quarterTurns();
                assertThat(quarterTurns).isGreaterThanOrEqualTo(0);
                assertThat(quarterTurns).isLessThanOrEqualTo(3);
            }
        }

        @Test
        void reassemblesToTheOriginalAngle() {
            for (double d : new double[] {-173.2, -45.5, 0, 12.5, 88.8, 179.9}) {
                Angle.Split split = Angle.degrees(d).split();
                Angle rebuilt = Angle.degrees(
                        split.quarterTurns() * 90 + split.residual().degrees());
                assertThat(rebuilt.approxEquals(Angle.degrees(d), 1e-9)).isTrue();
            }
        }
    }

    @Nested
    class Arithmetic {
        @Test
        void addsSubtractsScalesAndNegates() {
            assertThat(Angle.degrees(30).plus(Angle.degrees(15)).degrees()).isEqualTo(45);
            assertThat(Angle.degrees(30).minus(Angle.degrees(45)).degrees()).isEqualTo(-15);
            assertThat(Angle.degrees(30).times(3).degrees()).isEqualTo(90);
            assertThat(Angle.degrees(30).negated().degrees()).isEqualTo(-30);
        }

        @Test
        void takesAnAbsoluteValueAndKeepsIdentityWhenAlreadyPositive() {
            Angle a = Angle.degrees(30);
            assertThat(a.abs()).isSameAs(a);
            assertThat(Angle.degrees(-30).abs().degrees()).isEqualTo(30);
        }

        @Test
        void findsTheShortWayRoundAcrossTheWrap() {
            assertThat(Angle.degrees(170).shortestTo(Angle.degrees(-170)).degrees())
                    .isEqualTo(20);
            assertThat(Angle.degrees(-170).shortestTo(Angle.degrees(170)).degrees())
                    .isEqualTo(-20);
        }
    }

    @Nested
    class Comparison {
        @Test
        void treatsAnglesAFullTurnApartAsEqual() {
            assertThat(Angle.degrees(10).approxEquals(Angle.degrees(370))).isTrue();
            assertThat(Angle.degrees(10).approxEquals(Angle.degrees(10.4))).isFalse();
            assertThat(Angle.degrees(10).approxEquals(Angle.degrees(10.4), 0.5)).isTrue();
            // approxEquals only takes an angle, so a bare number is compared through equals instead.
            assertThat(Angle.degrees(10).equals(10.0)).isFalse();
        }

        @Test
        void answersWhetherAResidualIsInsideASkewTolerance() {
            assertThat(Angle.degrees(0.3).isNearZero(0.5)).isTrue();
            assertThat(Angle.degrees(-0.3).isNearZero(0.5)).isTrue();
            assertThat(Angle.degrees(0.6).isNearZero(0.5)).isFalse();
            assertThat(Angle.degrees(360.1).isNearZero(0.5)).isTrue();
        }
    }

    @Nested
    class Printing {
        @Test
        void quotesDegreesToFourPlaces() {
            assertThat(String.valueOf(Angle.degrees(1.23456))).isEqualTo("1.2346 deg");
            assertThat(Angle.degrees(2).toJson()).isEqualTo(2.0);
        }
    }

    @Nested
    class MeanAngle {
        @Test
        void averagesTheOrdinaryCase() {
            assertThat(meanAngle(List.of(Angle.degrees(10), Angle.degrees(20))).degrees())
                    .isCloseTo(15, byLessThan(5e-10));
        }

        @Test
        void averagesAcrossTheWrapInsteadOfThroughTheMiddle() {
            Angle m = meanAngle(List.of(Angle.degrees(179), Angle.degrees(-179)));
            assertThat(Math.abs(m.degrees())).isCloseTo(180, byLessThan(5e-10));
        }

        @Test
        void refusesAnEmptyRunAndASetThatCancels() {
            assertThatThrownBy(() -> meanAngle(List.of()))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("no angles to average");
            assertThatThrownBy(() -> meanAngle(List.of(Angle.degrees(0), Angle.degrees(180))))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("cancel out");
        }
    }

    @Nested
    class MeanLineAngle {
        @Test
        void treatsAHalfTurnAsTheSameLine() {
            Angle m = meanLineAngle(List.of(Angle.degrees(1), Angle.degrees(-179)));
            assertThat(m.degrees()).isCloseTo(1, byLessThan(5e-7));
        }

        @Test
        void matchesThePlainMeanWhenEveryAnglePointsTheSameWay() {
            List<Angle> angles = List.of(Angle.degrees(10), Angle.degrees(12), Angle.degrees(14));
            assertThat(meanLineAngle(angles).degrees()).isCloseTo(12, byLessThan(5e-7));
        }
    }
}
