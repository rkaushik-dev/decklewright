package io.github.rkaushikdev.decklewright.qc.checks;

import static io.github.rkaushikdev.decklewright.qc.Check.runCheck;
import static io.github.rkaushikdev.decklewright.qc.checks.GrayBalance.GRAY_BALANCE_LADDER;
import static io.github.rkaushikdev.decklewright.qc.checks.GrayBalance.castDirection;
import static io.github.rkaushikdev.decklewright.qc.checks.GrayBalance.grayBalanceCheck;
import static io.github.rkaushikdev.decklewright.qc.checks.GrayBalance.neutralError;
import static io.github.rkaushikdev.decklewright.qc.checks.TestContexts.grayBalanceLimit;
import static io.github.rkaushikdev.decklewright.qc.checks.TestContexts.patchesOver;
import static io.github.rkaushikdev.decklewright.qc.checks.TestContexts.targetOver;
import static io.github.rkaushikdev.decklewright.testing.Bench.paint;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.detect.Patch;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.qc.Finding;
import io.github.rkaushikdev.decklewright.qc.QcContext;
import io.github.rkaushikdev.decklewright.qc.Rung;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Surface;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GraybalanceTest {

    private static final List<Rect> PATCH_RECTS =
            List.of(Rect.of(0, 0, 20, 20), Rect.of(20, 0, 20, 20), Rect.of(40, 0, 20, 20), Rect.of(60, 0, 20, 20));

    /** A wedge of up to four patches, one colour each. */
    static Surface wedge(double[]... colours) {
        Surface surface = Surface.create(80, 20, Layout.RGB);
        for (int i = 0; i < colours.length; i += 1) {
            paint(surface, PATCH_RECTS.get(i), colours[i]);
        }
        return surface;
    }

    static double[] rgb(double red, double green, double blue) {
        return new double[] {red, green, blue};
    }

    static QcContext context(Surface surface) {
        return context(surface, 6);
    }

    static QcContext context(Surface surface, double tolerance) {
        return QcContext.empty()
                .withSurface(surface)
                .withTarget(targetOver(PATCH_RECTS))
                .withLimits(grayBalanceLimit(tolerance));
    }

    static Surface neutral() {
        return wedge(rgb(40, 40, 40), rgb(90, 90, 90), rgb(150, 150, 150), rgb(210, 210, 210));
    }

    @Nested
    class NeutralError {
        @Test
        void isZeroForANeutralPatch() {
            assertThat(neutralError(new double[] {100, 100, 100})).isEqualTo(0);
        }

        @Test
        void isTheSpreadAcrossTheThreeChannels() {
            assertThat(neutralError(new double[] {90, 100, 104})).isEqualTo(14);
        }

        @Test
        void ignoresAnAlphaChannel() {
            assertThat(neutralError(new double[] {100, 100, 100, 0})).isEqualTo(0);
        }

        @Test
        void isZeroForACaptureWithFewerThanThreeChannels() {
            assertThat(neutralError(new double[] {100})).isEqualTo(0);
        }
    }

    @Nested
    class GrayBalanceCheck {
        @Test
        void givesFourStarsToANeutralWedge() {
            Finding out = runCheck(grayBalanceCheck(), context(neutral()));
            assertThat(out.stars()).isEqualTo(4);
            assertThat(out.measured()).isEqualTo(0);
        }

        @Test
        void takesTheWorstPatchNotTheAverage() {
            Surface mostlyNeutral = wedge(rgb(40, 40, 40), rgb(90, 90, 90), rgb(150, 150, 150), rgb(200, 210, 220));
            Finding out = runCheck(grayBalanceCheck(), context(mostlyNeutral, 30));
            assertThat(out.measured()).isEqualTo(20);
            assertThat(out.stars()).isEqualTo(1);
        }

        @Test
        void givesNoStarsPastTheProfileLimit() {
            Surface cast = wedge(rgb(40, 44, 40), rgb(90, 100, 90), rgb(150, 160, 150), rgb(200, 214, 200));
            assertThat(runCheck(grayBalanceCheck(), context(cast, 6)).stars()).isEqualTo(0);
        }

        @Test
        void passesAGrayscaleCaptureWhichCannotHaveACast() {
            Surface gray = Surface.create(80, 20, Layout.GRAY, 8, 100);
            Finding out = runCheck(grayBalanceCheck(), context(gray));
            assertThat(out.stars()).isEqualTo(4);
            assertThat(out.note()).containsPattern("grayscale capture cannot have a cast");
        }

        @Test
        void saysItCouldNotRunWithoutATarget() {
            Finding out = runCheck(
                    grayBalanceCheck(),
                    QcContext.empty().withSurface(wedge(rgb(1, 1, 1))).withLimits(grayBalanceLimit(6)));
            assertThat(out.ran()).isFalse();
            assertThat(out.note()).containsPattern("no target");
        }

        @Test
        void saysSoWhenNoPatchCouldBeRead() {
            Finding out = runCheck(
                    grayBalanceCheck(),
                    QcContext.empty()
                            .withSurface(wedge(rgb(1, 1, 1)))
                            .withTarget(targetOver(List.of(Rect.of(900, 900, 10, 10))))
                            .withLimits(grayBalanceLimit(6)));
            assertThat(out.stars()).isEqualTo(0);
            assertThat(out.note()).containsPattern("no patch of the target could be read");
        }

        @Test
        void countsThePatchesItRead() {
            assertThat(runCheck(grayBalanceCheck(), context(neutral())).note()).containsPattern("across 4 patches");
        }
    }

    @Nested
    class CastDirection {
        private final List<Patch> patches = patchesOver(PATCH_RECTS);

        @Test
        void namesTheChannelThatRunsHigh() {
            Surface blueish = wedge(rgb(90, 90, 110), rgb(120, 120, 140), rgb(150, 150, 170), rgb(180, 180, 200));
            Cast out = castDirection(blueish, patches).orElseThrow();
            assertThat(out.channel()).isEqualTo("blue");
            assertThat(out.amount()).isGreaterThan(0);
        }

        @Test
        void namesTheChannelThatRunsLowWithANegativeAmount() {
            Surface shortRed = wedge(rgb(70, 100, 100), rgb(90, 120, 120), rgb(120, 150, 150), rgb(150, 180, 180));
            Cast out = castDirection(shortRed, patches).orElseThrow();
            assertThat(out.channel()).isEqualTo("red");
            assertThat(out.amount()).isLessThan(0);
        }

        @Test
        void givesNothingBackForAGrayscaleCapture() {
            Surface gray = Surface.create(80, 20, Layout.GRAY, 8, 100);
            assertThat(castDirection(gray, patches)).isEmpty();
        }

        @Test
        void givesNothingBackWhenNoPatchCouldBeRead() {
            assertThat(castDirection(wedge(rgb(1, 1, 1)), patchesOver(List.of(Rect.of(900, 900, 5, 5)))))
                    .isEmpty();
        }
    }

    @Nested
    class TheLadder {
        @Test
        void isFrozenAndRunsBestFirst() {
            assertThatThrownBy(() -> GRAY_BALANCE_LADDER.add(new Rung(0, 48)))
                    .isInstanceOf(UnsupportedOperationException.class);
            List<Double> limits = GRAY_BALANCE_LADDER.stream().map(Rung::limit).toList();
            assertThat(limits.stream().sorted().toList()).isEqualTo(limits);
        }
    }
}
