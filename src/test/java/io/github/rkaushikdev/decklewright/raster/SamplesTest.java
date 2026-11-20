package io.github.rkaushikdev.decklewright.raster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The sample storage: how a store truncates and wraps at each depth, which views share samples and
 * which copies own them, and how one run is copied into another.
 */
class SamplesTest {

    @Nested
    class StoringANumber {
        @ParameterizedTest
        @CsvSource({
            "300, 44",
            "255, 255",
            "256, 0",
            "-1, 255",
            "3.7, 3",
            "-3.7, 253",
            "-0.5, 0",
            "-256.5, 0",
            "NaN, 0",
            "Infinity, 0",
            "-Infinity, 0",
            "1e20, 0",
            "9007199254740994, 2"
        })
        void atEightBitsTruncatesThenWrapsModulo256(double value, int stored) {
            Uint8Samples samples = Uint8Samples.allocate(1);
            samples.set(0, value);
            assertThat(samples.get(0)).isEqualTo(stored);
            assertThat(Samples.storedValue(8, value)).isEqualTo(stored);
        }

        @ParameterizedTest
        @CsvSource({
            "70000, 4464",
            "65535.9, 65535",
            "65536, 0",
            "-1, 65535",
            "-1.5, 65535",
            "NaN, 0",
            "Infinity, 0",
            "9223372036854777856, 2048",
            "-9223372036854777856, 63488"
        })
        void atSixteenBitsTruncatesThenWrapsModulo65536(double value, int stored) {
            Uint16Samples samples = Uint16Samples.allocate(1);
            samples.set(0, value);
            assertThat(samples.get(0)).isEqualTo(stored);
            assertThat(Samples.storedValue(16, value)).isEqualTo(stored);
        }

        @Test
        void keepsTheLowBitsOfAWholeNumber() {
            Uint8Samples bytes = Uint8Samples.allocate(2);
            bytes.set(0, 300);
            bytes.set(1, -1);
            assertThat(bytes.toArray()).containsExactly(44, 255);
            Uint16Samples shorts = Uint16Samples.allocate(2);
            shorts.set(0, 70000);
            shorts.set(1, -1);
            assertThat(shorts.toArray()).containsExactly(4464, 65535);
        }

        @Test
        void fillsEverySampleTheSameWay() {
            Uint8Samples bytes = Uint8Samples.allocate(3);
            bytes.fill(258.9);
            assertThat(bytes.toArray()).containsExactly(2, 2, 2);
            Uint16Samples shorts = Uint16Samples.allocate(2);
            shorts.fill(-2);
            assertThat(shorts.toArray()).containsExactly(65534, 65534);
        }
    }

    @Nested
    class Allocating {
        @Test
        void picksTheStorageForTheDepth() {
            assertThat(Samples.allocate(8, 4)).isInstanceOf(Uint8Samples.class);
            assertThat(Samples.allocate(16, 4)).isInstanceOf(Uint16Samples.class);
            assertThat(Samples.allocate(16, 4).length()).isEqualTo(4);
            assertThat(Samples.allocate(8, 4).depth()).isEqualTo(8);
            assertThat(Samples.allocate(16, 4).depth()).isEqualTo(16);
            assertThatThrownBy(() -> Samples.allocate(12, 4))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("depth must be one of 8, 16");
        }

        @Test
        void wrapsAnArrayWithoutCopyingIt() {
            byte[] bytes = new byte[4];
            Uint8Samples whole = Uint8Samples.wrap(bytes);
            Uint8Samples part = Uint8Samples.wrap(bytes, 1, 2);
            part.set(0, 200);
            assertThat(bytes[1]).isEqualTo((byte) 200);
            assertThat(whole.get(1)).isEqualTo(200);
            assertThat(part.length()).isEqualTo(2);

            short[] shorts = new short[3];
            Uint16Samples.wrap(shorts, 2, 1).set(0, 60000);
            assertThat(Uint16Samples.wrap(shorts).get(2)).isEqualTo(60000);
            assertThatThrownBy(() -> Uint8Samples.wrap(bytes, 3, 2)).isInstanceOf(IndexOutOfBoundsException.class);
            assertThatThrownBy(() -> Uint16Samples.wrap(shorts, -1, 1)).isInstanceOf(IndexOutOfBoundsException.class);
        }
    }

