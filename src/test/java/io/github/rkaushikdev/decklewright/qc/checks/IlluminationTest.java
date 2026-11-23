package io.github.rkaushikdev.decklewright.qc.checks;

import static io.github.rkaushikdev.decklewright.qc.Check.runCheck;
import static io.github.rkaushikdev.decklewright.qc.checks.Illumination.ILLUMINATION_LADDER;
import static io.github.rkaushikdev.decklewright.qc.checks.Illumination.darkestCorner;
import static io.github.rkaushikdev.decklewright.qc.checks.Illumination.illuminationCheck;
import static io.github.rkaushikdev.decklewright.qc.checks.TestContexts.illuminationLimit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.qc.Finding;
import io.github.rkaushikdev.decklewright.qc.QcContext;
import io.github.rkaushikdev.decklewright.qc.Rung;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.testing.Bench;
import io.github.rkaushikdev.decklewright.util.Num;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class IlluminationTest {

    /**
     * A bed lit unevenly from one side.
     *
     * @param falloff how much darker the far side is, as a share
     */
    static Surface litBed(double falloff) {
        Surface surface = Surface.create(200, 160, Layout.GRAY);
        for (int y = 0; y < 160; y += 1) {
            for (int x = 0; x < 200; x += 1) {
                double share = 1 - (x / 199.0) * falloff;
                surface.set(x, y, 0, Num.roundHalfUp(200 * share));
            }
        }
        return surface;
    }

    static QcContext context(Surface surface) {
        return context(surface, 0.1);
    }

    static QcContext context(Surface surface, double tolerance) {
        return QcContext.empty().withSurface(surface).withLimits(illuminationLimit(tolerance));
    }

    @Nested
    class IlluminationCheck {
        @Test
        void givesFourStarsToAnEvenlyLitBed() {
            Surface flat = Surface.create(200, 160, Layout.GRAY, 8, 180);
            Finding out = runCheck(illuminationCheck(), context(flat));
            assertThat(out.stars()).isEqualTo(4);
            assertThat(out.measured()).isCloseTo(0, byLessThan(0.5e-6));
        }

        @Test
        void dropsStarsAsTheFalloffGrows() {
            assertThat(runCheck(illuminationCheck(), context(litBed(0.03))).stars())
                    .isEqualTo(4);
            assertThat(runCheck(illuminationCheck(), context(litBed(0.08), 0.2)).stars())
                    .isEqualTo(3);
            assertThat(runCheck(illuminationCheck(), context(litBed(0.3), 0.4)).stars())
                    .isLessThanOrEqualTo(2);
        }

        @Test
        void givesNoStarsOnceTheProfileLimitIsBroken() {
            Finding out = runCheck(illuminationCheck(), context(litBed(0.3), 0.1));
            assertThat(out.stars()).isEqualTo(0);
            assertThat(out.passed()).isFalse();
        }

        @Test
        void saysHowManyPatchesItReadAndWhatTheLimitWas() {
            Finding out = runCheck(illuminationCheck(3, 2), context(litBed(0.05)));
            assertThat(out.note()).containsPattern("across 6 patches, against a limit of 10.0");
        }

        @Test
        void measuresOnlyTheRegionItIsGiven() {
            Surface surface = litBed(0.4);
            double whole = runCheck(illuminationCheck(), context(surface, 1)).measured();
            double corner = runCheck(illuminationCheck(), context(surface, 1).withBed(Rect.of(0, 0, 40, 160)))
                    .measured();
            assertThat(corner).isLessThan(whole);
        }

        @Test
        void saysItCouldNotRunWithoutACapture() {
            Finding out = runCheck(illuminationCheck(), QcContext.empty().withLimits(illuminationLimit(0.1)));
            assertThat(out.ran()).isFalse();
        }

        @Test
        void saysSoWhenTheRegionIsTooSmallToGrid() {
            Surface tiny = Surface.create(2, 2, Layout.GRAY, 8, 100);
            Finding out = runCheck(illuminationCheck(1, 1), context(tiny));
            assertThat(out.stars()).isEqualTo(0);
            assertThat(out.note()).containsPattern("not enough of the bed");
        }
    }

    @Nested
    class DarkestCorner {
        @Test
        void namesTheCornerALampHasToMoveTowards() {
            assertThat(darkestCorner(litBed(0.4)).corner()).containsPattern("right");
        }

        @Test
        void reportsTheMeanItFoundThere() {
            DarkCorner out = darkestCorner(litBed(0.4));
            assertThat(out.mean()).isGreaterThan(0);
            assertThat(out.mean()).isLessThan(200);
        }

        @Test
        void saysHowFarUnderTheBrightestCornerItSits() {
            DarkCorner out = darkestCorner(litBed(0.4));
            assertThat(out.under()).isBetween(0.2, 0.4);
            assertThat(darkestCorner(Surface.create(40, 40, Layout.GRAY, 8, 120))
                            .under())
                    .isZero();
        }

        @Test
        void readsTheBedAndNotADarkSheetLyingOnIt() {
            Surface surface = litBed(0.3);
            Rect sheet = Rect.of(10, 10, 70, 140);
            Bench.paint(surface, sheet, 30);
            assertThat(darkestCorner(surface, surface.bounds()).corner()).isEqualTo("top left");
            assertThat(darkestCorner(surface, surface.bounds(), List.of(sheet)).corner())
                    .isEqualTo("top right");
        }

        @Test
        void passesOverAQuarterTheSheetCoversWhole() {
            Surface surface = litBed(0);
            Rect sheet = Rect.of(0, 0, 100, 80);
            Bench.paint(surface, sheet, 10);
            DarkCorner out = darkestCorner(surface, surface.bounds(), List.of(sheet));
            assertThat(out.corner()).isEqualTo("top right");
            assertThat(out.mean()).isEqualTo(200);
        }

        @Test
        void hasNoCornerWhenEveryQuarterIsCovered() {
            Surface surface = litBed(0.2);
            DarkCorner out = darkestCorner(surface, surface.bounds(), List.of(surface.bounds()));
            assertThat(out.corner()).isEqualTo("top left");
            assertThat(out.mean()).isNaN();
        }

        @Test
        void picksTheFirstCornerOnAFlatBed() {
            Surface flat = Surface.create(40, 40, Layout.GRAY, 8, 120);
            assertThat(darkestCorner(flat).corner()).isEqualTo("top left");
        }
    }

    @Nested
    class TheLadder {
        @Test
        void isFrozenAndRunsBestFirst() {
            assertThatThrownBy(() -> ILLUMINATION_LADDER.add(new Rung(0, 1)))
                    .isInstanceOf(UnsupportedOperationException.class);
            List<Double> limits = ILLUMINATION_LADDER.stream().map(Rung::limit).toList();
            assertThat(limits.stream().sorted().toList()).isEqualTo(limits);
        }
    }
}
