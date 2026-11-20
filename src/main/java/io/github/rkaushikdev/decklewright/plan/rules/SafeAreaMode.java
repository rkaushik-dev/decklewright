package io.github.rkaushikdev.decklewright.plan.rules;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.Arrays;
import java.util.List;

/**
 * Which edges a safe area pulls the crop in on.
 *
 * @see SafeAreaRules#safeAreaFor(io.github.rkaushikdev.decklewright.geom.Insets, SafeAreaMode)
 */
public enum SafeAreaMode implements JsonValue {
    /** Every edge, so the crop never leaves the area inside the anchor. */
    WHOLE("whole"),
    /** Only the edges the safe area names, leaving the rest wherever the bleed put them. */
    EDGES("edges");

    /** Every label, in declaration order. */
    private static final List<String> LABELS =
            Arrays.stream(values()).map(SafeAreaMode::label).toList();

    private final String label;

    SafeAreaMode(String label) {
        this.label = label;
    }

    /**
     * The mode a caller names.
     *
     * @throws BadArgumentError naming both modes when the value is neither of them
     */
    public static SafeAreaMode parse(String value) {
        return values()[LABELS.indexOf(Guards.requireOneOf(value, LABELS, "mode"))];
    }

    /** The name a caller writes, such as {@code edges}. */
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
