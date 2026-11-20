package io.github.rkaushikdev.decklewright.codec.tiff;

import static io.github.rkaushikdev.decklewright.codec.tiff.Lzw.lzwDecode;
import static io.github.rkaushikdev.decklewright.codec.tiff.Lzw.lzwEncode;
import static io.github.rkaushikdev.decklewright.codec.tiff.TiffReader.readTiff;
import static io.github.rkaushikdev.decklewright.testing.MakeTiff.makeTiff;
import static io.github.rkaushikdev.decklewright.testing.TestBytes.u8;
import static io.github.rkaushikdev.decklewright.testing.TestBytes.unsigned;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.CodecError;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.testing.MakeTiff.Options;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LzwTest {

    /**
     * Seeded noise in the byte range. The product is taken in doubles, which round once it passes
     * two to the fifty third, and then masked to thirty one bits; the sequence is fixed by that
     * rounding.
     */
    private static int[] noise(int count, int seed) {
        int[] out = new int[count];
        int state = seed;
        for (int i = 0; i < count; i += 1) {
            state = (int) (long) (state * 1103515245.0 + 12345.0) & 0x7fffffff;
            out[i] = state % 256;
        }
        return out;
    }

    private static int[] filled(int count, int value) {
        int[] out = new int[count];
        Arrays.fill(out, value);
        return out;
    }

    @Nested
    class RoundTrips {
        static Stream<Arguments> cases() {
            List<int[]> cases = List.of(
                    new int[0],
                    new int[] {0},
                    new int[] {1, 2, 3},
                    filled(4, 7),
                    filled(1000, 0),
                    IntStream.range(0, 512).map(i -> i % 256).toArray(),
                    IntStream.range(0, 5000)
                            .map(i -> i % 3 == 0 ? 200 : i % 256)
                            .toArray(),
                    noise(3000, 11),
                    noise(20000, 5));
            return IntStream.range(0, cases.size()).mapToObj(i -> Arguments.of(i, cases.get(i)));
        }

        @ParameterizedTest(name = "case {0} survives both ways")
        @MethodSource("cases")
        void caseSurvivesBothWays(int index, int[] values) {
            byte[] encoded = lzwEncode(u8(values));
            assertThat(unsigned(lzwDecode(encoded, values.length))).containsExactly(values);
        }

        @Test
        void crossesTheNineToTenBitBoundaryCorrectly() {
            int[] values = IntStream.range(0, 600).map(i -> i % 251).toArray();
            assertThat(unsigned(lzwDecode(lzwEncode(u8(values))))).containsExactly(values);
        }

        @Test
        void crossesEveryWidthBoundaryOnALongRun() {
            int[] values = IntStream.range(0, 60000).map(i -> (i * 7) % 256).toArray();
            assertThat(unsigned(lzwDecode(lzwEncode(u8(values))))).containsExactly(values);
        }

        @Test
        void handlesTheRepeatedPatternThatNeedsTheNotYetDefinedCode() {
            int[] values = {1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2};
            assertThat(unsigned(lzwDecode(lzwEncode(u8(values))))).containsExactly(values);
        }
    }

    @Nested
    class LzwEncode {
        @Test
        void compressesAFlatRunHard() {
            assertThat(lzwEncode(u8(filled(10000, 3))).length).isLessThan(500);
        }

        @Test
        void opensWithAClearCode() {
            byte[] encoded = lzwEncode(u8(1, 2, 3));
            int first = ((encoded[0] & 0xff) << 1) | ((encoded[1] & 0xff) >> 7);
            assertThat(first).isEqualTo(256);
        }

        @Test
        void givesBackOnlyTheClearAndEndCodesForNothing() {
            assertThat(lzwDecode(lzwEncode(new byte[0]), 0)).hasSize(0);
        }
    }

    @Nested
    class LzwDecode {
        @Test
        void checksTheLengthWhenItIsToldWhatToExpect() {
            byte[] encoded = lzwEncode(u8(1, 2, 3));
            assertThatThrownBy(() -> lzwDecode(encoded, 4))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("shorter than the strip claims");
            assertThatThrownBy(() -> lzwDecode(encoded, 2))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("longer than the strip claims");
        }

        @Test
        void stopsAtTheEndCodeAndIgnoresWhatFollows() {
            byte[] encoded = lzwEncode(u8(1, 2, 3));
            byte[] padded = Arrays.copyOf(encoded, encoded.length + 4);
            Arrays.fill(padded, encoded.length, padded.length, (byte) 0xff);
            assertThat(unsigned(lzwDecode(padded, 3))).containsExactly(1, 2, 3);
        }

        @Test
        void refusesACodeTheTableCannotHold() {
            // a clear code followed by code three hundred, which nothing has defined
            assertThatThrownBy(() -> lzwDecode(u8(0x80, 0x4b, 0x00)))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("the table does not have");
        }

        @Test
        void stopsCleanlyWhenTheDataRunsOutMidCode() {
            byte[] encoded = lzwEncode(u8(1, 2, 3, 4, 5));
            byte[] cut = Arrays.copyOf(encoded, 2);
            assertThat(lzwDecode(cut).length).isLessThanOrEqualTo(5);
        }
    }

    @Nested
    class ThroughTheTiffReader {
        @Test
        void readsAnLzwStrip() {
            int[] samples = IntStream.range(0, 64).map(i -> (i * 4) % 256).toArray();
            byte[] tiff = makeTiff(Options.of(8, 8, samples).withCompression(5));
            assertThat(readTiff(tiff).surface().data().toArray()).containsExactly(samples);
        }

        @Test
        void readsLzwSplitAcrossStrips() {
            int[] samples = IntStream.range(0, 64).map(i -> (i * 4) % 256).toArray();
            byte[] tiff = makeTiff(Options.of(8, 8, samples).withCompression(5).withRowsPerStrip(3));
            assertThat(readTiff(tiff).surface().data().toArray()).containsExactly(samples);
        }

        @Test
        void readsAnLzwColourStrip() {
            int[] samples = IntStream.range(0, 48).map(i -> (i * 5) % 256).toArray();
            byte[] tiff = makeTiff(
                    Options.of(4, 4, samples).withChannels(3).withPhotometric(2).withCompression(5));
            Surface surface = readTiff(tiff).surface();
            assertThat(surface.layout()).isEqualTo(Layout.RGB);
            assertThat(surface.data().toArray()).containsExactly(samples);
        }

        @Test
        void readsASixteenBitLzwStrip() {
            int[] samples = IntStream.range(0, 16).map(i -> i * 4000).toArray();
            byte[] tiff = makeTiff(Options.of(4, 4, samples).withDepth(16).withCompression(5));
            assertThat(readTiff(tiff).surface().data().toArray()).containsExactly(samples);
        }
    }
}
