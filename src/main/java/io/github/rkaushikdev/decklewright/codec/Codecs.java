package io.github.rkaushikdev.decklewright.codec;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.CodecError;
import io.github.rkaushikdev.decklewright.UnsupportedError;
import io.github.rkaushikdev.decklewright.raster.Raster;
import io.github.rkaushikdev.decklewright.raster.Surface;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Which codec handles which bytes.
 *
 * <p>Nothing anywhere else in the tree names a format. The intake stage reads whatever landed in
 * the folder, the derivative stage writes whatever the recipe asked for, and both of them come
 * through here. Extensions are used only when there are no bytes to look at yet.
 */
public final class Codecs {
    /** The name of every format, in the order the bytes are tried against them. */
    public static final List<String> CODEC_NAMES =
            Arrays.stream(ImageFormat.values()).map(ImageFormat::label).toList();

    /** How many leading bytes an unrecognised file's error quotes. */
    private static final int HEAD = 8;

    private Codecs() {}

    /**
     * The format of that name, ignoring case.
     *
     * @throws BadArgumentError when the name is empty
     * @throws UnsupportedError when no format has that name; the details list every known one
     */
    public static ImageFormat codecNamed(String name) {
        return ImageFormat.parse(name);
    }

    /** The format a file name's extension says, ignoring case; empty for an unknown or missing one. */
    public static Optional<ImageFormat> codecForPath(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        if (dot < 0) {
            return Optional.empty();
        }
        String extension = lower.substring(dot);
        return Arrays.stream(ImageFormat.values())
                .filter(format -> format.extensions().contains(extension))
                .findFirst();
    }

    /** The first format whose signature the bytes carry; empty when none does. */
    public static Optional<ImageFormat> codecForBytes(byte[] bytes) {
        return Arrays.stream(ImageFormat.values())
                .filter(format -> format.sniff(bytes))
                .findFirst();
    }

    /**
     * The bytes decoded by whichever codec recognises them.
     *
     * @see #readImage(byte[], String)
     */
    public static Surface readImage(byte[] bytes) {
        return read(bytes, null);
    }

    /**
     * The bytes decoded by whichever codec recognises them.
     *
     * <p>The bytes decide. A file named .png that is really a netpbm is read as a netpbm, because the
     * watch folder receives whatever the stand software felt like naming it and arguing with the name
     * helps nobody.
     *
     * @param path where the bytes came from; used only in the error message
     * @throws CodecError when no codec recognises the bytes, quoting the first eight, or the codec
     *     that does cannot read them
     * @throws UnsupportedError when the codec recognises a variant it does not read
     */
    public static Surface readImage(byte[] bytes, String path) {
        return read(bytes, Objects.requireNonNull(path, "path"));
    }

    /**
     * The shape of the bytes, from whichever codec recognises them, without decoding the samples.
     *
     * @see #probeImage(byte[], String)
     */
    public static ImageShape probeImage(byte[] bytes) {
        return probe(bytes, null);
    }

    /**
     * The shape of the bytes, from whichever codec recognises them, without decoding the samples.
     *
     * @param path where the bytes came from; used only in the error message
     * @throws CodecError when no codec recognises the bytes, quoting the first eight, or the header
     *     is wrong
     * @throws UnsupportedError when the codec recognises a variant it does not read
     */
    public static ImageShape probeImage(byte[] bytes, String path) {
        return probe(bytes, Objects.requireNonNull(path, "path"));
    }

    /**
     * The raster written in the named format with every writer's defaults.
     *
     * @see #writeImage(Raster, String, WriteOptions)
     */
    public static byte[] writeImage(Raster raster, String format) {
        return writeImage(raster, format, WriteOptions.defaults());
    }

    /**
     * The raster written in the named format, which takes the options it understands.
     *
     * @throws BadArgumentError when the format name is empty
     * @throws UnsupportedError when no format has that name, or the format is only read here
     * @throws CodecError when the format cannot hold the raster
     */
    public static byte[] writeImage(Raster raster, String format, WriteOptions options) {
        Objects.requireNonNull(options, "options");
        ImageFormat codec = codecNamed(format);
        if (!codec.isWritable()) {
            throw new UnsupportedError(codec.label() + " is read only here", Map.of("format", format));
        }
        return codec.write(raster, options);
    }

    /**
     * The preferred extension of the named format, including the leading dot.
     *
     * @throws BadArgumentError when the name is empty
     * @throws UnsupportedError when no format has that name
     */
    public static String extensionFor(String format) {
        return codecNamed(format).extension();
    }

    /** The format the bytes carry the signature of; empty when none. */
    public static Optional<ImageFormat> formatOf(byte[] bytes) {
        return codecForBytes(bytes);
    }

    private static Surface read(byte[] bytes, String path) {
        return codecForBytes(bytes).orElseThrow(() -> unrecognised(bytes, path)).read(bytes);
    }

    private static ImageShape probe(byte[] bytes, String path) {
        return codecForBytes(bytes).orElseThrow(() -> unrecognised(bytes, path)).probe(bytes);
    }

    /** The refusal for bytes no codec recognises, quoting where they came from, or null, and their head. */
    private static CodecError unrecognised(byte[] bytes, String path) {
        List<Integer> head = new ArrayList<>();
        for (int i = 0; i < Math.min(HEAD, bytes.length); i += 1) {
            head.add(bytes[i] & 0xff);
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("path", path);
        details.put("head", head);
        return new CodecError("nothing here recognises those bytes", details);
    }
}
