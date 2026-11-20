package io.github.rkaushikdev.decklewright.raster;

import static io.github.rkaushikdev.decklewright.testing.TestSurfaces.ramp;
import static io.github.rkaushikdev.decklewright.testing.TestSurfaces.rowsOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.geom.Rect;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RasterTest {

    @Nested
    class BothReadableRegionsShareOneBase {
        @Test
        void aSurfaceAndAViewAreBothRasters() {
            Surface s = ramp(4, 4);
            assertThat(s).isInstanceOf(Raster.class);
            assertThat(SurfaceView.whole(s)).isInstanceOf(Raster.class);
        }

        @Test
        void theyAgreeOnTheDerivedValues() {
            Surface s = Surface.create(4, 4, Layout.RGBA, 16);
            SurfaceView v = SurfaceView.whole(s);
            assertThat(v.layout()).isEqualTo(s.layout());
            assertThat(v.depth()).isEqualTo(s.depth());
            assertThat(v.channels()).isEqualTo(s.channels());
            assertThat(v.maxValue()).isEqualTo(s.maxValue());
            assertThat(v.rowStride()).isEqualTo(s.rowStride());
            assertThat(v.toJson()).isEqualTo(s.toJson());
        }

        @Test
        void aWindowReportsItsOwnExtentRatherThanTheParentOne() {
            Surface s = ramp(8, 8);
            SurfaceView v = new SurfaceView(s, Rect.of(2, 2, 3, 4));
            assertThat(v.size().toJson()).isEqualTo(Map.of("width", 3.0, "height", 4.0));
            assertThat(v.bounds().toJson()).isEqualTo(Map.of("x", 0.0, "y", 0.0, "width", 3.0, "height", 4.0));
            assertThat(v.rowStride()).isEqualTo(3);
        }
    }

    @Nested
    class TheSharedPixelAccessors {
        @Test
        void readAndWriteWholePixelsThroughEitherRegion() {
            Surface s = Surface.create(2, 2, Layout.RGB);
            SurfaceView v = new SurfaceView(s, Rect.of(1, 1, 1, 1));
            v.setPixel(0, 0, 7, 8, 9);
            assertThat(s.getPixel(1, 1)).containsExactly(7, 8, 9);
            assertThat(v.getPixel(0, 0)).containsExactly(7, 8, 9);
        }

        @Test
        void nameTheRegionTheyAreComplainingAbout() {
            Surface s = ramp(4, 4);
            assertThatThrownBy(() -> s.get(9, 0))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("outside the surface");
            assertThatThrownBy(() -> SurfaceView.whole(s).get(9, 0))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("outside the view");
        }

        @Test
        void rejectAChannelOutsideTheLayoutTheSameWay() {
            Surface s = ramp(4, 4);
            assertThatThrownBy(() -> s.get(0, 0, 2))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("outside the layout");
            assertThatThrownBy(() -> SurfaceView.whole(s).get(0, 0, 2))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("outside the layout");
        }

        @Test
        void rejectAPixelWithTheWrongChannelCountTheSameWay() {
            Surface s = Surface.create(1, 1, Layout.RGB);
            assertThatThrownBy(() -> s.setPixel(0, 0, 1))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("wrong number of channels");
            assertThatThrownBy(() -> SurfaceView.whole(s).setPixel(0, 0, 1))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("wrong number of channels");
        }
    }

    @Nested
    class Rows {
        @Test
        void walksEveryRowOfASurface() {
            Surface s = ramp(3, 2);
            assertThat(rowsOf(s)).containsExactly(new int[] {0, 1, 2}, new int[] {3, 4, 5});
        }

        @Test
        void walksOnlyTheRowsAViewCovers() {
            Surface s = ramp(3, 3);
            SurfaceView v = new SurfaceView(s, Rect.of(1, 1, 2, 2));
            assertThat(rowsOf(v)).containsExactly(new int[] {4, 5}, new int[] {7, 8});
        }

        @Test
        void yieldsLiveRows() {
            Surface s = ramp(2, 2);
            for (Samples row : s.rows()) {
                row.set(0, 200);
            }
            assertThat(s.get(0, 0)).isEqualTo(200);
            assertThat(s.get(0, 1)).isEqualTo(200);
        }
    }

    @Nested
    class Printing {
        @Test
        void saysTheGeometryForASurfaceAndTheWindowForAView() {
            Surface s = Surface.create(4, 3, Layout.GRAY, 16);
            assertThat(String.valueOf(s)).isEqualTo("4x3 gray16");
            assertThat(String.valueOf(new SurfaceView(s, Rect.of(1, 0, 2, 2)))).isEqualTo("view 2x2+1+0 of 4x3 gray16");
        }
    }
}
