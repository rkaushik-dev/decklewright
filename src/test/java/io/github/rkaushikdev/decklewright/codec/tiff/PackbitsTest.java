package io.github.rkaushikdev.decklewright.codec.tiff;

import static io.github.rkaushikdev.decklewright.codec.tiff.PackBits.packBits;
import static io.github.rkaushikdev.decklewright.codec.tiff.PackBits.packedLength;
import static io.github.rkaushikdev.decklewright.codec.tiff.PackBits.unpackBits;
import static io.github.rkaushikdev.decklewright.testing.TestBytes.u8;
import static io.github.rkaushikdev.decklewright.testing.TestBytes.unsigned;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.CodecError;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PackbitsTest {

    private static int[] filled(int count, int value) {
        int[] out = new int[count];
        Arrays.fill(out, value);
        return out;
    }

    @Nested
    class UnpackBits {
        @Test
        void expandsALiteralRun() {
            assertThat(unsigned(unpackBits(u8(2, 10, 20, 30)))).containsExactly(10, 20, 30);
        }

        @Test
        void expandsARepeatRun() {
            assertThat(unsigned(unpackBits(u8(254, 7)))).containsExactly(7, 7, 7);
        }

        @Test
        void handlesTheLongestRuns() {
            int[] longLiteral =
                    IntStream.concat(IntStream.of(127), IntStream.range(0, 128)).toArray();
            assertThat(unpackBits(u8(longLiteral))).hasSize(128);
            assertThat(unpackBits(u8(129, 9))).hasSize(128);
        }

        @Test
        void skipsTheNoOperationControlByte() {
            assertThat(unsigned(unpackBits(u8(128, 1, 5, 6, 128)))).containsExactly(5, 6);
        }

        @Test
        void expandsTheExampleFromTheSpecification() {
            byte[] packed =
                    u8(0xfe, 0xaa, 0x02, 0x80, 0x00, 0x2a, 0xfd, 0xaa, 0x03, 0x80, 0x00, 0x2a, 0x22, 0xf7, 0xaa);
            int[] out = unsigned(unpackBits(packed));
            assertThat(out).hasSize(24);
            assertThat(Arrays.copyOfRange(out, 0, 3)).containsExactly(0xaa, 0xaa, 0xaa);
            assertThat(Arrays.copyOfRange(out, 3, 6)).containsExactly(0x80, 0x00, 0x2a);
            assertThat(Arrays.copyOfRange(out, out.length - 3, out.length)).containsExactly(0xaa, 0xaa, 0xaa);
        }

        @Test
        void checksTheLengthWhenItIsToldWhatToExpect() {
            assertThat(unpackBits(u8(2, 1, 2, 3), 3)).hasSize(3);
            assertThatThrownBy(() -> unpackBits(u8(2, 1, 2, 3), 4))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("shorter than the strip claims");
            assertThatThrownBy(() -> unpackBits(u8(2, 1, 2, 3), 2))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("longer than the strip claims");
        }

        @Test
        void refusesALiteralRunThatRunsOffTheEnd() {
            assertThatThrownBy(() -> unpackBits(u8(5, 1, 2)))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("reaches past the end");
        }

        @Test
        void refusesARepeatRunWithNothingToRepeat() {
            assertThatThrownBy(() -> unpackBits(u8(254)))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("no byte to repeat");
        }

        @Test
        void givesNothingBackForNothing() {
            assertThat(unpackBits(new byte[0])).hasSize(0);
        }
    }

    @Nested
    class PackBitsBehaviour {
        @Test
        void turnsALongRepeatIntoARepeatRun() {
            byte[] packed = packBits(u8(filled(10, 9)));
            assertThat(unsigned(packed)).containsExactly(247, 9);
        }

        @Test
        void leavesVariedBytesAsALiteralRun() {
            assertThat(unsigned(packBits(u8(1, 2, 3)))).containsExactly(2, 1, 2, 3);
        }

        @Test
        void breaksALiteralRunWhenARepeatStarts() {
            byte[] packed = packBits(u8(1, 2, 3, 3, 3, 3));
            assertThat(unsigned(packed)).containsExactly(1, 1, 2, 253, 3);
        }

        @Test
        void doesNotUseARepeatRunForAPair() {
            assertThat(unsigned(packBits(u8(4, 4)))).containsExactly(1, 4, 4);
        }

        @Test
        void splitsARepeatLongerThanTheMaximum() {
            byte[] packed = packBits(u8(filled(200, 3)));
            assertThat(packed[0] & 0xff).isEqualTo(129);
            assertThat(packed).hasSize(4);
        }

        @Test
        void splitsALiteralRunLongerThanTheMaximum() {
            int[] values = IntStream.range(0, 300).map(i -> i % 251).toArray();
            byte[] packed = packBits(u8(values));
            assertThat(packed[0] & 0xff).isEqualTo(127);
            assertThat(unsigned(unpackBits(packed))).containsExactly(values);
        }

        @Test
        void givesNothingBackForNothing() {
            assertThat(packBits(new byte[0])).hasSize(0);
        }
    }

    @Nested
    class RoundTrips {
        static Stream<Arguments> cases() {
            List<int[]> cases = List.of(
                    new int[0],
                    new int[] {1},
                    new int[] {5, 5},
                    new int[] {5, 5, 5},
                    filled(500, 0),
                    IntStream.range(0, 400).map(i -> i % 7 == 0 ? 200 : i % 251).toArray(),
                    IntStream.range(0, 129).toArray());
            return IntStream.range(0, cases.size()).mapToObj(i -> Arguments.of(i, cases.get(i)));
        }

        @ParameterizedTest(name = "case {0} survives both ways")
        @MethodSource("cases")
        void caseSurvivesBothWays(int index, int[] values) {
            byte[] packed = packBits(u8(values));
            assertThat(unsigned(unpackBits(packed, values.length))).containsExactly(values);
        }

        @Test
        void compressesAFlatRunHard() {
            byte[] flat = u8(filled(1000, 77));
            assertThat(packBits(flat).length).isLessThan(20);
        }

        @Test
        void growsARandomRunOnlySlightly() {
            int seed = 3;
            int[] values = new int[1000];
            for (int i = 0; i < values.length; i += 1) {
                // the original multiplied in doubles, which round past two to the fifty third, before
                // masking; doing the same keeps the sequence identical
                seed = (int) (long) (seed * 1103515245.0 + 12345.0) & 0x7fffffff;
                values[i] = seed % 256;
            }
            assertThat(packBits(u8(values)).length).isLessThan(1100);
        }
    }

    @Nested
    class PackedLength {
        @Test
        void agreesWithWhatPackBitsProduced() {
            byte[] values = u8(1, 1, 1, 2, 3, 4);
            assertThat(packedLength(values)).isEqualTo(packBits(values).length);
        }
    }
}
