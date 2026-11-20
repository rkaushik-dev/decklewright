package io.github.rkaushikdev.decklewright.qc;

import static io.github.rkaushikdev.decklewright.qc.Patches.PATCH_MARGIN;
import static io.github.rkaushikdev.decklewright.qc.Patches.channelMeans;
import static io.github.rkaushikdev.decklewright.qc.Patches.gridOver;
import static io.github.rkaushikdev.decklewright.qc.Patches.lightestFlatPatch;
import static io.github.rkaushikdev.decklewright.qc.Patches.patchGrid;
import static io.github.rkaushikdev.decklewright.qc.Patches.patchMeans;
import static io.github.rkaushikdev.decklewright.qc.Patches.patchView;
import static io.github.rkaushikdev.decklewright.testing.Bench.paint;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.decklewright.detect.Patch;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.raster.SurfaceView;
import io.github.rkaushikdev.decklewright.util.Num;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SampleTest {

    private static final List<Rect> WEDGE_RECTS =
            List.of(Rect.of(0, 0, 30, 30), Rect.of(30, 0, 30, 30), Rect.of(60, 0, 30, 30), Rect.of(90, 0, 30, 30));

    /** The wedge's rects as patches, which is how the lightest flat patch is handed them. */
    private static final List<Patch> WEDGE_PATCHES = WEDGE_RECTS.stream()
            .map(rect -> new Patch(rect, List.of(), List.of()))
            .toList();

    /** A gray wedge of up to four patches, one tone each. */
    static Surface wedge(double... tones) {
        Surface surface = Surface.create(120, 30, Layout.GRAY);
        for (int i = 0; i < tones.length; i += 1) {
            paint(surface, WEDGE_RECTS.get(i), tones[i]);
        }
        return surface;
    }

    /** A colour wedge of up to four patches, one colour each. */
    static Surface colourWedge(double[]... colours) {
        Surface surface = Surface.create(120, 30, Layout.RGB);
        for (int i = 0; i < colours.length; i += 1) {
            paint(surface, WEDGE_RECTS.get(i), colours[i]);
        }
        return surface;
    }

    @Nested
    class PatchView {
        @Test
        void takesTheMiddleOfAPatch() {
            SurfaceView view = patchView(wedge(100), WEDGE_RECTS.get(0)).orElseThrow();
            assertThat(view.width()).isLessThan(30);
            assertThat(view.width()).isGreaterThan(10);
        }

        @Test
        void leavesTheSameShareOffBothSides() {
            SurfaceView view = patchView(wedge(100), Rect.of(0, 0, 30, 30)).orElseThrow();
            assertThat(view.width()).isEqualTo((int) (30 - 2 * Math.floor(30 * PATCH_MARGIN)));
        }

        @Test
        void takesTheWholePatchWhenAskedForNoMargin() {
            assertThat(patchView(wedge(100), WEDGE_RECTS.get(0), 0)
                            .orElseThrow()
                            .width())
                    .isEqualTo(30);
        }

        @Test
        void givesNothingBackForAPatchOffTheCapture() {
            assertThat(patchView(wedge(100), Rect.of(500, 500, 10, 10))).isEmpty();
        }
    }

    @Nested
    class ChannelMeans {
        @Test
        void readsEveryChannelOfAFlatPatch() {
            Surface surface = colourWedge(new double[] {10, 20, 30});
            assertThat(Arrays.stream(channelMeans(surface, WEDGE_RECTS.get(0))).map(Num::roundHalfUp))
                    .containsExactly(10.0, 20.0, 30.0);
        }

        @Test
        void ignoresTheEdgesOfThePatch() {
            Surface surface = colourWedge(new double[] {100, 100, 100});
            paint(surface, Rect.of(0, 0, 30, 1), 0, 0, 0);
            assertThat(channelMeans(surface, WEDGE_RECTS.get(0))[0]).isEqualTo(100);
        }

        @Test
        void givesNothingBackForAPatchOffTheCapture() {
            assertThat(channelMeans(colourWedge(new double[] {1, 1, 1}), Rect.of(500, 500, 20, 20)))
                    .isEmpty();
        }
    }

    @Nested
    class PatchGrid {
        @Test
        void makesOnePatchPerCell() {
            assertThat(patchGrid(Rect.of(0, 0, 100, 100), 5, 4)).hasSize(20);
        }

        @Test
        void keepsEveryPatchInsideTheRegion() {
            Rect within = Rect.of(10, 20, 100, 80);
            for (Rect patch : patchGrid(within, 4, 3)) {
                assertThat(within.containsRect(patch)).isTrue();
            }
        }

        @Test
        void leavesAGapBetweenPatches() {
            List<Rect> patches = patchGrid(Rect.of(0, 0, 100, 100), 2, 1);
            assertThat(patches.get(0).right()).isLessThan(patches.get(1).left());
        }

        @Test
        void neverMakesAPatchWithNoArea() {
            for (Rect patch : patchGrid(Rect.of(0, 0, 5, 5), 4, 4)) {
                assertThat(patch.isEmpty()).isFalse();
            }
        }
    }

    @Nested
    class PatchMeans {
        @Test
        void readsOneNumberPerPatch() {
            Surface surface = Surface.create(40, 40, Layout.GRAY, 8, 100);
            assertThat(patchMeans(surface, patchGrid(surface.bounds(), 2, 2))).containsExactly(100, 100, 100, 100);
        }

        @Test
        void dropsAPatchThatFellOffTheCapture() {
            Surface surface = Surface.create(40, 40, Layout.GRAY, 8, 100);
            assertThat(patchMeans(surface, List.of(Rect.of(0, 0, 10, 10), Rect.of(100, 100, 10, 10))))
                    .hasSize(1);
        }

        @Test
        void readsTheWholePatchUnlessItIsToldAMargin() {
            Surface surface = Surface.create(40, 40, Layout.GRAY, 8, 100);
            paint(surface, Rect.of(10, 10, 20, 20), 0);
            assertThat(patchMeans(surface, List.of(Rect.of(0, 0, 40, 40)))[0]).isCloseTo(75, byLessThan(0.5e-6));
            assertThat(patchMeans(surface, List.of(Rect.of(0, 0, 40, 40)), 0.4)[0])
                    .isEqualTo(0);
        }
    }

    @Nested
    class GridOver {
        @Test
        void isThePlainGridWhenThereIsNothingToKeepOff() {
            Rect within = Rect.of(0, 0, 100, 100);
            assertThat(gridOver(within, 4, 4)).hasSize(patchGrid(within, 4, 4).size());
        }

        @Test
        void dropsAPatchThatTouchesSomethingItWasToldToAvoid() {
            Rect within = Rect.of(0, 0, 100, 100);
            List<Rect> kept = gridOver(within, 4, 4, List.of(Rect.of(0, 0, 50, 50)));
            assertThat(kept.size()).isLessThan(16);
            for (Rect patch : kept) {
                assertThat(patch.intersects(Rect.of(0, 0, 50, 50))).isFalse();
            }
        }

        @Test
        void dropsAPatchWholeRatherThanTrimmingIt() {
            Rect within = Rect.of(0, 0, 100, 100);
            List<Double> sizes = gridOver(within, 4, 4, List.of(Rect.of(0, 0, 30, 30))).stream()
                    .map(Rect::width)
                    .distinct()
                    .collect(Collectors.toList());
            assertThat(sizes).hasSize(1);
        }

        @Test
        void canEndUpWithNothingLeft() {
            assertThat(gridOver(Rect.of(0, 0, 100, 100), 2, 2, List.of(Rect.of(0, 0, 100, 100))))
                    .isEqualTo(List.of());
        }

        @Test
        void passesTheMarginThroughToTheGrid() {
            List<Rect> wide = gridOver(Rect.of(0, 0, 100, 100), 2, 2, 0.05, List.of());
            List<Rect> tight = gridOver(Rect.of(0, 0, 100, 100), 2, 2, 0.4, List.of());
            assertThat(wide.get(0).width()).isGreaterThan(tight.get(0).width());
        }
    }

    @Nested
    class LightestFlatPatch {
        @Test
        void picksTheLightestPatchThatIsNotNearClipping() {
            Optional<SurfaceView> view = lightestFlatPatch(wedge(40, 120, 200, 250), WEDGE_PATCHES);
            assertThat(view).isPresent();
            assertThat(view.orElseThrow().get(0, 0)).isEqualTo(200);
        }

        @Test
        void skipsPatchesThatAreTooDark() {
            assertThat(lightestFlatPatch(wedge(10, 20, 30, 40), WEDGE_PATCHES)).isEmpty();
        }

        @Test
        void skipsPatchesThatAreNearWhite() {
            assertThat(lightestFlatPatch(wedge(250, 252, 254, 255), WEDGE_PATCHES))
                    .isEmpty();
        }

        @Test
        void takesAnotherWindowWhenTheCollectionWantsOne() {
            Optional<SurfaceView> view = lightestFlatPatch(wedge(10, 20, 30, 40), WEDGE_PATCHES, 0.5, 0.02);
            assertThat(view.orElseThrow().get(0, 0)).isEqualTo(40);
        }

        @Test
        void givesNothingBackWhenThereAreNoPatchesAtAll() {
            assertThat(lightestFlatPatch(wedge(100), List.of())).isEmpty();
        }
    }
}
