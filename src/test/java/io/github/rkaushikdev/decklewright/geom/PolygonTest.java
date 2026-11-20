package io.github.rkaushikdev.decklewright.geom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.GeometryError;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PolygonTest {
    private final Rect box = Rect.of(0, 0, 100, 50);

    @Nested
    class BuildingAPolygon {
        @Test
        void keepsTheVerticesFrozen() {
            Polygon p = Polygon.fromRect(box);
            assertThat(p.count()).isEqualTo(4);
            assertThatThrownBy(() -> p.vertices().add(Point.origin()))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void refusesFewerThanThreeVertices() {
            assertThatThrownBy(() -> new Polygon(List.of(Point.origin(), Point.of(1, 1))))
                    .isInstanceOf(GeometryError.class)
                    .hasMessageFindingMatch("at least three vertices");
        }

        @Test
        void acceptsLoosePairs() {
            Polygon p = new Polygon(List.of(List.of(0, 0), List.of(4, 0), List.of(0, 3)));
            assertThat(p.area()).isEqualTo(6);
        }
    }

    @Nested
    class AreaWindingAndPerimeter {
        @Test
        void measuresARectangle() {
            Polygon p = Polygon.fromRect(box);
            assertThat(p.area()).isEqualTo(5000);
            assertThat(p.perimeter()).isEqualTo(300);
            assertThat(p.isClockwise()).isTrue();
        }

        @Test
        void flipsTheSignWhenTheWindingReverses() {
            Polygon p = Polygon.fromRect(box).reversed();
            assertThat(p.isClockwise()).isFalse();
            assertThat(p.signedArea()).isEqualTo(-5000);
            assertThat(p.area()).isEqualTo(5000);
        }

        @Test
        void normalisesTheWindingOnRequest() {
            Polygon p = Polygon.fromRect(box).reversed().clockwise();
            assertThat(p.isClockwise()).isTrue();
            Polygon already = Polygon.fromRect(box);
            assertThat(already.clockwise()).isSameAs(already);
        }

        @Test
        void measuresAnLShapeCorrectly() {
            Polygon l = new Polygon(List.of(
                    Point.of(0, 0),
                    Point.of(10, 0),
                    Point.of(10, 4),
                    Point.of(4, 4),
                    Point.of(4, 10),
                    Point.of(0, 10)));
            assertThat(l.area()).isEqualTo(64);
        }
    }

    @Nested
    class CentroidAndBounds {
        @Test
        void centresARectangle() {
            assertThat(Polygon.fromRect(box).centroid().toJson()).isEqualTo(Map.of("x", 50.0, "y", 25.0));
        }

        @Test
        void weightsByAreaRatherThanByVertex() {
            Polygon l = new Polygon(List.of(
                    Point.of(0, 0),
                    Point.of(12, 0),
                    Point.of(12, 2),
                    Point.of(2, 2),
                    Point.of(2, 12),
                    Point.of(0, 12)));
            assertThat(l.centroid().x()).isCloseTo(164.0 / 44, byLessThan(5e-10));
            assertThat(l.centroid().y()).isCloseTo(164.0 / 44, byLessThan(5e-10));
            assertThat(l.centroid().x()).isGreaterThan(l.boundingRect().centre().x() - 6);
        }

        @Test
        void refusesACentroidWhenThereIsNoArea() {
            Polygon flat = new Polygon(List.of(Point.of(0, 0), Point.of(5, 0), Point.of(10, 0)));
            assertThatThrownBy(flat::centroid)
                    .isInstanceOf(GeometryError.class)
                    .hasMessageFindingMatch("no area has no centroid");
        }

        @Test
        void reportsTheBoundingRect() {
            assertThat(Polygon.fromRect(box).boundingRect().approxEquals(box)).isTrue();
        }
    }

    @Nested
    class Contains {
        private final Polygon p = Polygon.fromRect(Rect.of(0, 0, 10, 10));

        @Test
        void findsAnInteriorPoint() {
            assertThat(p.contains(Point.of(5, 5))).isTrue();
        }

        @Test
        void rejectsAnExteriorPoint() {
            assertThat(p.contains(Point.of(15, 5))).isFalse();
            assertThat(p.contains(Point.of(-1, 5))).isFalse();
            assertThat(p.contains(Point.of(5, 20))).isFalse();
        }

        @Test
        void countsAPointOnTheBoundaryAsInside() {
            assertThat(p.contains(Point.of(0, 5))).isTrue();
            assertThat(p.contains(Point.of(10, 5))).isTrue();
            assertThat(p.contains(Point.of(0, 0))).isTrue();
        }

        @Test
        void handlesAConcaveNotch() {
            Polygon notched = new Polygon(
                    List.of(Point.of(0, 0), Point.of(10, 0), Point.of(10, 10), Point.of(5, 5), Point.of(0, 10)));
            assertThat(notched.contains(Point.of(5, 8))).isFalse();
            assertThat(notched.contains(Point.of(2, 2))).isTrue();
        }
    }

    @Nested
    class ConvexHull {
        @Test
        void dropsTheInteriorPoints() {
            Polygon hull = Polygon.convexHull(List.of(
                    Point.of(0, 0),
                    Point.of(10, 0),
                    Point.of(10, 10),
                    Point.of(0, 10),
                    Point.of(5, 5),
                    Point.of(3, 7)));
            assertThat(hull.count()).isEqualTo(4);
            assertThat(hull.area()).isEqualTo(100);
        }

        @Test
        void comesBackClockwiseOnScreen() {
            Polygon hull =
                    Polygon.convexHull(List.of(Point.of(0, 10), Point.of(10, 10), Point.of(10, 0), Point.of(0, 0)));
            assertThat(hull.isClockwise()).isTrue();
        }

        @Test
        void refusesTooFewPointsAndCollinearOnes() {
            assertThatThrownBy(() -> Polygon.convexHull(List.of(Point.origin(), Point.of(1, 1))))
                    .isInstanceOf(GeometryError.class)
                    .hasMessageFindingMatch("at least three points");
            assertThatThrownBy(() ->
                            Polygon.convexHull(List.of(Point.of(0, 0), Point.of(1, 1), Point.of(2, 2), Point.of(3, 3))))
                    .isInstanceOf(GeometryError.class)
                    .hasMessageFindingMatch("collinear");
        }
    }

    @Nested
    class Simplified {
        @Test
        void dropsVerticesThatSitOnAStraightRun() {
            Polygon dense = new Polygon(List.of(
                    Point.of(0, 0),
                    Point.of(5, 0),
                    Point.of(10, 0),
                    Point.of(10, 10),
                    Point.of(5, 10),
                    Point.of(0, 10)));
            Polygon thin = dense.simplified(0.5);
            assertThat(thin.count()).isEqualTo(4);
            assertThat(thin.area()).isEqualTo(100);
        }

        @Test
        void keepsARealCornerThatSitsOutsideTheTolerance() {
            Polygon bumped = new Polygon(
                    List.of(Point.of(0, 0), Point.of(5, 3), Point.of(10, 0), Point.of(10, 10), Point.of(0, 10)));
            assertThat(bumped.simplified(0.5).count()).isEqualTo(5);
            assertThat(bumped.simplified(5).count()).isLessThan(5);
        }

        @Test
        void isANoOpAtZeroToleranceAndOnATriangle() {
            Polygon triangle = new Polygon(List.of(Point.of(0, 0), Point.of(10, 0), Point.of(0, 10)));
            assertThat(triangle.simplified(5)).isSameAs(triangle);
            Polygon p = Polygon.fromRect(box);
            assertThat(p.simplified(0)).isSameAs(p);
        }

        @Test
        void refusesANegativeTolerance() {
            assertThatThrownBy(() -> Polygon.fromRect(box).simplified(-1))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("tolerance must not be negative");
        }
    }

    @Nested
    class TransformedByAndPrinting {
        @Test
        void carriesEveryVertexThrough() {
            Polygon moved = Polygon.fromRect(box).transformedBy(Matrix.translation(10, 10));
            assertThat(moved.boundingRect().toJson())
                    .isEqualTo(Map.of("x", 10.0, "y", 10.0, "width", 100.0, "height", 50.0));
        }

        @Test
        void serialisesToAListOfPairs() {
            assertThat(Polygon.fromRect(Rect.of(0, 0, 1, 1)).toJson()).hasSize(4);
            assertThat(String.valueOf(Polygon.fromRect(box))).isEqualTo("polygon(4 vertices, area 5000)");
        }
    }
}
