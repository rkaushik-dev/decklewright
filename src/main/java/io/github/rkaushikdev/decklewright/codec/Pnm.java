package io.github.rkaushikdev.decklewright.codec;

import io.github.rkaushikdev.decklewright.CodecError;
import io.github.rkaushikdev.decklewright.UnsupportedError;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Raster;
import io.github.rkaushikdev.decklewright.raster.Samples;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.raster.Uint8Samples;
import io.github.rkaushikdev.decklewright.util.Whitespace;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Netpbm, which is what the copy stand software drops on the watch folder.
 *
 * <p>It is a header of five whitespace separated tokens and then the samples, so there is nothing to
 * get wrong and nothing to depend on. The lab keeps it as the intake format for exactly that reason,
 * and everything downstream is written against a surface rather than against this.
 */
public final class Pnm {
    private static final Set<String> MAGICS = Set.of("P2", "P3", "P5", "P6");

    private static final byte HASH = 0x23;
    private static final byte LINE_FEED = 0x0a;

    private Pnm() {}

    /**
     * The header, read token by token.
     *
     * @throws CodecError when the magic is not one of the four, the header stops early, a dimension
     *     or the maximum is not a positive whole number, or the maximum is above sixteen bits
     * @throws UnsupportedError when a dimension is more pixels than one buffer can hold
     */
    public static PnmHeader readPnmHeader(byte[] bytes) {
        Token magic = readToken(bytes, 0);
        if (!MAGICS.contains(magic.text())) {
            throw new CodecError("not a netpbm file, magic was " + magic.text(), Map.of("magic", magic.text()));
        }
        Token width = readToken(bytes, magic.next());
        Token height = readToken(bytes, width.next());
        Token max = readToken(bytes, height.next());

        double w = parseInteger(width.text());
        double h = parseInteger(height.text());
        double m = parseInteger(max.text());
        requirePositiveWhole("width", w);
        requirePositiveWhole("height", h);
        requirePositiveWhole("max", m);
        if (m > 65535) {
            throw new CodecError("a netpbm maximum above 65535 is not supported", Map.of("max", m));
        }
        ImageShape shape = shapeOf(magic.text(), w, h, m);
        return new PnmHeader(magic.text(), shape.width(), shape.height(), (int) m, max.next() + 1);
    }

