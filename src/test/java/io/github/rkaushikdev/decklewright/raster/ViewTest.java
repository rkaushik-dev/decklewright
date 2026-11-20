package io.github.rkaushikdev.decklewright.raster;

import static io.github.rkaushikdev.decklewright.testing.TestSurfaces.ramp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.geom.Rect;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ViewTest {

    @Nested
    class BuildingAView {
        @Test
        void takesTheWholeSurface() {
            Surface s = ramp(4, 3);
            SurfaceView v = SurfaceView.whole(s);
            assertThat(v.width()).isEqualTo(4);
            assertThat(v.height()).isEqualTo(3);
            assertThat(v.bounds().toJson()).isEqualTo(Map.of("x", 0.0, "y", 0.0, "width", 4.0, "height", 3.0));
            assertThat(v.layout()).isEqualTo(Layout.GRAY);
            assertThat(v.depth()).isEqualTo(8);
            assertThat(v.channels()).isEqualTo(1);
            assertThat(v.maxValue()).isEqualTo(255);
            assertThat(v.size().toJson()).isEqualTo(Map.of("width", 4.0, "height", 3.0));
        }

        @Test
        void refusesARectThatRunsOffTheSurface() {
            Surface s = ramp(4, 3);
            assertThatThrownBy(() -> new SurfaceView(s, Rect.of(2, 0, 4, 3)))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("runs off the surface");
            assertThatThrownBy(() -> new SurfaceView(s, Rect.of(-1, 0, 2, 2)))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("runs off the surface");
        }

        @Test
        void refusesAFractionalOrEmptyRect() {
            Surface s = ramp(4, 3);
            assertThatThrownBy(() -> new SurfaceView(s, Rect.of(0.5, 0, 2, 2)))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("whole pixels");
            assertThatThrownBy(() -> new SurfaceView(s, Rect.of(0, 0, 0, 2)))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("cannot be empty");
        }

        @Test
        void refusesAnythingThatIsNotASurface() {
            // Java's types only let a surface through, so the one thing that is not a surface a
            // caller can still hand over is no surface at all.
            assertThatThrownBy(() -> new SurfaceView(null, Rect.of(0, 0, 1, 1)))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("needs a surface");
        }
    }

    @Nested
    class ReadingThroughAView {
        private final Surface s = ramp(4, 3);
        private final SurfaceView v = new SurfaceView(s, Rect.of(1, 1, 2, 2));

        @Test
        void offsetsTheCoordinates() {
            assertThat(v.get(0, 0)).isEqualTo(s.get(1, 1));
            assertThat(v.get(1, 1)).isEqualTo(s.get(2, 2));
        }

        @Test
        void knowsItsOwnBounds() {
            assertThat(v.contains(1, 1)).isTrue();
            assertThat(v.contains(2, 0)).isFalse();
        }

        @Test
        void refusesAPixelOutsideTheWindowEvenWhenItIsOnTheSurface() {
            assertThatThrownBy(() -> v.get(2, 0))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("outside the view");
            assertThatThrownBy(() -> v.get(-1, 0))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("outside the view");
        }

        @Test
        void readsWholePixels() {
            Surface colour = Surface.create(2, 1, Layout.RGB);
            colour.setPixel(1, 0, 7, 8, 9);
            SurfaceView window = new SurfaceView(colour, Rect.of(1, 0, 1, 1));
            assertThat(window.getPixel(0, 0)).containsExactly(7, 8, 9);
        }
    }

    @Nested
    class WritingThroughAView {
        @Test
        void landsOnTheParentSurface() {
            Surface s = ramp(4, 3);
            SurfaceView v = new SurfaceView(s, Rect.of(1, 1, 2, 2));
            v.set(0, 0, 0, 200);
            assertThat(s.get(1, 1)).isEqualTo(200);
        }

        @Test
        void writesWholePixelsThrough() {
            Surface s = Surface.create(3, 1, Layout.RGB);
            SurfaceView v = new SurfaceView(s, Rect.of(2, 0, 1, 1));
            v.setPixel(0, 0, 1, 2, 3);
            assertThat(s.getPixel(2, 0)).containsExactly(1, 2, 3);
        }

        @Test
        void refusesAWriteOutsideTheWindow() {
            Surface s = ramp(4, 3);
            SurfaceView v = new SurfaceView(s, Rect.of(1, 1, 2, 2));
            assertThatThrownBy(() -> v.set(5, 0, 0, 1))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("outside the view");
            assertThatThrownBy(() -> v.setPixel(5, 0, 1))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("outside the view");
        }
    }

    @Nested
    class Row {
        @Test
        void coversOnlyTheColumnsTheViewHolds() {
            Surface s = ramp(4, 3);
            SurfaceView v = new SurfaceView(s, Rect.of(1, 1, 2, 2));
            assertThat(v.row(0).toArray()).containsExactly(5, 6);
        }

        @Test
        void isLive() {
            Surface s = ramp(4, 3);
            SurfaceView v = new SurfaceView(s, Rect.of(1, 1, 2, 2));
            v.row(1).set(0, 99);
            assertThat(s.get(1, 2)).isEqualTo(99);
        }

        @Test
        void accountsForTheChannelCount() {
            Surface s = Surface.create(3, 1, Layout.RGB);
            s.setPixel(1, 0, 4, 5, 6);
            SurfaceView v = new SurfaceView(s, Rect.of(1, 0, 2, 1));
            assertThat(v.row(0).toArray()).containsExactly(4, 5, 6, 0, 0, 0);
        }

        @Test
        void refusesARowOutsideTheWindow() {
            Surface s = ramp(4, 3);
            SurfaceView v = new SurfaceView(s, Rect.of(1, 1, 2, 2));
            assertThatThrownBy(() -> v.row(2))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("outside the view");
        }
    }

    @Nested
    class Subview {
        @Test
        void nestsInViewLocalCoordinates() {
            Surface s = ramp(6, 6);
            SurfaceView outer = new SurfaceView(s, Rect.of(1, 1, 4, 4));
            SurfaceView inner = outer.subview(Rect.of(1, 1, 2, 2));
            assertThat(inner.get(0, 0)).isEqualTo(s.get(2, 2));
            assertThat(inner.rect().toJson()).isEqualTo(Map.of("x", 2.0, "y", 2.0, "width", 2.0, "height", 2.0));
        }

        @Test
        void refusesASubviewThatLeavesTheParentView() {
            Surface s = ramp(6, 6);
            SurfaceView outer = new SurfaceView(s, Rect.of(1, 1, 2, 2));
            assertThatThrownBy(() -> outer.subview(Rect.of(0, 0, 4, 4)))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("runs off the parent view");
        }
    }

    @Nested
    class ToSurface {
        @Test
        void copiesTheWindowOut() {
            Surface s = ramp(4, 3);
            Surface out = new SurfaceView(s, Rect.of(1, 1, 2, 2)).toSurface();
            assertThat(out.toJson()).isEqualTo(Map.of("width", 2.0, "height", 2.0, "layout", "gray", "depth", 8.0));
            assertThat(out.data().toArray()).containsExactly(5, 6, 9, 10);
        }

        @Test
        void noLongerSharesSamplesWithTheParent() {
            Surface s = ramp(4, 3);
            Surface out = new SurfaceView(s, Rect.of(1, 1, 2, 2)).toSurface();
            out.set(0, 0, 0, 0);
            assertThat(s.get(1, 1)).isEqualTo(5);
        }

        @Test
        void carriesAColourWindowThrough() {
            Surface s = Surface.create(2, 2, Layout.RGBA, 16);
            s.setPixel(1, 1, 1000, 2000, 3000, 4000);
            Surface out = SurfaceView.whole(s).toSurface();
            assertThat(out.getPixel(1, 1)).containsExactly(1000, 2000, 3000, 4000);
            assertThat(out.depth()).isEqualTo(16);
        }
    }
}
