package io.github.rkaushikdev.decklewright.codec.png;

import io.github.rkaushikdev.decklewright.raster.Surface;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A decoded png: its samples and the metadata the lab reads back out of it.
 *
 * @param surface the samples
 * @param text every text chunk, key to value, in the order the file holds them
 * @param physical the sampling frequency chunk, when the file has a well formed one
 */
public record PngImage(Surface surface, Map<String, String> text, Optional<Physical> physical) {
    /** An image of the given parts; the text is copied, keeping its order. */
    public PngImage {
        Objects.requireNonNull(surface, "surface");
        text = Collections.unmodifiableMap(new LinkedHashMap<>(text));
        Objects.requireNonNull(physical, "physical");
    }
}
