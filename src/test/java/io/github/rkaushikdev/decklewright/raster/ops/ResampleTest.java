package io.github.rkaushikdev.decklewright.raster.ops;

import static io.github.rkaushikdev.decklewright.raster.ops.Resampling.fitLongestSide;
import static io.github.rkaushikdev.decklewright.raster.ops.Resampling.reduceInSteps;
import static io.github.rkaushikdev.decklewright.raster.ops.Resampling.resample;
import static io.github.rkaushikdev.decklewright.raster.ops.Resampling.scaleBy;
import static io.github.rkaushikdev.decklewright.testing.TestSurfaces.gray;
import static io.github.rkaushikdev.decklewright.testing.TestSurfaces.row;
import static io.github.rkaushikdev.decklewright.testing.TestSurfaces.rowsOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.geom.Size;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Surface;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ResampleTest {

    private static final ResampleOptions BOX = ResampleOptions.defaults().withKernel("box");

    static Surface checker(int width, int height) {
        Surface s = Surface.create(width, height, Layout.GRAY);
        for (int y = 0; y < height; y += 1) {
            for (int x = 0; x < width; x += 1) {
                s.set(x, y, 0, (x + y) % 2 == 0 ? 255 : 0);
            }
        }
        return s;
    }

    /** The distinct sample values, in the order they first appear. */
    static List<Integer> distinct(Surface s) {
        return Arrays.stream(s.data().toArray()).distinct().boxed().toList();
    }

    @Nested
    class Resample {
        @Test
        void halvesAFlatBlockToItsAverage() {
            Surface out = resample(gray(row(0, 100), row(200, 60)), Size.of(1, 1), BOX);
            assertThat(out.get(0, 0)).isEqualTo(90);
        }

        @Test
        void keepsAConstantFieldConstantAtAnySize() {
            Surface flat = Surface.create(8, 8, Layout.GRAY, 8, 137);
            for (Size size : List.of(Size.of(3, 5), Size.of(16, 2), Size.of(8, 8))) {
                Surface out = resample(flat, size, ResampleOptions.defaults().withKernel("catmullRom"));
                assertThat(distinct(out)).containsExactly(137);
            }
        }

        @Test
        void clonesRatherThanWorkingWhenTheSizeAlreadyMatches() {
            Surface s = gray(row(1, 2));
            Surface out = resample(s, Size.of(2, 1));
            assertThat(rowsOf(out)).containsExactly(row(1, 2));
            assertThat(out).isNotSameAs(s);
        }

        @Test
        void doublesWithABoxByRepeatingSamples() {
            Surface out = resample(gray(row(0, 255)), Size.of(4, 1), BOX);
            assertThat(rowsOf(out)).containsExactly(row(0, 0, 255, 255));
        }

        @Test
        void doublesWithATriangleByInterpolatingBetweenThem() {
            Surface out = resample(
                    gray(row(0, 240)), Size.of(4, 1), ResampleOptions.defaults().withKernel("triangle"));
            int[] first = rowsOf(out).get(0);
            assertThat(first[0]).isEqualTo(0);
            assertThat(first[3]).isEqualTo(240);
            assertThat(first[1]).isGreaterThan(0);
            assertThat(first[1]).isLessThan(first[2]);
        }

        @Test
        void averagesACheckerToMidGrayWhenReducedHard() {
            Surface out = resample(checker(16, 16), Size.of(1, 1), BOX);
            assertThat(out.get(0, 0)).isGreaterThan(120);
            assertThat(out.get(0, 0)).isLessThan(136);
        }

        @Test
        void handlesAColourCaptureChannelByChannel() {
            Surface s = Surface.create(2, 1, Layout.RGB);
            s.setPixel(0, 0, 0, 100, 200);
            s.setPixel(1, 0, 100, 200, 0);
            Surface out = resample(s, Size.of(1, 1), BOX);
            assertThat(out.getPixel(0, 0)).containsExactly(50, 150, 100);
        }

        @Test
        void keepsTheDepthOfTheSource() {
            Surface s = Surface.create(4, 4, Layout.GRAY, 16, 40000);
            Surface out = resample(s, Size.of(2, 2), BOX);
            assertThat(out.depth()).isEqualTo(16);
            assertThat(out.get(0, 0)).isEqualTo(40000);
        }

        @Test
        void neverLeavesTheRangeEvenWhenTheKernelUndershoots() {
            Surface s = gray(row(0, 0, 255, 255, 0, 0));
            Surface out = resample(s, Size.of(12, 1), ResampleOptions.defaults().withKernel("lanczos3"));
            for (int value : out.data().toArray()) {
                assertThat(value).isGreaterThanOrEqualTo(0);
                assertThat(value).isLessThanOrEqualTo(255);
            }
        }

        @Test
        void refusesATargetWithNoPixels() {
            assertThatThrownBy(() -> resample(gray(row(1, 2)), Size.of(0, 1)))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("at least one pixel");
        }
    }

    @Nested
    class ScaleBy {
        @Test
        void scalesBothAxesAndRoundsTheExtents() {
            Surface out = scaleBy(checker(10, 6), 0.5, BOX);
            assertThat(out.size().toJson()).isEqualTo(Map.of("width", 5.0, "height", 3.0));
        }

        @Test
        void neverFallsBelowOnePixel() {
            Surface out = scaleBy(checker(10, 6), 0.01, BOX);
            assertThat(out.size().toJson()).isEqualTo(Map.of("width", 1.0, "height", 1.0));
        }

        @Test
        void refusesAFactorOfZero() {
            assertThatThrownBy(() -> scaleBy(checker(4, 4), 0))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("factor must be greater than zero");
        }
    }

    @Nested
    class FitLongestSide {
        @Test
        void reducesSoTheLongSideLandsOnTheTarget() {
            Surface out = fitLongestSide(checker(40, 20), 10, BOX);
            assertThat(out.size().toJson()).isEqualTo(Map.of("width", 10.0, "height", 5.0));
        }

        @Test
        void leavesACaptureThatIsAlreadySmallEnough() {
            Surface s = checker(8, 4);
            Surface out = fitLongestSide(s, 10);
            assertThat(out.size().toJson()).isEqualTo(Map.of("width", 8.0, "height", 4.0));
            assertThat(out).isNotSameAs(s);
        }

        @Test
        void refusesATargetOfZero() {
            assertThatThrownBy(() -> fitLongestSide(checker(4, 4), 0))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("longest must be greater than zero");
        }
    }

    @Nested
    class ReduceInSteps {
        @Test
        void halvesItsWayDownBeforeTheFinalPass() {
            Reduction out = reduceInSteps(checker(64, 64), Size.of(7, 7));
            assertThat(out.surface().size().toJson()).isEqualTo(Map.of("width", 7.0, "height", 7.0));
            assertThat(out.halvings()).isGreaterThan(0);
        }

        @Test
        void takesNoHalvingsForASmallReduction() {
            Reduction out = reduceInSteps(checker(16, 16), Size.of(12, 12));
            assertThat(out.halvings()).isEqualTo(0);
        }

        @Test
        void landsOnTheSameFlatValueASinglePassWould() {
            Surface flat = Surface.create(64, 64, Layout.GRAY, 8, 90);
            Reduction out = reduceInSteps(flat, Size.of(5, 5));
            assertThat(distinct(out.surface())).containsExactly(90);
        }
    }
}
