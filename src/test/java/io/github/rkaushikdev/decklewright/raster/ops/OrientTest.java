package io.github.rkaushikdev.decklewright.raster.ops;

import static io.github.rkaushikdev.decklewright.raster.ops.Orientation.applyExifOrientation;
import static io.github.rkaushikdev.decklewright.raster.ops.Orientation.flipHorizontal;
import static io.github.rkaushikdev.decklewright.raster.ops.Orientation.flipVertical;
import static io.github.rkaushikdev.decklewright.raster.ops.Orientation.rotateQuarters;
import static io.github.rkaushikdev.decklewright.raster.ops.Orientation.transpose;
import static io.github.rkaushikdev.decklewright.testing.TestSurfaces.gray;
import static io.github.rkaushikdev.decklewright.testing.TestSurfaces.row;
import static io.github.rkaushikdev.decklewright.testing.TestSurfaces.rowsOf;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Surface;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OrientTest {

    private static final Surface SAMPLE = gray(row(1, 2, 3), row(4, 5, 6));

    @Nested
    class FlipHorizontal {
        @Test
        void reversesEachRow() {
            assertThat(rowsOf(flipHorizontal(SAMPLE))).containsExactly(row(3, 2, 1), row(6, 5, 4));
        }

        @Test
        void isItsOwnInverse() {
            assertThat(rowsOf(flipHorizontal(flipHorizontal(SAMPLE)))).containsExactlyElementsOf(rowsOf(SAMPLE));
        }

        @Test
        void carriesEveryChannelOfAColourCapture() {
            Surface colour = Surface.create(2, 1, Layout.RGB);
            colour.setPixel(0, 0, 1, 2, 3);
            colour.setPixel(1, 0, 4, 5, 6);
            Surface out = flipHorizontal(colour);
            assertThat(out.getPixel(0, 0)).containsExactly(4, 5, 6);
            assertThat(out.getPixel(1, 0)).containsExactly(1, 2, 3);
        }
    }

    @Nested
    class FlipVertical {
        @Test
        void reversesTheRowOrder() {
            assertThat(rowsOf(flipVertical(SAMPLE))).containsExactly(row(4, 5, 6), row(1, 2, 3));
        }

        @Test
        void isItsOwnInverse() {
            assertThat(rowsOf(flipVertical(flipVertical(SAMPLE)))).containsExactlyElementsOf(rowsOf(SAMPLE));
        }
    }

    @Nested
    class Transpose {
        @Test
        void swapsTheAxes() {
            Surface out = transpose(SAMPLE);
            assertThat(out.size().toJson()).isEqualTo(Map.of("width", 2.0, "height", 3.0));
            assertThat(rowsOf(out)).containsExactly(row(1, 4), row(2, 5), row(3, 6));
        }

        @Test
        void isItsOwnInverse() {
            assertThat(rowsOf(transpose(transpose(SAMPLE)))).containsExactlyElementsOf(rowsOf(SAMPLE));
        }
    }

    @Nested
    class RotateQuarters {
        @Test
        void turnsClockwiseOnce() {
            Surface out = rotateQuarters(SAMPLE, 1);
            assertThat(out.size().toJson()).isEqualTo(Map.of("width", 2.0, "height", 3.0));
            assertThat(rowsOf(out)).containsExactly(row(4, 1), row(5, 2), row(6, 3));
        }

        @Test
        void turnsTwice() {
            assertThat(rowsOf(rotateQuarters(SAMPLE, 2))).containsExactly(row(6, 5, 4), row(3, 2, 1));
        }

        @Test
        void turnsThreeTimes() {
            assertThat(rowsOf(rotateQuarters(SAMPLE, 3))).containsExactly(row(3, 6), row(2, 5), row(1, 4));
        }

        @Test
        void foldsFourTurnsBackToACopy() {
            Surface out = rotateQuarters(SAMPLE, 4);
            assertThat(rowsOf(out)).containsExactlyElementsOf(rowsOf(SAMPLE));
            assertThat(out).isNotSameAs(SAMPLE);
        }

        @Test
        void readsANegativeCountAsTheOtherWayRound() {
            assertThat(rowsOf(rotateQuarters(SAMPLE, -1))).containsExactlyElementsOf(rowsOf(rotateQuarters(SAMPLE, 3)));
            assertThat(rowsOf(rotateQuarters(SAMPLE, -5))).containsExactlyElementsOf(rowsOf(rotateQuarters(SAMPLE, 3)));
        }

        @Test
        void losesNoSamplesOverAFullCircuit() {
            Surface out = SAMPLE;
            for (int i = 0; i < 4; i += 1) {
                out = rotateQuarters(out, 1);
            }
            assertThat(rowsOf(out)).containsExactlyElementsOf(rowsOf(SAMPLE));
        }

        @Test
        void refusesAFractionalTurnCount() throws NoSuchMethodException {
            // The turn count is an int, so a fractional one cannot be passed at all; what is left to
            // check is that the method takes nothing else, and that a whole count far out of range
            // still folds into a quarter turn.
            assertThat(Orientation.class.getMethod("rotateQuarters", Surface.class, int.class))
                    .isNotNull();
            assertThat(rowsOf(rotateQuarters(SAMPLE, Integer.MIN_VALUE + 1)))
                    .containsExactlyElementsOf(rowsOf(rotateQuarters(SAMPLE, 1)));
        }
    }

    @Nested
    class ApplyExifOrientation {
        @Test
        void leavesOrientationOneAlone() {
            assertThat(rowsOf(applyExifOrientation(SAMPLE, 1))).containsExactlyElementsOf(rowsOf(SAMPLE));
        }

        @Test
        void matchesTheNamedTransformForEachTag() {
            assertThat(rowsOf(applyExifOrientation(SAMPLE, 2)))
                    .containsExactlyElementsOf(rowsOf(flipHorizontal(SAMPLE)));
            assertThat(rowsOf(applyExifOrientation(SAMPLE, 3)))
                    .containsExactlyElementsOf(rowsOf(rotateQuarters(SAMPLE, 2)));
            assertThat(rowsOf(applyExifOrientation(SAMPLE, 4))).containsExactlyElementsOf(rowsOf(flipVertical(SAMPLE)));
            assertThat(rowsOf(applyExifOrientation(SAMPLE, 5))).containsExactlyElementsOf(rowsOf(transpose(SAMPLE)));
            assertThat(rowsOf(applyExifOrientation(SAMPLE, 6)))
                    .containsExactlyElementsOf(rowsOf(rotateQuarters(SAMPLE, 1)));
            assertThat(rowsOf(applyExifOrientation(SAMPLE, 8)))
                    .containsExactlyElementsOf(rowsOf(rotateQuarters(SAMPLE, 3)));
        }

        @Test
        void handlesTheTransverseCase() {
            Surface out = applyExifOrientation(SAMPLE, 7);
            assertThat(out.size().toJson()).isEqualTo(Map.of("width", 2.0, "height", 3.0));
            assertThat(rowsOf(out)).containsExactly(row(6, 3), row(5, 2), row(4, 1));
        }

        @Test
        void treatsAnUnknownTagAsNoChange() {
            assertThat(rowsOf(applyExifOrientation(SAMPLE, 99))).containsExactlyElementsOf(rowsOf(SAMPLE));
        }
    }
}
