package io.github.rkaushikdev.decklewright.store;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * What a ledger line may carry beyond its accession and its outcome.
 *
 * <p>An empty profile, digest or reason counts as none.
 *
 * @param at when it happened; empty for now
 * @param profile the profile it ran under
 * @param stars the level it graded at
 * @param bytes how much was written
 * @param digest the delivery's digest
 * @param reason why, when it did not go out
 * @param warnings what an operator should know; copied
 * @see Ledger#entryFor(String, String, EntryOptions)
 */
public record EntryOptions(
        Optional<Instant> at,
        Optional<String> profile,
        OptionalInt stars,
        OptionalLong bytes,
        Optional<String> digest,
        Optional<String> reason,
        List<String> warnings) {

    /** Options of the given parts; the warnings are copied. */
    public EntryOptions {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(stars, "stars");
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(digest, "digest");
        Objects.requireNonNull(reason, "reason");
        warnings = List.copyOf(warnings);
    }

    /** Nothing beyond the time it is now. */
    public static EntryOptions defaults() {
        return new EntryOptions(
                Optional.empty(),
                Optional.empty(),
                OptionalInt.empty(),
                OptionalLong.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of());
    }

    /** These options at a time. */
    public EntryOptions withAt(Instant instant) {
        return new EntryOptions(Optional.of(instant), profile, stars, bytes, digest, reason, warnings);
    }

    /** These options under a profile. */
    public EntryOptions withProfile(String name) {
        return new EntryOptions(at, Optional.of(name), stars, bytes, digest, reason, warnings);
    }

    /** These options with a grading. */
    public EntryOptions withStars(int level) {
        return new EntryOptions(at, profile, OptionalInt.of(level), bytes, digest, reason, warnings);
    }

    /** These options with a size. */
    public EntryOptions withBytes(long size) {
        return new EntryOptions(at, profile, stars, OptionalLong.of(size), digest, reason, warnings);
    }

    /** These options with a delivery digest. */
    public EntryOptions withDigest(String value) {
        return new EntryOptions(at, profile, stars, bytes, Optional.of(value), reason, warnings);
    }

    /** These options with a reason. */
    public EntryOptions withReason(String why) {
        return new EntryOptions(at, profile, stars, bytes, digest, Optional.of(why), warnings);
    }

    /** These options with warnings. */
    public EntryOptions withWarnings(List<String> others) {
        return new EntryOptions(at, profile, stars, bytes, digest, reason, others);
    }
}
