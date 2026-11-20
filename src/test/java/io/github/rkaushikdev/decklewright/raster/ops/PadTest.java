package io.github.rkaushikdev.decklewright.raster.ops;

import static io.github.rkaushikdev.decklewright.raster.ops.Padding.pad;
import static io.github.rkaushikdev.decklewright.raster.ops.Padding.padTo;
import static io.github.rkaushikdev.decklewright.raster.ops.Padding.padUniform;
import static io.github.rkaushikdev.decklewright.testing.TestSurfaces.gray;
import static io.github.rkaushikdev.decklewright.testing.TestSurfaces.row;
import static io.github.rkaushikdev.decklewright.testing.TestSurfaces.rowsOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.geom.Insets;
import io.github.rkaushikdev.decklewright.geom.Size;
import io.github.rkaushikdev.decklewright.raster.Fill;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Surface;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PadTest {

    private static final PadOptions EDGE = PadOptions.defaults().withMode(PadMode.EDGE);
    private static final PadOptions MIRROR = PadOptions.defaults().withMode(PadMode.MIRROR);

    @Nested
    class PadWithAConstant {
        @Test
        void growsByTheInsetsAndFillsTheBorder() {
            Surface out = pad(
                    gray(row(1, 2)),
                    new Insets(1, 2, 1, 0),
                    PadOptions.defaults().withFill(9));
            assertThat(out.size().toJson()).isEqualTo(Map.of("width", 4.0, "height", 3.0));
            assertThat(rowsOf(out)).containsExactly(row(9, 9, 9, 9), row(1, 2, 9, 9), row(9, 9, 9, 9));
        }

        @Test
        void defaultsTheFillToZero() {
            Surface out = padUniform(gray(row(5)), 1);
            assertThat(rowsOf(out)).containsExactly(row(0, 0, 0), row(0, 5, 0), row(0, 0, 0));
        }

        @Test
        void takesAColourFill() {
            Surface s = Surface.create(1, 1, Layout.RGB, 8, 1);
            Surface out = padUniform(s, 1, PadOptions.defaults().withFill(Fill.perChannel(4, 5, 6)));
            assertThat(out.getPixel(0, 0)).containsExactly(4, 5, 6);
            assertThat(out.getPixel(1, 1)).containsExactly(1, 1, 1);
        }

        @Test
        void refusesAFillWithTheWrongChannelCount() {
            Surface s = Surface.create(1, 1, Layout.RGB);
            assertThatThrownBy(() -> padUniform(s, 1, PadOptions.defaults().withFill(Fill.perChannel(1, 2))))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("wrong number of channels");
        }

        @Test
        void isANoOpForZeroInsets() {
            Surface out = pad(gray(row(1, 2)), Insets.zero());
            assertThat(rowsOf(out)).containsExactly(row(1, 2));
        }
    }

    @Nested
    class PadWithEdgeRepeat {
        @Test
        void repeatsTheNearestSample() {
            Surface out = padUniform(gray(row(1, 2), row(3, 4)), 1, EDGE);
            assertThat(rowsOf(out)).containsExactly(row(1, 1, 2, 2), row(1, 1, 2, 2), row(3, 3, 4, 4), row(3, 3, 4, 4));
        }

        @Test
        void worksWhenTheCaptureIsASinglePixel() {
            assertThat(rowsOf(padUniform(gray(row(7)), 2, EDGE)))
                    .containsExactly(
                            row(7, 7, 7, 7, 7),
                            row(7, 7, 7, 7, 7),
                            row(7, 7, 7, 7, 7),
                            row(7, 7, 7, 7, 7),
                            row(7, 7, 7, 7, 7));
        }
    }

    @Nested
    class PadWithMirror {
        @Test
        void reflectsWithoutRepeatingTheEdgeSample() {
            Surface out = pad(gray(row(1, 2, 3)), new Insets(0, 2, 0, 2), MIRROR);
            assertThat(rowsOf(out)).containsExactly(row(3, 2, 1, 2, 3, 2, 1));
        }

        @Test
        void fallsBackToTheSingleSampleWhenThereIsNothingToReflect() {
            assertThat(rowsOf(pad(gray(row(4)), new Insets(0, 1, 0, 1), MIRROR)))
                    .containsExactly(row(4, 4, 4));
        }

        @Test
        void mirrorsOnBothAxesAtOnce() {
            Surface out = padUniform(gray(row(1, 2), row(3, 4)), 1, MIRROR);
            assertThat(rowsOf(out)).containsExactly(row(4, 3, 4, 3), row(2, 1, 2, 1), row(4, 3, 4, 3), row(2, 1, 2, 1));
        }
    }

    @Nested
    class PadTo {
        @Test
        void splitsTheExtraBorderAndPutsTheOddPixelOnTheTrailingEdge() {
            Surface out =
                    padTo(gray(row(1, 2)), Size.of(5, 3), PadOptions.defaults().withFill(8));
            assertThat(out.size().toJson()).isEqualTo(Map.of("width", 5.0, "height", 3.0));
            assertThat(rowsOf(out)).containsExactly(row(8, 8, 8, 8, 8), row(8, 1, 2, 8, 8), row(8, 8, 8, 8, 8));
        }

        @Test
        void isANoOpWhenTheSizesAlreadyMatch() {
            assertThat(rowsOf(padTo(gray(row(1, 2)), Size.of(2, 1)))).containsExactly(row(1, 2));
        }

        @Test
        void refusesToShrink() {
            assertThatThrownBy(() -> padTo(gray(row(1, 2, 3)), Size.of(2, 1)))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("cannot shrink a capture");
        }
    }

    @Nested
    class Validation {
        @Test
        void refusesANegativeOrFractionalInset() {
            Surface s = gray(row(1));
            assertThatThrownBy(() -> pad(s, new Insets(-1, 0, 0, 0)))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("never negative");
            assertThatThrownBy(() -> pad(s, new Insets(0.5, 0, 0, 0)))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("whole pixels");
        }

        @Test
        void refusesAModeItDoesNotKnow() {
            // A pad mode is an enum, so an unknown one can only arrive by name.
            assertThatThrownBy(() ->
                            padUniform(gray(row(1)), 1, PadOptions.defaults().withMode(PadMode.parse("wrap"))))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("mode must be one of");
        }
    }
}
