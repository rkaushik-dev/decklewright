package io.github.rkaushikdev.decklewright.geom;

import static io.github.rkaushikdev.decklewright.geom.Point.centroid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PointTest {

    @Nested
    class BuildingAPoint {
        @Test
        void isFrozenAndKeepsItsCoordinates() {
            Point p = Point.of(3, -4);
            assertThat(p.x()).isEqualTo(3);
            assertThat(p.y()).isEqualTo(-4);
            // A record's components are final and these are primitives, so nothing can change them.
            assertThat(Point.class.isRecord()).isTrue();
        }

        @Test
        void hasAnOrigin() {
            assertThat(Point.origin().toJson()).isEqualTo(Map.of("x", 0.0, "y", 0.0));
        }

        @Test
        void refusesACoordinateThatIsNotFinite() {
            assertThatThrownBy(() -> Point.of(Double.NaN, 0))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("x must be a finite number");
            assertThatThrownBy(() -> Point.of(0, Double.POSITIVE_INFINITY))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("y must be a finite number");
        }
    }

    @Nested
    class PointFrom {
        @Test
        void takesAnInstanceAPairAndABag() {
            Point p = Point.of(1, 2);
            assertThat(Point.from(p)).isSameAs(p);
            assertThat(Point.from(List.of(1, 2)).approxEquals(p)).isTrue();
            assertThat(Point.from(Map.of("x", 1, "y", 2)).approxEquals(p)).isTrue();
        }

        @Test
        void rejectsAnythingElse() {
            assertThatThrownBy(() -> Point.from(List.of(1, 2, 3)))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("cannot read a point from that");
            assertThatThrownBy(() -> Point.from("1,2"))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("cannot read a point from that");
        }
    }

    @Nested
    class Arithmetic {
        private final Point a = Point.of(10, 20);
        private final Point b = Point.of(4, 5);

        @Test
        void addsAndSubtracts() {
            assertThat(a.plus(b).toJson()).isEqualTo(Map.of("x", 14.0, "y", 25.0));
            assertThat(a.minus(b).toJson()).isEqualTo(Map.of("x", 6.0, "y", 15.0));
        }

        @Test
        void scalesUniformlyOrPerAxis() {
            assertThat(a.times(2).toJson()).isEqualTo(Map.of("x", 20.0, "y", 40.0));
            assertThat(a.times(2, 0.5).toJson()).isEqualTo(Map.of("x", 20.0, "y", 10.0));
        }

        @Test
        void offsetsByALoosePair() {
            assertThat(a.offset(-10, 0).toJson()).isEqualTo(Map.of("x", 0.0, "y", 20.0));
        }

        @Test
        void interpolatesTowardsAnotherPoint() {
            assertThat(a.lerpTo(b, 0).approxEquals(a)).isTrue();
            assertThat(a.lerpTo(b, 1).approxEquals(b)).isTrue();
            assertThat(a.lerpTo(b, 0.5).toJson()).isEqualTo(Map.of("x", 7.0, "y", 12.5));
        }
    }

    @Nested
    class Distance {
        @Test
        void measuresTheStraightLine() {
            assertThat(Point.of(0, 0).distanceTo(Point.of(3, 4))).isEqualTo(5);
            assertThat(Point.of(0, 0).distanceSquaredTo(Point.of(3, 4))).isEqualTo(25);
        }

        @Test
        void isZeroToItself() {
            Point p = Point.of(7, 7);
            assertThat(p.distanceTo(p)).isEqualTo(0);
        }
    }

    @Nested
    class RotatedAround {
        @Test
        void turnsClockwiseForAPositiveAngle() {
            Point rotated = Point.of(1, 0).rotatedAround(Point.origin(), Math.PI / 2);
            assertThat(rotated.x()).isCloseTo(0, byLessThan(5e-13));
            assertThat(rotated.y()).isCloseTo(1, byLessThan(5e-13));
        }

        @Test
        void leavesTheCentreWhereItIs() {
            Point centre = Point.of(100, 50);
            assertThat(centre.rotatedAround(centre, 0.3).approxEquals(centre)).isTrue();
        }

        @Test
        void comesBackAfterAFullTurn() {
            Point p = Point.of(13, -7);
            Point round = p.rotatedAround(Point.of(2, 2), Math.PI * 2);
            assertThat(round.approxEquals(p, 1e-9)).isTrue();
        }
    }

    @Nested
    class Rounded {
        @Test
        void usesMathRoundByDefault() {
            assertThat(Point.of(1.4, 1.6).rounded().toJson()).isEqualTo(Map.of("x", 1.0, "y", 2.0));
        }

        @Test
        void acceptsAnotherRounder() {
            assertThat(Point.of(1.4, 1.6).rounded(Math::floor).toJson()).isEqualTo(Map.of("x", 1.0, "y", 1.0));
        }
    }

    @Nested
    class EqualsAndPrinting {
        @Test
        void comparesWithinAPixelTolerance() {
            assertThat(Point.of(1, 1).approxEquals(Point.of(1.0000000001, 1))).isTrue();
            assertThat(Point.of(1, 1).approxEquals(Point.of(1.5, 1))).isFalse();
            assertThat(Point.of(1, 1).approxEquals(Point.of(1.5, 1), 1)).isTrue();
            // approxEquals only takes a point, so a bag is compared through equals instead.
            assertThat(Point.of(1, 1).equals(Map.of("x", 1.0, "y", 1.0))).isFalse();
        }

        @Test
        void printsToThreePlaces() {
            assertThat(String.valueOf(Point.of(1.23456, -2))).isEqualTo("(1.235, -2)");
        }
    }

    @Nested
    class Centroid {
        @Test
        void averagesTheCorners() {
            List<Point> points = List.of(Point.of(0, 0), Point.of(10, 0), Point.of(10, 10), Point.of(0, 10));
            assertThat(centroid(points).toJson()).isEqualTo(Map.of("x", 5.0, "y", 5.0));
        }

        @Test
        void refusesAnEmptyRun() {
            assertThatThrownBy(() -> centroid(List.of()))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("no points to average");
        }
    }
}
