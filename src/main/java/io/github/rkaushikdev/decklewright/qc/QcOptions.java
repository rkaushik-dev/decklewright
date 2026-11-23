package io.github.rkaushikdev.decklewright.qc;

import java.util.List;
import java.util.Optional;

/**
 * Which checks a run makes, and which of them a delivery cannot be graded without.
 *
 * @param checks the checks to run, in the order a report lists them; copied
 * @param required the names of the checks that have to run; copied, and empty for the ones the
 *     profile names
 * @see QcRunner#runQc(QcInput, QcOptions)
 */
public record QcOptions(List<Check> checks, Optional<List<String>> required) {
    /** Options of the given parts. */
    public QcOptions {
        checks = List.copyOf(checks);
        required = required.map(List::copyOf);
    }

    /** The {@link QcRunner#standardChecks() standard checks}, with the ones the profile names required. */
    public static QcOptions defaults() {
        return new QcOptions(QcRunner.standardChecks(), Optional.empty());
    }

    /** These options running another set of checks. */
    public QcOptions withChecks(List<Check> checks) {
        return new QcOptions(checks, required);
    }

    /** These options requiring another list of checks. */
    public QcOptions withRequired(List<String> required) {
        return new QcOptions(checks, Optional.of(required));
    }
}
