package io.github.rkaushikdev.decklewright.derive;

import static io.github.rkaushikdev.decklewright.derive.Rendering.cutMaster;
import static io.github.rkaushikdev.decklewright.derive.Rendering.cutOut;
import static io.github.rkaushikdev.decklewright.derive.Rendering.named;
import static io.github.rkaushikdev.decklewright.derive.Rendering.renderAll;
import static io.github.rkaushikdev.decklewright.derive.Rendering.renderDerivative;
import static io.github.rkaushikdev.decklewright.derive.Rendering.straighten;
import static io.github.rkaushikdev.decklewright.derive.Rendering.targetSize;
import static io.github.rkaushikdev.decklewright.testing.Bench.paint;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.PipelineError;
import io.github.rkaushikdev.decklewright.geom.Angle;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.geom.Size;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.plan.CropPlan;
import io.github.rkaushikdev.decklewright.plan.CropPlanner;
import io.github.rkaushikdev.decklewright.profile.DerivativeRecipe;
import io.github.rkaushikdev.decklewright.raster.Fill;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.raster.ops.PaddedCrop;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RenderTest {

    /** The whole capture the plans are drawn against, before any turn is taken out. */
    private static final Rect FRAME = Rect.of(0, 0, 200, 160);

    /** Where the sheet sits on the capture, and where a plan cuts by default. */
    private static final Rect SHEET = Rect.of(20, 20, 160, 120);

    /** A crop that runs off the bottom right of the capture. */
    private static final Rect OVERHANG = Rect.of(150, 120, 100, 80);

    private static final List<DerivativeRecipe> RECIPES = List.of(
            TestRecipe.named("master")
                    .withDepth(16)
                    .withLayout(Layout.RGB)
                    .withKernel("lanczos3")
                    .recipe(),
            TestRecipe.named("service")
                    .withLongestSide(80)
                    .withDepth(8)
                    .withLayout(Layout.RGB)
                    .withKernel("catmullRom")
                    .recipe(),
            TestRecipe.named("thumb")
                    .withLongestSide(20)
                    .withDepth(8)
                    .withLayout(Layout.GRAY)
                    .withKernel("box")
                    .recipe());

    /** A plan in the straightened frame of the capture, at 400 ppi. */
    static CropPlan plan(Rect rect, double degrees) {
        Angle skew = Angle.degrees(degrees);
        return CropPlan.start(CropPlanner.straightFrame(FRAME, skew).capture(), rect, Sampling.of(400), skew);
    }

    /** A square plan that cuts the sheet. */
    static CropPlan plan() {
        return plan(SHEET, 0);
    }

    /** A capture of the given layout and depth, the sheet painted lighter than the bed. */
    static Surface capture(Layout layout, int depth) {
        Surface surface = Surface.create(200, 160, layout, depth, 40);
        if (layout == Layout.GRAY) {
            paint(surface, SHEET, 200);
        } else {
            paint(surface, SHEET, 200, 190, 180);
        }
        return surface;
    }

    /** An eight bit rgb capture. */
    static Surface capture() {
        return capture(Layout.RGB, 8);
    }

    @Nested
    class Straighten {
        @Test
        void handsBackWhatItWasGivenWhenThereIsNoTurnToTake() {
            Surface surface = capture();
            assertThat(straighten(surface, plan())).isSameAs(surface);
        }

        @Test
        void turnsTheCaptureTheOtherWay() {
            Surface turned = straighten(capture(), plan(SHEET, 3));
            assertThat(turned.width()).isGreaterThan(200);
        }
    }

    @Nested
    class CutOut {
        @Test
        void cutsThePlanOutOfTheCapture() {
            Surface cut = cutOut(capture(), plan()).surface();
            assertThat(cut.width()).isEqualTo(160);
            assertThat(cut.height()).isEqualTo(120);
        }

        @Test
        void padsRatherThanClipsACropThatReachesPastTheEdge() {
            PaddedCrop out = cutOut(capture(), plan(OVERHANG, 0), Fill.uniform(7));
            Surface cut = out.surface();
            assertThat(Size.of(cut.width(), cut.height())).isEqualTo(Size.of(100, 80));
            assertThat(out.filled()).isTrue();
            assertThat(cut.get(99, 79, 0)).isEqualTo(7);
        }
    }

    @Nested
    class CutMaster {
        @Test
        void saysWhatItDid() {
            List<String> steps = cutMaster(capture(), plan()).steps();
            assertThat(String.join(" ", steps)).containsPattern("cut 160 by 120 out of 200 by 160");
        }

        @Test
        void saysNothingAboutStraighteningACaptureThatWasSquare() {
            assertThat(String.join(" ", cutMaster(capture(), plan()).steps())).doesNotContainPattern("straightened");
        }

        @Test
        void namesTheAngleItTookOut() {
            List<String> steps = cutMaster(capture(), plan(SHEET, 2)).steps();
            assertThat(steps.get(0)).containsPattern("straightened by 2\\.000 degrees");
        }

        @Test
        void saysWhenItHadToPad() {
            List<String> steps = cutMaster(capture(), plan(OVERHANG, 0)).steps();
            assertThat(String.join(" ", steps)).containsPattern("reaches \\d+ px past the capture");
        }
    }

    @Nested
    class TargetSize {
        @Test
        void isNothingWhenTheRecipeAsksForNoChange() {
            assertThat(targetSize(Size.of(100, 80), TestRecipe.named("master").recipe()))
                    .isEmpty();
        }

        @Test
        void fitsTheLongestSide() {
            assertThat(targetSize(
                            Size.of(100, 80),
                            TestRecipe.unnamed().withLongestSide(50).recipe()))
                    .contains(Size.of(50, 40));
        }

        @Test
        void neverEnlargesToReachALongestSide() {
            assertThat(targetSize(
                            Size.of(100, 80),
                            TestRecipe.unnamed().withLongestSide(500).recipe()))
                    .isEmpty();
        }

        @Test
        void takesAPlainScale() {
            assertThat(targetSize(
                            Size.of(100, 80),
                            TestRecipe.unnamed().withScale(0.25).recipe()))
                    .contains(Size.of(25, 20));
        }

        @Test
        void readsAScaleOfOneAsNoChange() {
            assertThat(targetSize(
                            Size.of(100, 80), TestRecipe.unnamed().withScale(1).recipe()))
                    .isEmpty();
        }
    }

    @Nested
    class RenderDerivative {
        private final Surface master = cutOut(capture(), plan()).surface();
        private final Sampling sampling = Sampling.of(400);

        @Test
        void leavesAMasterAtTheSizeItWasCut() {
            Rendered out = renderDerivative(master, RECIPES.get(0), sampling);
            assertThat(out.surface().width()).isEqualTo(160);
            assertThat(out.scale()).isEqualTo(1.0);
        }

        @Test
        void carriesTheFrequencyDownWithThePixels() {
            Rendered out = renderDerivative(master, RECIPES.get(1), sampling);
            assertThat(out.surface().width()).isEqualTo(80);
            assertThat(out.sampling().x()).isEqualTo(200.0);
        }

        @Test
        void leavesTheFrequencyAloneWhenTheSizeIsLeftAlone() {
            assertThat(renderDerivative(master, RECIPES.get(0), sampling)
                            .sampling()
                            .x())
                    .isEqualTo(400.0);
        }

        @Test
        void changesTheLayoutTheRecipeAsksFor() {
            Rendered out = renderDerivative(master, RECIPES.get(2), sampling);
            assertThat(out.surface().layout()).isEqualTo(Layout.GRAY);
            assertThat(out.surface().channels()).isEqualTo(1);
        }

        @Test
        void changesTheDepthTheRecipeAsksFor() {
            assertThat(renderDerivative(master, RECIPES.get(0), sampling)
                            .surface()
                            .depth())
                    .isEqualTo(16);
            assertThat(renderDerivative(master, RECIPES.get(1), sampling)
                            .surface()
                            .depth())
                    .isEqualTo(8);
        }

        @Test
        void halvesItsWayDownALargeReduction() {
            Rendered out = renderDerivative(master, RECIPES.get(2), sampling);
            assertThat(String.join(" ", out.steps())).containsPattern("after [1-9] halvings");
        }

        @Test
        void resamplesInOnePassForASmallReduction() {
            DerivativeRecipe near =
                    TestRecipe.named("near").withLongestSide(120).recipe();
            Rendered out = renderDerivative(master, near, sampling);
            assertThat(String.join(" ", out.steps())).doesNotContainPattern("halvings");
            assertThat(out.surface().width()).isEqualTo(120);
        }

        @Test
        void saysWhatItDidInTheOrderItDidIt() {
            Surface deep = cutOut(capture(Layout.RGB, 16), plan()).surface();
            Rendered out = renderDerivative(deep, RECIPES.get(2), sampling);
            assertThat(out.steps().get(0)).isEqualTo("to gray");
            assertThat(out.steps().get(1)).containsPattern("^to 20 by 15");
            assertThat(out.steps().get(2)).isEqualTo("to 8 bit");
        }

        @Test
        void saysNothingAboutADepthItDidNotHaveToChange() {
            Rendered out = renderDerivative(master, RECIPES.get(2), sampling);
            assertThat(String.join(" ", out.steps())).doesNotContainPattern("bit");
        }

        @Test
        void refusesARecipeWithNoName() {
            DerivativeRecipe nameless = TestRecipe.unnamed().withLongestSide(40).recipe();
            assertThatThrownBy(() -> renderDerivative(master, nameless, sampling))
                    .isInstanceOf(PipelineError.class)
                    .hasMessageFindingMatch("no name");
        }
    }

    @Nested
    class RenderAll {
        @Test
        void rendersEveryRecipeOffOneCut() {
            RenderedSet out = renderAll(capture(), plan(), RECIPES);
            assertThat(out.rendered()).extracting(Rendered::name).containsExactly("master", "service", "thumb");
            assertThat(out.master().width()).isEqualTo(160);
        }

        @Test
        void cutsOnceSoEveryDerivativeAgreesOnTheFrame() {
            RenderedSet out = renderAll(capture(), plan(SHEET, 2), RECIPES);
            Set<String> aspects = out.rendered().stream()
                    .map(item -> NumberText.fixed(
                            item.surface().width() / (double) item.surface().height(), 1))
                    .collect(Collectors.toSet());
            assertThat(aspects).hasSize(1);
        }

        @Test
        void carriesTheCutStepsAsWellAsTheRenderSteps() {
            RenderedSet out = renderAll(capture(), plan(), RECIPES);
            assertThat(String.join(" ", out.cut())).containsPattern("cut 160 by 120");
        }

        @Test
        void refusesAProfileThatAsksForNothing() {
            assertThatThrownBy(() -> renderAll(capture(), plan(), List.of()))
                    .isInstanceOf(PipelineError.class)
                    .hasMessageFindingMatch("no derivatives");
        }

        @Test
        void refusesTwoDerivativesUnderTheSameName() {
            List<DerivativeRecipe> twice = List.of(
                    RECIPES.get(0),
                    TestRecipe.of(RECIPES.get(1)).withName("master").recipe());
            assertThatThrownBy(() -> renderAll(capture(), plan(), twice))
                    .isInstanceOf(PipelineError.class)
                    .hasMessageFindingMatch("same name");
        }
    }

    @Nested
    class Named {
        @Test
        void picksOneOutByName() {
            RenderedSet out = renderAll(capture(), plan(), RECIPES);
            assertThat(named(out.rendered(), "thumb").surface().layout()).isEqualTo(Layout.GRAY);
        }

        @Test
        void refusesANameThatWasNeverRendered() {
            RenderedSet out = renderAll(capture(), plan(), RECIPES);
            assertThatThrownBy(() -> named(out.rendered(), "poster"))
                    .isInstanceOf(PipelineError.class)
                    .hasMessageFindingMatch("no derivative");
        }
    }
}
