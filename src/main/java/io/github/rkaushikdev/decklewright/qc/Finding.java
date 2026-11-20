package io.github.rkaushikdev.decklewright.qc;

import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * What one check said about one capture.
 *
 * <p>A check that could not run is not a check that failed: {@link #ran()} is false, the
 * measurement is not a number, the note says why, and the level computed over a set of findings
 * leaves it out rather than counting it as zero.
 *
 * @param name the check's name, such as {@code grayBalance}
 * @param ran false when the check had nothing to measure or its body threw
 * @param passed true when it ran and reached the star floor
 * @param measured the reading, in the check's own units; not a number when it did not run
 * @param stars the level the reading earned; zero when it did not run
 * @param note one line for the report
 * @param limit the floor it was held to, such as {@code 3 stars}; empty when it did not run
 */
public record Finding(String name, boolean ran, boolean passed, double measured, int stars, String note, String limit)
        implements Graded, JsonValue {

    /** A finding of the given parts. */
    public Finding {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(note, "note");
        Objects.requireNonNull(limit, "limit");
    }

    /**
     * {@code {name, ran, passed, measured, stars, note, limit}}, in a new map the caller may keep,
     * with null for a measurement that is not finite, which is what json holds for one.
     */
    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("name", name);
        json.put("ran", ran);
        json.put("passed", passed);
        json.put("measured", Double.isFinite(measured) ? measured : null);
        json.put("stars", (double) stars);
        json.put("note", note);
        json.put("limit", limit);
        return json;
    }
}
