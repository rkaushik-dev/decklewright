package io.github.rkaushikdev.decklewright.codec.tiff;

import io.github.rkaushikdev.decklewright.CodecError;
import io.github.rkaushikdev.decklewright.UnsupportedError;
import io.github.rkaushikdev.decklewright.codec.Bytes;
import io.github.rkaushikdev.decklewright.codec.ImageShape;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Samples;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Baseline tiff, strips only.
 *
 * <p>The flatbed writes one strip per few rows, uncompressed or packbits, one plane, black is zero.
 * That is what this reads. Tiled tiff, planar layouts and floating point samples are all real parts
 * of the format and none of them has ever come out of this lab, so they are refused by name rather
 * than half supported.
 *
 * <p>Only the first directory is read.
 */
public final class TiffReader {
    /** What a file with no bits per sample tag is read as. */
    private static final List<Double> DEFAULT_BITS = List.of(8.0);

    private static final double CENTIMETRES_PER_INCH = 2.54;

    /** The layout of each number of samples per pixel the reader takes. */
    private static final List<Layout> BY_SAMPLES = List.of(Layout.GRAY, Layout.GRAYA, Layout.RGB, Layout.RGBA);

    private TiffReader() {}

    /**
     * The sampling frequency the resolution tags state, in pixels per inch.
     *
     * <p>Empty when either axis is missing or not above zero, when the unit says the scale is
     * unknown, or when the figure is not a finite number, which no frequency can be.
     */
    public static Optional<Sampling> resolutionOf(List<Entry> entries) {
        double x = Tags.scalar(entries, "xResolution", 0);
        double y = Tags.scalar(entries, "yResolution", 0);
        if (x <= 0 || y <= 0) {
            return Optional.empty();
        }
        double unit = Tags.scalar(entries, "resolutionUnit", ResolutionUnit.INCH.code());
        if (unit == ResolutionUnit.NONE.code()) {
            return Optional.empty();
        }
        double factor = unit == ResolutionUnit.CENTIMETRE.code() ? CENTIMETRES_PER_INCH : 1;
        double ppiX = x * factor;
        double ppiY = y * factor;
        if (!Double.isFinite(ppiX) || !Double.isFinite(ppiY)) {
            return Optional.empty();
        }
        return Optional.of(new Sampling(ppiX, ppiY));
    }

    /**
     * The whole image from the first directory, with its tags and its sampling frequency.
     *
     * @throws CodecError when the header or the directory is wrong, a required tag is missing, the
     *     strip tags disagree, a strip runs past the end of the file or decodes to the wrong length,
     *     or the strips do not cover the image
     * @throws UnsupportedError when the file is planar, a palette, of mixed or unusual bit widths, of
     *     an unusual number of samples per pixel, or compressed some way the reader does not know
     */
    public static TiffImage readTiff(byte[] bytes) {
        TiffHeader header = Tags.readTiffHeader(bytes);
        boolean little = header.littleEndian();
        List<Entry> entries = Tags.readIfd(bytes, header.firstIfd(), little).entries();

        double width = Tags.scalar(entries, "imageWidth");
        double height = Tags.scalar(entries, "imageLength");
        double samples = Tags.scalar(entries, "samplesPerPixel", 1);
        List<Double> bits = Tags.list(entries, "bitsPerSample");
        double compression = Tags.scalar(entries, "compression", Compression.NONE.code());
        double photometric = Tags.scalar(entries, "photometricInterpretation", Photometric.BLACK_IS_ZERO.code());
        double planar = Tags.scalar(entries, "planarConfiguration", 1);

        if (planar != 1) {
            throw new UnsupportedError("a planar tiff is not read here", Map.of("planar", planar));
        }
        if (photometric == Photometric.PALETTE.code()) {
            throw new UnsupportedError("a palette tiff is not a capture", Map.of("photometric", photometric));
        }

        Format format = formatFor(bits.isEmpty() ? DEFAULT_BITS : bits, samples);
        double rowsPerStrip = Tags.scalar(entries, "rowsPerStrip", height);
        List<Double> offsets = Tags.list(entries, "stripOffsets");
        List<Double> counts = Tags.list(entries, "stripByteCounts");
        if (offsets.isEmpty()) {
            throw new CodecError("the strip offsets tag is missing", Map.of());
        }
        if (offsets.size() != counts.size()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("offsets", offsets.size());
            details.put("counts", counts.size());
            throw new CodecError("the strip offsets and byte counts disagree", details);
        }

        ImageShape shape = ImageShape.of(width, height, format.layout(), format.depth());
        Surface surface = Surface.create(shape.width(), shape.height(), shape.layout(), shape.depth());
        long bytesPerRow = (long) shape.width() * surface.channels() * (format.depth() / 8);
        boolean white = photometric == Photometric.WHITE_IS_ZERO.code();
        int top = surface.maxValue();

        int row = 0;
        for (int strip = 0; strip < offsets.size(); strip += 1) {
            double start = offsets.get(strip);
            double length = counts.get(strip);
            if (start + length > bytes.length) {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("strip", strip);
                details.put("start", start);
                details.put("length", length);
                throw new CodecError("a strip runs past the end of the file", details);
            }
            double remaining = Math.min(rowsPerStrip, shape.height() - row);
            if (remaining <= 0) {
                break;
            }
            int rows = (int) requireWhole(remaining, "rowsPerStrip", "rows");
            int offset = (int) requireWhole(start, "stripOffsets", "bytes");
            int count = (int) requireWhole(length, "stripByteCounts", "bytes");
            long expected = bytesPerRow * rows;

            byte[] raw;
            int base;
            if (compression == Compression.NONE.code()) {
                if (count < expected) {
                    Map<String, Object> details = new LinkedHashMap<>();
                    details.put("expected", expected);
                    details.put("found", count);
                    throw new CodecError("a strip is shorter than its row count needs", details);
                }
                raw = bytes;
                base = offset;
            } else {
                raw = decompress(bytes, offset, count, compression, expected);
                base = 0;
            }

            for (int r = 0; r < rows; r += 1) {
                Samples target = surface.row(row + r);
                int rowStart = base + (int) (r * bytesPerRow);
                for (int i = 0; i < target.length(); i += 1) {
                    int value = format.depth() == 8 ? raw[rowStart + i] & 0xff : wide(raw, rowStart + i * 2, little);
                    target.set(i, white ? top - value : value);
                }
            }
            row += rows;
        }

        if (row < shape.height()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("covered", row);
            details.put("height", shape.height());
            throw new CodecError("the strips do not cover the whole image", details);
        }

        return new TiffImage(surface, Tags.asBag(entries), resolutionOf(entries), photometric);
    }

