package io.github.rkaushikdev.decklewright.detect;

import static io.github.rkaushikdev.decklewright.detect.Corners.cornerResidual;
import static io.github.rkaushikdev.decklewright.detect.Corners.cornerShift;
import static io.github.rkaushikdev.decklewright.detect.Corners.cornersFromLines;
import static io.github.rkaushikdev.decklewright.detect.Corners.intersect;
import static io.github.rkaushikdev.decklewright.detect.Corners.lineConfidence;
import static io.github.rkaushikdev.decklewright.detect.Corners.quadConfidence;
import static io.github.rkaushikdev.decklewright.detect.Corners.refineCorners;
import static io.github.rkaushikdev.decklewright.detect.Edges.edgeLines;
import static io.github.rkaushikdev.decklewright.detect.Edges.fitLine;
import static io.github.rkaushikdev.decklewright.detect.QuadFit.fitQuad;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.decklewright.DetectionError;
import io.github.rkaushikdev.decklewright.geom.Point;
import io.github.rkaushikdev.decklewright.geom.Quad;
import io.github.rkaushikdev.decklewright.geom.Rect;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CornersTest {

    private static final Line HORIZONTAL = fitLine(List.of(Point.of(0, 10), Point.of(100, 10)));
    private static final Line VERTICAL = fitLine(List.of(Point.of(20, 0), Point.of(20, 100)));

    /** An 80 by 60 sheet turned about the centre of a 140 by 120 mask. */
    static Mask turnedSheet(double degrees) {
        Mask mask = new Mask(140, 120);
        double radians = (degrees * Math.PI) / 180;
        double cos = StrictMath.cos(radians);
        double sin = StrictMath.sin(radians);
        for (int y = 0; y < mask.height(); y += 1) {
            for (int x = 0; x < mask.width(); x += 1) {
                double dx = x - 70;
                double dy = y - 60;
                if (Math.abs(dx * cos + dy * sin) <= 40 && Math.abs(-dx * sin + dy * cos) <= 30) {
                    mask.set(x, y, 1);
                }
            }
        }
        return mask;
    }

    @Nested
    class Intersect {
        @Test
        void crossesAHorizontalAndAVerticalLine() {
            assertThat(intersect(HORIZONTAL, VERTICAL).approxEquals(Point.of(20, 10), 1e-6))
                    .isTrue();
        }

        @Test
        void crossesTwoSlantedLines() {
            Line a = fitLine(List.of(Point.of(0, 0), Point.of(10, 10)));
            Line b = fitLine(List.of(Point.of(0, 10), Point.of(10, 0)));
            assertThat(intersect(a, b).approxEquals(Point.of(5, 5), 1e-6)).isTrue();
        }

        @Test
        void refusesTwoParallelLines() {
            Line other = fitLine(List.of(Point.of(0, 40), Point.of(100, 40)));
            assertThatThrownBy(() -> intersect(HORIZONTAL, other))
                    .isInstanceOf(DetectionError.class)
                    .hasMessageFindingMatch("parallel and never meet");
        }
    }

    @Nested
    class CornersFromLines {
        @Test
        void buildsAQuadFromFourSides() {
            SideLines lines = edgeLines(turnedSheet(0));
            Quad quad = cornersFromLines(lines);
            assertThat(quad.corners()).hasSize(4);
            assertThat(quad.isConvex()).isTrue();
            assertThat(quad.area()).isGreaterThan(80 * 60 * 0.9);
        }

        @Test
        void agreesWithTheCalipersOnASquareSheet() {
            Mask mask = turnedSheet(0);
            Quad caliper = fitQuad(mask).quad();
            Quad crossed = cornersFromLines(edgeLines(mask));
            assertThat(cornerShift(caliper, crossed).worst()).isLessThan(2);
        }
    }

    @Nested
    class CornerShiftBehaviour {
        @Test
        void isZeroBetweenAQuadAndItself() {
            Quad quad = Quad.fromRect(Rect.of(0, 0, 10, 10));
            CornerShift shift = cornerShift(quad, quad);
            assertThat(shift.worst()).isEqualTo(0.0);
            assertThat(shift.mean()).isEqualTo(0.0);
            assertThat(shift.moves()).isEqualTo(List.of(0.0, 0.0, 0.0, 0.0));
        }

        @Test
        void measuresEachCornerSeparately() {
            Quad a = Quad.fromRect(Rect.of(0, 0, 10, 10));
            Quad b = new Quad(List.of(Point.of(3, 4), Point.of(10, 0), Point.of(10, 10), Point.of(0, 10)));
            CornerShift shift = cornerShift(a, b);
            assertThat(shift.moves().get(0)).isCloseTo(5, byLessThan(0.5e-9));
            assertThat(shift.worst()).isCloseTo(5, byLessThan(0.5e-9));
            assertThat(shift.mean()).isCloseTo(1.25, byLessThan(0.5e-9));
        }
    }

    @Nested
    class LineConfidence {
        @Test
        void isOneForAPerfectFit() {
            assertThat(lineConfidence(HORIZONTAL)).isEqualTo(1.0);
        }

        @Test
        void fallsAsTheResidualGrows() {
            Line noisy = fitLine(List.of(Point.of(0, 0), Point.of(10, 6), Point.of(20, 0)));
            assertThat(lineConfidence(noisy)).isLessThan(0.5);
        }

        @Test
        void isAHalfAtTheTolerance() {
            Line line = new Line(HORIZONTAL.through(), HORIZONTAL.angle(), 2, HORIZONTAL.points());
            assertThat(lineConfidence(line, 2)).isCloseTo(0.5, byLessThan(0.5e-9));
        }

        @Test
        void isZeroForALineWithNothingBehindIt() {
            Line empty = new Line(HORIZONTAL.through(), HORIZONTAL.angle(), HORIZONTAL.residual(), List.of());
            assertThat(lineConfidence(empty)).isEqualTo(0.0);
        }
    }

    @Nested
    class QuadConfidence {
        @Test
        void reportsTheWeakestOfTheFourSides() {
            SideLines lines = edgeLines(turnedSheet(4));
            double confidence = quadConfidence(lines);
            assertThat(confidence).isGreaterThan(0);
            assertThat(confidence).isLessThanOrEqualTo(1);
        }

        @Test
        void isHighForACleanSheet() {
            assertThat(quadConfidence(edgeLines(turnedSheet(0)))).isGreaterThan(0.6);
        }
    }

    @Nested
    class RefineCorners {
        @Test
        void movesTheCornersALittleOnACleanSheet() {
            Mask mask = turnedSheet(6);
            Quad caliper = fitQuad(mask).quad();
            RefinedCorners refined = refineCorners(caliper, edgeLines(mask));
            assertThat(refined.clamped()).isFalse();
            assertThat(refined.shift().worst()).isLessThan(8);
            assertThat(refined.quad().isConvex()).isTrue();
        }

        @Test
        void keepsTheRectangleWhenTheCrossingIsWild() {
            Mask mask = turnedSheet(3);
            Quad caliper = fitQuad(mask).quad();
            SideLines lines = edgeLines(mask);
            SideLines broken = new SideLines(
                    fitLine(List.of(Point.of(0, 0), Point.of(10, 90))), lines.right(), lines.bottom(), lines.left());
            RefinedCorners refined = refineCorners(caliper, broken);
            assertThat(refined.clamped()).isTrue();
            assertThat(refined.quad()).isSameAs(caliper);
        }

        @Test
        void keepsTheRectangleWhenTwoSidesAreParallel() {
            Mask mask = turnedSheet(0);
            Quad caliper = fitQuad(mask).quad();
            SideLines lines = edgeLines(mask);
            SideLines broken = new SideLines(lines.top(), lines.right(), lines.bottom(), lines.top());
            RefinedCorners refined = refineCorners(caliper, broken);
            assertThat(refined.clamped()).isTrue();
            assertThat(refined.quad()).isSameAs(caliper);
        }

        @Test
        void honoursTheLimitItIsGiven() {
            Mask mask = turnedSheet(6);
            Quad caliper = fitQuad(mask).quad();
            SideLines lines = edgeLines(mask);
            assertThat(refineCorners(caliper, lines, 0).clamped()).isTrue();
        }
    }

    @Nested
    class CornerResidual {
        @Test
        void isNearZeroWhenTheQuadSitsOnItsOwnLines() {
            Mask mask = turnedSheet(0);
            SideLines lines = edgeLines(mask);
            assertThat(cornerResidual(cornersFromLines(lines), lines)).isLessThan(1e-6);
        }

        @Test
        void growsWhenTheQuadDoesNotMatchTheLines() {
            Mask mask = turnedSheet(0);
            SideLines lines = edgeLines(mask);
            Quad wrong = Quad.fromRect(Rect.of(0, 0, 10, 10));
            assertThat(cornerResidual(wrong, lines)).isGreaterThan(10);
        }
    }
}
