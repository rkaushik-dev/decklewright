package io.github.rkaushikdev.decklewright.detect;

import static io.github.rkaushikdev.decklewright.detect.Deskew.coarseSkew;
import static io.github.rkaushikdev.decklewright.detect.Deskew.measureSkew;
import static io.github.rkaushikdev.decklewright.detect.Deskew.projectionScore;
import static io.github.rkaushikdev.decklewright.detect.Deskew.refineSkew;
import static io.github.rkaushikdev.decklewright.detect.Deskew.sampleSetPixels;
import static io.github.rkaushikdev.decklewright.detect.QuadFit.fitQuad;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.DetectionError;
import io.github.rkaushikdev.decklewright.geom.Angle;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DeskewTest {

    private static final SkewOptions DEFAULTS = SkewOptions.defaults();

    /** A 90 by 64 sheet turned about the centre of a 160 by 130 mask. */
    static Mask turnedSheet(double degrees) {
        return turnedSheet(degrees, 45, 32);
    }

    /** A sheet of the given half extents turned about the centre of a 160 by 130 mask. */
    static Mask turnedSheet(double degrees, double halfWidth, double halfHeight) {
        Mask mask = new Mask(160, 130);
        double radians = (degrees * Math.PI) / 180;
        double cos = StrictMath.cos(radians);
        double sin = StrictMath.sin(radians);
        for (int y = 0; y < mask.height(); y += 1) {
            for (int x = 0; x < mask.width(); x += 1) {
                double dx = x - 80;
                double dy = y - 65;
                if (Math.abs(dx * cos + dy * sin) <= halfWidth && Math.abs(-dx * sin + dy * cos) <= halfHeight) {
                    mask.set(x, y, 1);
                }
            }
        }
        return mask;
    }

    @Nested
    class SampleSetPixels {
        @Test
        void collectsEverySetPixelWhenToldToSkipTheOutline() {
            Mask mask = new Mask(4, 4);
            mask.set(1, 1, 1);
            mask.set(2, 3, 1);
            SetPixels pixels = sampleSetPixels(mask, 1, false);
            assertThat(pixels.xs()).containsExactly(1, 2);
            assertThat(pixels.ys()).containsExactly(1, 3);
        }

        @Test
        void takesTheOutlineByDefaultWhichIsFarFewerPixels() {
            Mask mask = turnedSheet(0);
            double filled = sampleSetPixels(mask, 1, false).xs().length;
            double outline = sampleSetPixels(mask).xs().length;
            assertThat(outline).isLessThan(filled / 4);
        }

        @Test
        void honoursAStep() {
            Mask mask = turnedSheet(0);
            assertThat((double) sampleSetPixels(mask, 4, true).xs().length).isLessThan(mask.count() / 3.0);
        }

        @Test
        void refusesAnEmptyMask() {
            assertThatThrownBy(() -> sampleSetPixels(new Mask(4, 4)))
                    .isInstanceOf(DetectionError.class)
                    .hasMessageFindingMatch("nothing to measure skew from");
        }
    }

    @Nested
    class ProjectionScore {
        @Test
        void peaksAtTheAngleTheSheetIsTurnedTo() {
            SetPixels pixels = sampleSetPixels(turnedSheet(5));
            double atTruth = projectionScore(pixels, Angle.degrees(5));
            assertThat(atTruth).isGreaterThan(projectionScore(pixels, Angle.degrees(0)));
            assertThat(atTruth).isGreaterThan(projectionScore(pixels, Angle.degrees(10)));
        }

        @Test
        void isSymmetricAboutTheTruth() {
            SetPixels pixels = sampleSetPixels(turnedSheet(0));
            double left = projectionScore(pixels, Angle.degrees(-4));
            double right = projectionScore(pixels, Angle.degrees(4));
            assertThat(Math.abs(left - right) / left).isLessThan(0.15);
        }
    }

    @Nested
    class CoarseSkew {
        @Test
        void landsWithinAStepOfTheTruth() {
            for (double degrees : new double[] {-8, -3, 0, 3, 8}) {
                Angle angle =
                        coarseSkew(turnedSheet(degrees), DEFAULTS.withStep(0.5)).angle();
                assertThat(Math.abs(angle.degrees() - degrees)).isLessThanOrEqualTo(1);
            }
        }

        @Test
        void reportsHowManyCandidatesItTried() {
            int tried = coarseSkew(turnedSheet(0), DEFAULTS.withRange(5).withStep(1))
                    .tried();
            assertThat(tried).isEqualTo(11);
        }

        @Test
        void seesNothingOutsideTheRangeItWasGiven() {
            Angle angle = coarseSkew(turnedSheet(9), DEFAULTS.withRange(3).withStep(0.5))
                    .angle();
            assertThat(Math.abs(angle.degrees())).isLessThanOrEqualTo(3);
        }

        @Test
        void refusesARangeOrStepOfZero() {
            assertThatThrownBy(() -> coarseSkew(turnedSheet(0), DEFAULTS.withRange(0)))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("range must be greater");
            assertThatThrownBy(() -> coarseSkew(turnedSheet(0), DEFAULTS.withStep(0)))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("step must be greater");
        }
    }

    @Nested
    class RefineSkew {
        @Test
        void landsOnABetterScoreThanTheCoarseCandidate() {
            Mask mask = turnedSheet(4.3);
            SkewEstimate coarse = coarseSkew(mask, DEFAULTS.withStep(1));
            SkewEstimate fine = refineSkew(mask, coarse.angle(), DEFAULTS.withWindow(1.5));
            assertThat(fine.score()).isGreaterThanOrEqualTo(coarse.score());
            assertThat(fine.angle().degrees()).isNotEqualTo(coarse.angle().degrees());
        }

        @Test
        void staysInsideTheWindowItWasGiven() {
            Mask mask = turnedSheet(3);
            SkewEstimate fine = refineSkew(mask, Angle.degrees(3), DEFAULTS.withWindow(0.4));
            assertThat(Math.abs(fine.angle().degrees() - 3)).isLessThanOrEqualTo(0.4);
        }

        @Test
        void stopsAtTheToleranceItWasGiven() {
            Mask mask = turnedSheet(2);
            SkewEstimate loose = refineSkew(mask, Angle.degrees(2), DEFAULTS.withTolerance(0.5));
            SkewEstimate tight = refineSkew(mask, Angle.degrees(2), DEFAULTS.withTolerance(0.001));
            assertThat(tight.tried()).isGreaterThan(loose.tried());
        }
    }

    @Nested
    class MeasureSkew {
        @Test
        void readsASmallTurnToAFractionOfADegree() {
            for (double degrees : new double[] {-6, -2.5, 0, 2.5, 6}) {
                Angle angle = measureSkew(turnedSheet(degrees)).angle();
                assertThat(Math.abs(angle.degrees() - degrees)).isLessThan(0.6);
            }
        }

        @Test
        void reportsTheCoarseAnswerAlongsideTheFineOne() {
            SkewMeasurement out = measureSkew(turnedSheet(3.2));
            assertThat(Math.abs(out.coarse().degrees() - 3.2)).isLessThan(1);
            assertThat(out.tried()).isGreaterThan(10);
        }

        @Test
        void agreesWithTheCaliperFit() {
            for (double degrees : new double[] {-7, -1, 1, 7}) {
                Mask mask = turnedSheet(degrees);
                double projection = measureSkew(mask).angle().degrees();
                double calipers = fitQuad(mask).angle().degrees();
                assertThat(Math.abs(projection - calipers)).isLessThan(0.6);
            }
        }

        @Test
        void worksOnANarrowSheetAsWellAsAWideOne() {
            Mask mask = turnedSheet(4, 50, 12);
            assertThat(Math.abs(measureSkew(mask).angle().degrees() - 4)).isLessThan(0.6);
        }

        @Test
        void measuresTheFillDirectlyWhenTheOutlineIsTurnedOff() {
            SkewMeasurement out = measureSkew(turnedSheet(0), DEFAULTS.withOutline(false));
            assertThat(Math.abs(out.angle().degrees())).isLessThan(1);
        }
    }
}
