package io.github.rkaushikdev.decklewright.derive;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * How large an encoded set came out, which is the number a lab plans storage on.
 *
 * @param total the bytes of every file together
 * @param largest the name of the largest file, the first of them on a tie; empty for an empty set
 * @param perName each derivative's name to the bytes of its file, in the order of the set; copied
 * @see Recipes#sizeOf(java.util.List)
 */
public record SetSize(long total, Optional<String> largest, Map<String, Integer> perName) {
    /** A size of the given parts; the map is copied, keeping its order. */
    public SetSize {
        Objects.requireNonNull(largest, "largest");
        perName = Collections.unmodifiableMap(new LinkedHashMap<>(perName));
    }
}
