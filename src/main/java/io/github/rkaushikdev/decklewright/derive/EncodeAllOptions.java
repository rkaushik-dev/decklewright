package io.github.rkaushikdev.decklewright.derive;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * How a whole rendered set is written out.
 *
 * @param accession the accession each file's provenance text names; with none, no file carries
 *     provenance text
 * @param profile the name of the profile the provenance text names, when it names one
 * @param level the zlib level, where a format has one; the codec's own default when empty
 * @see Recipes#encodeAll(java.util.List, java.util.List, EncodeAllOptions)
 */
public record EncodeAllOptions(Optional<String> accession, Optional<String> profile, OptionalInt level) {

    /** Options of the given parts. */
    public EncodeAllOptions {
        Objects.requireNonNull(accession, "accession");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(level, "level");
    }

    /** No provenance text, at the codec's own level. */
    public static EncodeAllOptions defaults() {
        return new EncodeAllOptions(Optional.empty(), Optional.empty(), OptionalInt.empty());
    }

    /** These options naming the accession in each file's provenance text. */
    public EncodeAllOptions withAccession(String accession) {
        return new EncodeAllOptions(Optional.of(accession), profile, level);
    }

    /** These options naming the profile in each file's provenance text. */
    public EncodeAllOptions withProfile(String profile) {
        return new EncodeAllOptions(accession, Optional.of(profile), level);
    }

    /** These options with a compression level. */
    public EncodeAllOptions withLevel(int level) {
        return new EncodeAllOptions(accession, profile, OptionalInt.of(level));
    }
}
