package io.github.rkaushikdev.decklewright.codec.png;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Metadata to add to a png without touching its image data.
 *
 * @param text text chunks to add, key to value, in the map's order; each replaces any chunk with the
 *     same key
 * @param time the capture time to write, replacing any the file already has
 * @see Rewriting#withMetadata(byte[], Metadata)
 */
public record Metadata(Map<String, String> text, Optional<Instant> time) {
    /** Metadata of the given parts; the text is copied, keeping its order. */
    public Metadata {
        text = Collections.unmodifiableMap(new LinkedHashMap<>(text));
        Objects.requireNonNull(time, "time");
    }

    /** Nothing to add. */
    public static Metadata defaults() {
        return new Metadata(Map.of(), Optional.empty());
    }

    /** This metadata with other text chunks to add. */
    public Metadata withText(Map<String, String> text) {
        return new Metadata(text, time);
    }

    /** This metadata with a capture time to write. */
    public Metadata withTime(Instant time) {
        return new Metadata(text, Optional.of(time));
    }
}
