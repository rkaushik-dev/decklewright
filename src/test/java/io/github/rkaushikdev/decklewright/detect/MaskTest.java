package io.github.rkaushikdev.decklewright.detect;

import static io.github.rkaushikdev.decklewright.testing.TestSurfaces.gray;
import static io.github.rkaushikdev.decklewright.testing.TestSurfaces.row;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.raster.SurfaceView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MaskTest {

    static List<int[]> rowsOf(Mask mask) {
        List<int[]> out = new ArrayList<>();
        for (int y = 0; y < mask.height(); y += 1) {
            out.add(mask.row(y).toArray());
        }
        return out;
    }

    @Nested
    class BuildingAMask {
        @Test
        void startsEmptyAtTheStatedGeometry() {
            Mask mask = new Mask(3, 2);
            assertThat(mask.width()).isEqualTo(3);
            assertThat(mask.count()).isEqualTo(0);
            assertThat(mask.coverage()).isEqualTo(0.0);
            assertThat(mask.size().toJson()).isEqualTo(Map.of("width", 3.0, "height", 2.0));
            assertThat(mask.bounds().toJson()).isEqualTo(Map.of("x", 0.0, "y", 0.0, "width", 3.0, "height", 2.0));
        }

        @Test
        void refusesABufferOfTheWrongLength() {
            assertThatThrownBy(() -> Mask.wrap(3, 2, new byte[5]))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("does not match the stated geometry");
        }

        @Test
        void refusesAnExtentThatIsNotAWholeNumberAboveZero() {
            assertThatThrownBy(() -> new Mask(0, 2))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("width must be a whole number");
        }
    }

    @Nested
    class Threshold {
        private final Surface source = gray(row(10, 200), row(30, 250));

        @Test
        void setsWhereTheSampleIsAbove() {
            assertThat(rowsOf(Mask.threshold(source, 100))).containsExactly(row(0, 1), row(0, 1));
        }

        @Test
        void setsWhereTheSampleIsBelowWhenAsked() {
            assertThat(rowsOf(Mask.threshold(source, 100, 0, false))).containsExactly(row(1, 0), row(1, 0));
        }

        @Test
        void treatsTheThresholdItselfAsNotSetEitherWay() {
            assertThat(Mask.threshold(gray(row(100)), 100).count()).isEqualTo(0);
            assertThat(Mask.threshold(gray(row(100)), 100, 0, false).count()).isEqualTo(0);
        }

        @Test
        void readsTheChannelItIsToldTo() {
            Surface colour = Surface.create(2, 1, Layout.RGB);
            colour.setPixel(0, 0, 0, 250, 0);
            colour.setPixel(1, 0, 250, 0, 0);
            assertThat(Mask.threshold(colour, 100, 1, true).row(0).toArray()).containsExactly(1, 0);
        }

        @Test
        void runsOverAViewInViewLocalCoordinates() {
            SurfaceView view = new SurfaceView(source, Rect.of(1, 0, 1, 2));
            Mask mask = Mask.threshold(view, 100);
            assertThat(mask.width()).isEqualTo(1);
            assertThat(mask.count()).isEqualTo(2);
        }
    }

    @Nested
    class ReadingAndWriting {
        @Test
        void readsZeroOutsideTheMaskRatherThanThrowing() {
            Mask mask = new Mask(2, 2);
            mask.set(0, 0, 1);
            assertThat(mask.get(0, 0)).isEqualTo(1);
            assertThat(mask.get(-1, 0)).isEqualTo(0);
            assertThat(mask.get(5, 5)).isEqualTo(0);
        }

        @Test
        void ignoresAWriteOutsideTheMask() {
            Mask mask = new Mask(2, 2);
            mask.set(9, 9, 1);
            assertThat(mask.count()).isEqualTo(0);
        }

        @Test
        void normalisesAnyNonZeroValueToOne() {
            Mask mask = new Mask(2, 2);
            mask.set(0, 0, 7);
            assertThat(mask.get(0, 0)).isEqualTo(1);
        }

        @Test
        void handsBackLiveRows() {
            Mask mask = new Mask(2, 2);
            mask.row(1).set(0, 1);
            assertThat(mask.get(0, 1)).isEqualTo(1);
        }
    }

    @Nested
    class Combining {
        private final Mask a = Mask.threshold(gray(row(10, 200, 200)), 100);
        private final Mask b = Mask.threshold(gray(row(200, 200, 10)), 100);

        @Test
        void intersectsUnionsAndSubtracts() {
            assertThat(a.and(b).row(0).toArray()).containsExactly(0, 1, 0);
            assertThat(a.or(b).row(0).toArray()).containsExactly(1, 1, 1);
            assertThat(a.without(b).row(0).toArray()).containsExactly(0, 0, 1);
        }

        @Test
        void inverts() {
            assertThat(a.inverted().row(0).toArray()).containsExactly(1, 0, 0);
        }

        @Test
        void clonesWithoutSharing() {
            Mask copy = a.copy();
            copy.set(0, 0, 1);
            assertThat(a.get(0, 0)).isEqualTo(0);
        }

        @Test
        void refusesMasksOfDifferentShapes() {
            assertThatThrownBy(() -> a.and(new Mask(1, 1)))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("not the same shape");
            assertThatThrownBy(() -> a.or(new Mask(1, 1)))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("not the same shape");
            assertThatThrownBy(() -> a.without(new Mask(1, 1)))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("not the same shape");
        }
    }

    @Nested
    class Extent {
        @Test
        void boundsTheSetPixels() {
            Mask mask = new Mask(5, 5);
            mask.set(1, 2, 1);
            mask.set(3, 4, 1);
            assertThat(mask.extent().toJson()).isEqualTo(Map.of("x", 1.0, "y", 2.0, "width", 3.0, "height", 3.0));
        }

        @Test
        void isEmptyWhenNothingIsSet() {
            assertThat(new Mask(5, 5).extent().isEmpty()).isTrue();
        }

        @Test
        void coversTheWholeMaskWhenEverythingIsSet() {
            Mask mask = new Mask(3, 2);
            mask.data().fill(1);
            assertThat(mask.extent().toJson()).isEqualTo(Map.of("x", 0.0, "y", 0.0, "width", 3.0, "height", 2.0));
            assertThat(mask.coverage()).isEqualTo(1.0);
        }
    }

    @Nested
    class Printing {
        @Test
        void saysTheShapeAndHowMuchIsSet() {
            Mask mask = new Mask(4, 2);
            mask.set(0, 0, 1);
            assertThat(String.valueOf(mask)).isEqualTo("mask 4x2, 1 set");
        }
    }
}
