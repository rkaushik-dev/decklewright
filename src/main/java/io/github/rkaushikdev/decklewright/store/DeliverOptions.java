package io.github.rkaushikdev.decklewright.store;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Where a delivery goes and what its ledger line records.
 *
 * @param folder where the files go; a delivery put in rejected is recorded as a rejection
 * @param overwrite true replaces a delivery already under that accession
 * @param profile the profile it ran under
 * @param stars the level it graded at
 * @param warnings what an operator should know; copied
 * @param at when, for the ledger; empty for now
 * @see Dispatch#deliver(Workspace, io.github.rkaushikdev.decklewright.manifest.Payload, DeliverOptions)
 */
public record DeliverOptions(
        Folder folder,
        boolean overwrite,
        Optional<String> profile,
        OptionalInt stars,
        List<String> warnings,
        Optional<Instant> at) {

    /** Options of the given parts; the warnings are copied. */
    public DeliverOptions {
        Objects.requireNonNull(folder, "folder");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(stars, "stars");
        warnings = List.copyOf(warnings);
        Objects.requireNonNull(at, "at");
    }

    /** Into delivered, never over another, with nothing more to record, now. */
    public static DeliverOptions defaults() {
        return new DeliverOptions(
                Folder.DELIVERED, false, Optional.empty(), OptionalInt.empty(), List.of(), Optional.empty());
    }

    /** These options into another folder. */
    public DeliverOptions withFolder(Folder other) {
        return new DeliverOptions(other, overwrite, profile, stars, warnings, at);
    }

    /** These options, replacing a delivery that is already there or not. */
    public DeliverOptions withOverwrite(boolean replace) {
        return new DeliverOptions(folder, replace, profile, stars, warnings, at);
    }

    /** These options recording a profile. */
    public DeliverOptions withProfile(String name) {
        return new DeliverOptions(folder, overwrite, Optional.of(name), stars, warnings, at);
    }

    /** These options recording a grading. */
    public DeliverOptions withStars(int level) {
        return new DeliverOptions(folder, overwrite, profile, OptionalInt.of(level), warnings, at);
    }

    /** These options recording warnings. */
    public DeliverOptions withWarnings(List<String> others) {
        return new DeliverOptions(folder, overwrite, profile, stars, others, at);
    }

    /** These options recording a time. */
    public DeliverOptions withAt(Instant instant) {
        return new DeliverOptions(folder, overwrite, profile, stars, warnings, Optional.of(instant));
    }
}
