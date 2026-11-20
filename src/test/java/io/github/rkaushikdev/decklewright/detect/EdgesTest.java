package io.github.rkaushikdev.decklewright.detect;

import static io.github.rkaushikdev.decklewright.detect.Edges.distanceToLine;
import static io.github.rkaushikdev.decklewright.detect.Edges.edgeLines;
import static io.github.rkaushikdev.decklewright.detect.Edges.edgePoints;
import static io.github.rkaushikdev.decklewright.detect.Edges.fitLine;
import static io.github.rkaushikdev.decklewright.detect.Edges.fitLineRobust;
import static io.github.rkaushikdev.decklewright.detect.Edges.skewFromLines;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.DetectionError;
import io.github.rkaushikdev.decklewright.geom.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EdgesTest {

    /** An upright filled rectangle, 50 by 40 at (15, 10) on an 80 by 60 mask. */
    static Mask sheet() {
        return sheet(0);
    }

    /** The same rectangle sheared so its sides are not axis aligned. */
    static Mask sheet(double shear) {
        Mask mask = new Mask(80, 60);
        for (int y = 10; y < 50; y += 1) {
            int offset = (int) Math.round((y - 30) * shear);
            for (int x = 15 + offset; x < 65 + offset; x += 1) {
                mask.set(x, y, 1);
            }
        }
        return mask;
    }

    /**
     * The same sheet turned about its centre, which is what a real capture looks like. A shear is
     * not a rotation and the two read differently, so the skew tests use this one.
     */
    static Mask turnedSheet(double degrees) {
        Mask mask = new Mask(120, 100);
        double radians = (degrees * Math.PI) / 180;
        double cos = StrictMath.cos(radians);
        double sin = StrictMath.sin(radians);
        for (int y = 0; y < mask.height(); y += 1) {
            for (int x = 0; x < mask.width(); x += 1) {
                double dx = x - 60;
                double dy = y - 50;
                double ux = dx * cos + dy * sin;
                double uy = -dx * sin + dy * cos;
                if (Math.abs(ux) <= 35 && Math.abs(uy) <= 25) {
                    mask.set(x, y, 1);
                }
            }
        }
        return mask;
    }

    @Nested
    class EdgePoints {
        @Test
        void findsTheFirstSetPixelScanningInward() {
            List<Point> points = edgePoints(sheet(), Side.TOP);
            List<Point> inside =
                    points.stream().filter(p -> p.x() >= 15 && p.x() < 65).toList();
            assertThat(inside).hasSize(50);
            assertThat(inside.stream().allMatch(p -> p.y() == 10)).isTrue();
        }

        @Test
        void scansFromTheFarSideForTheOppositeEdge() {
            List<Point> points = edgePoints(sheet(), Side.BOTTOM).stream()
                    .filter(p -> p.x() >= 15 && p.x() < 65)
                    .toList();
            assertThat(points.stream().allMatch(p -> p.y() == 49)).isTrue();
        }

        @Test
        void scansAcrossForTheVerticalSides() {
            List<Point> left = edgePoints(sheet(), Side.LEFT).stream()
                    .filter(p -> p.y() >= 10 && p.y() < 50)
                    .toList();
            List<Point> right = edgePoints(sheet(), Side.RIGHT).stream()
                    .filter(p -> p.y() >= 10 && p.y() < 50)
                    .toList();
            assertThat(left.stream().allMatch(p -> p.x() == 15)).isTrue();
            assertThat(right.stream().allMatch(p -> p.x() == 64)).isTrue();
        }

        @Test
        void skipsScanLinesThatHoldNothing() {
            assertThat(edgePoints(sheet(), Side.TOP)).hasSize(50);
        }

        @Test
        void honoursAStep() {
            assertThat(edgePoints(sheet(), Side.TOP, 5, 0).size()).isLessThan(50);
        }

        @Test
        void refusesASideItDoesNotKnow() {
            // Side is an enum, so the unknown name is refused where it is turned into one.
            assertThatThrownBy(() -> edgePoints(sheet(), Side.parse("front")))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("side must be one of");
        }
    }

    @Nested
    class FitLine {
        @Test
        void readsAHorizontalRunAsZeroDegrees() {
            List<Point> points = List.of(Point.of(0, 5), Point.of(10, 5), Point.of(20, 5));
            Line line = fitLine(points);
            assertThat(line.angle().degrees()).isCloseTo(0, byLessThan(0.5e-9));
            assertThat(line.residual()).isCloseTo(0, byLessThan(0.5e-9));
            assertThat(line.through().toJson()).isEqualTo(Map.of("x", 10.0, "y", 5.0));
        }

        @Test
        void handlesAVerticalRunWhichAPlainRegressionCannot() {
            List<Point> points = List.of(Point.of(5, 0), Point.of(5, 10), Point.of(5, 20));
            Line line = fitLine(points);
            assertThat(Math.abs(line.angle().degrees())).isCloseTo(90, byLessThan(0.5e-6));
            assertThat(line.residual()).isCloseTo(0, byLessThan(0.5e-9));
        }

        @Test
        void readsASlope() {
            List<Point> points = List.of(Point.of(0, 0), Point.of(10, 1), Point.of(20, 2));
            assertThat(fitLine(points).angle().degrees()).isCloseTo(5.71, byLessThan(0.05));
        }

        @Test
        void reportsAResidualWhenThePointsAreNotCollinear() {
            List<Point> points = List.of(Point.of(0, 0), Point.of(10, 4), Point.of(20, 0));
            assertThat(fitLine(points).residual()).isGreaterThan(1);
        }

        @Test
        void refusesFewerThanTwoPoints() {
            assertThatThrownBy(() -> fitLine(List.of(Point.origin())))
                    .isInstanceOf(DetectionError.class)
                    .hasMessageFindingMatch("at least two points");
        }
    }

    @Nested
    class DistanceToLine {
        @Test
        void isZeroOnTheLineAndGrowsOffIt() {
            Line line = fitLine(List.of(Point.of(0, 5), Point.of(10, 5)));
            assertThat(distanceToLine(line, Point.of(5, 5))).isCloseTo(0, byLessThan(0.5e-9));
            assertThat(distanceToLine(line, Point.of(5, 8))).isCloseTo(3, byLessThan(0.5e-9));
            assertThat(distanceToLine(line, Point.of(5, 2))).isCloseTo(-3, byLessThan(0.5e-9));
        }
    }

    @Nested
    class FitLineRobust {
        @Test
        void ignoresAHandfulOfOutliers() {
            List<Point> points = new ArrayList<>();
            for (int x = 0; x < 40; x += 1) {
                points.add(Point.of(x, 10));
            }
            points.add(Point.of(20, 40));
            points.add(Point.of(21, 45));
            Line plain = fitLine(points);
            Line robust = fitLineRobust(points);
            assertThat(robust.residual()).isLessThan(plain.residual());
            assertThat(robust.angle().degrees()).isCloseTo(0, byLessThan(0.5e-3));
        }

        @Test
        void fallsBackToThePlainFitOnAShortRun() {
            List<Point> points = List.of(Point.of(0, 0), Point.of(5, 0), Point.of(10, 3));
            assertThat(fitLineRobust(points).points()).hasSize(3);
        }

        @Test
        void keepsEveryPointWhenToldToDropNothing() {
            List<Point> points = new ArrayList<>();
            for (int x = 0; x < 20; x += 1) {
                points.add(Point.of(x, x % 3));
            }
            assertThat(fitLineRobust(points, 0, Edges.DEFAULT_PASSES).points()).hasSize(20);
        }
    }

    @Nested
    class EdgeLines {
        @Test
        void fitsAllFourSidesOfASquareSheet() {
            SideLines lines = edgeLines(sheet());
            for (Side side : Edges.SIDES) {
                assertThat(lines.get(side).residual()).isLessThan(1);
            }
        }

        @Test
        void refusesAMaskWithNothingInIt() {
            assertThatThrownBy(() -> edgeLines(new Mask(20, 20)))
                    .isInstanceOf(DetectionError.class)
                    .hasMessageFindingMatch("no edge to fit");
        }
    }

    @Nested
    class SkewFromLines {
        @Test
        void readsZeroForASquareSheet() {
            assertThat(skewFromLines(edgeLines(sheet())).degrees()).isCloseTo(0, byLessThan(0.05));
        }

        @Test
        void readsTheTurnThatWasAppliedToAboutADegree() {
            for (double degrees : new double[] {-12, -6, -3, 3, 6, 12}) {
                double skew = skewFromLines(edgeLines(turnedSheet(degrees))).degrees();
                assertThat(Math.abs(skew - degrees)).isLessThan(1.5);
            }
        }

        @Test
        void readsTheOtherDirectionAsTheOtherSign() {
            double positive = skewFromLines(edgeLines(turnedSheet(4))).degrees();
            double negative = skewFromLines(edgeLines(turnedSheet(-4))).degrees();
            assertThat(positive).isGreaterThan(0);
            assertThat(negative).isLessThan(0);
        }

        @Test
        void growsWithTheTurn() {
            double small = skewFromLines(edgeLines(turnedSheet(3))).degrees();
            double large = skewFromLines(edgeLines(turnedSheet(10))).degrees();
            assertThat(large).isGreaterThan(small);
        }

        @Test
        void landsInsideTheHalfTurnBand() {
            for (double degrees : new double[] {-12, -3, 0, 3, 12}) {
                double skew = skewFromLines(edgeLines(turnedSheet(degrees))).degrees();
                assertThat(skew).isGreaterThanOrEqualTo(-45);
                assertThat(skew).isLessThanOrEqualTo(45);
            }
        }

        @Test
        void isNotFooledByAShearWhichIsNotATurnAtAll() {
            double skew = skewFromLines(edgeLines(sheet(0.1))).degrees();
            assertThat(Math.abs(skew)).isLessThan(6);
        }
    }
}
