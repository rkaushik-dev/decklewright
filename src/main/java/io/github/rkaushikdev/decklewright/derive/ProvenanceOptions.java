package io.github.rkaushikdev.decklewright.derive;

import java.util.Objects;
import java.util.Optional;

/**
 * What a file's provenance text says beyond the accession it came from.
 *
 * @param derivative the derivative the file is, which gives the text a title
 * @param profile the name of the profile it was made under, which gives the text a comment
 * @param software what made it
 * @see Recipes#provenanceText(String, ProvenanceOptions)
 */
public record ProvenanceOptions(Optional<String> derivative, Optional<String> profile, String software) {

    /** What the lab's own files say they were made by. */
    private static final String SOFTWARE = "decklewright";

    /** Options of the given parts. */
    public ProvenanceOptions {
        Objects.requireNonNull(derivative, "derivative");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(software, "software");
    }

    /** No derivative and no profile, made by decklewright. */
    public static ProvenanceOptions defaults() {
        return new ProvenanceOptions(Optional.empty(), Optional.empty(), SOFTWARE);
    }

    /** These options naming the derivative. */
    public ProvenanceOptions withDerivative(String derivative) {
        return new ProvenanceOptions(Optional.of(derivative), profile, software);
    }

    /** These options naming the profile. */
    public ProvenanceOptions withProfile(String profile) {
        return new ProvenanceOptions(derivative, Optional.of(profile), software);
    }

    /** These options naming other software. */
    public ProvenanceOptions withSoftware(String software) {
        return new ProvenanceOptions(derivative, profile, software);
    }
}
