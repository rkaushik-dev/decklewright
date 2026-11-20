package io.github.rkaushikdev.decklewright.geom;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import io.github.rkaushikdev.decklewright.util.Num;
import java.util.Arrays;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

/**
 * Which way a planned crop is turned into whole pixels. Which way it rounds is a collection
 * decision, not an implementation detail, so a profile names one of these.
 *
 * @see Rounding#roundRect(Rect, RoundPolicy)
 */
public enum RoundPolicy implements JsonValue {
    /** Every edge to the nearest whole pixel, exact halves to the even one. */
    NEAREST("nearest", Num::roundHalfEven, Num::roundHalfEven),
    /** The left and top edges down and the right and bottom up, so the crop only ever grows. */
    OUTWARD("outward", Math::floor, Math::ceil),
    /** The left and top edges up and the right and bottom down, so the crop only ever shrinks. */
    INWARD("inward", Math::ceil, Math::floor),
    /** Every edge down. */
    FLOOR("floor", Math::floor, Math::floor);

    /** Every label, in declaration order. */
    private static final List<String> LABELS =
            Arrays.stream(values()).map(RoundPolicy::label).toList();

    private final String label;
    private final DoubleUnaryOperator low;
    private final DoubleUnaryOperator high;

    RoundPolicy(String label, DoubleUnaryOperator low, DoubleUnaryOperator high) {
        this.label = label;
        this.low = low;
        this.high = high;
    }

    /**
     * The policy a profile names.
     *
     * @throws BadArgumentError naming every policy when the value is none of them
     */
    public static RoundPolicy parse(String value) {
        return values()[LABELS.indexOf(Guards.requireOneOf(value, LABELS, "policy"))];
    }

    /** The name a profile writes, such as {@code outward}. */
    public String label() {
        return label;
    }

    /** The {@linkplain #label() label}, which is how a policy is written in json. */
    @Override
    public String toJson() {
        return label;
    }

    /** The {@linkplain #label() label}, which is how a policy reads in a message. */
    @Override
    public String toString() {
        return label;
    }

    /** The left or top edge, rounded. */
    double low(double edge) {
        return low.applyAsDouble(edge);
    }

    /** The right or bottom edge, rounded. */
    double high(double edge) {
        return high.applyAsDouble(edge);
    }
}
