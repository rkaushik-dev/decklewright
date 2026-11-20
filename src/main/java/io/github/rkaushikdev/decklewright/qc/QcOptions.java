package io.github.rkaushikdev.decklewright.qc;

import java.util.List;

/**
 * Which checks a run makes, and which of them a delivery cannot be graded without.
 *
 * @param checks the checks to run, in the order a report lists them; copied
 * @param required the names of the checks that have to run; copied
 * @see QcRunner#runQc(QcInput, QcOptions)
 */
public record QcOptions(List<Check> checks, List<String> required) {
    /** Options of the given parts. */
    public QcOptions {
        checks = List.copyOf(checks);
        required = List.copyOf(required);
    }

    /** The {@link QcRunner#standardChecks() standard checks}, with {@link QcRunner#REQUIRED} required. */
    public static QcOptions defaults() {
        return new QcOptions(QcRunner.standardChecks(), QcRunner.REQUIRED);
    }

    /** These options running another set of checks. */
    public QcOptions withChecks(List<Check> checks) {
        return new QcOptions(checks, required);
    }

    /** These options requiring another list of checks. */
    public QcOptions withRequired(List<String> required) {
        return new QcOptions(checks, required);
    }
}
