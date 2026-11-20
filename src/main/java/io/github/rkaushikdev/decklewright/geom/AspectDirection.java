package io.github.rkaushikdev.decklewright.geom;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.Arrays;
import java.util.List;

/**
 * Which axis gives way when a crop is forced to an aspect. Growing is the safe direction: a crop
 * that grows takes in more paper, and a crop that shrinks throws image away.
 *
 * @see Fitting#forceAspect(Rect, double, AspectDirection)
 */
public enum AspectDirection implements JsonValue {
    /** The short axis grows to match. */
    GROW("grow"),
    /** The long axis shrinks to match. */
    SHRINK("shrink");

    /** Every label, in declaration order. */
    private static final List<String> LABELS =
            Arrays.stream(values()).map(AspectDirection::label).toList();

    private final String label;

    AspectDirection(String label) {
        this.label = label;
    }

    /**
     * The direction a profile names.
     *
     * @throws BadArgumentError naming every direction when the value is none of them
     */
    public static AspectDirection parse(String value) {
        return values()[LABELS.indexOf(Guards.requireOneOf(value, LABELS, "direction"))];
    }

    /** The name a profile writes, such as {@code grow}. */
    public String label() {
        return label;
    }

    /** The {@linkplain #label() label}, which is how a direction is written in json. */
    @Override
    public String toJson() {
        return label;
    }

    /** The {@linkplain #label() label}, which is how a direction reads in a message. */
    @Override
    public String toString() {
        return label;
    }
}
