package io.github.rkaushikdev.decklewright.raster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SurfaceTest {

    @Nested
    class CreatingASurface {
        @Test
        void allocatesTheRightBufferForTheLayoutAndDepth() {
            Surface s = Surface.create(4, 3, Layout.RGB, 8);
            assertThat(s.data()).isInstanceOf(Uint8Samples.class);
            assertThat(s.sampleCount()).isEqualTo(36);
            assertThat(s.channels()).isEqualTo(3);
            assertThat(s.maxValue()).isEqualTo(255);
            assertThat(s.rowStride()).isEqualTo(12);
        }

        @Test
        void allocatesSixteenBitBuffersWhenAsked() {
            Surface s = Surface.create(2, 2, Layout.GRAY, 16);
            assertThat(s.data()).isInstanceOf(Uint16Samples.class);
            assertThat(s.maxValue()).isEqualTo(65535);
        }

        @Test
        void fillsOnCreation() {
            Surface s = Surface.create(2, 2, Layout.GRAY, 8, 200);
            assertThat(s.data().toArray()).containsExactly(200, 200, 200, 200);
        }

        @Test
        void reportsItsGeometry() {
            Surface s = Surface.create(4, 3, Layout.GRAY);
            assertThat(s.size().toJson()).isEqualTo(Map.of("width", 4.0, "height", 3.0));
            assertThat(s.bounds().toJson()).isEqualTo(Map.of("x", 0.0, "y", 0.0, "width", 4.0, "height", 3.0));
            assertThat(String.valueOf(s)).isEqualTo("4x3 gray8");
            assertThat(s.toJson()).isEqualTo(Map.of("width", 4.0, "height", 3.0, "layout", "gray", "depth", 8.0));
        }

        @Test
        void refusesAZeroOrFractionalExtent() {
            assertThatThrownBy(() -> Surface.create(0, 4))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("width must be a whole number");
            // The extents are ints, so a fractional one cannot be passed; a negative one is refused
            // by the same rule.
            assertThatThrownBy(() -> Surface.create(-4, 4))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("whole number");
        }
    }

    @Nested
    class WrappingAnExistingBuffer {
        @Test
        void takesABufferThatMatchesTheGeometry() {
            Uint8Samples data = Uint8Samples.allocate(12);
            Surface s = Surface.wrap(data, 2, 2, Layout.RGB, 8);
            assertThat(s.data()).isSameAs(data);
        }

        @Test
        void refusesABufferOfTheWrongLength() {
            assertThatThrownBy(() -> Surface.wrap(Uint8Samples.allocate(11), 2, 2, Layout.RGB, 8))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("does not match the stated geometry");
        }

        @Test
        void refusesABufferOfTheWrongElementType() {
            assertThatThrownBy(() -> Surface.wrap(Uint8Samples.allocate(4), 2, 2, Layout.GRAY, 16))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("16 bit surface needs Uint16Samples");
        }
    }

    @Nested
    class Addressing {
        private final Surface s = Surface.create(4, 3, Layout.RGB);

        @Test
        void walksTheInterleavedBufferRowByRow() {
            assertThat(s.indexOf(0, 0, 0)).isEqualTo(0);
            assertThat(s.indexOf(1, 0, 0)).isEqualTo(3);
            assertThat(s.indexOf(0, 1, 0)).isEqualTo(12);
            assertThat(s.indexOf(3, 2, 2)).isEqualTo(35);
        }

        @Test
        void answersWhetherAPixelIsInside() {
            assertThat(s.contains(0, 0)).isTrue();
            assertThat(s.contains(3, 2)).isTrue();
            assertThat(s.contains(4, 0)).isFalse();
            assertThat(s.contains(-1, 0)).isFalse();
        }

        @Test
        void refusesAPixelOutsideTheSurface() {
            assertThatThrownBy(() -> s.indexOf(4, 0))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("outside the surface");
            assertThatThrownBy(() -> s.indexOf(0, -1))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("outside the surface");
        }

        @Test
        void refusesAChannelOutsideTheLayout() {
            assertThatThrownBy(() -> s.indexOf(0, 0, 3))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("outside the layout");
            assertThatThrownBy(() -> s.indexOf(0, 0, -1))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("outside the layout");
        }
    }

    @Nested
    class ReadingAndWriting {
        @Test
        void roundTripsASingleSample() {
            Surface s = Surface.create(2, 2, Layout.RGB);
            s.set(1, 1, 2, 77);
            assertThat(s.get(1, 1, 2)).isEqualTo(77);
            assertThat(s.get(1, 1, 0)).isEqualTo(0);
        }

        @Test
        void roundTripsAWholePixel() {
            Surface s = Surface.create(2, 2, Layout.RGBA);
            s.setPixel(0, 1, 1, 2, 3, 4);
            assertThat(s.getPixel(0, 1)).containsExactly(1, 2, 3, 4);
        }

        @Test
        void refusesAPixelWithTheWrongChannelCount() {
            Surface s = Surface.create(2, 2, Layout.RGB);
            assertThatThrownBy(() -> s.setPixel(0, 0, 1, 2))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("wrong number of channels");
        }

        @Test
        void wrapsAValueThatDoesNotFitTheDepthModuloItsRange() {
            Surface s = Surface.create(1, 1, Layout.GRAY, 8);
            s.set(0, 0, 0, 300);
            assertThat(s.get(0, 0, 0)).isEqualTo(44);
        }
    }

    @Nested
    class Row {
        @Test
        void handsBackALiveView() {
            Surface s = Surface.create(3, 2, Layout.GRAY);
            Samples row = s.row(1);
            assertThat(row.length()).isEqualTo(3);
            row.set(0, 9);
            assertThat(s.get(0, 1)).isEqualTo(9);
        }

        @Test
        void refusesARowOutsideTheSurface() {
            Surface s = Surface.create(3, 2, Layout.GRAY);
            assertThatThrownBy(() -> s.row(2))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("outside the surface");
            // The row is an int, so a fractional one cannot be passed; a negative one is outside.
            assertThatThrownBy(() -> s.row(-1))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("outside the surface");
        }
    }

    @Nested
    class FillBehaviour {
        @Test
        void fillsEverySample() {
            Surface s = Surface.create(2, 1, Layout.RGB);
            s.fill(5);
            assertThat(s.data().toArray()).containsExactly(5, 5, 5, 5, 5, 5);
        }

        @Test
        void fillsOneChannelAndLeavesTheRest() {
            Surface s = Surface.create(2, 1, Layout.RGB);
            s.fill(9, 1);
            assertThat(s.data().toArray()).containsExactly(0, 9, 0, 0, 9, 0);
        }

        @Test
        void refusesAChannelOutsideTheLayout() {
            Surface s = Surface.create(2, 1, Layout.RGB);
            assertThatThrownBy(() -> s.fill(1, 5))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("outside the layout");
        }
    }

    @Nested
    class CopyAndLike {
        @Test
        void copiesTheSamplesIntoANewBuffer() {
            Surface s = Surface.create(2, 2, Layout.GRAY, 8, 3);
            Surface copy = s.copy();
            copy.set(0, 0, 0, 9);
            assertThat(s.get(0, 0)).isEqualTo(3);
            assertThat(copy.get(1, 1)).isEqualTo(3);
        }

        @Test
        void makesAnEmptySurfaceOfTheSameShape() {
            Surface s = Surface.create(2, 2, Layout.RGB, 16, 7);
            Surface blank = s.like();
            assertThat(blank.toJson()).isEqualTo(s.toJson());
            assertThat(blank.get(0, 0)).isEqualTo(0);
        }

        @Test
        void takesOverridesForThePartsThatChange() {
            Surface s = Surface.create(2, 2, Layout.RGB);
            Surface gray = s.like(4, s.height(), Layout.GRAY, s.depth());
            assertThat(gray.toJson()).isEqualTo(Map.of("width", 4.0, "height", 2.0, "layout", "gray", "depth", 8.0));
        }
    }
}
