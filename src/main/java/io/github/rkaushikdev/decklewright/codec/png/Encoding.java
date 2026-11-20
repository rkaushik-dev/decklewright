package io.github.rkaushikdev.decklewright.codec.png;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.CodecError;
import io.github.rkaushikdev.decklewright.codec.Bytes;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Raster;
import io.github.rkaushikdev.decklewright.raster.Samples;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.Num;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Writing png.
 *
 * <p>Masters go out at sixteen bits with the metre based physical chunk filled in, because the
 * archive reads sampling frequency out of the file rather than out of a sidecar that can get
 * separated from it. Derivatives go out at eight with nothing but the pixels.
 *
 * <p>Every layout a raster can have is one png carries, so there is no layout to refuse.
 */
public final class Encoding {
    private static final double METRES_PER_INCH = 0.0254;

    /** The longest text key the format allows. */
    private static final int MAX_KEY = 79;

    private Encoding() {}

    /**
     * The raster as a png with no metadata, the filter heuristic on, at level six.
     *
     * @see #encodePng(Raster, PngOptions)
     */
    public static byte[] encodePng(Raster raster) {
        return encodePng(raster, PngOptions.defaults());
    }

    /**
     * The raster as a png: the header, then the sampling frequency and the text when the options
     * have them, then the image data.
     *
     * @throws BadArgumentError when the level is not zero through nine
     * @throws CodecError when a text key or value will not go into a text chunk
     */
    public static byte[] encodePng(Raster raster, PngOptions options) {
        Objects.requireNonNull(options, "options");
        Guards.requireInRange(options.level(), 0, 9, "level");

        int bytesPerSample = raster.depth() / 8;
        int step = raster.channels() * bytesPerSample;
        long stride = (long) raster.width() * step;
        byte[] raw = Bytes.allocate((stride + 1) * raster.height());
        int rowBytes = (int) stride;

        byte[] previousRaw = null;
        byte[] current = new byte[rowBytes];
        for (int y = 0; y < raster.height(); y += 1) {
            Samples samples = raster.row(y);
            for (int i = 0; i < samples.length(); i += 1) {
                int value = samples.get(i);
                if (bytesPerSample == 1) {
                    current[i] = (byte) value;
                } else {
                    current[i * 2] = (byte) (value >> 8);
                    current[i * 2 + 1] = (byte) value;
                }
            }
            int at = y * (rowBytes + 1);
            if (options.filter() == FilterMode.NONE) {
                raw[at] = Filters.FILTER_NONE;
                System.arraycopy(current, 0, raw, at + 1, rowBytes);
            } else {
                ChosenFilter chosen = Filters.chooseFilter(current, previousRaw, step);
                raw[at] = (byte) chosen.filter();
                System.arraycopy(chosen.data(), 0, raw, at + 1, rowBytes);
            }
            previousRaw = current.clone();
        }

        List<Chunk> chunks = new ArrayList<>();
        chunks.add(Chunk.adopt("IHDR", headerChunk(raster)));
        options.ppi().ifPresent(ppi -> chunks.add(physicalChunk(ppi.x(), ppi.y())));
        for (Map.Entry<String, String> entry : options.text().entrySet()) {
            chunks.add(textChunk(entry.getKey(), entry.getValue()));
        }
        chunks.add(Chunk.adopt("IDAT", Zlib.deflate(raw, options.level())));
        chunks.add(Chunk.adopt("IEND", new byte[0]));
        return Chunks.assemble(chunks);
    }

    /**
     * A {@code tEXt} chunk: the key, a zero, then the value.
     *
     * @throws CodecError when the key is empty or longer than seventy nine characters, or either
     *     holds anything outside latin one
     */
    public static Chunk textChunk(String key, String value) {
        if (key.isEmpty() || key.length() > MAX_KEY) {
            throw new CodecError("a png text key is one to seventy nine characters", Map.of("key", key));
        }
        byte[] keyBytes = Bytes.latin1Bytes(key, "a png text key");
        byte[] valueBytes = Bytes.latin1Bytes(value, "png text");
        byte[] data = new byte[keyBytes.length + 1 + valueBytes.length];
        System.arraycopy(keyBytes, 0, data, 0, keyBytes.length);
        System.arraycopy(valueBytes, 0, data, keyBytes.length + 1, valueBytes.length);
        return Chunk.adopt("tEXt", data);
    }

    /**
     * A {@code pHYs} chunk holding the frequency in pixels per metre, the unit the format has, each
     * axis rounded to the nearest whole number.
     */
    public static Chunk physicalChunk(double ppiX, double ppiY) {
        byte[] data = new byte[9];
        Bytes.writeUint32BE(data, 0, toUint32(Num.roundHalfUp(ppiX / METRES_PER_INCH)));
        Bytes.writeUint32BE(data, 4, toUint32(Num.roundHalfUp(ppiY / METRES_PER_INCH)));
        data[8] = 1;
        return Chunk.adopt("pHYs", data);
    }

    private static byte[] headerChunk(Raster raster) {
        byte[] out = new byte[13];
        Bytes.writeUint32BE(out, 0, raster.width());
        Bytes.writeUint32BE(out, 4, raster.height());
        out[8] = (byte) raster.depth();
        out[9] = (byte) colourTypeOf(raster.layout());
        return out;
    }

    private static int colourTypeOf(Layout layout) {
        return switch (layout) {
            case GRAY -> 0;
            case RGB -> 2;
            case GRAYA -> 4;
            case RGBA -> 6;
        };
    }

    /**
     * The number as an unsigned 32 bit word: truncated toward zero, then wrapped modulo two to the
     * thirty two, so an out of range frequency wraps rather than saturating; not a number and the
     * infinities give zero.
     */
    private static long toUint32(double value) {
        return (long) (value % 4294967296.0) & 0xffffffffL;
    }
}
