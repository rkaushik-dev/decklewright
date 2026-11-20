package io.github.rkaushikdev.decklewright.store;

import io.github.rkaushikdev.decklewright.StoreError;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The store's errors for a file system that said no.
 *
 * <p>Each says in words what could not be done, carries where in its details, and keeps the file
 * system's own failure twice: as its cause, and as the {@code cause} detail, its type and message,
 * so a report that prints only details still says what the disk said.
 */
final class StoreFailures {

    private StoreFailures() {}

    /** A failure with the given details, then the cause. */
    static StoreError failure(String message, Map<String, ?> details, Exception cause) {
        Map<String, Object> all = new LinkedHashMap<>(details);
        all.put("cause", cause.getClass().getSimpleName() + ": " + cause.getMessage());
        return new StoreError(message, all, cause);
    }

    /** One named detail, in a map whose order is kept. */
    static Map<String, Object> detail(String name, Object value) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(name, value);
        return out;
    }

    /** Two named details, in order. */
    static Map<String, Object> details(String first, Object one, String second, Object two) {
        Map<String, Object> out = detail(first, one);
        out.put(second, two);
        return out;
    }
}
