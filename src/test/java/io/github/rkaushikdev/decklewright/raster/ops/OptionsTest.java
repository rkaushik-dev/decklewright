package io.github.rkaushikdev.decklewright.raster.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.geom.Angle;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.geom.Size;
import io.github.rkaushikdev.decklewright.raster.EdgeMode;
import io.github.rkaushikdev.decklewright.raster.Fill;
import io.github.rkaushikdev.decklewright.raster.Kernels;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.SampleMode;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.testing.TestSurfaces;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The option records that stand in for the original's option bags: their defaults are the
 * original's, and each {@code withX} changes only its own part.
 */
class OptionsTest {

    @Nested
    class PadOptionsBehaviour {
        @Test
        void defaultToAConstantBlackBorder() {
            assertThat(PadOptions.defaults()).isEqualTo(new PadOptions(PadMode.CONSTANT, Fill.uniform(0)));
            assertThat(Padding.PAD_MODES).containsExactly(PadMode.CONSTANT, PadMode.EDGE, PadMode.MIRROR);
        }

        @Test
        void changeOnePartAtATime() {
            PadOptions options = PadOptions.defaults().withMode(PadMode.MIRROR).withFill(Fill.perChannel(1, 2));
            assertThat(options.mode()).isEqualTo(PadMode.MIRROR);
            assertThat(options.fill()).isEqualTo(Fill.perChannel(1, 2));
            assertThat(options.withFill(3).fill()).isEqualTo(Fill.uniform(3));
            assertThatThrownBy(() -> options.withMode(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class ResampleOptionsBehaviour {
        @Test
        void defaultToACatmullRomClampedAtTheEdge() {
            assertThat(ResampleOptions.defaults()).isEqualTo(new ResampleOptions(Kernels.CATMULL_ROM, EdgeMode.CLAMP));
        }

        @Test
        void changeOnePartAtATime() {
            ResampleOptions options =
                    ResampleOptions.defaults().withEdge(EdgeMode.MIRROR).withKernel(Kernels.BOX);
            assertThat(options).isEqualTo(new ResampleOptions(Kernels.BOX, EdgeMode.MIRROR));
            assertThat(options.withKernel("triangle").kernel()).isSameAs(Kernels.TRIANGLE);
            assertThatThrownBy(() -> options.withKernel("sinc"))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessage("unknown kernel sinc");
        }

        @Test
        void readPastTheEdgeTheWayTheyAreTold() {
            Surface s = TestSurfaces.gray(TestSurfaces.row(0, 200));
            ResampleOptions triangle = ResampleOptions.defaults().withKernel(Kernels.TRIANGLE);
            Surface clamped = Resampling.resample(s, Size.of(4, 1), triangle);
            Surface mirrored = Resampling.resample(s, Size.of(4, 1), triangle.withEdge(EdgeMode.MIRROR));
            assertThat(clamped.data().toArray()).containsExactly(0, 50, 150, 200);
            assertThat(mirrored.data().toArray()).containsExactly(50, 50, 150, 150);
            assertThat(Resampling.scaleBy(s, 1).data().toArray()).containsExactly(0, 200);
        }
    }

    @Nested
    class RotateOptionsBehaviour {
        @Test
        void defaultToAnExpandedBilinearRotationWithBlackCorners() {
            assertThat(RotateOptions.defaults())
                    .isEqualTo(new RotateOptions(true, SampleMode.BILINEAR, EdgeMode.CONSTANT, Fill.uniform(0)));
        }

        @Test
        void changeOnePartAtATime() {
            RotateOptions options = RotateOptions.defaults()
                    .withExpand(false)
                    .withSample(SampleMode.NEAREST)
                    .withEdge(EdgeMode.MIRROR)
                    .withBackground(Fill.perChannel(1, 2, 3));
            assertThat(options)
                    .isEqualTo(new RotateOptions(false, SampleMode.NEAREST, EdgeMode.MIRROR, Fill.perChannel(1, 2, 3)));
            assertThat(options.withBackground(4).background()).isEqualTo(Fill.uniform(4));
        }

        @Test
        void fillTheCornersOfAColourCaptureOneValuePerChannel() {
            Surface s = Surface.create(10, 10, Layout.RGB, 8, 100);
            Surface out = Rotation.deskew(
                            s, Angle.degrees(-20), RotateOptions.defaults().withBackground(Fill.perChannel(1, 2, 3)))
                    .surface();
            assertThat(out.getPixel(0, 0)).containsExactly(1, 2, 3);
            assertThat(Rotation.rotatedBounds(Rect.of(0, 0, 10, 4), Angle.degrees(180)))
                    .isEqualTo(Rect.of(0, 0, 10, 4));
        }
    }
}