    @Nested
    class ViewsAndCopies {
        @Test
        void aSubarrayIsLiveAndASliceIsNot() {
            Uint16Samples samples = Uint16Samples.allocate(5);
            Samples window = samples.subarray(1, 4);
            Samples copy = samples.subarray(1, 4).slice();
            window.set(0, 7);
            assertThat(samples.get(1)).isEqualTo(7);
            assertThat(copy.get(0)).isEqualTo(0);
            assertThat(window.subarray(1, 3).length()).isEqualTo(2);
            assertThat(Uint8Samples.allocate(3).subarray(1, 3).slice().length()).isEqualTo(2);
        }

        @Test
        void refusesToReachOutsideTheRun() {
            Samples row = Uint8Samples.allocate(8).subarray(2, 4);
            assertThatThrownBy(() -> row.get(2)).isInstanceOf(IndexOutOfBoundsException.class);
            assertThatThrownBy(() -> row.set(-1, 1)).isInstanceOf(IndexOutOfBoundsException.class);
            assertThatThrownBy(() -> row.subarray(1, 3)).isInstanceOf(IndexOutOfBoundsException.class);
            assertThatThrownBy(() -> Uint16Samples.allocate(2).subarray(2, 1))
                    .isInstanceOf(IndexOutOfBoundsException.class);
        }
    }

    @Nested
    class CopyingRuns {
        @Test
        void copiesARunOfTheSameDepth() {
            Uint8Samples target = Uint8Samples.allocate(4);
            Uint8Samples source = Uint8Samples.allocate(2);
            source.set(0, 5);
            source.set(1, 6);
            target.set(source, 1);
            assertThat(target.toArray()).containsExactly(0, 5, 6, 0);
            target.set(source);
            assertThat(target.toArray()).containsExactly(5, 6, 6, 0);
        }

        @Test
        void convertsEachValueBetweenDepths() {
            Uint16Samples wide = Uint16Samples.allocate(2);
            wide.set(0, 0x1234);
            wide.set(1, 255);
            Uint8Samples narrow = Uint8Samples.allocate(2);
            narrow.set(wide);
            assertThat(narrow.toArray()).containsExactly(0x34, 255);
            Uint16Samples back = Uint16Samples.allocate(3);
            back.set(narrow, 1);
            assertThat(back.toArray()).containsExactly(0, 0x34, 255);
        }

        @Test
        void copiesAnOverlappingRunAsIfThroughACopy() {
            Uint16Samples samples = Uint16Samples.allocate(4);
            for (int i = 0; i < 4; i += 1) {
                samples.set(i, i + 1);
            }
            samples.subarray(1, 4).set(samples.subarray(0, 3));
            assertThat(samples.toArray()).containsExactly(1, 1, 2, 3);
        }

        @Test
        void refusesARunThatDoesNotFit() {
            assertThatThrownBy(() -> Uint8Samples.allocate(2).set(Uint8Samples.allocate(2), 1))
                    .isInstanceOf(IndexOutOfBoundsException.class);
            assertThatThrownBy(() -> Uint16Samples.allocate(1).set(Uint8Samples.allocate(2)))
                    .isInstanceOf(IndexOutOfBoundsException.class);
        }
    }

    @Nested
    class Printing {
        @Test
        void namesTheStorageAndShowsTheFirstSamples() {
            Uint8Samples samples = Uint8Samples.allocate(3);
            samples.set(2, 9);
            assertThat(samples).hasToString("Uint8Samples[0, 0, 9]");
            assertThat(Uint16Samples.allocate(20)).hasToString("Uint16Samples[" + "0, ".repeat(16) + "... 4 more]");
        }
    }
}
