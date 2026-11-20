package io.github.rkaushikdev.decklewright.detect;

import static io.github.rkaushikdev.decklewright.detect.Sheets.describeFinding;
import static io.github.rkaushikdev.decklewright.detect.Sheets.findSheet;
import static io.github.rkaushikdev.decklewright.detect.Sheets.objections;
import static io.github.rkaushikdev.decklewright.detect.Sheets.spreadOf;
import static io.github.rkaushikdev.decklewright.testing.Bench.bench;
import static io.github.rkaushikdev.decklewright.testing.Bench.paint;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.decklewright.DetectionError;
import io.github.rkaushikdev.decklewright.geom.Angle;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.testing.Bench;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SheetTest {

    private static final Bench.Options BENCH = Bench.Options.defaults();
    private static final SheetOptions DEFAULTS = SheetOptions.defaults();

    /**
     * A sheet with a tab that reaches the right hand edge of the bed, which is what a badly placed
     * oversize drawing looks like.
     */
    static Surface overrunning() {
        Surface surface = bench(BENCH.withHalfWidth(40).withHalfHeight(30));
        return paint(surface, Rect.of(140, 70, 60, 20), 214);
    }

    /** The finding with another confidence, everything else as it was. */
    static SheetFinding withConfidence(SheetFinding finding, double confidence) {
        return new SheetFinding(
                finding.quad(),
                finding.skew(),
                finding.mask(),
                confidence,
                finding.spread(),
                finding.touchesEdge(),
                finding.coverage(),
                finding.readings());
    }

    @Nested
    class FindSheetOnACleanBench {
        @Test
        void findsASquareSheet() {
            SheetFinding finding = findSheet(bench());
            assertThat(finding.skew().degrees()).isCloseTo(0, byLessThan(0.05));
            assertThat(finding.confidence()).isGreaterThan(0.5);
            assertThat(finding.touchesEdge()).isFalse();
        }

        @Test
        void landsTheQuadOnTheSheet() {
            Rect box = findSheet(bench()).quad().boundingRect();
            assertThat(box.width()).isGreaterThan(120);
            assertThat(box.width()).isLessThan(130);
            assertThat(box.height()).isGreaterThan(85);
            assertThat(box.height()).isLessThan(93);
        }

        @Test
        void readsTheTurnThatWasApplied() {
            for (double degrees : new double[] {-8, -3, 3, 8}) {
                SheetFinding finding = findSheet(bench(BENCH.withDegrees(degrees)));
                assertThat(Math.abs(finding.skew().degrees() - degrees)).isLessThan(0.6);
            }
        }

        @Test
        void hasTheThreeMeasuresAgree() {
            for (double degrees : new double[] {-5, 0, 5}) {
                assertThat(findSheet(bench(BENCH.withDegrees(degrees))).spread())
                        .isLessThan(1.5);
            }
        }

        @Test
        void reportsHowMuchOfTheBedTheSheetTakes() {
            SheetFinding finding = findSheet(bench());
            assertThat(finding.coverage()).isGreaterThan(0.3);
            assertThat(finding.coverage()).isLessThan(0.4);
        }

        @Test
        void worksOnAColourCapture() {
            SheetFinding finding = findSheet(bench(BENCH.withLayout(Layout.RGB)));
            assertThat(finding.confidence()).isGreaterThan(0.5);
        }

        @Test
        void survivesANoisyBed() {
            SheetFinding finding = findSheet(bench(BENCH.withNoise(9).withDegrees(4)), DEFAULTS.withSigma(4));
            assertThat(Math.abs(finding.skew().degrees() - 4)).isLessThan(1);
        }
    }

    @Nested
    class WhatFindSheetRefuses {
        @Test
        void refusesAnEmptyBed() {
            Surface flat = Surface.create(80, 60, Layout.GRAY, 8, 40);
            assertThatThrownBy(() -> findSheet(flat))
                    .isInstanceOf(DetectionError.class)
                    .hasMessageFindingMatch("stands out from it");
        }

        @Test
        void refusesASpeckOfDust() {
            Surface surface = bench(BENCH.withHalfWidth(3).withHalfHeight(3));
            assertThatThrownBy(() -> findSheet(surface))
                    .isInstanceOf(DetectionError.class)
                    .hasMessageFindingMatch("too small to be a sheet");
        }

        @Test
        void canBeToldToAcceptASmallerObject() {
            Surface surface = bench(BENCH.withHalfWidth(12).withHalfHeight(10));
            assertThatThrownBy(() -> findSheet(surface))
                    .isInstanceOf(DetectionError.class)
                    .hasMessageFindingMatch("too small to be a sheet");
            assertThat(findSheet(surface, DEFAULTS.withMinimumCoverage(0.001)).coverage())
                    .isGreaterThan(0);
        }

        @Test
        void reportsASheetThatRunsOffTheBedRatherThanRefusingIt() {
            assertThat(findSheet(overrunning(), DEFAULTS.withSigma(2)).touchesEdge())
                    .isTrue();
        }

        @Test
        void refusesABedTheSheetCoversCompletelySinceThereIsNoBedToRead() {
            assertThatThrownBy(() -> findSheet(bench(BENCH.withHalfWidth(130).withHalfHeight(100))))
                    .isInstanceOf(DetectionError.class)
                    .hasMessageFindingMatch("stands out from it");
        }
    }

    @Nested
    class SpreadOf {
        @Test
        void isTheDistanceBetweenTheFurthestApartPair() {
            SkewReadings readings = new SkewReadings(Angle.degrees(1), Angle.degrees(1.4), Angle.degrees(-0.2));
            assertThat(spreadOf(readings)).isCloseTo(1.6, byLessThan(0.5e-9));
        }

        @Test
        void isZeroWhenAllThreeAgree() {
            Angle one = Angle.degrees(2);
            assertThat(spreadOf(new SkewReadings(one, one, one))).isEqualTo(0.0);
        }
    }

    @Nested
    class DescribeFinding {
        @Test
        void writesOneLineForTheRunLog() {
            String line = describeFinding(findSheet(bench()));
            assertThat(line)
                    .matches("\\d+x\\d+, skew -?\\d+\\.\\d\\d deg, spread \\d+\\.\\d\\d, confidence \\d\\.\\d\\d");
        }

        @Test
        void saysWhenTheSheetRunsOffTheBed() {
            assertThat(describeFinding(findSheet(overrunning(), DEFAULTS.withSigma(2))))
                    .contains("runs off the bed");
        }
    }

    @Nested
    class Objections {
        @Test
        void hasNothingToSayAboutACleanFinding() {
            assertThat(objections(findSheet(bench()))).isEqualTo(List.of());
        }

        @Test
        void objectsToASheetThatRunsOffTheBed() {
            SheetFinding finding = findSheet(overrunning(), DEFAULTS.withSigma(2));
            assertThat(objections(finding)).contains("the sheet runs off the bed");
        }

        @Test
        void objectsToAWeakFit() {
            SheetFinding finding = withConfidence(findSheet(bench()), 0.1);
            assertThat(objections(finding)).contains("the sides do not fit well");
        }

        @Test
        void takesAnotherConfidenceFloor() {
            SheetFinding finding = withConfidence(findSheet(bench()), 0.6);
            assertThat(objections(finding, 0.9)).hasSize(1);
        }
    }
}
