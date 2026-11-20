package io.github.rkaushikdev.decklewright.pipeline;

import io.github.rkaushikdev.decklewright.PipelineError;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Working out what order the stages go in.
 *
 * <p>Nobody writes the order down. Each stage says what it needs and what it produces, and the order
 * falls out of that. An order that is written down is an order that goes stale the first time
 * somebody adds a stage in the middle, and the way it goes stale is that a stage runs before the one
 * that feeds it and gets a value from the previous accession.
 *
 * <p>The sort is stable: among stages that could run in any order, the one listed first goes first.
 * That matters for reading a log more than for correctness, and reading a log is most of what
 * anybody does with this.
 */
public final class StageGraph {

    private StageGraph() {}

    /**
     * Which stage produces each value. Two stages producing the same value is refused, because then
     * the order between them decides the answer and nothing in the graph says what that order should
     * be.
     *
     * @return each value to the name of the stage that produces it, in the order they are produced;
     *     read only
     * @throws PipelineError when two stages produce the same value; the details name it and both
     *     stages
     */
    public static Map<String, String> producers(List<Stage> stages) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Stage item : stages) {
            for (String value : item.produces()) {
                String held = out.get(value);
                if (held != null) {
                    Map<String, Object> details = new LinkedHashMap<>();
                    details.put("value", value);
                    details.put("stages", held + " and " + item.name());
                    throw new PipelineError("two stages produce the same value", details);
                }
                out.put(value, item.name());
            }
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * What each stage has to wait for.
     *
     * <p>A need nothing produces is not an error here. It is a value the run is expected to start
     * with, like the surface that was read off the disk, and saying which ones those are is more
     * useful than refusing. A stage that produces what it needs does not wait for itself.
     *
     * @throws PipelineError when two stages produce the same value
     */
    public static StageDependencies dependencies(List<Stage> stages) {
        Map<String, String> made = producers(stages);
        Map<String, Set<String>> edges = new LinkedHashMap<>();
        Set<String> outside = new TreeSet<>();

        for (Stage item : stages) {
            Set<String> waits = new LinkedHashSet<>();
            for (String need : item.needs()) {
                String from = made.get(need);
                if (from == null) {
                    outside.add(need);
                } else if (!from.equals(item.name())) {
                    waits.add(from);
                }
            }
            edges.put(item.name(), waits);
        }
        return new StageDependencies(edges, List.copyOf(outside));
    }

    /**
     * The order to run them in: every stage after the ones that feed it, and otherwise as listed.
     *
     * @throws PipelineError when two stages share a name, two produce the same value, or stages wait
     *     on each other in a circle; the last names every stage that could not be placed
     */
    public static List<Stage> order(List<Stage> stages) {
        Stages.requireDistinct(stages);
        StageDependencies wiring = dependencies(stages);

        List<Stage> out = new ArrayList<>(stages.size());
        Set<String> placed = new HashSet<>();
        List<Stage> remaining = new ArrayList<>(stages);

        while (!remaining.isEmpty()) {
            Stage next = remaining.stream()
                    .filter(item -> placed.containsAll(wiring.waitsOf(item.name())))
                    .findFirst()
                    .orElseThrow(() -> new PipelineError(
                            "these stages wait on each other in a circle",
                            Map.of("stages", remaining.stream().map(Stage::name).collect(Collectors.joining(", ")))));
            remaining.remove(next);
            placed.add(next.name());
            out.add(next);
        }
        return List.copyOf(out);
    }

    /**
     * Everything wrong with a set of stages that has to start with nothing.
     *
     * @see #objectionsToGraph(List, List)
     */
    public static List<String> objectionsToGraph(List<Stage> stages) {
        return objectionsToGraph(stages, List.of());
    }

    /**
     * Everything wrong with a set of stages, given what the run will start with.
     *
     * <p>This is the check that runs before anything is read off a disk. A pipeline that cannot
     * possibly work should say so in the first second rather than after the four hundred megabyte
     * master has been decoded. It reports rather than throws: a circle, a name used twice and two
     * stages producing one value are each an objection, after any needs the run does not supply.
     *
     * @param starting the values the run supplies itself
     * @return one sentence per objection; empty when the stages can run
     */
    public static List<String> objectionsToGraph(List<Stage> stages, List<String> starting) {
        if (stages.isEmpty()) {
            return List.of("there are no stages to run");
        }

        List<String> out = new ArrayList<>();
        try {
            Stages.requireDistinct(stages);
            Set<String> supplied = Set.copyOf(starting);
            for (String need : dependencies(stages).fromOutside()) {
                if (!supplied.contains(need)) {
                    out.add("nothing produces " + need + " and the run does not supply it");
                }
            }
            order(stages);
        } catch (PipelineError objection) {
            out.add(objection.getMessage());
        }
        return List.copyOf(out);
    }

    /**
     * The order, with what each stage is waiting for, for a person reading it: a line saying what the
     * run starts with when it needs anything, then one numbered line per stage, such as {@code 3. plan
     * after read, detect making plan}.
     *
     * @throws PipelineError when the stages cannot be ordered
     */
    public static List<String> describeGraph(List<Stage> stages) {
        StageDependencies wiring = dependencies(stages);
        List<Stage> sorted = order(stages);

        List<String> lines = new ArrayList<>(sorted.size() + 1);
        if (!wiring.fromOutside().isEmpty()) {
            lines.add("starts with " + String.join(", ", wiring.fromOutside()));
        }
        for (int i = 0; i < sorted.size(); i += 1) {
            Stage item = sorted.get(i);
            Set<String> waits = wiring.waitsOf(item.name());
            String after = waits.isEmpty() ? "" : " after " + String.join(", ", waits);
            String makes = item.produces().isEmpty() ? "" : " making " + String.join(", ", item.produces());
            lines.add((i + 1) + ". " + item.name() + after + makes);
        }
        return List.copyOf(lines);
    }

    /**
     * The stages that could run at the same time, in waves. Nothing here runs a wave in parallel, and
     * that is deliberate: a master is hundreds of megabytes and two stages holding one each is how a
     * laptop in a reading room runs out of memory. The waves are here because they say something true
     * about the shape of the pipeline, and a lab with a real machine can use them.
     *
     * @return the stage names of each wave, in run order; read only
     * @throws PipelineError when the stages cannot be ordered
     */
    public static List<List<String>> waves(List<Stage> stages) {
        StageDependencies wiring = dependencies(stages);
        List<Stage> sorted = order(stages);

        Map<String, Integer> depth = new HashMap<>();
        List<List<String>> out = new ArrayList<>();
        for (Stage item : sorted) {
            int deepest = wiring.waitsOf(item.name()).stream()
                    .mapToInt(name -> depth.get(name) + 1)
                    .max()
                    .orElse(0);
            depth.put(item.name(), deepest);
            if (out.size() == deepest) {
                out.add(new ArrayList<>());
            }
            out.get(deepest).add(item.name());
        }
        return out.stream().map(List::copyOf).toList();
    }
}
