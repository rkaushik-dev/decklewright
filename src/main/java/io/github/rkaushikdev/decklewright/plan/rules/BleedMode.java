package io.github.rkaushikdev.decklewright.plan.rules;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.Arrays;
import java.util.List;

/**
 * How far a bleed may reach.
 *
 * @see BleedRules#bleedFor(io.github.rkaushikdev.decklewright.geom.Insets, BleedMode,
 *     io.github.rkaushikdev.decklewright.geom.Rect)
 */
public enum BleedMode implements JsonValue {
    /** As far as it asks, off the capture if need be. */
    PLAIN("plain"),
    /** Trimmed at the edge of the capture. */
    TRIMMED("trimmed"),
    /** Held inside the paper edge. */
    WITHIN_SHEET("withinSheet");

    /** Every label, in declaration order. */
    private static final List<String> LABELS =
            Arrays.stream(values()).map(BleedMode::label).toList();

    private final String label;

    BleedMode(String label) {
        this.label = label;
    }

    /**
     * The mode a caller names.
     *
     * @throws BadArgumentError naming every mode when the value is none of them
     */
    public static BleedMode parse(String value) {
        return values()[LABELS.indexOf(Guards.requireOneOf(value, LABELS, "mode"))];
    }

    /** The name a caller writes, such as {@code withinSheet}. */
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
