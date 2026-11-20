package io.github.rkaushikdev.decklewright.manifest;

import java.util.List;
import java.util.Objects;

/**
 * A delivery, as a set of named blobs: everything that goes into one folder and travels together.
 *
 * <p>The payload is bytes and names and nothing else. It does not know where it will be written,
 * which is what makes it possible to build one in memory, check it, and only then let the store put
 * it somewhere. A delivery that is checked after it is written is a delivery that gets half
 * written.
 *
 * @param accession what the delivery is of
 * @param entries every file in the folder; copied
 * @param digest one number for the whole delivery, as {@link Checksums#digestOfSet(List)} gives it
 * @see Payloads#payloadFor
 */
public record Payload(String accession, List<PayloadEntry> entries, String digest) {

    /** A payload of the given parts, the digest taken as given; the entries are copied. */
    public Payload {
        Objects.requireNonNull(accession, "accession");
        entries = List.copyOf(entries);
        Objects.requireNonNull(digest, "digest");
    }

    /** A payload of the given entries, with the digest of the set worked out from them. */
    public static Payload of(String accession, List<PayloadEntry> entries) {
        return new Payload(accession, entries, Checksums.digestOfSet(entries));
    }
}