    /**
     * The shape from the first directory without reading a strip.
     *
     * @throws CodecError when the header or the directory is wrong, or the width or length tag is
     *     missing
     * @throws UnsupportedError when the bit widths or the samples per pixel are ones the reader does
     *     not take
     */
    public static ImageShape probeTiff(byte[] bytes) {
        TiffHeader header = Tags.readTiffHeader(bytes);
        List<Entry> entries =
                Tags.readIfd(bytes, header.firstIfd(), header.littleEndian()).entries();
        List<Double> bits = Tags.list(entries, "bitsPerSample");
        double samples = Tags.scalar(entries, "samplesPerPixel", 1);
        Format format = formatFor(bits.isEmpty() ? DEFAULT_BITS : bits, samples);
        return ImageShape.of(
                Tags.scalar(entries, "imageWidth"),
                Tags.scalar(entries, "imageLength"),
                format.layout(),
                format.depth());
    }

    private static Format formatFor(List<Double> bits, double samples) {
        double depth = bits.get(0);
        if (!bits.stream().allMatch(value -> value == depth)) {
            throw new UnsupportedError(
                    "channels of different widths are not read here", Map.of("bits", List.copyOf(bits)));
        }
        if (depth != 8 && depth != 16) {
            throw new UnsupportedError("only eight and sixteen bit tiff is read here", Map.of("depth", depth));
        }
        Layout layout = BY_SAMPLES.stream()
                .filter(candidate -> candidate.channelCount() == samples)
                .findFirst()
                .orElseThrow(() -> new UnsupportedError(
                        "a tiff with " + NumberText.of(samples) + " samples per pixel is not read here",
                        Map.of("samples", samples)));
        return new Format(layout, (int) depth);
    }

    private static byte[] decompress(byte[] bytes, int start, int length, double compression, long expected) {
        if (compression == Compression.PACKBITS.code()) {
            return PackBits.unpackBits(bytes, start, start + length, Bytes.allocate(expected));
        }
        if (compression == Compression.LZW.code()) {
            return Lzw.lzwDecode(bytes, start, start + length, Bytes.allocate(expected));
        }
        throw new UnsupportedError(
                "compression " + NumberText.of(compression) + " is not read here", Map.of("compression", compression));
    }

    /**
     * A tag value the reader counts or indexes with. A baseline writer only ever puts whole numbers
     * there; anything else, such as a fraction from a rational field, is refused rather than guessed
     * at.
     */
    private static double requireWhole(double value, String name, String unit) {
        if (!(value >= 0 && value == Math.rint(value))) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("name", name);
            details.put("value", value);
            throw new CodecError("the " + name + " tag does not hold a whole number of " + unit, details);
        }
        return value;
    }

    private static int wide(byte[] raw, int at, boolean little) {
        int first = raw[at] & 0xff;
        int second = raw[at + 1] & 0xff;
        return little ? first | (second << 8) : (first << 8) | second;
    }

    /** The layout and depth the bit widths and samples per pixel make. */
    private record Format(Layout layout, int depth) {}
}
