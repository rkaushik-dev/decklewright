package io.github.rkaushikdev.decklewright.derive;

import io.github.rkaushikdev.decklewright.codec.ImageFormat;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * How one rendered derivative is written out.
 *
 * @param format what it is written as; {@linkplain Recipes#DEFAULT_FORMAT png} when empty
 * @param keepMetadata false leaves the text out of the file, though never the frequency
 * @param text the text chunks to carry, key to value in the order they are written; copied
 * @param level the zlib level, where the format has one; the codec's own default when empty
 * @see Recipes#encodeRendered(Rendered, EncodeOptions)
 */
public record EncodeOptions(
        Optional<ImageFormat> format, boolean keepMetadata, Map<String, String> text, OptionalInt level) {

    /** Options of the given parts; the text is copied, keeping its order. */
    public EncodeOptions {
        Objects.requireNonNull(format, "format");
        text = Collections.unmodifiableMap(new LinkedHashMap<>(text));
        Objects.requireNonNull(level, "level");
    }

    /** A png, with whatever metadata it is given, at the codec's own level. */
    public static EncodeOptions defaults() {
        return new EncodeOptions(Optional.empty(), true, Map.of(), OptionalInt.empty());
    }

    /** These options with a format named. */
    public EncodeOptions withFormat(ImageFormat format) {
        return new EncodeOptions(Optional.of(format), keepMetadata, text, level);
    }

    /** These options keeping the text metadata or leaving it out. */
    public EncodeOptions withKeepMetadata(boolean keepMetadata) {
        return new EncodeOptions(format, keepMetadata, text, level);
    }

    /** These options with text to carry. */
    public EncodeOptions withText(Map<String, String> text) {
        return new EncodeOptions(format, keepMetadata, text, level);
    }

    /** These options with a compression level. */
    public EncodeOptions withLevel(int level) {
        return new EncodeOptions(format, keepMetadata, text, OptionalInt.of(level));
    }
}