    /**
     * The whole image.
     *
     * <p>Trailing bytes after the samples are ignored. An ascii sample above the maximum is stored as
     * the surface stores any value, keeping only the bits the depth holds.
     *
     * @throws CodecError when the header is wrong, the file stops before the samples do, or an ascii
     *     sample is not a whole number
     */
    public static Surface readPnm(byte[] bytes) {
        PnmHeader header = readPnmHeader(bytes);
        Layout layout = layoutOf(header.magic());
        int depth = depthOf(header.max());
        Surface surface = Surface.create(header.width(), header.height(), layout, depth);
        Samples data = surface.data();
        int wanted = surface.sampleCount();

        if (isAscii(header.magic())) {
            int cursor = header.offset() - 1;
            for (int i = 0; i < wanted; i += 1) {
                Token token = readToken(bytes, cursor);
                double value = parseInteger(token.text());
                if (!isWhole(value)) {
                    Map<String, Object> details = new LinkedHashMap<>();
                    details.put("index", i);
                    details.put("token", token.text());
                    throw new CodecError("a sample is not a whole number", details);
                }
                data.set(i, value);
                cursor = token.next();
            }
            return surface;
        }

        boolean wide = depth == 16;
        long needed = (long) wanted * (wide ? 2 : 1);
        long available = (long) bytes.length - header.offset();
        if (available < needed) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("needed", needed);
            details.put("available", available);
            throw new CodecError("the file stops before the samples do", details);
        }
        if (wide) {
            for (int i = 0; i < wanted; i += 1) {
                data.set(i, Bytes.readUint16BE(bytes, header.offset() + i * 2));
            }
        } else {
            data.set(Uint8Samples.wrap(bytes, header.offset(), wanted));
        }
        return surface;
    }

    /**
     * Enough of the header to know the shape without reading the samples, which is what the intake
     * stage uses to decide whether a capture is worth loading.
     *
     * @throws CodecError when the header is wrong
     */
    public static ImageShape probePnm(byte[] bytes) {
        PnmHeader header = readPnmHeader(bytes);
        return new ImageShape(header.width(), header.height(), layoutOf(header.magic()), depthOf(header.max()));
    }

    /** True when the bytes open with one of the four magics. */
    public static boolean looksLikePnm(byte[] bytes) {
        if (bytes.length < 2) {
            return false;
        }
        return bytes[0] == 0x50 && MAGICS.contains("P" + (char) (bytes[1] & 0xff));
    }

    /**
     * The raster as a binary netpbm file with no comment.
     *
     * @see #writePnm(Raster, PnmOptions)
     */
    public static byte[] writePnm(Raster raster) {
        return writePnm(raster, PnmOptions.defaults());
    }

    /**
     * Write a raster back out.
     *
     * <p>Only gray and rgb go out, because netpbm has no coverage channel and silently dropping one
     * would be the kind of quiet loss this whole tree exists to avoid. A capture with alpha has to be
     * composited first, on purpose, by a caller who knows what the background should be.
     *
     * @throws CodecError when the layout has alpha, or the comment would span lines
     */
    public static byte[] writePnm(Raster raster, PnmOptions options) {
        Objects.requireNonNull(options, "options");
        Layout layout = raster.layout();
        if (layout != Layout.GRAY && layout != Layout.RGB) {
            throw new CodecError("netpbm holds gray or rgb and nothing else", Map.of("layout", layout.label()));
        }
        if (options.comment().filter(comment -> comment.contains("\n")).isPresent()) {
            throw new CodecError(
                    "a netpbm comment cannot span lines",
                    Map.of("comment", options.comment().orElseThrow()));
        }
        boolean colour = layout == Layout.RGB;
        String magic = options.ascii() ? (colour ? "P3" : "P2") : colour ? "P6" : "P5";
        String commentLine =
                options.comment().map(comment -> "# " + comment + "\n").orElse("");
        String header =
                magic + "\n" + commentLine + raster.width() + " " + raster.height() + "\n" + raster.maxValue() + "\n";

        if (options.ascii()) {
            StringBuilder text = new StringBuilder(header);
            for (Samples row : raster.rows()) {
                for (int i = 0; i < row.length(); i += 1) {
                    if (i > 0) {
                        text.append(' ');
                    }
                    text.append(row.get(i));
                }
                text.append('\n');
            }
            return Bytes.asciiBytes(text.toString());
        }

        byte[] head = Bytes.asciiBytes(header);
        boolean wide = raster.depth() == 16;
        long samples = (long) raster.height() * raster.rowStride();
        byte[] out = Bytes.allocate(head.length + samples * (wide ? 2 : 1));
        System.arraycopy(head, 0, out, 0, head.length);
        int at = head.length;
        for (Samples row : raster.rows()) {
            for (int i = 0; i < row.length(); i += 1) {
                int value = row.get(i);
                if (wide) {
                    out[at] = (byte) (value >> 8);
                    at += 1;
                }
                out[at] = (byte) value;
                at += 1;
            }
        }
        return out;
    }

    /**
     * Header tokens are separated by any run of whitespace, and a comment runs from a hash to the end
     * of its line. A comment may sit between any two tokens, including between the width and the
     * height.
     */
    private static Token readToken(byte[] bytes, int start) {
        int i = start;
        for (; ; ) {
            while (i < bytes.length && isSpace(bytes[i])) {
                i += 1;
            }
            if (i < bytes.length && bytes[i] == HASH) {
                while (i < bytes.length && bytes[i] != LINE_FEED) {
                    i += 1;
                }
                continue;
            }
            break;
        }
        if (i >= bytes.length) {
            throw new CodecError("the header stops early", Map.of("at", start));
        }
        int end = i;
        while (end < bytes.length && !isSpace(bytes[end])) {
            end += 1;
        }
        return new Token(Bytes.readAscii(bytes, i, end - i), end);
    }

    /** The five blank bytes netpbm separates tokens with: space, tab, line feed, return, vertical tab. */
    private static boolean isSpace(byte value) {
        return value == 0x20 || value == 0x09 || value == 0x0a || value == 0x0d || value == 0x0b;
    }

    /**
     * A token read as a whole number, leniently, as the lab's files have always been read: leading
     * blank space skipped, an optional sign, then the longest run of digits, with anything after it
     * ignored, so {@code 4x} reads as four. Not a number when there are no digits at all.
     */
    private static double parseInteger(String token) {
        int i = 0;
        int length = token.length();
        while (i < length && Whitespace.isBlank(token.charAt(i))) {
            i += 1;
        }
        boolean negative = false;
        if (i < length && (token.charAt(i) == '+' || token.charAt(i) == '-')) {
            negative = token.charAt(i) == '-';
            i += 1;
        }
        int digits = i;
        while (i < length && token.charAt(i) >= '0' && token.charAt(i) <= '9') {
            i += 1;
        }
        if (i == digits) {
            return Double.NaN;
        }
        double magnitude = Double.parseDouble(token.substring(digits, i));
        return negative ? -magnitude : magnitude;
    }

    private static void requirePositiveWhole(String field, double value) {
        if (!isWhole(value) || value <= 0) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("field", field);
            details.put("value", value);
            throw new CodecError("the " + field + " in the header is not a positive whole number", details);
        }
    }

    private static boolean isWhole(double value) {
        return Double.isFinite(value) && value == Math.rint(value);
    }

    private static ImageShape shapeOf(String magic, double width, double height, double max) {
        return ImageShape.of(width, height, layoutOf(magic), depthOf(max));
    }

    private static Layout layoutOf(String magic) {
        return magic.equals("P3") || magic.equals("P6") ? Layout.RGB : Layout.GRAY;
    }

    private static int depthOf(double max) {
        return max > 255 ? 16 : 8;
    }

    private static boolean isAscii(String magic) {
        return magic.equals("P2") || magic.equals("P3");
    }

    /** One header token and the index just past it. */
    private record Token(String text, int next) {}
}
