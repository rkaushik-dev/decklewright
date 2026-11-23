package io.github.rkaushikdev.decklewright.store;

import java.util.Objects;
import java.util.Optional;

/**
 * Which ledger entries to pick. Every part given must hold; an empty one, or one given as the
 * empty string, picks everything.
 *
 * <p>Times are compared as text, which for ISO 8601 in UTC is the order of the times, and lets a
 * day such as {@code 2025-11-21} stand for its first moment.
 *
 * @param outcome how the run ended
 * @param profile the profile it ran under
 * @param since the earliest time, inclusive
 * @param until the time to stop before, exclusive
 * @see Ledger#select(java.util.List, LedgerFilter)
 */
public record LedgerFilter(
        Optional<String> outcome, Optional<String> profile, Optional<String> since, Optional<String> until) {

    /** A filter of the given parts. */
    public LedgerFilter {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(since, "since");
        Objects.requireNonNull(until, "until");
    }

    /** A filter that picks everything. */
    public static LedgerFilter everything() {
        return new LedgerFilter(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    /** This filter, picking only entries that ended this way. */
    public LedgerFilter withOutcome(String value) {
        return new LedgerFilter(Optional.of(value), profile, since, until);
    }

    /** This filter, picking only entries under this profile. */
    public LedgerFilter withProfile(String value) {
        return new LedgerFilter(outcome, Optional.of(value), since, until);
    }

    /** This filter, picking only entries at or after this time. */
    public LedgerFilter withSince(String value) {
        return new LedgerFilter(outcome, profile, Optional.of(value), until);
    }

    /** True when the entry passes every part of the filter that was given. */
    public boolean picks(LedgerEntry entry) {
        return given(outcome).map(entry.outcome()::equals).orElse(true)
                && given(profile)
                        .map(wanted -> entry.profile().equals(Optional.of(wanted)))
                        .orElse(true)
                && given(since).map(from -> entry.at().compareTo(from) >= 0).orElse(true)
                && given(until).map(to -> entry.at().compareTo(to) < 0).orElse(true);
    }

    private static Optional<String> given(Optional<String> value) {
        return value.filter(text -> !text.isEmpty());
    }

    /** This filter, picking only entries before this time. */
    public LedgerFilter withUntil(String value) {
        return new LedgerFilter(outcome, profile, since, Optional.of(value));
    }
}
