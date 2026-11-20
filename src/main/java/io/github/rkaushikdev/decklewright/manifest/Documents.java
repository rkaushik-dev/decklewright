package io.github.rkaushikdev.decklewright.manifest;

import io.github.rkaushikdev.decklewright.util.Json;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Reading the plain data a written document holds: a sidecar or a provenance chain that came off a
 * disk, perhaps edited by hand, perhaps by a later version.
 *
 * <p>Nothing here refuses anything. A reader that wants a list and finds something else sees an
 * empty list; one that wants an object sees nothing in it. The callers decide what is worth an
 * objection, and they say it in words.
 */
final class Documents {
    private static final byte[] BYTE_ORDER_MARK = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private Documents() {}

    /**
     * The bytes as UTF-8 text, with one leading byte order mark dropped, as a text editor that
     * added it would expect. A malformed sequence reads as U+FFFD.
     */
    static String decode(byte[] bytes) {
        boolean marked = bytes.length >= BYTE_ORDER_MARK.length
                && Arrays.equals(bytes, 0, BYTE_ORDER_MARK.length, BYTE_ORDER_MARK, 0, BYTE_ORDER_MARK.length);
        int skip = marked ? BYTE_ORDER_MARK.length : 0;
        return new String(bytes, skip, bytes.length - skip, StandardCharsets.UTF_8);
    }

    /** The member of an object, or null when the value is not an object or has no such member. */
    static Object field(Object value, String key) {
        return value instanceof Map<?, ?> map ? map.get(key) : null;
    }

    /** The value when it is a list, and an empty list when it is anything else. */
    static List<?> listOf(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    /**
     * Whether a value counts as given: not null, not false, not zero or not a number, and not the
     * empty string. Every object and list counts, even an empty one.
     */
    static boolean given(Object value) {
        return switch (value) {
            case null -> false;
            case Boolean flag -> flag;
            case Number number -> number.doubleValue() != 0 && !Double.isNaN(number.doubleValue());
            case String text -> !text.isEmpty();
            default -> true;
        };
    }

    /**
     * A value as a sentence quotes it: a string as it is, a number as reports write one, a boolean as
     * {@code true} or {@code false}, a value that is not there as {@code null}, and an object or a
     * list as compact json.
     */
    static String text(Object value) {
        return switch (value) {
            case null -> "null";
            case String text -> text;
            case Number number -> NumberText.of(number);
            case Boolean flag -> flag.toString();
            default -> Json.stringify(value);
        };
    }
}
