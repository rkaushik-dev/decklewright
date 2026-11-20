package io.github.rkaushikdev.decklewright.store;

import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * One line of the ledger: what happened to one accession, once.
 *
 * @param at when, in ISO 8601
 * @param accession what it was of
 * @param outcome how the run ended, one of {@link Ledger#OUTCOMES} when it was recorded here
 * @param profile the profile it ran under
 * @param stars the level it graded at, when it was graded; zero is a grading, not a missing one
 * @param bytes how much was written
 * @param digest the delivery's digest, so a folder can be traced to a line
 * @param reason why, when it did not go out
 * @param warnings what an operator should know; copied, and empty for none
 * @see Ledger#entryFor(String, String, EntryOptions)
 */
public record LedgerEntry(
        String at,
        String accession,
        String outcome,
        Optional<String> profile,
        OptionalInt stars,
        OptionalLong bytes,
        Optional<String> digest,
        Optional<String> reason,
        List<String> warnings)
        implements JsonValue {

    /** An entry of the given parts; the warnings are copied. */
    public LedgerEntry {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(accession, "accession");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(stars, "stars");
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(digest, "digest");
        Objects.requireNonNull(reason, "reason");
        warnings = List.copyOf(warnings);
    }

    /**
     * The line as it is written: {@code at}, {@code accession} and {@code outcome} always, then
     * {@code profile}, {@code stars}, {@code bytes}, {@code digest}, {@code reason} and {@code
     * warnings} in that order when there are any, numbers as {@link Double}s.
     */
    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("at", at);
        json.put("accession", accession);
        json.put("outcome", outcome);
        profile.ifPresent(value -> json.put("profile", value));
        stars.ifPresent(value -> json.put("stars", (double) value));
        bytes.ifPresent(value -> json.put("bytes", (double) value));
        digest.ifPresent(value -> json.put("digest", value));
        reason.ifPresent(value -> json.put("reason", value));
        if (!warnings.isEmpty()) {
            json.put("warnings", new ArrayList<Object>(warnings));
        }
        return json;
    }

    /**
     * A line read back, when it is an entry at all: an object whose accession and outcome are
     * strings.
     *
     * <p>A field of the wrong kind reads as missing, a count as missing unless it is a whole number,
     * a warning that is not a string is dropped, and a missing time reads as empty, which sorts
     * before every real one.
     */
    static Optional<LedgerEntry> fromJson(Object parsed) {
        if (!(parsed instanceof Map<?, ?> line)
                || !(line.get("accession") instanceof String accession)
                || !(line.get("outcome") instanceof String outcome)) {
            return Optional.empty();
        }
        List<String> warnings = new ArrayList<>();
        if (line.get("warnings") instanceof List<?> listed) {
            listed.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .forEach(warnings::add);
        }
        OptionalLong stars = wholeNumber(line.get("stars"), Integer.MAX_VALUE);
        return Optional.of(new LedgerEntry(
                line.get("at") instanceof String at ? at : "",
                accession,
                outcome,
                text(line.get("profile")),
                stars.isPresent() ? OptionalInt.of((int) stars.getAsLong()) : OptionalInt.empty(),
                wholeNumber(line.get("bytes"), Long.MAX_VALUE),
                text(line.get("digest")),
                text(line.get("reason")),
                warnings));
    }

    private static Optional<String> text(Object value) {
        return value instanceof String given ? Optional.of(given) : Optional.empty();
    }

    /** The value when it is a whole number no larger in size than the limit. */
    private static OptionalLong wholeNumber(Object value, long limit) {
        if (value instanceof Number number) {
            double given = number.doubleValue();
            if (given == Math.rint(given) && Math.abs(given) <= limit) {
                return OptionalLong.of((long) given);
            }
        }
        return OptionalLong.empty();
    }
}
