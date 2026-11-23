package io.github.rkaushikdev.decklewright.qc.checks;

import static io.github.rkaushikdev.decklewright.qc.Check.runCheck;
import static io.github.rkaushikdev.decklewright.qc.checks.Sharpness.SHARPNESS_LADDER;
import static io.github.rkaushikdev.decklewright.qc.checks.Sharpness.edgeProfile;
import static io.github.rkaushikdev.decklewright.qc.checks.Sharpness.edgeStrips;
import static io.github.rkaushikdev.decklewright.qc.checks.Sharpness.riseDistance;
import static io.github.rkaushikdev.decklewright.qc.checks.Sharpness.sharpnessCheck;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.measure.Axis;
import io.github.rkaushikdev.decklewright.qc.Finding;
import io.github.rkaushikdev.decklewright.qc.QcContext;
import io.github.rkaushikdev.decklewright.qc.Rung;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.util.Num;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SharpnessTest {

    private static final Rect SHEET = Rect.of(60, 50, 180, 140);

    /**
     * A sheet on a bed with a chosen amount of blur across every edge.
     *
     * @param blur in pixels of transition
     */
    static Surface bench(double blur) {
        Surface surface = Surface.create(300, 240, Layout.GRAY, 8, 40);

        for (int y = 0; y < 240; y += 1) {
            for (int x = 0; x < 300; x += 1) {
                double inside = Math.min(
                        Math.min(x - SHEET.left(), SHEET.right() - 1 - x),
                        Math.min(y - SHEET.top(), SHEET.bottom() - 1 - y));
                double share = blur <= 0 ? (inside >= 0 ? 1 : 0) : Math.max(0, Math.min(1, inside / blur + 0.5));
                surface.set(x, y, 0, Num.roundHalfUp(40 + (210 - 40) * share));
            }
        }
        return surface;
    }

    @Nested
    class RiseDistance {
        @Test
        void isSmallForAStepThatHappensInOneSample() {
            assertThat(riseDistance(new double[] {0, 0, 0, 100, 100, 100})).isCloseTo(0.8, byLessThan(0.05));
        }

        @Test
        void growsWithTheWidthOfTheTransition() {
            double sharp = riseDistance(new double[] {0, 0, 50, 100, 100});
            double soft = riseDistance(new double[] {0, 20, 40, 60, 80, 100});
            assertThat(soft).isGreaterThan(sharp);
        }

        @Test
        void readsAFallingStepAsWellAsARisingOne() {
            assertThat(riseDistance(new double[] {100, 100, 50, 0, 0}))
                    .isCloseTo(riseDistance(new double[] {0, 0, 50, 100, 100}), byLessThan(0.5e-6));
        }

        @Test
        void givesNothingBackForAFlatProfile() {
            assertThat(riseDistance(new double[] {50, 50, 50, 50})).isNaN();
        }

        @Test
        void givesNothingBackForAProfileTooShortToHaveAStep() {
            assertThat(riseDistance(new double[] {1, 2})).isNaN();
        }
    }

    @Nested
    class EdgeProfile {
        @Test
        void averagesAlongTheEdgeAndAcrossIt() {
            double[] profile =
                    edgeProfile(bench(1), Rect.of(100, 42, 60, 16), Axis.Y).orElseThrow();
            assertThat(profile).hasSize(16);
            assertThat(profile[0]).isLessThan(profile[15]);
        }

        @Test
        void givesNothingBackForAStripOffTheCapture() {
            assertThat(edgeProfile(bench(1), Rect.of(900, 900, 10, 10), Axis.Y)).isEmpty();
        }

        @Test
        void givesNothingBackForAStripTooThinToMeasure() {
            assertThat(edgeProfile(bench(1), Rect.of(100, 40, 60, 2), Axis.Y)).isEmpty();
        }
    }

    @Nested
    class EdgeStrips {
        @Test
        void makesOneStripPerSide() {
            assertThat(edgeStrips(SHEET)).hasSize(4);
        }

        @Test
        void straddlesEachEdge() {
            EdgeStrip top =
                    edgeStrips(SHEET, StripOptions.defaults().withReach(6)).get(0);
            assertThat(top.strip().top()).isEqualTo(SHEET.top() - 6);
            assertThat(top.strip().bottom()).isEqualTo(SHEET.top() + 6);
        }

        @Test
        void averagesOverTheMiddleOfEachEdge() {
            EdgeStrip top =
                    edgeStrips(SHEET, StripOptions.defaults().withAlong(0.5)).get(0);
            assertThat(top.strip().width()).isEqualTo(90);
            assertThat(Math.abs(top.strip().centre().x() - SHEET.centre().x())).isLessThanOrEqualTo(1);
        }

        @Test
        void neverMakesAStripTooThinToMeasure() {
            for (EdgeStrip edge : edgeStrips(Rect.of(0, 0, 4, 4))) {
                assertThat(edge.strip().width()).isGreaterThanOrEqualTo(3);
                assertThat(edge.strip().height()).isGreaterThanOrEqualTo(3);
            }
        }
    }

    @Nested
    class SharpnessCheck {
        private Finding run(double blur) {
            return runCheck(
                    sharpnessCheck(), QcContext.empty().withSurface(bench(blur)).withSheet(SHEET));
        }

        @Test
        void givesFourStarsToAHardEdge() {
            Finding out = run(0);
            assertThat(out.stars()).isEqualTo(4);
            assertThat(out.note()).containsPattern("across 4 edges");
        }

        @Test
        void stillPassesACaptureWithOnePixelOfSoftness() {
            assertThat(run(1).stars()).isGreaterThanOrEqualTo(3);
        }

        @Test
        void dropsStarsAsTheEdgeSoftens() {
            assertThat(run(3).stars()).isLessThan(4);
            assertThat(run(8).stars()).isLessThan(run(3).stars());
        }

        @Test
        void givesNoStarsToABadlyOutOfFocusCapture() {
            assertThat(run(20).stars()).isEqualTo(0);
        }

        @Test
        void reportsTheSoftestEdgeNotTheAverage() {
            Finding out = run(4);
            assertThat(out.measured()).isGreaterThan(2);
        }

        @Test
        void saysItCouldNotRunWithoutASheet() {
            Finding out = runCheck(sharpnessCheck(), QcContext.empty().withSurface(bench(1)));
            assertThat(out.ran()).isFalse();
            assertThat(out.note()).containsPattern("no sheet");
        }

        @Test
        void saysSoWhenNoEdgeGaveAStep() {
            Surface flat = Surface.create(300, 240, Layout.GRAY, 8, 100);
            Finding out = runCheck(
                    sharpnessCheck(), QcContext.empty().withSurface(flat).withSheet(SHEET));
            assertThat(out.stars()).isEqualTo(0);
            assertThat(out.note()).containsPattern("no edge of the sheet gave a usable step");
        }
    }

    /** The bench with a rim of the given height drawn just inside every edge and a trench just outside. */
    static Surface sharpened(double rim) {
        Surface surface = bench(0);
        for (int y = 0; y < 240; y += 1) {
            for (int x = 0; x < 300; x += 1) {
                double inside = Math.min(
                        Math.min(x - SHEET.left(), SHEET.right() - 1 - x),
                        Math.min(y - SHEET.top(), SHEET.bottom() - 1 - y));
                if (inside == 0) {
                    surface.set(x, y, 0, Math.min(255, 210 + rim));
                } else if (inside == -1) {
                    surface.set(x, y, 0, Math.max(0, 40 - rim));
                }
            }
        }
        return surface;
    }

    @Nested
    class HaloOf {
        @Test
        void isZeroForAStepThatStaysBetweenItsEnds() {
            assertThat(Sharpness.haloOf(new double[] {10, 10, 50, 90, 90})).isZero();
        }

        @Test
        void measuresAnOvershootPastTheFarEndAsAShareOfTheStep() {
            assertThat(Sharpness.haloOf(new double[] {10, 10, 100, 90, 90})).isEqualTo(0.125);
        }

        @Test
        void measuresAnUndershootPastTheNearEndToo() {
            assertThat(Sharpness.haloOf(new double[] {90, 90, 0, 10, 10})).isEqualTo(0.125);
        }

        @Test
        void givesNothingBackWithoutAStep() {
            assertThat(Sharpness.haloOf(new double[] {40, 40, 40, 40})).isNaN();
            assertThat(Sharpness.haloOf(new double[] {40, 90})).isNaN();
        }
    }

    @Nested
    class SharpeningCheck {
        private Finding run(Surface surface) {
            return runCheck(
                    Sharpness.sharpeningCheck(),
                    QcContext.empty().withSurface(surface).withSheet(SHEET));
        }

        @Test
        void givesFourStarsToAnEdgeWithNoHalo() {
            Finding out = run(bench(2));
            assertThat(out.stars()).isEqualTo(4);
            assertThat(out.note()).isEqualTo("no halo along 4 edges");
        }

        @Test
        void dropsStarsAsTheSharpeningRimGrows() {
            // the step is 170 values tall, so a 25 value rim is just under fifteen percent
            Finding out = run(sharpened(25));
            assertThat(out.measured()).isCloseTo(25 / 170.0, byLessThan(1e-9));
            assertThat(out.stars()).isEqualTo(2);
            assertThat(out.note()).isEqualTo("the worst edge overshoots by 14.7 percent of its step across 4 edges");
            // the rim clips at white, so the trench below the bed is what grades this one
            assertThat(run(sharpened(70)).measured()).isCloseTo(45 / 170.0, byLessThan(1e-9));
            assertThat(run(sharpened(70)).stars()).isEqualTo(1);
        }

        @Test
        void passesTheSharpnessCheckThatTheHaloFools() {
            Surface surface = sharpened(25);
            Finding sharp = runCheck(
                    sharpnessCheck(), QcContext.empty().withSurface(surface).withSheet(SHEET));
            assertThat(sharp.stars()).isEqualTo(4);
            assertThat(run(surface).stars()).isLessThan(sharp.stars());
        }

        @Test
        void saysSoWhenNoEdgeGaveAStep() {
            Finding out = run(Surface.create(300, 240, Layout.GRAY, 8, 100));
            assertThat(out.stars()).isZero();
            assertThat(out.note()).isEqualTo("no edge of the sheet gave a usable step");
        }

        @Test
        void readsTheSameEdgesTheSharpnessCheckReads() {
            assertThat(Sharpness.edgeProfiles(bench(2), SHEET, 8)).hasSize(4);
            assertThat(Sharpness.edgeProfiles(bench(2), Rect.of(400, 400, 20, 20), 8))
                    .isEmpty();
        }
    }

    @Nested
    class TheLadder {
        @Test
        void isFrozenAndRunsBestFirst() {
            assertThatThrownBy(() -> SHARPNESS_LADDER.add(new Rung(0, 12)))
                    .isInstanceOf(UnsupportedOperationException.class);
            List<Double> limits = SHARPNESS_LADDER.stream().map(Rung::limit).toList();
            assertThat(limits.stream().sorted().toList()).isEqualTo(limits);
        }
    }
}
