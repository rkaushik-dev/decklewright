package io.github.rkaushikdev.decklewright.geom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.GeometryError;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RectTest {

    @Nested
    class BuildingARect {
        @Test
        void keepsOriginAndExtentAndIsFrozen() {
            Rect r = Rect.of(10, 20, 30, 40);
            assertThat(r.toJson()).isEqualTo(Map.of("x", 10.0, "y", 20.0, "width", 30.0, "height", 40.0));
            // A record's components are final and these are primitives, so nothing can change them.
            assertThat(Rect.class.isRecord()).isTrue();
        }

        @Test
        void allowsANegativeOriginButNotANegativeExtent() {
            assertThat(Rect.of(-5, -5, 10, 10).left()).isEqualTo(-5);
            assertThatThrownBy(() -> Rect.of(0, 0, -1, 10))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("width must not be negative");
        }

        @Test
        void buildsFromEdgesAndRefusesInsideOutOnes() {
            assertThat(Rect.fromEdges(10, 20, 40, 60).toJson())
                    .isEqualTo(Map.of("x", 10.0, "y", 20.0, "width", 30.0, "height", 40.0));
            assertThatThrownBy(() -> Rect.fromEdges(40, 20, 10, 60))
                    .isInstanceOf(GeometryError.class)
                    .hasMessageFindingMatch("edges are inside out");
        }

        @Test
        void buildsFromCornersInAnyOrder() {
            Rect a = Rect.fromCorners(Point.of(40, 60), Point.of(10, 20));
            assertThat(a.toJson()).isEqualTo(Map.of("x", 10.0, "y", 20.0, "width", 30.0, "height", 40.0));
        }

        @Test
        void buildsFromACentreAndFromAnOrigin() {
            assertThat(Rect.fromCentre(Point.of(50, 50), Size.of(20, 10)).toJson())
                    .isEqualTo(Map.of("x", 40.0, "y", 45.0, "width", 20.0, "height", 10.0));
            assertThat(Rect.fromOrigin(Point.of(1, 2), Size.of(3, 4)).toJson())
                    .isEqualTo(Map.of("x", 1.0, "y", 2.0, "width", 3.0, "height", 4.0));
        }

        @Test
        void boundsAScatterOfPoints() {
            Rect r = Rect.bounding(List.of(Point.of(5, 9), Point.of(-1, 2), Point.of(3, 20)));
            assertThat(r.toJson()).isEqualTo(Map.of("x", -1.0, "y", 2.0, "width", 6.0, "height", 18.0));
            assertThatThrownBy(() -> Rect.bounding(List.of()))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("no points to bound");
        }
    }

    @Nested
    class EdgesAndDerivedValues {
        private final Rect r = Rect.of(10, 20, 30, 40);

        @Test
        void reportsTheFourEdgesWithTheRightOneExclusive() {
            assertThat(new double[] {r.left(), r.top(), r.right(), r.bottom()}).containsExactly(10, 20, 40, 60);
        }

        @Test
        void reportsOriginSizeCentreAndArea() {
            assertThat(r.origin().toJson()).isEqualTo(Map.of("x", 10.0, "y", 20.0));
            assertThat(r.size().toJson()).isEqualTo(Map.of("width", 30.0, "height", 40.0));
            assertThat(r.centre().toJson()).isEqualTo(Map.of("x", 25.0, "y", 40.0));
            assertThat(r.area()).isEqualTo(1200);
            assertThat(r.isEmpty()).isFalse();
            assertThat(Rect.empty().isEmpty()).isTrue();
        }

        @Test
        void listsCornersClockwiseFromTheTopLeft() {
            assertThat(r.corners().stream().map(Point::toJson).toList())
                    .isEqualTo(List.of(
                            Map.of("x", 10.0, "y", 20.0),
                            Map.of("x", 40.0, "y", 20.0),
                            Map.of("x", 40.0, "y", 60.0),
                            Map.of("x", 10.0, "y", 60.0)));
        }
    }

    @Nested
    class MovingAndResizing {
        private final Rect r = Rect.of(10, 20, 30, 40);

        @Test
        void translatesAndScales() {
            assertThat(r.translated(5, -5).toJson())
                    .isEqualTo(Map.of("x", 15.0, "y", 15.0, "width", 30.0, "height", 40.0));
            assertThat(r.scaled(2).toJson()).isEqualTo(Map.of("x", 20.0, "y", 40.0, "width", 60.0, "height", 80.0));
            assertThat(r.scaled(2, 1).toJson()).isEqualTo(Map.of("x", 20.0, "y", 20.0, "width", 60.0, "height", 40.0));
        }

        @Test
        void replacesOnePartAtATime() {
            assertThat(r.withSize(Size.of(4, 4)).toJson())
                    .isEqualTo(Map.of("x", 10.0, "y", 20.0, "width", 4.0, "height", 4.0));
            assertThat(r.withOrigin(Point.origin()).toJson())
                    .isEqualTo(Map.of("x", 0.0, "y", 0.0, "width", 30.0, "height", 40.0));
            assertThat(r.withCentre(Point.origin()).toJson())
                    .isEqualTo(Map.of("x", -15.0, "y", -20.0, "width", 30.0, "height", 40.0));
        }
    }

    @Nested
    class InsetAndOutset {
        private final Rect r = Rect.of(0, 0, 100, 100);

        @Test
        void pullsTheEdgesInAboutTheCentre() {
            assertThat(r.inset(10).toJson()).isEqualTo(Map.of("x", 10.0, "y", 10.0, "width", 80.0, "height", 80.0));
            assertThat(r.inset(10, 20).toJson()).isEqualTo(Map.of("x", 10.0, "y", 20.0, "width", 80.0, "height", 60.0));
        }

        @Test
        void pushesTheEdgesOut() {
            assertThat(r.outset(10).toJson())
                    .isEqualTo(Map.of("x", -10.0, "y", -10.0, "width", 120.0, "height", 120.0));
        }

        @Test
        void collapsesInsteadOfInvertingWhenTheInsetIsTooBig() {
            Rect collapsed = r.inset(80);
            assertThat(collapsed.width()).isEqualTo(0);
            assertThat(collapsed.centre().x()).isEqualTo(50);
        }
    }

    @Nested
    class ContainmentAndOverlap {
        private final Rect r = Rect.of(0, 0, 10, 10);

        @Test
        void treatsTheRightAndBottomEdgesAsOutside() {
            assertThat(r.contains(Point.of(0, 0))).isTrue();
            assertThat(r.contains(Point.of(9.999, 9.999))).isTrue();
            assertThat(r.contains(Point.of(10, 5))).isFalse();
            assertThat(r.contains(Point.of(5, 10))).isFalse();
        }

        @Test
        void containsARectThatSharesAnEdge() {
            assertThat(r.containsRect(Rect.of(0, 0, 10, 10))).isTrue();
            assertThat(r.containsRect(Rect.of(0, 0, 11, 10))).isFalse();
        }

        @Test
        void intersectsToTheOverlapAndEmptyWhenThereIsNone() {
            assertThat(r.intersect(Rect.of(5, 5, 20, 20)).toJson())
                    .isEqualTo(Map.of("x", 5.0, "y", 5.0, "width", 5.0, "height", 5.0));
            assertThat(r.intersect(Rect.of(20, 20, 5, 5)).isEmpty()).isTrue();
            assertThat(r.intersects(Rect.of(20, 20, 5, 5))).isFalse();
        }

        @Test
        void countsATouchingEdgeAsNoOverlap() {
            assertThat(r.intersects(Rect.of(10, 0, 5, 5))).isFalse();
        }

        @Test
        void unionsToTheBoundingBoxAndIgnoresEmpties() {
            assertThat(r.union(Rect.of(20, 20, 5, 5)).toJson())
                    .isEqualTo(Map.of("x", 0.0, "y", 0.0, "width", 25.0, "height", 25.0));
            assertThat(r.union(Rect.empty())).isSameAs(r);
            assertThat(Rect.empty().union(r)).isSameAs(r);
        }
    }

    @Nested
    class NudgedInto {
        private final Rect bed = Rect.of(0, 0, 100, 100);

        @Test
        void slidesASmallerRectBackInside() {
            assertThat(Rect.of(-10, 50, 20, 20).nudgedInto(bed).toJson())
                    .isEqualTo(Map.of("x", 0.0, "y", 50.0, "width", 20.0, "height", 20.0));
            assertThat(Rect.of(95, 50, 20, 20).nudgedInto(bed).x()).isEqualTo(80);
        }

        @Test
        void leavesARectThatIsAlreadyInsideWhereItIs() {
            Rect inside = Rect.of(10, 10, 20, 20);
            assertThat(inside.nudgedInto(bed).approxEquals(inside)).isTrue();
        }

        @Test
        void centresAnOversizedRectOnTheAxisItOverflows() {
            Rect big = Rect.of(0, 10, 200, 20).nudgedInto(bed);
            assertThat(big.x()).isEqualTo(-50);
            assertThat(big.y()).isEqualTo(10);
        }
    }

    @Nested
    class RoundingBehaviour {
        @Test
        void roundsEachEdgeAndNeverInverts() {
            assertThat(Rect.of(10.4, 20.6, 30.4, 40.6).rounded().toJson())
                    .isEqualTo(Map.of("x", 10.0, "y", 21.0, "width", 31.0, "height", 40.0));
        }

        @Test
        void growsOutwardToWholePixels() {
            assertThat(Rect.of(10.4, 20.6, 30.4, 40.6).outward().toJson())
                    .isEqualTo(Map.of("x", 10.0, "y", 20.0, "width", 31.0, "height", 42.0));
        }

        @Test
        void keepsADegenerateRectAtZeroWidth() {
            Rect flat = Rect.of(10.2, 5, 0.1, 10).rounded();
            assertThat(flat.width()).isEqualTo(0);
        }
    }

    @Nested
    class EqualityAndPrinting {
        @Test
        void comparesWithinATolerance() {
            assertThat(Rect.of(0, 0, 10, 10).approxEquals(Rect.of(0, 0, 10, 10)))
                    .isTrue();
            assertThat(Rect.of(0, 0, 10, 10).approxEquals(Rect.of(0.4, 0, 10, 10)))
                    .isFalse();
            assertThat(Rect.of(0, 0, 10, 10).approxEquals(Rect.of(0.4, 0, 10, 10), 0.5))
                    .isTrue();
            // approxEquals only takes a rect, so a bag is compared through equals instead.
            assertThat(Rect.of(0, 0, 10, 10).equals(Map.of("x", 0.0, "y", 0.0, "width", 10.0, "height", 10.0)))
                    .isFalse();
        }

        @Test
        void printsInTheGeometryFormTheLogUses() {
            assertThat(String.valueOf(Rect.of(10, 20, 30, 40))).isEqualTo("30x40+10+20");
        }
    }
}
