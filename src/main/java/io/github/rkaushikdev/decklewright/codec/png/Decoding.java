package io.github.rkaushikdev.decklewright.codec.png;

import io.github.rkaushikdev.decklewright.CodecError;
import io.github.rkaushikdev.decklewright.UnsupportedError;
import io.github.rkaushikdev.decklewright.codec.Bytes;
import io.github.rkaushikdev.decklewright.codec.ImageShape;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Samples;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.raster.Uint8Samples;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.DataFormatException;

/**
 * Reading png.
 *
 * <p>Only the shapes the lab writes and receives: gray, gray with alpha, rgb and rgba, at eight or
 * sixteen bits, not interlaced. A palette image is refused rather than expanded, because a palette in
 * the intake folder means someone ran a capture through a screenshot tool and that is worth stopping.
 */
public final class Decoding {
    private static final int HEADER_LENGTH = 13;
    private static final int PHYSICAL_LENGTH = 9;

    private Decoding() {}

    /**
     * The header chunk's fields.
     *
     * @throws CodecError when the data is not thirteen bytes long
     */
    public static PngHeader readHeaderChunk(byte[] data) {
        if (data.length != HEADER_LENGTH) {
            throw new CodecError("the IHDR chunk is the wrong length", Map.of("length", data.length));
        }
        return new PngHeader(
                Bytes.readUint32BE(data, 0),
                Bytes.readUint32BE(data, 4),
                data[8] & 0xff,
                data[9] & 0xff,
                data[12] & 0xff);
    }

    /**
     * The whole image, with its text and its sampling frequency.
     *
     * @throws CodecError when the container is wrong, the header is malformed, there is no image data,
     *     the image data will not inflate or inflates to less than the image, or a row filter is unknown
     * @throws UnsupportedError when the image is interlaced, a palette, or neither eight nor sixteen
     *     bits
     */
    public static PngImage decodePng(byte[] bytes) {
        List<Chunk> chunks = Chunks.splitChunks(bytes);
        PngHeader header = readHeaderChunk(headerOf(chunks).data());
        Layout layout = requireSupported(header);

        List<Chunk> idat = Chunks.chunksOfType(chunks, "IDAT");
        if (idat.isEmpty()) {
            throw new CodecError("no IDAT chunk", Map.of());
        }

        int bytesPerSample = header.depth() / 8;
        int step = layout.channelCount() * bytesPerSample;
        long rowBytes = header.width() * step;
        long expected = (rowBytes + 1) * header.height();

        byte[] raw;
        try {
            raw = Zlib.inflate(Chunks.concatenated(idat), expected);
        } catch (DataFormatException cause) {
            throw new CodecError(
                    "the compressed data will not inflate",
                    Map.of("cause", Objects.toString(cause.getMessage(), "")),
                    cause);
        }
        if (raw.length < expected) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("expected", expected);
            details.put("found", raw.length);
            throw new CodecError("the inflated data is shorter than the image", details);
        }

        ImageShape shape = ImageShape.of(header.width(), header.height(), layout, header.depth());
        Surface surface = Surface.create(shape.width(), shape.height(), layout, shape.depth());
        int length = (int) rowBytes;
        int previous = -1;
        for (int y = 0; y < shape.height(); y += 1) {
            int at = y * (length + 1);
            int filter = raw[at] & 0xff;
            int row = at + 1;
            Filters.undoFilter(filter, raw, row, length, previous < 0 ? null : raw, previous, step);
            Samples target = surface.row(y);
            if (bytesPerSample == 1) {
                target.set(Uint8Samples.wrap(raw, row, length));
            } else {
                for (int i = 0; i < target.length(); i += 1) {
                    target.set(i, Bytes.readUint16BE(raw, row + i * 2));
                }
            }
            previous = row;
        }

        return new PngImage(surface, readText(chunks), readPhysical(chunks));
    }

    /**
     * Every well formed text chunk, key to value, in the order the file holds them. A chunk with no
     * separator, or an empty key, is skipped; a later chunk with the same key wins.
     */
    public static Map<String, String> readText(List<Chunk> chunks) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Chunk chunk : Chunks.chunksOfType(chunks, "tEXt")) {
            byte[] data = chunk.data();
            int split = indexOfZero(data);
            if (split <= 0) {
                continue;
            }
            String key = Bytes.readAsciiZ(data, 0, split);
            out.put(key, Bytes.readAsciiZ(data, split + 1, data.length - split - 1));
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * The {@code pHYs} chunk, which is where a capture keeps its sampling frequency; empty when there
     * is none, or it is not the nine bytes the format says.
     */
    public static Optional<Physical> readPhysical(List<Chunk> chunks) {
        return Chunks.findChunk(chunks, "pHYs")
                .filter(chunk -> chunk.length() == PHYSICAL_LENGTH)
                .map(Chunk::data)
                .map(data -> new Physical(Bytes.readUint32BE(data, 0), Bytes.readUint32BE(data, 4), data[8] & 0xff));
    }

    /**
     * The shape without the samples, which is what the intake stage wants before it decides whether
     * to load a four hundred megabyte master. The check values are not checked.
     *
     * @throws CodecError when the container or the header is wrong
     * @throws UnsupportedError when the image is interlaced, a palette, or neither eight nor sixteen
     *     bits
     */
    public static ImageShape probePng(byte[] bytes) {
        List<Chunk> chunks = Chunks.splitChunks(bytes, false);
        PngHeader header = readHeaderChunk(headerOf(chunks).data());
        Layout layout = requireSupported(header);
        return ImageShape.of(header.width(), header.height(), layout, header.depth());
    }

    private static Chunk headerOf(List<Chunk> chunks) {
        return Chunks.findChunk(chunks, "IHDR").orElseThrow(() -> new CodecError("no IHDR chunk", Map.of()));
    }

    /** The layout of a header the lab reads, after refusing every one it does not. */
    private static Layout requireSupported(PngHeader header) {
        if (header.width() == 0 || header.height() == 0) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("width", header.width());
            details.put("height", header.height());
            throw new CodecError("a png with no pixels", details);
        }
        if (header.interlace() != 0) {
            throw new UnsupportedError("interlaced png is not read here", Map.of("interlace", header.interlace()));
        }
        if (header.colourType() == 3) {
            throw new UnsupportedError("a palette png is not a capture", Map.of("colourType", 3));
        }
        Layout layout =
                switch (header.colourType()) {
                    case 0 -> Layout.GRAY;
                    case 2 -> Layout.RGB;
                    case 4 -> Layout.GRAYA;
                    case 6 -> Layout.RGBA;
                    default ->
                        throw new CodecError(
                                "unknown colour type " + header.colourType(),
                                Map.of("colourType", header.colourType()));
                };
        if (header.depth() != 8 && header.depth() != 16) {
            throw new UnsupportedError("only eight and sixteen bit png is read here", Map.of("depth", header.depth()));
        }
        return layout;
    }

    /** Where the first zero byte sits, which is where a text chunk's key ends; minus one when none does. */
    static int indexOfZero(byte[] data) {
        for (int i = 0; i < data.length; i += 1) {
            if (data[i] == 0) {
                return i;
            }
        }
        return -1;
    }
}
