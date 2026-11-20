package io.github.rkaushikdev.decklewright.util;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Argument guards.
 *
 * <p>Every public entry point in the tree runs its inputs through these so a bad batch fails at
 * the call that made the mistake rather than four stages later in a resampler. Each guard throws a
 * {@link BadArgumentError} whose message names the argument and whose details carry the name and
 * the offending value.
 *
 * <p>The guards taking {@code Object} are for values whose type is not known until run time, such
 * as a field read from a curator's json profile: they check the type as well as the value.
 */
public final class Guards {
    private Guards() {}

    /**
     * Throws when the condition does not hold.
     *
     * @throws BadArgumentError carrying the message and no details
     */
    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new BadArgumentError(message);
        }
    }

    /**
     * Throws when the condition does not hold.
     *
     * @throws BadArgumentError carrying the message and a copy of the details
     */
    public static void require(boolean condition, String message, Map<String, ?> details) {
        if (!condition) {
            throw new BadArgumentError(message, details);
        }
    }

    /** The value, when it is neither infinite nor not a number. */
    public static double requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw failure(name + " must be a finite number", name, value);
        }
        return value;
    }

    /** The value as a double, when it is a {@link Number} that is neither infinite nor not a number. */
    public static double requireFinite(Object value, String name) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw failure(name + " must be a finite number", name, value);
        }
        return number.doubleValue();
    }

    /** The value, when it is a finite whole number. Zero and negatives are whole numbers. */
    public static double requireInteger(double value, String name) {
        requireFinite(value, name);
        if (!isWhole(value)) {
            throw failure(name + " must be a whole number", name, value);
        }
        return value;
    }

    /** The value as a double, when it is a {@link Number} holding a finite whole number. */
    public static double requireInteger(Object value, String name) {
        double n = requireFinite(value, name);
        if (!isWhole(n)) {
            throw failure(name + " must be a whole number", name, value);
        }
        return n;
    }

    /** The value, when it is finite and strictly above zero. */
    public static double requirePositive(double value, String name) {
        requireFinite(value, name);
        if (!(value > 0)) {
            throw failure(name + " must be greater than zero", name, value);
        }
        return value;
    }

    /** The value as a double, when it is a finite {@link Number} strictly above zero. */
    public static double requirePositive(Object value, String name) {
        double n = requireFinite(value, name);
        if (!(n > 0)) {
            throw failure(name + " must be greater than zero", name, value);
        }
        return n;
    }

    /** The value, when it is finite and not below zero. */
    public static double requireNonNegative(double value, String name) {
        requireFinite(value, name);
        if (value < 0) {
            throw failure(name + " must not be negative", name, value);
        }
        return value;
    }

    /** The value as a double, when it is a finite {@link Number} not below zero. */
    public static double requireNonNegative(Object value, String name) {
        double n = requireFinite(value, name);
        if (n < 0) {
            throw failure(name + " must not be negative", name, value);
        }
        return n;
    }

    /** The value, when it is above zero. */
    public static int requirePositiveInteger(int value, String name) {
        if (value <= 0) {
            throw failure(name + " must be a whole number above zero", name, value);
        }
        return value;
    }

    /** The value, when it is a finite whole number above zero. */
    public static double requirePositiveInteger(double value, String name) {
        requireInteger(value, name);
        if (value <= 0) {
            throw failure(name + " must be a whole number above zero", name, value);
        }
        return value;
    }

    /** The value as a double, when it is a {@link Number} holding a whole number above zero. */
    public static double requirePositiveInteger(Object value, String name) {
        double n = requireInteger(value, name);
        if (n <= 0) {
            throw failure(name + " must be a whole number above zero", name, value);
        }
        return n;
    }

    /**
     * The value, when it lies between the bounds. Inclusive on both ends, because every range in
     * the lab is quoted that way.
     */
    public static double requireInRange(double value, double low, double high, String name) {
        requireFinite(value, name);
        if (value < low || value > high) {
            throw rangeFailure(name, value, low, high);
        }
        return value;
    }

    /** The value as a double, when it is a finite {@link Number} between the bounds inclusive. */
    public static double requireInRange(Object value, double low, double high, String name) {
        double n = requireFinite(value, name);
        if (n < low || n > high) {
            throw rangeFailure(name, value, low, high);
        }
        return n;
    }

    /**
     * The member of {@code allowed} equal to the value.
     *
     * @throws BadArgumentError listing what was allowed when nothing matches
     */
    public static <T> T requireOneOf(Object value, List<T> allowed, String name) {
        for (T member : allowed) {
            if (member.equals(value)) {
                return member;
            }
        }
        String listed = allowed.stream().map(Guards::text).collect(Collectors.joining(", "));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("name", name);
        details.put("value", value);
        details.put("allowed", new ArrayList<>(allowed));
        throw new BadArgumentError(name + " must be one of " + listed, details);
    }

    /** The value, when it is a string with at least one character. */
    public static String requireNonEmptyString(Object value, String name) {
        if (!(value instanceof String text) || text.isEmpty()) {
            throw failure(name + " must be a non empty string", name, value);
        }
        return text;
    }

    /**
     * The value, when it is a plain bag of named values: a {@link Map} whose keys are all strings.
     *
     * <p>Profiles and recipes are plain data and anything else at those seams, such as a list, a
     * date or a bare string, is a mistake worth catching early.
     */
    public static Map<String, Object> requirePlainObject(Object value, String name) {
        if (!(value instanceof Map<?, ?> map) || !map.keySet().stream().allMatch(String.class::isInstance)) {
            throw new BadArgumentError(name + " must be a plain object", Map.of("name", name));
        }
        @SuppressWarnings("unchecked") // every key was checked to be a string just above
        Map<String, Object> bag = (Map<String, Object>) map;
        return bag;
    }

    /**
     * The members of a list, each run through {@code check} with its index.
     *
     * <p>A member that fails its check surfaces with its own message.
     *
     * @return a new list of what {@code check} returned
     * @throws BadArgumentError when the value is not a list at all
     */
    public static <T> List<T> requireListOf(Object value, BiFunction<Object, Integer, T> check, String name) {
        if (!(value instanceof List<?> items)) {
            throw new BadArgumentError(name + " must be an array", Map.of("name", name));
        }
        List<T> out = new ArrayList<>(items.size());
        for (int index = 0; index < items.size(); index += 1) {
            out.add(check.apply(items.get(index), index));
        }
        return out;
    }

    private static boolean isWhole(double value) {
        return Double.isFinite(value) && value == Math.rint(value);
    }

    private static BadArgumentError failure(String message, String name, Object value) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("name", name);
        details.put("value", value);
        return new BadArgumentError(message, details);
    }

    private static BadArgumentError rangeFailure(String name, Object value, double low, double high) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("name", name);
        details.put("value", value);
        details.put("low", low);
        details.put("high", high);
        return new BadArgumentError(
                name + " must be between " + NumberText.of(low) + " and " + NumberText.of(high), details);
    }

    private static String text(Object value) {
        return value instanceof Number number ? NumberText.of(number) : String.valueOf(value);
    }
}
