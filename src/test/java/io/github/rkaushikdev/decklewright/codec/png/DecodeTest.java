package io.github.rkaushikdev.decklewright.codec.png;

import static io.github.rkaushikdev.decklewright.codec.png.Chunks.assemble;
import static io.github.rkaushikdev.decklewright.codec.png.Chunks.splitChunks;
import static io.github.rkaushikdev.decklewright.codec.png.Decoding.decodePng;
import static io.github.rkaushikdev.decklewright.codec.png.Decoding.probePng;
import static io.github.rkaushikdev.decklewright.codec.png.Decoding.readHeaderChunk;
import static io.github.rkaushikdev.decklewright.codec.png.Decoding.readPhysical;
import static io.github.rkaushikdev.decklewright.codec.png.Decoding.readText;
import static io.github.rkaushikdev.decklewright.testing.TestBytes.ascii;
import static io.github.rkaushikdev.decklewright.testing.TestBytes.u8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.CodecError;
import io.github.rkaushikdev.decklewright.UnsupportedError;
import io.github.rkaushikdev.decklewright.codec.ImageShape;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Surface;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DecodeTest {

    private static byte[] makePng(int width, int height, int depth, int colourType, int... samples) {
        return makePng(width, height, depth, colourType, samples, List.of(), 0);
    }

    private static byte[] makePng(int width, int height, int depth, int colourType, int[] samples, List<Chunk> extra) {
        return makePng(width, height, depth, colourType, samples, extra, 0);
    }

    /**
     * Build a png by hand so the decoder is measured against the format rather than against our own
     * encoder. Every row goes out unfiltered, and a missing sample is written as zero.
     */
    private static byte[] makePng(
            int width, int height, int depth, int colourType, int[] samples, List<Chunk> extra, int interlace) {
        int channels =
                switch (colourType) {
                    case 2 -> 3;
                    case 4 -> 2;
                    case 6 -> 4;
                    default -> 1;
                };
        int bytesPerSample = depth / 8;
        int rowBytes = width * channels * bytesPerSample;
        byte[] raw = new byte[(rowBytes + 1) * height];
        int at = 0;
        int index = 0;
        for (int y = 0; y < height; y += 1) {
            raw[at] = 0;
            at += 1;
            for (int i = 0; i < width * channels; i += 1) {
                int value = index < samples.length ? samples[index] : 0;
                index += 1;
                if (bytesPerSample == 1) {
                    raw[at] = (byte) value;
                    at += 1;
                } else {
                    raw[at] = (byte) ((value >> 8) & 0xff);
                    raw[at + 1] = (byte) (value & 0xff);
                    at += 2;
                }
            }
        }

        byte[] ihdr = new byte[13];
        ByteBuffer view = ByteBuffer.wrap(ihdr);
        view.putInt(0, width);
        view.putInt(4, height);
        ihdr[8] = (byte) depth;
        ihdr[9] = (byte) colourType;
        ihdr[12] = (byte) interlace;

        List<Chunk> chunks = new ArrayList<>();
        chunks.add(Chunk.of("IHDR", ihdr));
        chunks.addAll(extra);
        chunks.add(Chunk.of("IDAT", deflate(raw)));
        chunks.add(Chunk.of("IEND", new byte[0]));
        return assemble(chunks);
    }

    /** The data as one zlib stream at zlib's default level. */
    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater();
        try {
            deflater.setInput(data);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            while (!deflater.finished()) {
                out.write(buffer, 0, deflater.deflate(buffer));
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private static Chunk textChunk(String key, String value) {
        return Chunk.of("tEXt", ascii(key + (char) 0 + value));
    }

    /** The chunks rebuilt from their types and data, with the header's data replaced. */
    private static byte[] withHeader(List<Chunk> chunks, byte[] header) {
        List<Chunk> rebuilt = new ArrayList<>();
        rebuilt.add(Chunk.of("IHDR", header));
        for (Chunk chunk : chunks.subList(1, chunks.size())) {
            rebuilt.add(Chunk.of(chunk.type(), chunk.data()));
        }
        return assemble(rebuilt);
    }

    /** An eight bit gray header of the extent, with nothing else set. */
    private static byte[] tinyHeader(int width, int height) {
        byte[] ihdr = new byte[13];
        ByteBuffer view = ByteBuffer.wrap(ihdr);
        view.putInt(0, width);
        view.putInt(4, height);
        ihdr[8] = 8;
        return ihdr;
    }

    @Nested
    class ReadHeaderChunk {
        @Test
        void readsTheThirteenBytes() {
            byte[] png = makePng(2, 3, 8, 0, 1, 2, 3, 4, 5, 6);
            Chunk ihdr = splitChunks(png).get(0);
            assertThat(readHeaderChunk(ihdr.data())).isEqualTo(new PngHeader(2, 3, 8, 0, 0));
        }

        @Test
        void refusesAHeaderOfTheWrongLength() {
            assertThatThrownBy(() -> readHeaderChunk(new byte[12]))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("wrong length");
        }
    }

    @Nested
    class DecodePng {
        @Test
        void readsAnEightBitGrayscaleImage() {
            byte[] png = makePng(2, 2, 8, 0, 0, 64, 128, 255);
            Surface surface = decodePng(png).surface();
            assertThat(surface.toJson()).isEqualTo(Map.of("width", 2.0, "height", 2.0, "layout", "gray", "depth", 8.0));
            assertThat(surface.data().toArray()).containsExactly(0, 64, 128, 255);
        }

        @Test
        void readsAnEightBitColourImage() {
            byte[] png = makePng(2, 1, 8, 2, 1, 2, 3, 4, 5, 6);
            Surface surface = decodePng(png).surface();
            assertThat(surface.layout()).isEqualTo(Layout.RGB);
            assertThat(surface.getPixel(1, 0)).containsExactly(4, 5, 6);
        }

        @Test
        void readsASixteenBitImage() {
            byte[] png = makePng(2, 1, 16, 0, 1, 65535);
            Surface surface = decodePng(png).surface();
            assertThat(surface.depth()).isEqualTo(16);
            assertThat(surface.data().toArray()).containsExactly(1, 65535);
        }

        @Test
        void readsTheTwoLayoutsThatCarryAlpha() {
            byte[] graya = makePng(1, 1, 8, 4, 10, 20);
            assertThat(decodePng(graya).surface().getPixel(0, 0)).containsExactly(10, 20);

            byte[] rgba = makePng(1, 1, 8, 6, 1, 2, 3, 4);
            assertThat(decodePng(rgba).surface().getPixel(0, 0)).containsExactly(1, 2, 3, 4);
        }

        @Test
        void readsTextChunksIntoABag() {
            byte[] png = makePng(
                    1,
                    1,
                    8,
                    0,
                    new int[] {9},
                    List.of(textChunk("Title", "plate 14"), textChunk("Source", "stand two")));
            assertThat(decodePng(png).text()).isEqualTo(Map.of("Title", "plate 14", "Source", "stand two"));
        }

        @Test
        void readsThePhysicalChunkWhenItIsThere() {
            byte[] phys = new byte[9];
            ByteBuffer.wrap(phys).putInt(0, 15748);
            ByteBuffer.wrap(phys).putInt(4, 15748);
            phys[8] = 1;
            byte[] png = makePng(1, 1, 8, 0, new int[] {9}, List.of(Chunk.of("pHYs", phys)));
            assertThat(decodePng(png).physical()).contains(new Physical(15748, 15748, 1));
        }

        @Test
        void reportsNoPhysicalChunkAsNull() {
            byte[] png = makePng(1, 1, 8, 0, 9);
            assertThat(decodePng(png).physical()).isEmpty();
        }
    }

    @Nested
    class WhatItRefuses {
        @Test
        void refusesAPaletteImageRatherThanExpandingIt() {
            byte[] png = makePng(1, 1, 8, 3, 0);
            assertThatThrownBy(() -> decodePng(png))
                    .isInstanceOf(UnsupportedError.class)
                    .hasMessageFindingMatch("palette png is not a capture");
        }

        @Test
        void refusesAnInterlacedImage() {
            byte[] png = makePng(1, 1, 8, 0, new int[] {1}, List.of(), 1);
            assertThatThrownBy(() -> decodePng(png))
                    .isInstanceOf(UnsupportedError.class)
                    .hasMessageFindingMatch("interlaced png is not read here");
        }

        @Test
        void refusesABitDepthBelowEight() {
            byte[] png = makePng(1, 1, 8, 0, 1);
            List<Chunk> chunks = splitChunks(png);
            byte[] header = chunks.get(0).data();
            header[8] = 4;
            byte[] rebuilt = withHeader(chunks, header);
            assertThatThrownBy(() -> decodePng(rebuilt))
                    .isInstanceOf(UnsupportedError.class)
                    .hasMessageFindingMatch("only eight and sixteen bit");
        }

        @Test
        void refusesAColourTypeItDoesNotKnow() {
            byte[] png = makePng(1, 1, 8, 0, 1);
            List<Chunk> chunks = splitChunks(png);
            byte[] header = chunks.get(0).data();
            header[9] = 7;
            byte[] rebuilt = withHeader(chunks, header);
            assertThatThrownBy(() -> decodePng(rebuilt))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("unknown colour type 7");
        }

        @Test
        void refusesAnImageWithNoPixels() {
            byte[] png = makePng(1, 1, 8, 0, 1);
            List<Chunk> chunks = splitChunks(png);
            byte[] header = chunks.get(0).data();
            ByteBuffer.wrap(header).putInt(0, 0);
            byte[] rebuilt = withHeader(chunks, header);
            assertThatThrownBy(() -> decodePng(rebuilt))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("no pixels");
        }

        @Test
        void refusesAFileWithNoImageData() {
            byte[] png = assemble(List.of(Chunk.of("IHDR", tinyHeader(1, 1)), Chunk.of("IEND", new byte[0])));
            assertThatThrownBy(() -> decodePng(png))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("no IDAT chunk");
        }

        @Test
        void refusesDataThatWillNotInflate() {
            byte[] png = assemble(List.of(
                    Chunk.of("IHDR", tinyHeader(1, 1)),
                    Chunk.of("IDAT", u8(1, 2, 3, 4)),
                    Chunk.of("IEND", new byte[0])));
            assertThatThrownBy(() -> decodePng(png))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("will not inflate");
        }

        @Test
        void refusesDataThatInflatesToLessThanTheImage() {
            byte[] png = assemble(List.of(
                    Chunk.of("IHDR", tinyHeader(8, 8)),
                    Chunk.of("IDAT", deflate(new byte[4])),
                    Chunk.of("IEND", new byte[0])));
            assertThatThrownBy(() -> decodePng(png))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("shorter than the image");
        }
    }

    @Nested
    class ProbePng {
        @Test
        void reportsTheShapeWithoutInflating() {
            byte[] png = makePng(40, 30, 16, 6);
            assertThat(probePng(png)).isEqualTo(new ImageShape(40, 30, Layout.RGBA, 16));
        }
    }

    @Nested
    class TheChunkReadersOnTheirOwn {
        @Test
        void skipATextChunkWithNoSeparator() {
            byte[] png = makePng(1, 1, 8, 0, new int[] {1}, List.of(Chunk.of("tEXt", u8(65, 66, 67))));
            assertThat(readText(splitChunks(png))).isEqualTo(Map.of());
        }

        @Test
        void skipAPhysicalChunkOfTheWrongLength() {
            byte[] png = makePng(1, 1, 8, 0, new int[] {1}, List.of(Chunk.of("pHYs", new byte[4])));
            assertThat(readPhysical(splitChunks(png))).isEmpty();
        }
    }
}
