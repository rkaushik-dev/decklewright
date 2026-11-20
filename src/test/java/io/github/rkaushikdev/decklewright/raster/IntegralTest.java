package io.github.rkaushikdev.decklewright.raster;

import static io.github.rkaushikdev.decklewright.raster.IntegralImage.integralOf;
import static io.github.rkaushikdev.decklewright.raster.Stats.channelStats;
import static io.github.rkaushikdev.decklewright.testing.TestSurfaces.gray;
import static io.github.rkaushikdev.decklewright.testing.TestSurfaces.row;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.geom.Rect;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class IntegralTest {

    private static final Surface SAMPLE = gray(row(1, 2, 3), row(4, 5, 6), row(7, 8, 9));

    @Nested
    class BuildingATable {
        @Test
        void recordsTheGeometryOfTheSource() {
            IntegralImage table = integralOf(SAMPLE);
            assertThat(table.width()).isEqualTo(3);
            assertThat(table.height()).isEqualTo(3);
            // Frozen: a final class whose fields are all final, with no method that writes them.
            assertThat(Modifier.isFinal(IntegralImage.class.getModifiers())).isTrue();
            assertThat(Arrays.stream(IntegralImage.class.getDeclaredFields()).map(Field::getModifiers))
                    .allMatch(Modifier::isFinal);
        }

        @Test
        void refusesAChannelOutsideTheLayout() {
            assertThatThrownBy(() -> integralOf(SAMPLE, 1))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("outside the layout");
        }

        @Test
        void readsTheChannelItIsToldTo() {
            Surface colour = Surface.create(1, 1, Layout.RGB);
            colour.setPixel(0, 0, 10, 20, 30);
            assertThat(integralOf(colour, 2).sum(Rect.of(0, 0, 1, 1))).isEqualTo(30);
        }
    }

    @Nested
    class Sum {
        private final IntegralImage table = integralOf(SAMPLE);

        @Test
        void addsASinglePixel() {
            assertThat(table.sum(Rect.of(1, 1, 1, 1))).isEqualTo(5);
            assertThat(table.sum(Rect.of(0, 0, 1, 1))).isEqualTo(1);
        }

        @Test
        void addsTheWholeSurface() {
            assertThat(table.sum(Rect.of(0, 0, 3, 3))).isEqualTo(45);
        }

        @Test
        void addsAnInteriorBlock() {
            assertThat(table.sum(Rect.of(1, 0, 2, 2))).isEqualTo(2 + 3 + 5 + 6);
        }

        @Test
        void addsAFullRowAndAFullColumn() {
            assertThat(table.sum(Rect.of(0, 2, 3, 1))).isEqualTo(24);
            assertThat(table.sum(Rect.of(2, 0, 1, 3))).isEqualTo(18);
        }

        @Test
        void isZeroForAnEmptyWindow() {
            assertThat(table.sum(Rect.of(1, 1, 0, 2))).isEqualTo(0);
        }

        @Test
        void refusesAWindowThatLeavesTheTable() {
            assertThatThrownBy(() -> table.sum(Rect.of(2, 2, 2, 2)))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("not a whole rect inside the table");
            assertThatThrownBy(() -> table.sum(Rect.of(-1, 0, 2, 2)))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("not a whole rect inside the table");
            assertThatThrownBy(() -> table.sum(Rect.of(0.5, 0, 2, 2)))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("not a whole rect inside the table");
        }
    }

    @Nested
    class MeanAndWindow {
        private final IntegralImage table = integralOf(SAMPLE);

        @Test
        void averagesABlock() {
            assertThat(table.mean(Rect.of(0, 0, 3, 3))).isEqualTo(5);
            assertThat(table.mean(Rect.of(0, 0, 2, 2))).isEqualTo(3);
        }

        @Test
        void answersNotANumberForAnEmptyWindow() {
            assertThat(table.mean(Rect.of(0, 0, 0, 3))).isNaN();
            IntegralImage.Window empty = table.window(Rect.of(0, 0, 0, 3));
            assertThat(empty.count()).isEqualTo(0);
            assertThat(empty.stdDev()).isNaN();
        }

        @Test
        void agreesWithADirectPassOverTheSameRegion() {
            for (Rect rect : List.of(Rect.of(0, 0, 3, 3), Rect.of(1, 1, 2, 2), Rect.of(0, 1, 3, 2))) {
                SurfaceView view = new SurfaceView(SAMPLE, rect);
                ChannelStats direct = channelStats(view, 0);
                IntegralImage.Window fast = table.window(rect);
                assertThat(fast.mean()).isCloseTo(direct.mean(), byLessThan(0.5e-9));
                assertThat(fast.stdDev()).isCloseTo(direct.stdDev(), byLessThan(0.5e-9));
                assertThat(fast.count()).isEqualTo(direct.count());
            }
        }

        @Test
        void reportsZeroDeviationOnAFlatWindowWithoutGoingNegative() {
            Surface flat = Surface.create(16, 16, Layout.GRAY, 16, 60000);
            IntegralImage.Window stats = integralOf(flat).window(Rect.of(0, 0, 16, 16));
            assertThat(stats.variance()).isEqualTo(0);
            assertThat(stats.stdDev()).isEqualTo(0);
        }

        @Test
        void sumsTheSquaresAsWellAsTheValues() {
            assertThat(table.sumOfSquares(Rect.of(0, 0, 2, 1))).isEqualTo(1 + 4);
        }
    }

    @Nested
    class OverAView {
        @Test
        void indexesInViewLocalCoordinates() {
            SurfaceView view = new SurfaceView(SAMPLE, Rect.of(1, 1, 2, 2));
            IntegralImage table = integralOf(view);
            assertThat(table.width()).isEqualTo(2);
            assertThat(table.sum(Rect.of(0, 0, 2, 2))).isEqualTo(5 + 6 + 8 + 9);
        }
    }

    @Nested
    class TheClassItself {
        @Test
        void canBeBuiltFromPreparedTables() {
            IntegralImage table = new IntegralImage(new double[4], new double[4], 1, 1);
            assertThat(table.sum(Rect.of(0, 0, 1, 1))).isEqualTo(0);
        }
    }
}
