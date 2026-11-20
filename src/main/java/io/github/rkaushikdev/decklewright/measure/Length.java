package io.github.rkaushikdev.decklewright.measure;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import io.github.rkaushikdev.decklewright.util.Num;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A length that remembers what unit it was written in.
 *
 * <p>Profiles are written by curators in whatever unit the collection has always used, so a bleed of
 * 3 mm and a safe margin of 0.25 in end up in the same rule set. Carrying the unit means the report
 * can quote the figure back the way it was entered instead of turning everything into inches and
 * confusing people.
 *
 * <p>{@link #equals(Object)} is exact and unit sensitive, so 1 in and 25.4 mm are not equal under
 * it; {@link #approxEquals(Length)} and {@link #compare(Length)} compare across units. Lengths are
 * deliberately not {@link Comparable}: their comparison treats lengths a hair apart as the same,
 * which is not a total order.
 *
 * @param value the figure as written, which may be negative
 * @param unit the unit it was written in
 */
public record Length(double value, Unit unit) implements JsonValue {

    /** Two lengths whose inches are this close are the same length. */
    private static final double SAME_INCHES = 1e-12;

    /**
     * A length of the given figure and unit.
     *
     * @throws BadArgumentError when the value is not finite
     */
    public Length {
        Guards.requireFinite(value, "value");
        Objects.requireNonNull(unit, "unit");
    }

    /**
     * A length of the given figure and unit.
     *
     * @throws BadArgumentError when the value is not finite
     */
    public static Length of(double value, Unit unit) {
        return new Length(value, unit);
    }

    /**
     * Reads the string an operator types, such as {@code 3mm} or {@code 0.25 in}.
     *
     * @throws BadArgumentError as {@link Units#parseLength(String)} does
     */
    public static Length parse(String text) {
        return Units.parseLength(text);
    }

    /** Zero millimetres. */
    public static Length zero() {
        return zero(Unit.MILLIMETRE);
    }

    /**
     * Zero in the given unit. Zero is the one value that does not need a unit to be meaningful, but
     * it still gets one so the arithmetic never has a special case.
     */
    public static Length zero(Unit unit) {
        return new Length(0, unit);
    }

    /** The length in inches. */
    public double inches() {
        return Units.toInches(value, unit);
    }

    /** True when the figure is zero. */
    public boolean isZero() {
        return value == 0;
    }

    /** True when the figure is below zero. */
    public boolean isNegative() {
        return value < 0;
    }

    /** The same length written in the given unit; this same length when it already is. */
    public Length to(Unit unit) {
        Objects.requireNonNull(unit, "unit");
        if (unit == this.unit) {
            return this;
        }
        return new Length(Units.convert(value, this.unit, unit), unit);
    }

    /**
     * The sum, in this length's unit. The left operand keeps its unit, because in a rule the left
     * operand is the thing the curator wrote and the right operand is usually a correction.
     *
     * @throws BadArgumentError when the other length is null
     */
    public Length plus(Length other) {
        requireLength(other, "other");
        return new Length(value + other.to(unit).value, unit);
    }

    /**
     * The difference, in this length's unit, which can go negative.
     *
     * @throws BadArgumentError when the other length is null
     */
    public Length minus(Length other) {
        requireLength(other, "other");
        return new Length(value - other.to(unit).value, unit);
    }

    /**
     * This length scaled by a plain factor, in the same unit.
     *
     * @throws BadArgumentError when the factor is not finite
     */
    public Length times(double factor) {
        Guards.requireFinite(factor, "factor");
        return new Length(value * factor, unit);
    }

    /**
     * How many of the other length fit in this one, across units.
     *
     * @throws BadArgumentError when the other length is null or zero
     */
    public double over(Length other) {
        requireLength(other, "other");
        if (other.inches() == 0) {
            throw new BadArgumentError("cannot divide a length by zero", Map.of("other", other.toString()));
        }
        return inches() / other.inches();
    }

    /** The magnitude; this same length when it is not negative. */
    public Length abs() {
        return value < 0 ? new Length(-value, unit) : this;
    }

    /** The length with its sign turned over, in the same unit. */
    public Length negated() {
        return new Length(-value, unit);
    }

    /**
     * Orders two lengths across units. Lengths within a trillionth of an inch of each other are the
     * same.
     *
     * @return -1, 0 or 1 as this length is shorter than, the same as or longer than the other
     * @throws BadArgumentError when the other length is null
     */
    public int compare(Length other) {
        requireLength(other, "other");
        double a = inches();
        double b = other.inches();
        if (Num.approxEqual(a, b, SAME_INCHES)) {
            return 0;
        }
        return a < b ? -1 : 1;
    }

    /** True when the other is the same length in any unit, as {@link #compare(Length)} has it; never for null. */
    public boolean approxEquals(Length other) {
        return other != null && compare(other) == 0;
    }

    /**
     * True when the other is no further from this length than the tolerance, in any units; never for
     * null. The tolerance is a magnitude, so its sign does not matter.
     */
    public boolean approxEquals(Length other, Length tolerance) {
        if (other == null) {
            return false;
        }
        Objects.requireNonNull(tolerance, "tolerance");
        return Math.abs(inches() - other.inches()) <= Math.abs(tolerance.inches());
    }

    /** {@code {value, unit}}, in a new map the caller may keep, with the unit as its label. */
    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("value", value);
        json.put("unit", unit.label());
        return json;
    }

    /** The figure at the report precision of its own unit, such as {@code 3.14 mm}. */
    @Override
    public String toString() {
        return Units.formatLength(value, unit);
    }

    /**
     * The value, when it is a length.
     *
     * @param name what the value is, for the message, such as {@code bleed}
     * @throws BadArgumentError saying {@code <name> must be a Length} otherwise, null included
     */
    public static Length requireLength(Object value, String name) {
        if (!(value instanceof Length length)) {
            throw new BadArgumentError(name + " must be a Length", Map.of("name", name));
        }
        return length;
    }

    /**
     * Reads the three shapes a profile is allowed to write a length in: a length, which comes back as
     * it is, the string an operator typed, and the {@code {value, unit}} pair a json file holds.
     *
     * @throws BadArgumentError when the value is none of those, when the pair's unit is not one of the
     *     labels, or when its value is not a finite number
     */
    public static Length toLength(Object value) {
        if (value instanceof Length length) {
            return length;
        }
        if (value instanceof String text) {
            return parse(text);
        }
        if (value instanceof Map<?, ?> bag && bag.containsKey("value") && bag.containsKey("unit")) {
            // The unit is read before the figure, so a pair with both wrong reports its unit.
            Unit unit = Unit.fromJson(bag.get("unit"));
            return new Length(Guards.requireFinite(bag.get("value"), "value"), unit);
        }
        throw new BadArgumentError("cannot read a length from that", Collections.singletonMap("value", value));
    }

    /**
     * The longest of the lengths, across units; the first of them when several are the same.
     *
     * @throws BadArgumentError when there are none
     */
    public static Length maxLength(List<Length> lengths) {
        requireSome(lengths);
        Length best = lengths.get(0);
        for (Length item : lengths) {
            if (item.compare(best) > 0) {
                best = item;
            }
        }
        return best;
    }

    /**
     * The shortest of the lengths, across units; the first of them when several are the same.
     *
     * @throws BadArgumentError when there are none
     */
    public static Length minLength(List<Length> lengths) {
        requireSome(lengths);
        Length best = lengths.get(0);
        for (Length item : lengths) {
            if (item.compare(best) < 0) {
                best = item;
            }
        }
        return best;
    }

    private static void requireSome(List<Length> lengths) {
        if (lengths.isEmpty()) {
            throw new BadArgumentError("no lengths to compare", Map.of());
        }
    }
}
