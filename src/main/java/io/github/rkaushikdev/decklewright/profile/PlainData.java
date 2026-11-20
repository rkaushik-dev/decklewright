package io.github.rkaushikdev.decklewright.profile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The few operations the profile layer needs on plain data: the maps in key order, lists, strings,
 * numbers, booleans and nulls that a written profile is made of.
 *
 * <p>Keys are always strings in plain data, so a key read through a wildcard map is written back
 * with {@link String#valueOf(Object)}, which leaves a string as it is.
 */
final class PlainData {
    private PlainData() {}

    /**
     * True when the map holds a value for the key. An entry holding an empty {@link Optional} is a
     * value that is not there, the same as {@link io.github.rkaushikdev.decklewright.util.Json} treats
     * one, so a key can be present in a map and still say nothing.
     */
    static boolean has(Map<?, ?> bag, String key) {
        return bag.containsKey(key) && !(bag.get(key) instanceof Optional<?> optional && optional.isEmpty());
    }

    /**
     * A deep copy that the caller owns and may change: every map and list is copied, keys in order,
     * and everything else is shared, since a string, a number or a boolean cannot change.
     */
    static Map<String, Object> copyOf(Map<?, ?> map) {
        Map<String, Object> out = new LinkedHashMap<>();
        map.forEach((key, value) -> out.put(String.valueOf(key), copy(value)));
        return out;
    }

    /** A deep copy of a map or a list; anything else is a value and comes back as it is. */
    static Object copy(Object value) {
        return switch (value) {
            case Map<?, ?> map -> copyOf(map);
            case List<?> list -> {
                List<Object> out = new ArrayList<>(list.size());
                list.forEach(item -> out.add(copy(item)));
                yield out;
            }
            case null, default -> value;
        };
    }

    /**
     * A deep copy nobody can change, nulls and key order kept. The shipped profiles and every
     * registered document are held this way, so a caller cannot edit them behind the registry's back.
     */
    static Map<String, Object> frozen(Map<?, ?> map) {
        Map<String, Object> out = new LinkedHashMap<>();
        map.forEach((key, value) -> out.put(String.valueOf(key), freeze(value)));
        return Collections.unmodifiableMap(out);
    }

    private static Object freeze(Object value) {
        return switch (value) {
            case Map<?, ?> map -> frozen(map);
            case List<?> list -> {
                List<Object> out = new ArrayList<>(list.size());
                list.forEach(item -> out.add(freeze(item)));
                yield Collections.unmodifiableList(out);
            }
            case null, default -> value;
        };
    }
}
