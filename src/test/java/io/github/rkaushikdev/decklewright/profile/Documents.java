package io.github.rkaushikdev.decklewright.profile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Written documents for the tests, built the way the original wrote object literals.
 *
 * <p>What these build is what {@link io.github.rkaushikdev.decklewright.util.Json#parse(String)}
 * gives: a mutable map in key order and a mutable list, either of which may hold null. Numbers are
 * written as doubles for the same reason.
 */
final class Documents {
    private Documents() {}

    /** A map of the keys and values in turn, such as {@code bag("name", "atlas", "version", 2.0)}. */
    static Map<String, Object> bag(Object... keysAndValues) {
        if (keysAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("a bag needs a value for every key");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            out.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return out;
    }

    /** A list of the items, in order. */
    static List<Object> list(Object... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    /** A copy of the group a document holds under the key, typed so it can be asserted on. */
    static Map<String, Object> group(Map<?, ?> document, String key) {
        return PlainData.copyOf((Map<?, ?>) document.get(key));
    }

    /** A copy of the member of a list a document holds under the key, typed so it can be asserted on. */
    static Map<String, Object> item(Map<?, ?> document, String key, int index) {
        return PlainData.copyOf((Map<?, ?>) ((List<?>) document.get(key)).get(index));
    }
}
