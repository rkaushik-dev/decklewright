package io.github.rkaushikdev.decklewright.manifest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A file as a sum file is written from it: its name and every digest kept for it.
 *
 * @param name the file name the line carries
 * @param digests each digest in hex under its algorithm's label; copied
 * @see Checksums#toSumFile(java.util.List)
 */
public record SumEntry(String name, Map<String, String> digests) {

    /** An entry of the given parts; the digests are copied, keeping their order. */
    public SumEntry {
        Objects.requireNonNull(name, "name");
        digests = Collections.unmodifiableMap(new LinkedHashMap<>(digests));
    }
}
