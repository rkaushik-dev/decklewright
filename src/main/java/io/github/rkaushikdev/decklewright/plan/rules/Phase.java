package io.github.rkaushikdev.decklewright.plan.rules;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.Arrays;
import java.util.List;

/**
 * When a crop rule runs. The phase is the only ordering a rule gets to ask for, and these four are
 * enough. The constants are declared in the order the engine runs them.
 */
public enum Phase implements JsonValue {
    /** What the crop is measured from. Runs once, before anything else. */
    ANCHOR("anchor"),
    /** What shape the crop should be, before anything is rounded. */
    SHAPE("shape"),
    /** Whole pixels, multiples, and the other machine constraints. */
    GRID("grid"),
    /** Nothing moves, only complaints. Runs once, after everything has settled. */
    CHECK("check");

    /** Every label, in declaration order. */
    private static final List<String> LABELS =
            Arrays.stream(values()).map(Phase::label).toList();

    private final String label;

    Phase(String label) {
        this.label = label;
    }

    /**
     * The phase a rule set names.
     *
     * @throws BadArgumentError naming every phase when the value is none of them
     */
    public static Phase parse(String value) {
        return values()[LABELS.indexOf(Guards.requireOneOf(value, LABELS, "phase"))];
    }

    /** The name a rule set writes, such as {@code shape}. */
    public String label() {
        return label;
    }

    /** The {@linkplain #label() label}, which is how a phase is written in json. */
    @Override
    public String toJson() {
        return label;
    }

    /** The {@linkplain #label() label}, which is how a phase reads in a message. */
    @Override
    public String toString() {
        return label;
    }
}
