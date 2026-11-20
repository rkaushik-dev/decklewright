package io.github.rkaushikdev.decklewright.raster.ops;

import static io.github.rkaushikdev.decklewright.raster.ops.Crops.crop;
import static io.github.rkaushikdev.decklewright.raster.ops.Crops.cropClamped;
import static io.github.rkaushikdev.decklewright.raster.ops.Crops.cropPadded;
import static io.github.rkaushikdev.decklewright.raster.ops.Crops.cropView;
import static io.github.rkaushikdev.decklewright.testing.TestSurfaces.ramp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.geom.RoundPolicy;
import io.github.rkaushikdev.decklewright.raster.Fill;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.raster.SurfaceView;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CropTest {

    @Nested
    class Crop {
        private final Surface s = ramp(4, 4);

        @Test
        void cutsTheRectOut() {
            Surface out = crop(s, Rect.of(1, 1, 2, 2));
            assertThat(out.size().toJson()).isEqualTo(Map.of("width", 2.0, "height", 2.0));
            assertThat(out.data().toArray()).containsExactly(5, 6, 9, 10);
        }

        @Test
        void growsAFractionalRectOutwardByDefault() {
            Surface out = crop(s, Rect.of(1.2, 1.2, 1.6, 1.6));
            assertThat(out.size().toJson()).isEqualTo(Map.of("width", 2.0, "height", 2.0));
        }

        @Test
        void honoursAnotherRoundingPolicy() {
            Surface out = crop(s, Rect.of(0.4, 0.4, 3.2, 3.2), RoundPolicy.INWARD);
            assertThat(out.size().toJson()).isEqualTo(Map.of("width", 2.0, "height", 2.0));
            assertThat(out.data().toArray()).containsExactly(5, 6, 9, 10);
        }

        @Test
        void refusesACropThatLeavesTheCapture() {
            assertThatThrownBy(() -> crop(s, Rect.of(3, 3, 4, 4)))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("runs off the capture");
            assertThatThrownBy(() -> crop(s, Rect.of(-1, 0, 2, 2)))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("runs off the capture");
        }

        @Test
        void refusesAnEmptyCrop() {
            assertThatThrownBy(() -> crop(s, Rect.of(1, 1, 0, 2)))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("has no area");
        }

        @Test
        void doesNotShareSamplesWithTheCapture() {
            Surface out = crop(s, Rect.of(0, 0, 2, 2));
            out.set(0, 0, 0, 200);
            assertThat(s.get(0, 0)).isEqualTo(0);
        }
    }

    @Nested
    class CropView {
        @Test
        void givesALiveWindowRatherThanACopy() {
            Surface s = ramp(4, 4);
            SurfaceView v = cropView(s, Rect.of(1, 1, 2, 2));
            v.set(0, 0, 0, 88);
            assertThat(s.get(1, 1)).isEqualTo(88);
        }

        @Test
        void roundsTheSameWayCropDoes() {
            Surface s = ramp(4, 4);
            assertThat(cropView(s, Rect.of(1.2, 1.2, 1.6, 1.6)).width()).isEqualTo(2);
        }
    }

    @Nested
    class CropClamped {
        private final Surface s = ramp(4, 4);

        @Test
        void keepsOnlyWhatOverlaps() {
            Surface out = cropClamped(s, Rect.of(2, 2, 4, 4));
            assertThat(out.size().toJson()).isEqualTo(Map.of("width", 2.0, "height", 2.0));
            assertThat(out.data().toArray()).containsExactly(10, 11, 14, 15);
        }

        @Test
        void isTheSameAsCropWhenTheRectIsInside() {
            assertThat(cropClamped(s, Rect.of(1, 1, 2, 2)).data().toArray()).containsExactly(5, 6, 9, 10);
        }

        @Test
        void refusesARectThatMissesTheCaptureEntirely() {
            assertThatThrownBy(() -> cropClamped(s, Rect.of(10, 10, 2, 2)))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("does not touch the capture");
        }
    }

    @Nested
    class CropPadded {
        private final Surface s = ramp(4, 4);

        @Test
        void keepsThePlannedSizeAndReportsNothingWasFilled() {
            PaddedCrop out = cropPadded(s, Rect.of(1, 1, 2, 2));
            assertThat(out.surface().size().toJson()).isEqualTo(Map.of("width", 2.0, "height", 2.0));
            assertThat(out.filled()).isFalse();
            assertThat(out.covered().toJson()).isEqualTo(Map.of("x", 1.0, "y", 1.0, "width", 2.0, "height", 2.0));
        }

        @Test
        void padsThePartThatFellOffTheBed() {
            PaddedCrop out = cropPadded(s, Rect.of(3, 3, 2, 2), Fill.uniform(200));
            assertThat(out.surface().size().toJson()).isEqualTo(Map.of("width", 2.0, "height", 2.0));
            assertThat(out.filled()).isTrue();
            assertThat(out.covered().area()).isEqualTo(1);
            assertThat(out.surface().data().toArray()).containsExactly(15, 200, 200, 200);
        }

        @Test
        void padsOnTheLeadingEdgeToo() {
            PaddedCrop out = cropPadded(s, Rect.of(-1, -1, 2, 2), Fill.uniform(7));
            assertThat(out.surface().data().toArray()).containsExactly(7, 7, 7, 0);
        }

        @Test
        void fillsEverythingWhenTheRectMissesTheCapture() {
            PaddedCrop out = cropPadded(s, Rect.of(20, 20, 2, 2), Fill.uniform(3));
            assertThat(out.filled()).isTrue();
            assertThat(out.covered().isEmpty()).isTrue();
            assertThat(out.surface().data().toArray()).containsExactly(3, 3, 3, 3);
        }

        @Test
        void takesAColourFillForAColourCapture() {
            Surface colour = Surface.create(2, 2, Layout.RGB, 8, 1);
            PaddedCrop out = cropPadded(colour, Rect.of(1, 1, 2, 2), Fill.perChannel(9, 8, 7));
            assertThat(out.surface().getPixel(0, 0)).containsExactly(1, 1, 1);
            assertThat(out.surface().getPixel(1, 1)).containsExactly(9, 8, 7);
        }

        @Test
        void refusesAFillWithTheWrongChannelCount() {
            Surface colour = Surface.create(2, 2, Layout.RGB);
            assertThatThrownBy(() -> cropPadded(colour, Rect.of(0, 0, 2, 2), Fill.perChannel(1, 2)))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("wrong number of channels");
        }

        @Test
        void refusesAnEmptyCrop() {
            assertThatThrownBy(() -> cropPadded(s, Rect.of(0, 0, 0, 0)))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("has no area");
        }
    }
}
