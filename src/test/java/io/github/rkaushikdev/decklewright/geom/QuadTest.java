package io.github.rkaushikdev.decklewright.geom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.decklewright.GeometryError;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class QuadTest {
    private final Rect square = Rect.of(0, 0, 100, 100);

    @Nested
    class BuildingAQuad {
        @Test
        void takesFourCornersAndFreezesThem() {
            Quad q = Quad.fromRect(square);
            assertThat(q.corners()).hasSize(4);
            assertThatThrownBy(() -> q.corners().set(0, Point.origin()))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThat(q.topLeft().toJson()).isEqualTo(Map.of("x", 0.0, "y", 0.0));
            assertThat(q.bottomRight().toJson()).isEqualTo(Map.of("x", 100.0, "y", 100.0));
        }

        @Test
        void acceptsLoosePairs() {
            Quad q = new Quad(List.of(List.of(0, 0), List.of(10, 0), List.of(10, 10), List.of(0, 10)));
            assertThat(q.topRight().toJson()).isEqualTo(Map.of("x", 10.0, "y", 0.0));
        }

        @Test
        void refusesAnyCountButFour() {
            assertThatThrownBy(() -> new Quad(List.of(Point.origin())))
                    .isInstanceOf(GeometryError.class)
                    .hasMessageFindingMatch("exactly four corners");
            assertThatThrownBy(() -> Quad.ordered(List.of(Point.origin(), Point.of(1, 1))))
                    .isInstanceOf(GeometryError.class)
                    .hasMessageFindingMatch("exactly four corners");
        }
    }

    @Nested
    class QuadOrdered {
        @Test
        void sortsAScrambledSetIntoTopLeftThenClockwise() {
            Quad q = Quad.ordered(List.of(Point.of(100, 100), Point.of(0, 100), Point.of(100, 0), Point.origin()));
            assertThat(q.toJson())
                    .isEqualTo(List.of(
                            Map.of("x", 0.0, "y", 0.0),
                            Map.of("x", 100.0, "y", 0.0),
                            Map.of("x", 100.0, "y", 100.0),
                            Map.of("x", 0.0, "y", 100.0)));
        }

        @Test
        void keepsAnAlreadyOrderedQuadInPlace() {
            Quad q = Quad.fromRect(square);
            assertThat(Quad.ordered(q.corners()).approxEquals(q)).isTrue();
        }

        @Test
        void ordersASkewedSheetTheSameWay() {
            Quad skewed = Quad.fromRect(square).transformedBy(Matrix.rotation(Angle.degrees(3), square.centre()));
            assertThat(Quad.ordered(skewed.corners().reversed()).approxEquals(skewed, 1e-9))
                    .isTrue();
        }
    }

    @Nested
    class Measurements {
        private final Quad q = Quad.fromRect(Rect.of(10, 20, 100, 50));

        @Test
        void reportsTheAreaByTheShoelaceRule() {
            assertThat(q.area()).isEqualTo(5000);
        }

        @Test
        void reportsTheCentreAndTheBoundingRect() {
            assertThat(q.centre().toJson()).isEqualTo(Map.of("x", 60.0, "y", 45.0));
            assertThat(q.boundingRect().toJson())
                    .isEqualTo(Map.of("x", 10.0, "y", 20.0, "width", 100.0, "height", 50.0));
        }

        @Test
        void listsEdgeLengthsTopRightBottomThenLeft() {
            assertThat(q.edgeLengths()).containsExactly(100, 50, 100, 50);
        }

        @Test
        void listsEdgeDirectionsInTheSameOrder() {
            List<Double> angles = q.edgeAngles().stream().map(Angle::degrees).toList();
            assertThat(angles.get(0)).isCloseTo(0, byLessThan(5e-10));
            assertThat(angles.get(1)).isCloseTo(90, byLessThan(5e-10));
            assertThat(Math.abs(angles.get(2))).isCloseTo(180, byLessThan(5e-10));
            assertThat(angles.get(3)).isCloseTo(-90, byLessThan(5e-10));
        }

        @Test
        void isConvexAndNotDegenerate() {
            assertThat(q.isConvex()).isTrue();
            assertThat(q.isDegenerate()).isFalse();
        }

        @Test
        void spotsAFlattenedQuad() {
            Quad flat = new Quad(List.of(Point.of(0, 0), Point.of(10, 0), Point.of(20, 0), Point.of(5, 0)));
            assertThat(flat.isDegenerate()).isTrue();
            assertThat(flat.area()).isEqualTo(0);
        }

        @Test
        void spotsABowTieAsNotConvex() {
            Quad crossed = new Quad(List.of(Point.of(0, 0), Point.of(10, 0), Point.of(0, 10), Point.of(10, 10)));
            assertThat(crossed.isConvex()).isFalse();
        }
    }

    @Nested
    class Skew {
        @Test
        void readsZeroForASquareSheet() {
            assertThat(Quad.fromRect(square).skew().degrees()).isCloseTo(0, byLessThan(5e-10));
        }

        @Test
        void readsTheRotationThatWasApplied() {
            Quad turned = Quad.fromRect(square).transformedBy(Matrix.rotation(Angle.degrees(2.4), square.centre()));
            assertThat(turned.skew().degrees()).isCloseTo(2.4, byLessThan(5e-7));
        }

        @Test
        void readsANegativeTurnTheOtherWay() {
            Quad turned = Quad.fromRect(square).transformedBy(Matrix.rotation(Angle.degrees(-1.75), square.centre()));
            assertThat(turned.skew().degrees()).isCloseTo(-1.75, byLessThan(5e-7));
        }

        @Test
        void isNotFooledByTheBottomEdgeRunningTheOtherWay() {
            Quad turned = Quad.fromRect(Rect.of(0, 0, 400, 50)).transformedBy(Matrix.rotation(Angle.degrees(0.9)));
            assertThat(turned.skew().degrees()).isCloseTo(0.9, byLessThan(5e-7));
        }
    }

    @Nested
    class IsRectangular {
        @Test
        void acceptsAPlainRect() {
            assertThat(Quad.fromRect(square).isRectangular()).isTrue();
        }

        @Test
        void acceptsARotatedRectSinceRotationKeepsTheCornersSquare() {
            Quad turned = Quad.fromRect(square).transformedBy(Matrix.rotation(Angle.degrees(7), square.centre()));
            assertThat(turned.isRectangular()).isTrue();
        }

        @Test
        void rejectsAKeystonedCapture() {
            Quad keystone = new Quad(List.of(Point.of(10, 0), Point.of(90, 0), Point.of(100, 100), Point.of(0, 100)));
            assertThat(keystone.isRectangular()).isFalse();
        }

        @Test
        void canBeToldToAcceptASlackCorner() {
            Quad nearly = new Quad(List.of(Point.of(0, 0), Point.of(100, 0), Point.of(101.5, 100), Point.of(1.5, 100)));
            assertThat(nearly.isRectangular()).isFalse();
            assertThat(nearly.isRectangular(2, 1)).isTrue();
        }
    }

    @Nested
    class TransformedBy {
        @Test
        void carriesEveryCornerThroughTheMatrix() {
            Quad moved = Quad.fromRect(square).transformedBy(Matrix.translation(5, 5));
            assertThat(moved.topLeft().toJson()).isEqualTo(Map.of("x", 5.0, "y", 5.0));
            assertThat(moved.area()).isCloseTo(10000, byLessThan(5e-10));
        }

        @Test
        void scalesTheAreaByTheDeterminant() {
            Quad bigger = Quad.fromRect(square).transformedBy(Matrix.scaling(2, 3));
            assertThat(bigger.area()).isCloseTo(60000, byLessThan(5e-10));
        }
    }

    @Nested
    class EqualityAndPrinting {
        @Test
        void comparesCornerByCorner() {
            assertThat(Quad.fromRect(square).approxEquals(Quad.fromRect(square)))
                    .isTrue();
            assertThat(Quad.fromRect(square).approxEquals(Quad.fromRect(Rect.of(0, 0, 100, 99))))
                    .isFalse();
            // approxEquals only takes a quad, so the rect is compared through equals instead.
            assertThat(Quad.fromRect(square).equals(square)).isFalse();
        }

        @Test
        void serialisesToFourPairs() {
            assertThat(Quad.fromRect(Rect.of(0, 0, 1, 1)).toJson())
                    .isEqualTo(List.of(
                            Map.of("x", 0.0, "y", 0.0),
                            Map.of("x", 1.0, "y", 0.0),
                            Map.of("x", 1.0, "y", 1.0),
                            Map.of("x", 0.0, "y", 1.0)));
            assertThat(String.valueOf(Quad.fromRect(Rect.of(0, 0, 1, 1)))).isEqualTo("(0, 0) (1, 0) (1, 1) (0, 1)");
        }
    }
}
