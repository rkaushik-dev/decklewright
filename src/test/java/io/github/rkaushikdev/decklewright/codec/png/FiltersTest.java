package io.github.rkaushikdev.decklewright.codec.png;

import static io.github.rkaushikdev.decklewright.codec.png.Filters.FILTER_AVERAGE;
import static io.github.rkaushikdev.decklewright.codec.png.Filters.FILTER_NAMES;
import static io.github.rkaushikdev.decklewright.codec.png.Filters.FILTER_NONE;
import static io.github.rkaushikdev.decklewright.codec.png.Filters.FILTER_PAETH;
import static io.github.rkaushikdev.decklewright.codec.png.Filters.FILTER_SUB;
import static io.github.rkaushikdev.decklewright.codec.png.Filters.FILTER_UP;
import static io.github.rkaushikdev.decklewright.codec.png.Filters.applyFilter;
import static io.github.rkaushikdev.decklewright.codec.png.Filters.chooseFilter;
import static io.github.rkaushikdev.decklewright.codec.png.Filters.filterName;
import static io.github.rkaushikdev.decklewright.codec.png.Filters.paethPredictor;
import static io.github.rkaushikdev.decklewright.codec.png.Filters.undoFilter;
import static io.github.rkaushikdev.decklewright.testing.TestBytes.u8;
import static io.github.rkaushikdev.decklewright.testing.TestBytes.unsigned;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.CodecError;
import java.util.Arrays;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FiltersTest {

    @Nested
    class PaethPredictor {
        @Test
        void picksWhicheverOfTheThreeTheEstimateLandsNearest() {
            assertThat(paethPredictor(10, 20, 30)).isEqualTo(10);
            assertThat(paethPredictor(10, 20, 5)).isEqualTo(20);
            assertThat(paethPredictor(10, 20, 15)).isEqualTo(15);
            assertThat(paethPredictor(1, 1, 100)).isEqualTo(1);
        }

        @Test
        void breaksATieBetweenTheLeftAndTheOneAboveTowardsTheLeft() {
            assertThat(paethPredictor(10, 10, 10)).isEqualTo(10);
            assertThat(paethPredictor(10, 10, 4)).isEqualTo(10);
        }
    }

    @Nested
    class EveryFilterUndoesItself {
        private final byte[] row = u8(10, 20, 30, 200, 250, 5, 128, 128, 0);
        private final byte[] previous = u8(5, 25, 35, 190, 255, 1, 120, 130, 9);

        @ParameterizedTest(name = "filter {0} on a row with a row above")
        @ValueSource(ints = {FILTER_NONE, FILTER_SUB, FILTER_UP, FILTER_AVERAGE, FILTER_PAETH})
        void filterOnARowWithARowAbove(int filter) {
            byte[] encoded = applyFilter(filter, row, previous, 3);
            byte[] decoded = undoFilter(filter, encoded.clone(), previous, 3);
            assertThat(decoded).containsExactly(row);
        }

        @ParameterizedTest(name = "filter {0} on the first row")
        @ValueSource(ints = {FILTER_NONE, FILTER_SUB, FILTER_UP, FILTER_AVERAGE, FILTER_PAETH})
        void filterOnTheFirstRow(int filter) {
            byte[] encoded = applyFilter(filter, row, null, 3);
            byte[] decoded = undoFilter(filter, encoded.clone(), null, 3);
            assertThat(decoded).containsExactly(row);
        }

        @ParameterizedTest(name = "filter {0} on a single byte step")
        @ValueSource(ints = {FILTER_NONE, FILTER_SUB, FILTER_UP, FILTER_AVERAGE, FILTER_PAETH})
        void filterOnASingleByteStep(int filter) {
            byte[] encoded = applyFilter(filter, row, previous, 1);
            byte[] decoded = undoFilter(filter, encoded.clone(), previous, 1);
            assertThat(decoded).containsExactly(row);
        }
    }

    @Nested
    class TheIndividualFilters {
        @Test
        void nonePassesTheBytesStraightThrough() {
            byte[] row = u8(1, 2, 3);
            assertThat(unsigned(applyFilter(FILTER_NONE, row, null, 1))).containsExactly(1, 2, 3);
        }

        @Test
        void subTakesTheLeftNeighbour() {
            byte[] row = u8(10, 12, 15);
            assertThat(unsigned(applyFilter(FILTER_SUB, row, null, 1))).containsExactly(10, 2, 3);
        }

        @Test
        void upTakesTheRowAbove() {
            byte[] row = u8(10, 12, 15);
            byte[] previous = u8(10, 10, 10);
            assertThat(unsigned(applyFilter(FILTER_UP, row, previous, 1))).containsExactly(0, 2, 5);
        }

        @Test
        void averageHalvesThePair() {
            byte[] row = u8(10, 20);
            byte[] previous = u8(10, 10);
            assertThat(unsigned(applyFilter(FILTER_AVERAGE, row, previous, 1))).containsExactly(5, 10);
        }

        @Test
        void wrapsAroundTheByte() {
            byte[] row = u8(0, 0);
            byte[] previous = u8(1, 1);
            assertThat(unsigned(applyFilter(FILTER_UP, row, previous, 1))).containsExactly(255, 255);
        }

        @Test
        void writesIntoAScratchBufferWhenGivenOne() {
            byte[] scratch = new byte[3];
            byte[] out = applyFilter(FILTER_NONE, u8(1, 2, 3), null, 1, scratch);
            assertThat(out).isSameAs(scratch);
        }

        @Test
        void refusesAFilterNumberItDoesNotKnow() {
            assertThatThrownBy(() -> applyFilter(9, new byte[1], null, 1))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("unknown row filter 9");
            assertThatThrownBy(() -> undoFilter(9, new byte[1], null, 1))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("unknown row filter 9");
        }
    }

    @Nested
    class ChooseFilter {
        @Test
        void picksNoneForAFlatRowWithNothingAboveIt() {
            byte[] row = new byte[16];
            assertThat(chooseFilter(row, null, 1).filter()).isEqualTo(FILTER_NONE);
        }

        @Test
        void picksUpWhenTheRowRepeatsTheOneAbove() {
            byte[] row = filled(16, 200);
            byte[] previous = filled(16, 200);
            assertThat(chooseFilter(row, previous, 1).filter()).isEqualTo(FILTER_UP);
        }

        @Test
        void picksSubOnAFlatRunWithNoRowAbove() {
            byte[] row = filled(16, 200);
            assertThat(chooseFilter(row, null, 1).filter()).isEqualTo(FILTER_SUB);
        }

        @Test
        void returnsDataThatDecodesBackToTheRow() {
            byte[] row = u8(3, 9, 27, 81, 200, 12, 6, 3);
            byte[] previous = u8(1, 8, 30, 79, 210, 10, 6, 4);
            ChosenFilter chosen = chooseFilter(row, previous, 2);
            assertThat(undoFilter(chosen.filter(), chosen.data().clone(), previous, 2))
                    .containsExactly(row);
        }

        @Test
        void doesNotHandBackTheSharedScratchBuffer() {
            byte[] row = u8(1, 2, 3, 4);
            byte[] first = chooseFilter(row, null, 1).data();
            byte[] second = chooseFilter(u8(9, 9, 9, 9), null, 1).data();
            assertThat(first).isNotSameAs(second);
            assertThat(unsigned(first)).containsExactly(1, 1, 1, 1);
        }

        private static byte[] filled(int length, int value) {
            byte[] out = new byte[length];
            Arrays.fill(out, (byte) value);
            return out;
        }
    }

    @Nested
    class FilterName {
        @Test
        void namesEachFilter() {
            assertThat(FILTER_NAMES).hasSize(5);
            assertThat(filterName(FILTER_PAETH)).isEqualTo("paeth");
            assertThat(filterName(FILTER_NONE)).isEqualTo("none");
            assertThatThrownBy(() -> filterName(7))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("unknown row filter 7");
        }
    }
}
