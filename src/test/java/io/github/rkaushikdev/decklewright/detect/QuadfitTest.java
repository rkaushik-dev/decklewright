package io.github.rkaushikdev.decklewright.detect;

import static io.github.rkaushikdev.decklewright.detect.QuadFit.fitQuad;
import static io.github.rkaushikdev.decklewright.detect.QuadFit.foldToBand;
import static io.github.rkaushikdev.decklewright.detect.QuadFit.minimumAreaRect;
import static io.github.rkaushikdev.decklewright.detect.QuadFit.outlineHull;
import static io.github.rkaushikdev.decklewright.detect.QuadFit.quadSkew;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.decklewright.DetectionError;
import io.github.rkaushikdev.decklewright.geom.Angle;
import io.github.rkaushikdev.decklewright.geom.Polygon;
import io.github.rkaushikdev.decklewright.geom.Quad;
import io.github.rkaushikdev.decklewright.geom.Rect;
import java.util.Arrays;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class QuadfitTest {

    /** A 70 by 50 sheet turned about the centre of a 140 by 120 mask. */
    static Mask turnedSheet(double degrees) {
        return turnedSheet(degrees, 35, 25);
    }

    /** A sheet of the given half extents turned about the centre of a 140 by 120 mask. */
    static Mask turnedSheet(double degrees, double halfWidth, double halfHeight) {
        Mask mask = new Mask(140, 120);
        double radians = (degrees * Math.PI) / 180;
        double cos = StrictMath.cos(radians);
        double sin = StrictMath.sin(radians);
        for (int y = 0; y < mask.height(); y += 1) {
            for (int x = 0; x < mask.width(); x += 1) {
                double dx = x - 70;
                double dy = y - 60;
                double along = dx * cos + dy * sin;
                double across = -dx * sin + dy * cos;
                if (Math.abs(along) <= halfWidth && Math.abs(across) <= halfHeight) {
                    mask.set(x, y, 1);
                }
            }
        }
        return mask;
    }

    @Nested
    class OutlineHull {
        @Test
        void hullsTheOutlineOfASquareSheet() {
            Polygon hull = outlineHull(turnedSheet(0));
            assertThat(hull.count()).isGreaterThanOrEqualTo(4);
            assertThat(hull.area()).isGreaterThan(70 * 50 * 0.95);
        }

        @Test
        void takesEveryNthBoundaryPixelWhenToldTo() {
            Polygon dense = outlineHull(turnedSheet(7));
            Polygon sparse = outlineHull(turnedSheet(7), 5);
            assertThat(sparse.area()).isGreaterThan(dense.area() * 0.9);
        }

        @Test
        void refusesAMaskWithNothingInIt() {
            assertThatThrownBy(() -> outlineHull(new Mask(20, 20)))
                    .isInstanceOf(DetectionError.class)
                    .hasMessageFindingMatch("no outline to fit");
        }
    }

    @Nested
    class MinimumAreaRect {
        @Test
        void wrapsAnAxisAlignedRectangleExactly() {
            Polygon hull = Polygon.fromRect(Rect.of(10, 20, 40, 30));
            CaliperRect rect = minimumAreaRect(hull);
            assertThat(rect.area()).isCloseTo(1200, byLessThan(0.5e-6));
            assertThat(rect.quad().topLeft().x()).isCloseTo(10, byLessThan(0.5e-6));
            assertThat(rect.quad().bottomRight().y()).isCloseTo(50, byLessThan(0.5e-6));
        }

        @Test
        void beatsTheAxisAlignedBoxOnATurnedRectangle() {
            Polygon hull = outlineHull(turnedSheet(20));
            double area = minimumAreaRect(hull).area();
            assertThat(area).isLessThan(hull.boundingRect().area() * 0.95);
        }

        @Test
        void reportsAnOrientationInsideTheBand() {
            for (double degrees : new double[] {-40, -20, -5, 0, 5, 20, 40}) {
                Angle angle = minimumAreaRect(outlineHull(turnedSheet(degrees))).angle();
                assertThat(angle.degrees()).isGreaterThanOrEqualTo(-45);
                assertThat(angle.degrees()).isLessThanOrEqualTo(45);
            }
        }
    }

    @Nested
    class FoldToBand {
        @Test
        void leavesASmallAngleAlone() {
            assertThat(foldToBand(Angle.degrees(3)).degrees()).isCloseTo(3, byLessThan(0.5e-9));
            assertThat(foldToBand(Angle.degrees(-3)).degrees()).isCloseTo(-3, byLessThan(0.5e-9));
        }

        @Test
        void foldsAQuarterTurnOntoZero() {
            assertThat(foldToBand(Angle.degrees(90)).degrees()).isCloseTo(0, byLessThan(0.5e-9));
            assertThat(foldToBand(Angle.degrees(180)).degrees()).isCloseTo(0, byLessThan(0.5e-9));
            assertThat(foldToBand(Angle.degrees(-90)).degrees()).isCloseTo(0, byLessThan(0.5e-9));
        }

        @Test
        void foldsAnAngleJustPastTheBand() {
            assertThat(foldToBand(Angle.degrees(46)).degrees()).isCloseTo(-44, byLessThan(0.5e-9));
            assertThat(foldToBand(Angle.degrees(93)).degrees()).isCloseTo(3, byLessThan(0.5e-9));
        }

        @Test
        void alwaysLandsInsideTheBand() {
            for (int d = -360; d <= 360; d += 7) {
                double folded = foldToBand(Angle.degrees(d)).degrees();
                assertThat(folded).isGreaterThanOrEqualTo(-45);
                assertThat(folded).isLessThanOrEqualTo(45);
            }
        }
    }

    @Nested
    class FitQuad {
        @Test
        void readsTheTurnThatWasApplied() {
            for (double degrees : new double[] {-20, -12, -6, -2, 0, 2, 6, 12, 20}) {
                Angle angle = fitQuad(turnedSheet(degrees)).angle();
                assertThat(Math.abs(angle.degrees() - degrees)).isLessThan(1);
            }
        }

        @Test
        void landsTheQuadOnTheSheet() {
            Quad quad = fitQuad(turnedSheet(8)).quad();
            double[] lengths = quad.edgeLengths();
            double longest = Arrays.stream(lengths).max().orElseThrow();
            double shortest = Arrays.stream(lengths).min().orElseThrow();
            assertThat(longest).isGreaterThan(65);
            assertThat(longest).isLessThan(76);
            assertThat(shortest).isGreaterThan(45);
            assertThat(shortest).isLessThan(56);
        }

        @Test
        void reportsAHighFillForASolidSheet() {
            assertThat(fitQuad(turnedSheet(5)).fill()).isGreaterThan(0.9);
        }

        @Test
        void reportsALowFillForAShapeThatIsNothingLikeARectangle() {
            Mask mask = new Mask(70, 70);
            for (int i = 5; i < 65; i += 1) {
                for (int k = 0; k < 4; k += 1) {
                    mask.set(i, 33 + k, 1);
                    mask.set(33 + k, i, 1);
                }
            }
            assertThat(fitQuad(mask).fill()).isLessThan(0.3);
        }

        @Test
        void handlesANearlySquareSheet() {
            Angle angle = fitQuad(turnedSheet(7, 30, 30)).angle();
            assertThat(Math.abs(angle.degrees() - 7)).isLessThan(1.5);
        }
    }

    @Nested
    class QuadSkew {
        @Test
        void usesTheLongerPairOfSides() {
            Quad quad = fitQuad(turnedSheet(9)).quad();
            assertThat(Math.abs(quadSkew(quad).degrees() - 9)).isLessThan(1);
        }

        @Test
        void readsZeroForAnAxisAlignedQuad() {
            Quad quad = minimumAreaRect(Polygon.fromRect(Rect.of(0, 0, 40, 20))).quad();
            assertThat(quadSkew(quad).degrees()).isCloseTo(0, byLessThan(0.5e-6));
        }
    }
}
