package io.github.rkaushikdev.decklewright.codec.tiff;

import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.raster.Surface;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A decoded tiff: its samples and what its first directory says about the capture.
 *
 * @param surface the samples, already inverted when zero meant white
 * @param tags every tag by name, as {@link Tags#asBag(java.util.List)} gives them
 * @param resolution the sampling frequency in pixels per inch, when the file states a usable one
 * @param photometric the photometric interpretation code as the file states it
 */
public record TiffImage(Surface surface, Map<String, Object> tags, Optional<Sampling> resolution, double photometric) {
    /** An image of the given parts; the tags are copied, keeping their order. */
    public TiffImage {
        Objects.requireNonNull(surface, "surface");
        tags = Collections.unmodifiableMap(new LinkedHashMap<>(tags));
        Objects.requireNonNull(resolution, "resolution");
    }
}
