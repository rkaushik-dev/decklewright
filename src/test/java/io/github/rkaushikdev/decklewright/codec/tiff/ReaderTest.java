package io.github.rkaushikdev.decklewright.codec.tiff;

import static io.github.rkaushikdev.decklewright.codec.tiff.TiffReader.probeTiff;
import static io.github.rkaushikdev.decklewright.codec.tiff.TiffReader.readTiff;
import static io.github.rkaushikdev.decklewright.codec.tiff.TiffReader.resolutionOf;
import static io.github.rkaushikdev.decklewright.testing.MakeTiff.makeTiff;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.decklewright.CodecError;
import io.github.rkaushikdev.decklewright.UnsupportedError;
import io.github.rkaushikdev.decklewright.codec.ImageShape;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.testing.MakeTiff.Options;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ReaderTest {

    private static final int[] RAMP = {0, 64, 128, 192, 255, 32, 96, 160};

    private static ByteBuffer little(byte[] tiff) {
        return ByteBuffer.wrap(tiff).order(ByteOrder.LITTLE_ENDIAN);
    }

    @Nested
    class ReadTiffOnAPlainGrayscaleFile {
        @Test
        void readsTheSamples() {
            byte[] tiff = makeTiff(Options.of(4, 2, RAMP));
            Surface surface = readTiff(tiff).surface();
            assertThat(surface.toJson()).isEqualTo(Map.of("width", 4.0, "height", 2.0, "layout", "gray", "depth", 8.0));
            assertThat(surface.data().toArray()).containsExactly(RAMP);
        }

        @Test
        void readsTheSameFileInBigEndian() {
            byte[] tiff = makeTiff(Options.of(4, 2, RAMP).withLittle(false));
            assertThat(readTiff(tiff).surface().data().toArray()).containsExactly(RAMP);
        }

        @Test
        void readsAFileSplitIntoSeveralStrips() {
            byte[] tiff = makeTiff(Options.of(4, 2, RAMP).withRowsPerStrip(1));
            assertThat(readTiff(tiff).surface().data().toArray()).containsExactly(RAMP);
        }

        @Test
        void readsAStripCountThatDoesNotDivideTheHeight() {
            int[] samples = IntStream.range(0, 20).map(i -> i * 10).toArray();
            byte[] tiff = makeTiff(Options.of(4, 5, samples).withRowsPerStrip(2));
            assertThat(readTiff(tiff).surface().data().toArray()).containsExactly(samples);
        }
    }

    @Nested
    class ReadTiffOnColourAndDepth {
        @Test
        void readsThreeChannels() {
            byte[] tiff =
                    makeTiff(Options.of(2, 1, 1, 2, 3, 4, 5, 6).withChannels(3).withPhotometric(2));
            Surface surface = readTiff(tiff).surface();
            assertThat(surface.layout()).isEqualTo(Layout.RGB);
            assertThat(surface.getPixel(1, 0)).containsExactly(4, 5, 6);
        }

        @Test
        void readsFourChannels() {
            byte[] tiff = makeTiff(Options.of(1, 1, 1, 2, 3, 4).withChannels(4).withPhotometric(2));
            assertThat(readTiff(tiff).surface().layout()).isEqualTo(Layout.RGBA);
        }

        @Test
        void readsSixteenBitSamplesInEitherByteOrder() {
            for (boolean little : new boolean[] {true, false}) {
                byte[] tiff = makeTiff(Options.of(2, 1, 1, 65535).withDepth(16).withLittle(little));
                Surface surface = readTiff(tiff).surface();
                assertThat(surface.depth()).isEqualTo(16);
                assertThat(surface.data().toArray()).containsExactly(1, 65535);
            }
        }
    }

    @Nested
    class ReadTiffAndCompression {
        @Test
        void readsPackbitsStrips() {
            int[] samples = new int[64];
            Arrays.fill(samples, 200);
            samples[10] = 5;
            byte[] tiff =
                    makeTiff(Options.of(8, 8, samples).withCompression(32773).withRowsPerStrip(4));
            assertThat(readTiff(tiff).surface().data().toArray()).containsExactly(samples);
        }

        @Test
        void refusesACompressionItDoesNotKnow() {
            byte[] tiff = makeTiff(Options.of(2, 1, 1, 2).withCompression(7));
            assertThatThrownBy(() -> readTiff(tiff))
                    .isInstanceOf(UnsupportedError.class)
                    .hasMessageFindingMatch("compression 7 is not read here");
        }
    }

    @Nested
    class PhotometricInterpretation {
        @Test
        void invertsAWhiteIsZeroCapture() {
            byte[] tiff = makeTiff(Options.of(2, 1, 0, 255).withPhotometric(0));
            TiffImage image = readTiff(tiff);
            assertThat(image.photometric()).isEqualTo(0);
            assertThat(image.surface().data().toArray()).containsExactly(255, 0);
        }

        @Test
        void invertsSixteenBitAsWell() {
            byte[] tiff = makeTiff(Options.of(2, 1, 0, 65535).withDepth(16).withPhotometric(0));
            assertThat(readTiff(tiff).surface().data().toArray()).containsExactly(65535, 0);
        }

        @Test
        void refusesAPaletteCapture() {
            byte[] tiff = makeTiff(Options.of(2, 1, 0, 1).withPhotometric(3));
            assertThatThrownBy(() -> readTiff(tiff))
                    .isInstanceOf(UnsupportedError.class)
                    .hasMessageFindingMatch("palette tiff is not a capture");
        }
    }

    @Nested
    class Resolution {
        @Test
        void readsPixelsPerInchStraightThrough() {
            byte[] tiff = makeTiff(Options.of(2, 1, 1, 2).withResolution(400));
            assertThat(readTiff(tiff).resolution()).contains(new Sampling(400, 400));
        }

        @Test
        void convertsFromCentimetres() {
            byte[] tiff = makeTiff(Options.of(2, 1, 1, 2).withResolution(100).withResolutionUnit(3));
            assertThat(readTiff(tiff).resolution().map(Sampling::x).orElseThrow())
                    .isCloseTo(254, byLessThan(0.5e-6));
        }

        @Test
        void reportsNothingWhenTheUnitSaysTheScaleIsUnknown() {
            byte[] tiff = makeTiff(Options.of(2, 1, 1, 2).withResolution(100).withResolutionUnit(1));
            assertThat(readTiff(tiff).resolution()).isEmpty();
        }

        @Test
        void reportsNothingWhenThereIsNoResolutionTag() {
            byte[] tiff = makeTiff(Options.of(2, 1, 1, 2));
            assertThat(readTiff(tiff).resolution()).isEmpty();
            assertThat(resolutionOf(List.of())).isEmpty();
        }
    }

    @Nested
    class WhatItRefuses {
        @Test
        void refusesAPlanarLayout() {
            byte[] tiff = makeTiff(Options.of(2, 1, 1, 2).withPlanar(2));
            assertThatThrownBy(() -> readTiff(tiff))
                    .isInstanceOf(UnsupportedError.class)
                    .hasMessageFindingMatch("planar tiff is not read here");
        }

        @Test
        void refusesADepthItDoesNotCarry() {
            byte[] tiff = makeTiff(Options.of(2, 1, 1, 2));
            little(tiff).putShort(10 + 2 * 12 + 8, (short) 4);
            assertThatThrownBy(() -> readTiff(tiff))
                    .isInstanceOf(UnsupportedError.class)
                    .hasMessageFindingMatch("only eight and sixteen bit");
        }

        @Test
        void refusesAStripThatRunsPastTheEndOfTheFile() {
            byte[] tiff = makeTiff(Options.of(4, 2, RAMP));
            int offsetEntry = 10 + 5 * 12;
            little(tiff).putInt(offsetEntry + 8, 9000);
            assertThatThrownBy(() -> readTiff(tiff))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("runs past the end of the file");
        }

        @Test
        void refusesAStripShorterThanTheRowsItCovers() {
            byte[] tiff = makeTiff(Options.of(4, 2, RAMP));
            int countEntry = 10 + 8 * 12;
            little(tiff).putInt(countEntry + 8, 3);
            assertThatThrownBy(() -> readTiff(tiff))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("shorter than its row count");
        }
    }

    @Nested
    class ProbeTiff {
        @Test
        void reportsTheShapeWithoutReadingStrips() {
            byte[] tiff =
                    makeTiff(Options.of(40, 30).withChannels(3).withDepth(16).withPhotometric(2));
            assertThat(probeTiff(tiff)).isEqualTo(new ImageShape(40, 30, Layout.RGB, 16));
        }
    }
}
