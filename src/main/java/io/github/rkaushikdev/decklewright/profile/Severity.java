package io.github.rkaushikdev.decklewright.profile;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.Arrays;
import java.util.List;

/** How much a {@link Problem} with a profile matters. */
public enum Severity implements JsonValue {
    /** The profile cannot be used, and a run that names it stops. */
    FAULT("fault"),
    /** The profile works but will surprise somebody later, so the problem goes in the run log. */
    NOTE("note");

    /** Every label, in declaration order. */
    private static final List<String> LABELS =
            Arrays.stream(values()).map(Severity::label).toList();

    private final String label;

    Severity(String label) {
        this.label = label;
    }

    /**
     * The severity a report names.
     *
     * @throws BadArgumentError naming both severities when the value is neither of them
     */
    public static Severity parse(String value) {
        return values()[LABELS.indexOf(Guards.requireOneOf(value, LABELS, "severity"))];
    }

    /** The name a report prints, such as {@code fault}. */
    public String label() {
        return label;
    }

    /** The {@linkplain #label() label}, which is how a severity is written in json. */
    @Override
    public String toJson() {
        return label;
    }

    /** The {@linkplain #label() label}, which is how a severity reads in a message. */
    @Override
    public String toString() {
        return label;
    }
}
