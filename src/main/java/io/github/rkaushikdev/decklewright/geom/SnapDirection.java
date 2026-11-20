package io.github.rkaushikdev.decklewright.geom;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import io.github.rkaushikdev.decklewright.util.Num;
import java.util.Arrays;
import java.util.List;
import java.util.function.DoubleBinaryOperator;

/**
 * Which way an extent moves when it is snapped onto a multiple.
 *
 * @see Rounding#snapSize(Size, double, SnapDirection)
 */
public enum SnapDirection implements JsonValue {
    /** To the nearest multiple, exact halves to the even one. */
    NEAREST("nearest", Num::roundToMultiple),
    /** To the multiple at or above. */
    UP("up", Num::ceilToMultiple),
    /** To the multiple at or below. */
    DOWN("down", Num::floorToMultiple);

    /** Every label, in declaration order. */
    private static final List<String> LABELS =
            Arrays.stream(values()).map(SnapDirection::label).toList();

    private final String label;
    private final DoubleBinaryOperator snap;

    SnapDirection(String label, DoubleBinaryOperator snap) {
        this.label = label;
        this.snap = snap;
    }

    /**
     * The direction a profile names.
     *
     * @throws BadArgumentError naming every direction when the value is none of them
     */
    public static SnapDirection parse(String value) {
        return values()[LABELS.indexOf(Guards.requireOneOf(value, LABELS, "direction"))];
    }

    /** The name a profile writes, such as {@code up}. */
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

    /** The extent moved onto a multiple of the step this way. */
    double snap(double extent, double multiple) {
        return snap.applyAsDouble(extent, multiple);
    }
}
