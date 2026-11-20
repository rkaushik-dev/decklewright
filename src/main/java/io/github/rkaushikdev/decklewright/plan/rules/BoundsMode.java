package io.github.rkaushikdev.decklewright.plan.rules;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.Arrays;
import java.util.List;

/**
 * What to do when the crop reaches off the capture. The profile picks, because a sheet placed too
 * near the edge of the bed and a bleed a hair too wide look identical to the code and completely
 * different to a curator.
 *
 * @see BoundsRules#boundsFor(BoundsMode)
 */
public enum BoundsMode implements JsonValue {
    /** Say so and change nothing; the render pads the master. */
    WARN("warn"),
    /** Cut the crop back to what the capture holds. */
    CLAMP("clamp"),
    /** Slide the crop back onto the capture, keeping its size. */
    NUDGE("nudge"),
    /** Stop: the capture has to be taken again. */
    REFUSE("refuse");

    /** Every label, in declaration order. */
    private static final List<String> LABELS =
            Arrays.stream(values()).map(BoundsMode::label).toList();

    private final String label;

    BoundsMode(String label) {
        this.label = label;
    }

    /**
     * The mode a profile or an operator names.
     *
     * @throws BadArgumentError naming every mode when the value is none of them
     */
    public static BoundsMode parse(String value) {
        return values()[LABELS.indexOf(Guards.requireOneOf(value, LABELS, "mode"))];
    }

    /** The name a profile writes, such as {@code clamp}. */
    public String label() {
        return label;
    }

    /** The {@linkplain #label() label}, which is how a mode is written in json. */
    @Override
    public String toJson() {
        return label;
    }

    /** The {@linkplain #label() label}, which is how a mode reads in a message. */
    @Override
    public String toString() {
        return label;
    }
}
