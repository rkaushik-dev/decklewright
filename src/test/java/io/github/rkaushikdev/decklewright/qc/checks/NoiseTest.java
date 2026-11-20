package io.github.rkaushikdev.decklewright.qc.checks;

import static io.github.rkaushikdev.decklewright.qc.Check.runCheck;
import static io.github.rkaushikdev.decklewright.qc.checks.Noise.CLIPPING_LADDER;
import static io.github.rkaushikdev.decklewright.qc.checks.Noise.NOISE_LADDER;
import static io.github.rkaushikdev.decklewright.qc.checks.Noise.clippingCheck;
import static io.github.rkaushikdev.decklewright.qc.checks.Noise.noiseCheck;
import static io.github.rkaushikdev.decklewright.qc.checks.TestContexts.LIMITS;
import static io.github.rkaushikdev.decklewright.qc.checks.TestContexts.noiseLimits;
import static io.github.rkaushikdev.decklewright.qc.checks.TestContexts.targetOver;
import static io.github.rkaushikdev.decklewright.testing.Bench.paint;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.profile.QcLimits;
import io.github.rkaushikdev.decklewright.qc.Finding;
import io.github.rkaushikdev.decklewright.qc.QcContext;
import io.github.rkaushikdev.decklewright.qc.Rung;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Samples;
import io.github.rkaushikdev.decklewright.raster.Surface;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NoiseTest {

    private static final List<Rect> PATCH_RECTS =
            List.of(Rect.of(0, 0, 30, 30), Rect.of(30, 0, 30, 30), Rect.of(60, 0, 30, 30), Rect.of(90, 0, 30, 30));

    /** A clean gray wedge of up to four patches, one tone each. */
    static Surface wedge(double... tones) {
        return noisyWedge(0, tones);
    }

    /**
     * A wedge with a given tone per patch and a chosen amount of noise on it.
     *
     * <p>The noise comes from a seeded generator whose product is taken in doubles before the mask,
     * so every sample is fixed and the expected readings can be exact.
     *
     * @param noise plus or minus this much on every sample
     */
    static Surface noisyWedge(int noise, double... tones) {
        Surface surface = Surface.create(120, 30, Layout.GRAY);
        for (int i = 0; i < tones.length; i += 1) {
            paint(surface, PATCH_RECTS.get(i), tones[i]);
        }
        if (noise > 0) {
            Samples data = surface.data();
            int seed = 5;
            for (int i = 0; i < data.length(); i += 1) {
                seed = (int) (long) (seed * 1103515245.0 + 12345.0) & 0x7fffffff;
                int jitter = (seed % (noise * 2 + 1)) - noise;
                data.set(i, Math.max(0, Math.min(255, data.get(i) + jitter)));
            }
        }
        return surface;
    }

    static QcContext context(Surface surface) {
        return context(surface, LIMITS);
    }

    static QcContext context(Surface surface, QcLimits limits) {
        return QcContext.empty()
                .withSurface(surface)
                .withTarget(targetOver(PATCH_RECTS))
                .withLimits(limits);
    }

    @Nested
    class NoiseCheck {
        @Test
        void givesFourStarsToAPatchWithNoNoiseAtAll() {
            Finding out = runCheck(noiseCheck(), context(wedge(40, 120, 200, 250)));
            assertThat(out.stars()).isEqualTo(4);
            assertThat(out.note()).containsPattern("no measurable dB");
        }

        @Test
        void dropsStarsAsTheNoiseGrows() {
            int quiet = runCheck(noiseCheck(), context(noisyWedge(2, 40, 120, 200, 250)))
                    .stars();
            int loud = runCheck(noiseCheck(), context(noisyWedge(30, 40, 120, 200, 250), noiseLimits(10, 0.005)))
                    .stars();
            assertThat(loud).isLessThan(quiet);
        }

        @Test
        void givesNoStarsUnderTheProfileFloor() {
            Finding out = runCheck(noiseCheck(), context(noisyWedge(40, 40, 120, 200, 250), noiseLimits(40, 0.005)));
            assertThat(out.stars()).isEqualTo(0);
            assertThat(out.passed()).isFalse();
        }

        @Test
        void saysSoWhenNoPatchIsUsable() {
            Finding out = runCheck(noiseCheck(), context(wedge(10, 12, 14, 16)));
            assertThat(out.stars()).isEqualTo(0);
            assertThat(out.note()).containsPattern("no patch of the target is usable");
        }

        @Test
        void saysItCouldNotRunWithoutATarget() {
            Finding out = runCheck(
                    noiseCheck(), QcContext.empty().withSurface(wedge(100)).withLimits(noiseLimits(30, 0.005)));
            assertThat(out.ran()).isFalse();
        }
    }

    @Nested
    class ClippingCheck {
        @Test
        void givesFourStarsWhenNothingIsClipped() {
            Finding out = runCheck(clippingCheck(), context(wedge(40, 120, 200, 250)));
            assertThat(out.stars()).isEqualTo(4);
            assertThat(out.note()).isEqualTo("nothing is clipped");
        }

        @Test
        void findsClippingInTheHighlightsAndSaysWhere() {
            Surface surface = wedge(40, 120, 200, 250);
            paint(surface, Rect.of(0, 0, 20, 30), 255);
            Finding out = runCheck(clippingCheck(), context(surface, noiseLimits(30, 1)));
            assertThat(out.note()).containsPattern("clipped in the highlights");
            assertThat(out.measured()).isGreaterThan(0.1);
        }

        @Test
        void findsClippingInTheShadowsToo() {
            Surface surface = wedge(40, 120, 200, 250);
            paint(surface, Rect.of(0, 0, 20, 30), 0);
            Finding out = runCheck(clippingCheck(), context(surface, noiseLimits(30, 1)));
            assertThat(out.note()).containsPattern("clipped in the shadows");
        }

        @Test
        void givesNoStarsPastTheProfileLimit() {
            Surface surface = wedge(40, 120, 200, 250);
            paint(surface, Rect.of(0, 0, 20, 30), 255);
            assertThat(runCheck(clippingCheck(), context(surface)).stars()).isEqualTo(0);
        }

        @Test
        void measuresOnlyTheCropWhenThereIsOne() {
            Surface surface = wedge(40, 120, 200, 250);
            paint(surface, Rect.of(0, 0, 20, 30), 255);
            Finding out = runCheck(
                    clippingCheck(), context(surface, noiseLimits(30, 1)).withCrop(Rect.of(60, 0, 60, 30)));
            assertThat(out.measured()).isEqualTo(0);
        }

        @Test
        void readsEveryChannelOfAColourCapture() {
            Surface colour = Surface.create(20, 20, Layout.RGB, 8, 100);
            paint(colour, Rect.of(0, 0, 4, 20), 100, 255, 100);
            Finding out = runCheck(
                    clippingCheck(), QcContext.empty().withSurface(colour).withLimits(noiseLimits(30, 1)));
            assertThat(out.measured()).isGreaterThan(0.1);
        }
    }

    @Nested
    class TheLadders {
        @Test
        void areFrozen() {
            assertThatThrownBy(() -> NOISE_LADDER.add(new Rung(0, 0)))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> CLIPPING_LADDER.add(new Rung(0, 1)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void runBestFirstEachInItsOwnDirection() {
            List<Double> noise = NOISE_LADDER.stream().map(Rung::limit).toList();
            assertThat(noise.stream().sorted(Comparator.reverseOrder()).toList())
                    .isEqualTo(noise);
            List<Double> clipping = CLIPPING_LADDER.stream().map(Rung::limit).toList();
            assertThat(clipping.stream().sorted().toList()).isEqualTo(clipping);
        }
    }
}
