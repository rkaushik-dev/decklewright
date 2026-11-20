package io.github.rkaushikdev.decklewright.qc;

import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A capture's grading together with what to do about it.
 *
 * @param result what the run found
 * @param advice one line per check that fell short saying what to change, followed by which side is
 *     dark or which way a cast goes when either check was short and the capture could say; copied
 * @see Grading#gradeCapture(QcInput, QcOptions)
 */
public record GradedCapture(QcResult result, List<String> advice) implements Graded, JsonValue {
    /** A grading of the given parts. */
    public GradedCapture {
        Objects.requireNonNull(result, "result");
        advice = List.copyOf(advice);
    }

    /** The level the capture delivers at, the same as the result's. */
    @Override
    public int stars() {
        return result.stars();
    }

    /**
     * The result's fields with the advice after them, {@code {findings, stars, worst, missing,
     * passed, wanted, advice}}, in a new map the caller may keep.
     */
    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> json = result.toJson();
        json.put("advice", new ArrayList<Object>(advice));
        return json;
    }
}
