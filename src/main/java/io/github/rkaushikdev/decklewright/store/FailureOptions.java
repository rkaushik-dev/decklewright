package io.github.rkaushikdev.decklewright.store;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * How a run that produced nothing is recorded.
 *
 * @param outcome how it ended, one of {@link Ledger#OUTCOMES}
 * @param profile the profile it ran under
 * @param at when; empty for now
 * @see Dispatch#recordFailure(Workspace, String, String, FailureOptions)
 */
public record FailureOptions(String outcome, Optional<String> profile, Optional<Instant> at) {

    /** Options of the given parts. */
    public FailureOptions {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(at, "at");
    }

    /** As a failure, under no profile, now. */
    public static FailureOptions defaults() {
        return new FailureOptions("failed", Optional.empty(), Optional.empty());
    }

    /** These options with another outcome, such as {@code skipped} for a capture passed over. */
    public FailureOptions withOutcome(String other) {
        return new FailureOptions(other, profile, at);
    }

    /** These options recording a profile. */
    public FailureOptions withProfile(String name) {
        return new FailureOptions(outcome, Optional.of(name), at);
    }

    /** These options recording a time. */
    public FailureOptions withAt(Instant instant) {
        return new FailureOptions(outcome, profile, Optional.of(instant));
    }
}
