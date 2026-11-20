package io.github.rkaushikdev.decklewright.codec.png;

import io.github.rkaushikdev.decklewright.measure.Sampling;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * How a png is written.
 *
 * @param text text chunks to write, key to value, in the map's order
 * @param ppi the sampling frequency to write as a {@code pHYs} chunk, in pixels per inch
 * @param filter how the rows are filtered
 * @param level the zlib compression level, zero through nine; checked when the png is written
 */
public record PngOptions(Map<String, String> text, Optional<Sampling> ppi, FilterMode filter, int level) {
    /** The level zlib itself defaults to. */
    private static final int DEFAULT_LEVEL = 6;

    /** Options of the given parts; the text is copied, keeping its order. */
    public PngOptions {
        text = Collections.unmodifiableMap(new LinkedHashMap<>(text));
        Objects.requireNonNull(ppi, "ppi");
        Objects.requireNonNull(filter, "filter");
    }

    /** No metadata, the filter heuristic on, and level six. */
    public static PngOptions defaults() {
        return new PngOptions(Map.of(), Optional.empty(), FilterMode.AUTO, DEFAULT_LEVEL);
    }

    /** These options with other text chunks. */
    public PngOptions withText(Map<String, String> text) {
        return new PngOptions(text, ppi, filter, level);
    }

    /** These options with a sampling frequency to write. */
    public PngOptions withPpi(Sampling ppi) {
        return new PngOptions(text, Optional.of(ppi), filter, level);
    }

    /** These options with another way of filtering the rows. */
    public PngOptions withFilter(FilterMode filter) {
        return new PngOptions(text, ppi, filter, level);
    }

    /** These options with another compression level. */
    public PngOptions withLevel(int level) {
        return new PngOptions(text, ppi, filter, level);
    }
}
