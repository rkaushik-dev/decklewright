package io.github.rkaushikdev.decklewright.pipeline;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What each stage of a list has to wait for, and what the run has to supply itself.
 *
 * @param edges each stage's name, in list order, to the names of the stages that produce what it
 *     needs, in the order of its needs; read only
 * @param fromOutside the needs no stage produces, in code unit order; copied
 * @see StageGraph#dependencies(List)
 */
public record StageDependencies(Map<String, Set<String>> edges, List<String> fromOutside) {

    /** Dependencies of the given parts; the map and its sets are copied, keeping their order. */
    public StageDependencies {
        Map<String, Set<String>> copied = new LinkedHashMap<>();
        edges.forEach((name, waits) -> copied.put(name, Collections.unmodifiableSet(new LinkedHashSet<>(waits))));
        edges = Collections.unmodifiableMap(copied);
        fromOutside = List.copyOf(fromOutside);
    }

    /** The stages the named one waits for; none for a name that is not in the list. */
    public Set<String> waitsOf(String stage) {
        return edges.getOrDefault(stage, Set.of());
    }
}
