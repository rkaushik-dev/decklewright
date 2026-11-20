package io.github.rkaushikdev.decklewright.manifest;

import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A fixity record: the digests of a file and its size, and nothing else.
 *
 * <p>The size is here because it is free, and because a truncated file usually shows up as a wrong
 * size before anybody gets round to rehashing a terabyte.
 *
 * @param bytes how long the file is
 * @param digests each digest in lowercase hex under its algorithm's label, in the order they were
 *     computed; copied
 * @see Checksums#fixityOf(byte[])
 */
public record Fixity(long bytes, Map<String, String> digests) implements JsonValue {

    /** A record of the given parts; the digests are copied, keeping their order. */
    public Fixity {
        digests = Collections.unmodifiableMap(new LinkedHashMap<>(digests));
    }

    /** {@code {bytes, digests}}, in a new map the caller may keep, the size as a {@link Double}. */
    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("bytes", (double) bytes);
        json.put("digests", new LinkedHashMap<>(digests));
        return json;
    }
}
