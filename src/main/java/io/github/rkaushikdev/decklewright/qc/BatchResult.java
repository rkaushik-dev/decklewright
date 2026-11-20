package io.github.rkaushikdev.decklewright.qc;

import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The same set of checks run over a batch: each item's result, and the level the batch as a whole
 * delivers at.
 *
 * @param results one per item, in the order given; copied
 * @param stars the worst item's level; zero for an empty batch
 * @param passed how many items reached the level they wanted
 * @param failed how many did not
 * @see QcRunner#runBatch(List, QcOptions)
 */
public record BatchResult(List<QcResult> results, int stars, int passed, int failed) implements Graded, JsonValue {
    /** A batch of the given parts. */
    public BatchResult {
        results = List.copyOf(results);
    }

    /**
     * {@code {results, stars, passed, failed}}, in a new map the caller may keep, with every number
     * a {@link Double}.
     */
    @Override
    public Map<String, Object> toJson() {
        List<Object> items = new ArrayList<>(results.size());
        results.forEach(result -> items.add(result.toJson()));
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("results", items);
        json.put("stars", (double) stars);
        json.put("passed", (double) passed);
        json.put("failed", (double) failed);
        return json;
    }
}
