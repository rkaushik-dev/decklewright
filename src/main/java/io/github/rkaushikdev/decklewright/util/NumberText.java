package io.github.rkaushikdev.decklewright.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Numbers written the way the lab's reports have always written them.
 *
 * <p>Every message, sidecar and report in this tree prints numbers in one fixed shape: the
 * shortest decimal that reads back as the same double, with no trailing {@code .0} on a whole
 * number, plain notation from a millionth up to {@code 1e21}, and exponent notation such as
 * {@code 1e-7} or {@code 1.5e+21} outside that. {@link Double#toString(double)} prints {@code 3.0}
 * and switches to exponents at {@code 1e7}, which would change every report, so nothing in the tree
 * uses it for output.
 *
 * <p>{@link #fixed(double, int)} rounds the exact binary value half away from zero, which is what
 * a fixed-point figure in the reports has always meant, and {@link #parse(String)} reads a typed
 * figure back the way the lab's tools always have.
 */
public final class NumberText {
    private static final Pattern DECIMAL_TEXT =
            Pattern.compile("[+-]?(?:[0-9]+\\.?[0-9]*|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");
    private static final Pattern INFINITY_TEXT = Pattern.compile("([+-]?)Infinity");
    private static final Pattern RADIX_TEXT = Pattern.compile("0([xXoObB])([0-9a-fA-F]+)");

    private NumberText() {}

    /**
     * The shortest round-tripping decimal for the value, as reports print it.
     *
     * @return {@code "NaN"}, {@code "Infinity"} or {@code "-Infinity"} for those values, {@code "0"}
     *     for both zeros, otherwise digits such as {@code "3"}, {@code "0.25"}, {@code "1e-7"}
     */
    public static String of(double value) {
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (value == 0) {
            return "0";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "Infinity" : "-Infinity";
        }
        String sign = value < 0 ? "-" : "";
        BigDecimal shortest = shortest(Math.abs(value));
        String digits = shortest.unscaledValue().toString();
        int k = digits.length();
        // value = 0.digits * 10^n
        int n = k - shortest.scale();
        StringBuilder out = new StringBuilder(sign);
        if (k <= n && n <= 21) {
            out.append(digits).append("0".repeat(n - k));
        } else if (0 < n && n <= 21) {
            out.append(digits, 0, n).append('.').append(digits, n, k);
        } else if (-6 < n && n <= 0) {
            out.append("0.").append("0".repeat(-n)).append(digits);
        } else {
            int exponent = n - 1;
            out.append(digits.charAt(0));
            if (k > 1) {
                out.append('.').append(digits, 1, k);
            }
            out.append('e').append(exponent < 0 ? '-' : '+').append(Math.abs(exponent));
        }
        return out.toString();
    }

    /** A whole number, written without any decimal point. */
    public static String of(long value) {
        return Long.toString(value);
    }

    /**
     * A number given in any boxed form, written as {@link #of(double)} would write it. Integral box
     * types are written exactly.
     */
    public static String of(Number value) {
        return switch (value) {
            case Long l -> Long.toString(l);
            case Integer i -> Integer.toString(i);
            case Short s -> Short.toString(s);
            case Byte b -> Byte.toString(b);
            default -> of(value.doubleValue());
        };
    }

    /**
     * The value with exactly {@code digits} places after the point.
     *
     * <p>The exact binary value is rounded, half away from zero, so {@code fixed(1.005, 2)} is
     * {@code "1.00"} because the double nearest 1.005 is a hair below it. Magnitudes of {@code 1e21}
     * and above are written as {@link #of(double)} writes them.
     *
     * @param digits places after the point, zero through one hundred
     * @throws IllegalArgumentException when {@code digits} is out of that range
     */
    public static String fixed(double value, int digits) {
        if (digits < 0 || digits > 100) {
            throw new IllegalArgumentException("digits must be between 0 and 100");
        }
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (Math.abs(value) >= 1e21 || Double.isInfinite(value)) {
            return of(value);
        }
        String sign = value < 0 ? "-" : "";
        BigDecimal rounded = new BigDecimal(Math.abs(value)).setScale(digits, RoundingMode.HALF_UP);
        return sign + rounded.toPlainString();
    }

    /**
     * The number a piece of text spells, read the way the lab's tools have always read a typed
     * figure, or not a number when it spells none.
     *
     * <p>Blank space at either end is ignored ({@link Whitespace#trim(String)}), and blank text is
     * zero. A decimal may carry a sign, may start or end with its point ({@code .5}, {@code 5.}),
     * and may have an exponent; {@code Infinity} with an optional sign is infinite. Hexadecimal,
     * octal and binary integers are written {@code 0x1f}, {@code 0o17} and {@code 0b101}, with no
     * sign. Anything else, including digit separators, a trailing unit, {@code NaN} or Java's
     * {@code 1d}, is not a number.
     */
    public static double parse(String text) {
        String trimmed = Whitespace.trim(text);
        if (trimmed.isEmpty()) {
            return 0;
        }
        if (DECIMAL_TEXT.matcher(trimmed).matches()) {
            return Double.parseDouble(trimmed);
        }
        Matcher infinity = INFINITY_TEXT.matcher(trimmed);
        if (infinity.matches()) {
            return "-".equals(infinity.group(1)) ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        }
        Matcher radix = RADIX_TEXT.matcher(trimmed);
        if (radix.matches()) {
            int base =
                    switch (Character.toLowerCase(radix.group(1).charAt(0))) {
                        case 'x' -> 16;
                        case 'o' -> 8;
                        default -> 2;
                    };
            String digits = radix.group(2);
            if (digits.chars().allMatch(ch -> Character.digit(ch, base) >= 0)) {
                return new BigInteger(digits, base).doubleValue();
            }
        }
        return Double.NaN;
    }

    /**
     * The shortest decimal that reads back as {@code magnitude}, closest to it when several are
     * equally short. {@link Double#toString(double)} already finds that, except that it never
     * writes fewer than two significant digits, so a one digit answer is looked for separately.
     */
    private static BigDecimal shortest(double magnitude) {
        BigDecimal candidate = new BigDecimal(Double.toString(magnitude)).stripTrailingZeros();
        if (candidate.precision() != 2) {
            return candidate;
        }
        BigDecimal exact = new BigDecimal(magnitude);
        BigDecimal down = exact.round(new MathContext(1, RoundingMode.FLOOR));
        BigDecimal up = exact.round(new MathContext(1, RoundingMode.CEILING));
        boolean downReads = down.doubleValue() == magnitude;
        boolean upReads = up.doubleValue() == magnitude;
        if (downReads && upReads) {
            int order = exact.subtract(down).compareTo(up.subtract(exact));
            if (order == 0) {
                return evenDigit(down) ? down.stripTrailingZeros() : up.stripTrailingZeros();
            }
            return (order < 0 ? down : up).stripTrailingZeros();
        }
        if (downReads) {
            return down.stripTrailingZeros();
        }
        if (upReads) {
            return up.stripTrailingZeros();
        }
        return candidate;
    }

    private static boolean evenDigit(BigDecimal oneDigit) {
        return !oneDigit.unscaledValue().testBit(0);
    }
}
