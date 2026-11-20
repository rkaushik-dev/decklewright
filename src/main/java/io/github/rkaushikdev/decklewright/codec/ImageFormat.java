package io.github.rkaushikdev.decklewright.codec;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.UnsupportedError;
import io.github.rkaushikdev.decklewright.codec.png.Chunks;
import io.github.rkaushikdev.decklewright.codec.png.Decoding;
import io.github.rkaushikdev.decklewright.codec.png.Encoding;
import io.github.rkaushikdev.decklewright.codec.tiff.Tags;
import io.github.rkaushikdev.decklewright.codec.tiff.TiffReader;
import io.github.rkaushikdev.decklewright.raster.Raster;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The image formats the lab handles, each with the codec that reads it and, where there is one, the
 * codec that writes it.
 *
 * <p>Nothing outside the codec layer names a format's reader or writer: the intake stage reads
 * whatever landed in the folder and the derivative stage writes whatever the recipe asked for, both
 * through {@link Codecs}, which tries the formats in the order they are declared here.
 */
public enum ImageFormat implements JsonValue {
    /** Png, which the lab writes its masters and derivatives in. */
    PNG(
            "png",
            List.of(".png"),
            Chunks::looksLikePng,
            bytes -> Decoding.decodePng(bytes).surface(),
            Decoding::probePng,
            (raster, options) -> Encoding.encodePng(raster, options.png())),
    /** Baseline tiff off the flatbed, which is read and never written. */
    TIFF(
            "tiff",
            List.of(".tif", ".tiff"),
            Tags::looksLikeTiff,
            bytes -> TiffReader.readTiff(bytes).surface(),
            TiffReader::probeTiff,
            null),
    /** Netpbm, which the copy stand drops on the watch folder. */
    PNM(
            "pnm",
            List.of(".pnm", ".pgm", ".ppm"),
            Pnm::looksLikePnm,
            Pnm::readPnm,
            Pnm::probePnm,
            (raster, options) -> Pnm.writePnm(raster, options.pnm()));

    /** Every label, in declaration order. */
    private static final List<String> LABELS =
            Arrays.stream(values()).map(ImageFormat::label).toList();

    private final String label;
    private final List<String> extensions;
    private final Predicate<byte[]> sniffer;
    private final Function<byte[], Surface> reader;
    private final Function<byte[], ImageShape> prober;

    /** Null for a format that is only read. */
    private final BiFunction<Raster, WriteOptions, byte[]> writer;

    ImageFormat(
            String label,
            List<String> extensions,
            Predicate<byte[]> sniffer,
            Function<byte[], Surface> reader,
            Function<byte[], ImageShape> prober,
            BiFunction<Raster, WriteOptions, byte[]> writer) {
        this.label = label;
        this.extensions = extensions;
        this.sniffer = sniffer;
        this.reader = reader;
        this.prober = prober;
        this.writer = writer;
    }

    /**
     * The format a recipe or a caller names, ignoring case.
     *
     * @throws BadArgumentError when the name is empty
     * @throws UnsupportedError when no format has that name; the details list every known one
     */
    public static ImageFormat parse(String name) {
        Guards.requireNonEmptyString(name, "name");
        int index = LABELS.indexOf(name.toLowerCase(Locale.ROOT));
        if (index < 0) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("name", name);
            details.put("known", new ArrayList<>(LABELS));
            throw new UnsupportedError("no codec called " + name, details);
        }
        return values()[index];
    }

    /** The name a recipe writes, such as {@code png}. */
    public String label() {
        return label;
    }

    /** The file extensions the format goes by, each with its leading dot, the preferred one first. */
    public List<String> extensions() {
        return extensions;
    }

    /** The preferred file extension, with its leading dot. */
    public String extension() {
        return extensions.get(0);
    }

    /** True when the lab can write this format as well as read it. */
    public boolean isWritable() {
        return writer != null;
    }

    /** True when the bytes look like this format, from their first few bytes alone. */
    boolean sniff(byte[] bytes) {
        return sniffer.test(bytes);
    }

    /** The bytes decoded. */
    Surface read(byte[] bytes) {
        return reader.apply(bytes);
    }

    /** The shape the bytes declare, without decoding the samples. */
    ImageShape probe(byte[] bytes) {
        return prober.apply(bytes);
    }

    /** The raster encoded, taking the options this format understands; only for a {@linkplain #isWritable() writable} format. */
    byte[] write(Raster raster, WriteOptions options) {
        return writer.apply(raster, options);
    }

    /** The {@linkplain #label() label}, which is how a format is written in json. */
    @Override
    public String toJson() {
        return label;
    }

    /** The {@linkplain #label() label}, which is how a format reads in a message. */
    @Override
    public String toString() {
        return label;
    }
}
