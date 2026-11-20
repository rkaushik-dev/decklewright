package io.github.rkaushikdev.decklewright.measure;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import io.github.rkaushikdev.decklewright.util.Num;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An asymmetric tolerance band around a target.
 *
 * <p>Nearly every figure the funder cares about is quoted as a target with a band around it, and
 * the band is often lopsided. Sampling frequency may go a little high but never low. Skew may go
 * either way by the same amount. Keeping the two sides apart means a check can say which side it
 * failed on, which is the first thing an operator asks.
 *
 * <p>Two bands are {@linkplain #equals(Object) equal} when all three fields are; a side of minus zero
 * equals a side of zero.
 *
 * @param minus how far below the target is still acceptable, never negative
 * @param plus how far above the target is still acceptable, never negative
 * @param kind whether the sides are absolute amounts or shares of the target
 */
public record Tolerance(double minus, double plus, ToleranceKind kind) implements JsonValue {

    /** {@code +3/-1}, or {@code +10/-5%} with the percent sign once, after the minus side. */
    private static final Pattern TWO_SIDED =
            Pattern.compile("\\+([0-9.]+)" + Whitespace.CLASS + "*/" + Whitespace.CLASS + "*-([0-9.]+)(%?)");

    /** {@code 0.5}, {@code +2}, {@code -2} or {@code 5%}. */
    private static final Pattern ONE_FIGURE = Pattern.compile("([+-]?)([0-9.]+)(%?)");

    /** A run of digits and points that spells a decimal, which {@code 1.2.3} does not. */
    private static final Pattern DECIMAL = Pattern.compile("\\d+\\.?\\d*|\\.\\d+");

    /**
     * A band of the given sides and kind.
     *
     * @throws BadArgumentError when a side is negative or not finite
     */
    public Tolerance {
        Guards.requireNonNegative(minus, "minus");
        Guards.requireNonNegative(plus, "plus");
        Objects.requireNonNull(kind, "kind");
    }

    /**
     * An absolute band of the given sides.
     *
     * @throws BadArgumentError when a side is negative or not finite
     */
    public Tolerance(double minus, double plus) {
        this(minus, plus, ToleranceKind.ABSOLUTE);
    }

    /** An absolute band the same amount either side. */
    public static Tolerance symmetric(double amount) {
        return symmetric(amount, ToleranceKind.ABSOLUTE);
    }

    /** A band the same amount either side. */
    public static Tolerance symmetric(double amount, ToleranceKind kind) {
        return new Tolerance(amount, amount, kind);
    }

    /** A relative band the same percentage either side, so {@code percent(5)} is five percent. */
    public static Tolerance percent(double percent) {
        return new Tolerance(percent / 100, percent / 100, ToleranceKind.RELATIVE);
    }

    /** A band that admits only the target itself. */
    public static Tolerance exact() {
        return new Tolerance(0, 0, ToleranceKind.ABSOLUTE);
    }

    /** An absolute band allowing the given amount above the target and nothing below. */
    public static Tolerance upOnly(double amount) {
        return new Tolerance(0, amount, ToleranceKind.ABSOLUTE);
    }

    /** An absolute band allowing the given amount below the target and nothing above. */
    public static Tolerance downOnly(double amount) {
        return new Tolerance(amount, 0, ToleranceKind.ABSOLUTE);
    }

    /**
     * Reads what a profile writes. Accepts a bare number as symmetric absolute ({@code 0.5}), a
     * percentage ({@code 5%}), a plus or minus form ({@code +2}, {@code -2}), and the two sided form
     * ({@code +3/-1} or {@code +10/-5%}). Blank space around the whole is ignored.
     *
     * @throws BadArgumentError when the text is empty or none of those
     */
    public static Tolerance parse(String text) {
        Guards.requireNonEmptyString(text, "text");
        String trimmed = Whitespace.trim(text);

        Matcher both = TWO_SIDED.matcher(trimmed);
        if (both.matches()) {
            ToleranceKind kind = both.group(3).equals("%") ? ToleranceKind.RELATIVE : ToleranceKind.ABSOLUTE;
            double scale = kind == ToleranceKind.RELATIVE ? 100 : 1;
            return new Tolerance(decimal(both.group(2)) / scale, decimal(both.group(1)) / scale, kind);
        }

        Matcher single = ONE_FIGURE.matcher(trimmed);
        if (single.matches()) {
            double value = decimal(single.group(2));
            ToleranceKind kind = single.group(3).equals("%") ? ToleranceKind.RELATIVE : ToleranceKind.ABSOLUTE;
            double amount = kind == ToleranceKind.RELATIVE ? value / 100 : value;
            return switch (single.group(1)) {
                case "+" -> new Tolerance(0, amount, kind);
                case "-" -> new Tolerance(amount, 0, kind);
                default -> new Tolerance(amount, amount, kind);
            };
        }

        throw new BadArgumentError("cannot read " + text + " as a tolerance", Map.of("text", text));
    }

    /** True when the band reaches as far above the target as below it. */
    public boolean isSymmetric() {
        return minus == plus;
    }

    /** True when the band admits only the target itself. */
    public boolean isExact() {
        return minus == 0 && plus == 0;
    }

    /**
     * The lowest and highest acceptable values for the target. A relative band scales with the
     * magnitude of the target, so it does not turn inside out for a negative one and collapses onto
     * a target of zero.
     */
    public Bounds boundsFor(double target) {
        if (kind == ToleranceKind.RELATIVE) {
            double scale = Math.abs(target);
            return new Bounds(target - scale * minus, target + scale * plus);
        }
        return new Bounds(target - minus, target + plus);
    }

    /** True when the value lies in the band around the target, edges included. */
    public boolean contains(double target, double value) {
        Bounds bounds = boundsFor(target);
        return value >= bounds.low() && value <= bounds.high();
    }

    /**
     * How far outside the band the value sits, signed the way it failed: negative below the band,
     * positive above it, and zero inside, so a report can sort by this and put the worst first.
     */
    public double excursion(double target, double value) {
        Bounds bounds = boundsFor(target);
        if (value < bounds.low()) {
            return value - bounds.low();
        }
        if (value > bounds.high()) {
            return value - bounds.high();
        }
        return 0;
    }

    /** The value held inside the band around the target. */
    public double clampInto(double target, double value) {
        Bounds bounds = boundsFor(target);
        if (value < bounds.low()) {
            return bounds.low();
        }
        if (value > bounds.high()) {
            return bounds.high();
        }
        return value;
    }

    /**
     * Both sides multiplied by the factor, keeping the kind.
     *
     * @throws BadArgumentError when the factor is negative or not finite
     */
    public Tolerance widened(double factor) {
        Guards.requireNonNegative(factor, "factor");
        return new Tolerance(minus * factor, plus * factor, kind);
    }

    /** True when the other is a band with the same sides and kind. */
    @Override
    public boolean equals(Object other) {
        return other instanceof Tolerance that && minus == that.minus && plus == that.plus && kind == that.kind;
    }

    /** A hash that agrees with {@link #equals(Object)}, so minus zero hashes as zero. */
    @Override
    public int hashCode() {
        return Objects.hash(minus + 0.0, plus + 0.0, kind);
    }

    /** {@code {minus, plus, kind}}, in a new map the caller may keep, with the kind as its label. */
    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("minus", minus);
        json.put("plus", plus);
        json.put("kind", kind.label());
        return json;
    }

    /**
     * The band the way a profile writes it, to four places: {@code +/-0.5} when symmetric, {@code
     * +3/-1} when not, and in percent with a percent sign on each figure for a relative band, such
     * as {@code +10%/-5%}.
     */
    @Override
    public String toString() {
        String suffix = kind == ToleranceKind.RELATIVE ? "%" : "";
        double scale = kind == ToleranceKind.RELATIVE ? 100 : 1;
        String below = NumberText.of(Num.roundTo(minus * scale, 4));
        String above = NumberText.of(Num.roundTo(plus * scale, 4));
        if (isSymmetric()) {
            return "+/-" + below + suffix;
        }
        return "+" + above + suffix + "/-" + below + suffix;
    }

    /**
     * Reads the shapes a profile may hold a tolerance in: a band, which comes back as it is, a bare
     * number as a symmetric absolute band, the string a curator typed, and a map with {@code minus}
     * and {@code plus} entries and an optional {@code kind}, absolute when it is missing or null.
     *
     * @throws BadArgumentError when the value is none of those, or a side or the kind in it is wrong
     */
    public static Tolerance toTolerance(Object value) {
        if (value instanceof Tolerance tolerance) {
            return tolerance;
        }
        if (value instanceof Number amount) {
            return symmetric(amount.doubleValue());
        }
        if (value instanceof String text) {
            return parse(text);
        }
        if (value instanceof Map<?, ?> bag && bag.containsKey("minus") && bag.containsKey("plus")) {
            double minus = Guards.requireNonNegative(bag.get("minus"), "minus");
            double plus = Guards.requireNonNegative(bag.get("plus"), "plus");
            Object kind = bag.get("kind");
            return new Tolerance(minus, plus, kind == null ? ToleranceKind.ABSOLUTE : ToleranceKind.fromJson(kind));
        }
        throw new BadArgumentError("cannot read a tolerance from that", Collections.singletonMap("value", value));
    }

    /**
     * What a run of digits and points spells as a number: the decimal, or not a number when it
     * spells none, so that the band refuses it as not finite.
     */
    private static double decimal(String digits) {
        return DECIMAL.matcher(digits).matches() ? Double.parseDouble(digits) : Double.NaN;
    }

    /**
     * The edges of a band around one target, both acceptable.
     *
     * @param low the lowest acceptable value
     * @param high the highest acceptable value
     */
    public record Bounds(double low, double high) {}
}
