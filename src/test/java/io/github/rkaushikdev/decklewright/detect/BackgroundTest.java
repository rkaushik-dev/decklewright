package io.github.rkaushikdev.decklewright.detect;

import static io.github.rkaushikdev.decklewright.detect.Backgrounds.borderStrips;
import static io.github.rkaushikdev.decklewright.detect.Backgrounds.describeBackground;
import static io.github.rkaushikdev.decklewright.detect.Backgrounds.distanceFrom;
import static io.github.rkaushikdev.decklewright.detect.Backgrounds.estimateBackground;
import static io.github.rkaushikdev.decklewright.detect.Backgrounds.isUniform;
import static io.github.rkaushikdev.decklewright.detect.Backgrounds.objectMask;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.util.Num;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BackgroundTest {

    private static final int BED = 40;

    /** A flat bed with a flat sheet on it, so the numbers are checkable by hand. */
    static Surface capture() {
        return capture(210, 0);
    }

    /** The bench with a noisy bed. */
    static Surface noisy(int noise) {
        return capture(210, noise);
    }

    /**
     * A 100 by 80 gray bed of forty with a 50 by 40 sheet on it at (25, 20). Bed noise, when asked
     * for, is seeded and plus or minus the amount, and the sheet is painted over it.
     */
    static Surface capture(int sheet, int noise) {
        Surface s = Surface.create(100, 80, Layout.GRAY, 8, BED);
        int seed = 17;
        if (noise > 0) {
            for (int i = 0; i < s.data().length(); i += 1) {
                seed = (int) (long) (seed * 1103515245.0 + 12345.0) & 0x7fffffff;
                s.data().set(i, BED + (seed % (noise * 2 + 1)) - noise);
            }
        }
        for (int y = 20; y < 60; y += 1) {
            for (int x = 25; x < 75; x += 1) {
                s.set(x, y, 0, sheet);
            }
        }
        return s;
    }

    static Background background(List<Double> mean, List<Double> stdDev, int samples) {
        return new Background(mean, stdDev, samples);
    }

    @Nested
    class BorderStrips {
        @Test
        void returnsFourStripsThatDoNotOverlap() {
            List<Rect> strips = borderStrips(capture(), 0.1);
            assertThat(strips).hasSize(4);
            assertThat(strips.get(0).intersects(strips.get(1))).isFalse();
            assertThat(strips.get(2).intersects(strips.get(3))).isFalse();
        }

        @Test
        void keepsEveryStripInsideTheCapture() {
            Surface surface = capture();
            for (Rect strip : borderStrips(surface, 0.2)) {
                assertThat(surface.bounds().containsRect(strip)).isTrue();
            }
        }

        @Test
        void neverProducesAStripThinnerThanAPixel() {
            Surface tiny = Surface.create(10, 10, Layout.GRAY);
            for (Rect strip : borderStrips(tiny, 0.001)) {
                assertThat(strip.width()).isGreaterThanOrEqualTo(1);
                assertThat(strip.height()).isGreaterThanOrEqualTo(1);
            }
        }

        @Test
        void refusesAFractionThatWouldSwallowTheCapture() {
            assertThatThrownBy(() -> borderStrips(capture(), 0.6))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("fraction must be between");
            assertThatThrownBy(() -> borderStrips(capture(), 0))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("fraction must be between");
        }
    }

    @Nested
    class EstimateBackground {
        @Test
        void readsTheBedAndNotTheSheet() {
            Background background = estimateBackground(capture());
            assertThat(background.mean().get(0)).isCloseTo(40, byLessThan(0.5e-6));
            assertThat(background.stdDev().get(0)).isCloseTo(0, byLessThan(0.5e-6));
            assertThat(background.samples()).isGreaterThan(0);
        }

        @Test
        void reportsTheSpreadOfANoisyBed() {
            Background background = estimateBackground(noisy(10));
            assertThat(background.mean().get(0)).isGreaterThan(30);
            assertThat(background.mean().get(0)).isLessThan(50);
            assertThat(background.stdDev().get(0)).isGreaterThan(3);
        }

        @Test
        void readsEveryChannelOfAColourBed() {
            Surface s = Surface.create(60, 60, Layout.RGB);
            for (int y = 0; y < 60; y += 1) {
                for (int x = 0; x < 60; x += 1) {
                    s.setPixel(x, y, 20, 40, 60);
                }
            }
            Background background = estimateBackground(s);
            assertThat(background.mean().stream().map(Num::roundHalfUp).toList())
                    .isEqualTo(List.of(20.0, 40.0, 60.0));
        }

        @Test
        void isNotFooledByASheetThatReachesTheMiddleOfAnEdge() {
            Surface s = capture();
            for (int y = 0; y < 80; y += 1) {
                for (int x = 45; x < 55; x += 1) {
                    s.set(x, y, 0, 210);
                }
            }
            Background background = estimateBackground(s, 0.02);
            assertThat(background.mean().get(0)).isLessThan(90);
        }
    }

    @Nested
    class DistanceFrom {
        private final Background background = background(List.of(40.0, 40.0, 40.0), List.of(4.0, 4.0, 4.0), 100);

        @Test
        void isZeroAtTheBackgroundItself() {
            assertThat(distanceFrom(new double[] {40, 40, 40}, background)).isEqualTo(0.0);
        }

        @Test
        void countsSpreadsAway() {
            assertThat(distanceFrom(new double[] {48, 40, 40}, background)).isEqualTo(2.0);
            assertThat(distanceFrom(new double[] {32, 40, 40}, background)).isEqualTo(2.0);
        }

        @Test
        void takesTheWorstChannel() {
            assertThat(distanceFrom(new double[] {40, 44, 60}, background)).isEqualTo(5.0);
        }

        @Test
        void neverDividesByASpreadBelowOne() {
            Background flat = background(List.of(40.0), List.of(0.0), 10);
            assertThat(distanceFrom(new double[] {45}, flat)).isEqualTo(5.0);
        }
    }

    @Nested
    class ObjectMaskBehaviour {
        @Test
        void findsTheSheetAndNothingElse() {
            Mask mask = objectMask(capture()).mask();
            assertThat(mask.extent().toJson()).isEqualTo(Map.of("x", 25.0, "y", 20.0, "width", 50.0, "height", 40.0));
            assertThat(mask.count()).isEqualTo(50 * 40);
        }

        @Test
        void stillFindsItOnANoisyBed() {
            Mask mask = objectMask(noisy(8), 4).mask();
            Rect extent = mask.extent();
            assertThat(extent.width()).isGreaterThan(40);
            assertThat(extent.width()).isLessThan(60);
        }

        @Test
        void findsNothingWhenTheSheetIsTheSameToneAsTheBed() {
            Mask mask = objectMask(capture(BED, 0)).mask();
            assertThat(mask.count()).isEqualTo(0);
            assertThat(mask.extent().isEmpty()).isTrue();
        }

        @Test
        void takesABackgroundThatWasAlreadyMeasured() {
            Background background = estimateBackground(capture());
            ObjectMask found = objectMask(capture(), background, Backgrounds.DEFAULT_SIGMA);
            assertThat(found.background()).isSameAs(background);
            assertThat(found.mask().count()).isEqualTo(2000);
        }

        @Test
        void letsTheThresholdBeLoosened() {
            long loose = objectMask(noisy(20), 2).mask().count();
            long tight = objectMask(noisy(20), 8).mask().count();
            assertThat(loose).isGreaterThanOrEqualTo(tight);
        }

        @Test
        void worksOnAColourCapture() {
            Surface s = Surface.create(40, 40, Layout.RGB);
            for (int y = 0; y < 40; y += 1) {
                for (int x = 0; x < 40; x += 1) {
                    s.setPixel(x, y, 30, 30, 30);
                }
            }
            for (int y = 10; y < 30; y += 1) {
                for (int x = 10; x < 30; x += 1) {
                    s.setPixel(x, y, 30, 200, 30);
                }
            }
            assertThat(objectMask(s).mask().count()).isEqualTo(400);
        }
    }

    @Nested
    class IsUniform {
        @Test
        void acceptsAFlatBedAndRejectsAMottledOne() {
            assertThat(isUniform(background(List.of(40.0), List.of(3.0), 10))).isTrue();
            assertThat(isUniform(background(List.of(40.0), List.of(40.0), 10))).isFalse();
        }

        @Test
        void scalesToTheDepthItIsToldAbout() {
            Background background = background(List.of(10000.0), List.of(500.0), 10);
            assertThat(isUniform(background)).isFalse();
            assertThat(isUniform(background, Backgrounds.DEFAULT_MAX_SPREAD, 65535))
                    .isTrue();
        }
    }

    @Nested
    class DescribeBackground {
        @Test
        void writesOneLineForTheLog() {
            String out = describeBackground(background(List.of(40.4, 41.0), List.of(3.25, 3.0), 1200));
            assertThat(out).isEqualTo("bed at 40, 41 give or take 3.3, 3.0 over 1200 samples");
        }
    }
}
