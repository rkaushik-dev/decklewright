package io.github.rkaushikdev.decklewright.pipeline;

import io.github.rkaushikdev.decklewright.PipelineError;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One sheet, moving through the stages.
 *
 * <p>A job carries what has been worked out about an accession so far, and it is immutable: every
 * stage hands back a new job with what it produced added. That is not purity for its own sake. It
 * means a stage cannot quietly reach back and change something an earlier stage decided, which is
 * exactly the failure that makes a crop and the sidecar describing it disagree.
 *
 * <p>The values are a flat map with names the stages agree on. A flat map rather than a class
 * because the set of things a run knows grows with the profile, and because a stage a lab writes
 * itself has to be able to put something in it without changing this file. A null value is no value
 * at all: it is left out, and the job does not carry that name.
 */
public final class Job {

    private final String accession;
    private final Map<String, Object> values;
    private final List<String> done;
    private final List<String> notes;
    private final List<String> warnings;

    private Job(String accession, Map<String, ?> values, List<String> done, List<String> notes, List<String> warnings) {
        if (accession == null || accession.isEmpty()) {
            throw new PipelineError("a job has to be about something", Map.of());
        }
        this.accession = accession;
        Map<String, Object> carried = new LinkedHashMap<>();
        values.forEach((name, value) -> {
            if (value != null) {
                carried.put(name, value);
            }
        });
        this.values = Collections.unmodifiableMap(carried);
        this.done = List.copyOf(done);
        this.notes = List.copyOf(notes);
        this.warnings = List.copyOf(warnings);
    }

    /**
     * A job about an accession that carries nothing yet.
     *
     * @throws PipelineError when the accession is null or empty
     */
    public static Job start(String accession) {
        return start(accession, Map.of());
    }

    /**
     * A job about an accession that starts out carrying the given values, such as the bytes read off
     * the disk.
     *
     * @throws PipelineError when the accession is null or empty
     */
    public static Job start(String accession, Map<String, ?> values) {
        return new Job(accession, Objects.requireNonNull(values, "values"), List.of(), List.of(), List.of());
    }

    /**
     * The names among those given that a job does not carry, in the order given.
     *
     * @return the ones the job does not carry
     */
    public static List<String> missingFrom(Job job, List<String> names) {
        return names.stream().filter(name -> !job.has(name)).toList();
    }

    /** What the job is about. */
    public String accession() {
        return accession;
    }

    /** Every value the job carries, by name, in the order they were put on it; read only. */
    public Map<String, Object> values() {
        return values;
    }

    /** The stages that have run, in the order they ran. */
    public List<String> done() {
        return done;
    }

    /** What a person reading the log should know, such as a stage that was skipped. */
    public List<String> notes() {
        return notes;
    }

    /** What an operator should read before trusting the delivery. */
    public List<String> warnings() {
        return warnings;
    }

    /** The value of that name; empty when the job does not carry it. */
    public Optional<Object> get(String name) {
        return Optional.ofNullable(values.get(name));
    }

    /**
     * The value of that name, as the type the stages agree it has; empty when the job does not carry
     * it.
     *
     * @throws ClassCastException when the job carries it as something else, which means two stages
     *     disagree about what the value is
     */
    public <T> Optional<T> get(String name, Class<T> type) {
        return get(name).map(type::cast);
    }

    /**
     * The value of that name, for a stage that cannot go on without it.
     *
     * @throws PipelineError when the job does not carry it; the details name the accession, the
     *     value wanted and every value the job does carry
     */
    public Object need(String name) {
        Object value = values.get(name);
        if (value == null) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("accession", accession);
            details.put("wanted", name);
            details.put("has", values.isEmpty() ? "nothing" : String.join(", ", values.keySet()));
            throw new PipelineError("the job does not carry that yet", details);
        }
        return value;
    }

    /**
     * The value of that name, as the type the stages agree it has, for a stage that cannot go on
     * without it.
     *
     * @throws PipelineError when the job does not carry it
     * @throws ClassCastException when the job carries it as something else
     */
    public <T> T need(String name, Class<T> type) {
        return type.cast(need(name));
    }

    /** Whether the job carries a value of that name. */
    public boolean has(String name) {
        return values.containsKey(name);
    }

    /**
     * A new job with more known about it.
     *
     * <p>Overwriting a value a stage already produced is refused. Two stages both writing {@code
     * plan} is a graph that was wired wrong, and finding that out here is far cheaper than finding it
     * out from a delivery that was cropped twice.
     *
     * @throws PipelineError when the job already carries one of the names; the details name the
     *     accession and the key
     * @see #replacing(Map)
     */
    public Job with(Map<String, ?> more) {
        for (String key : more.keySet()) {
            if (has(key)) {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("accession", accession);
                details.put("key", key);
                throw new PipelineError("something has already put that on the job", details);
            }
        }
        return replacing(more);
    }

    /**
     * A new job with more known about it, deliberately overwriting any value it already carries
     * under the same name. A null value takes the name off the job.
     */
    public Job replacing(Map<String, ?> more) {
        Map<String, Object> merged = new LinkedHashMap<>(values);
        merged.putAll(more);
        return new Job(accession, merged, done, notes, warnings);
    }

    /**
     * A new job recording that a stage has run.
     *
     * @throws PipelineError when that stage has already run on this job; the details name the
     *     accession and the stage
     */
    public Job finished(String name) {
        if (done.contains(name)) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("accession", accession);
            details.put("stage", name);
            throw new PipelineError("that stage has already run on this job", details);
        }
        return new Job(accession, values, appended(done, List.of(name)), notes, warnings);
    }

    /** A new job with a note for the log. */
    public Job noted(String text) {
        return new Job(accession, values, done, appended(notes, List.of(text)), warnings);
    }

    /** A new job with a warning for the operator. */
    public Job warned(String text) {
        return new Job(accession, values, done, notes, appended(warnings, List.of(text)));
    }

    /** A new job with every one of the warnings added, or this same job when there are none. */
    public Job warnedAll(List<String> texts) {
        return texts.isEmpty() ? this : new Job(accession, values, done, notes, appended(warnings, texts));
    }

    /**
     * What a job looks like in a log, such as {@code g4181: after 2 stages, carrying plan, surface}.
     * The values are named in code unit order and not printed, because one of them is four hundred
     * megabytes of pixels.
     */
    @Override
    public String toString() {
        List<String> names = new ArrayList<>(values.keySet());
        Collections.sort(names);
        String carried = names.isEmpty() ? "nothing" : String.join(", ", names);
        return accession + ": after " + done.size() + " stages, carrying " + carried;
    }

    private static List<String> appended(List<String> list, List<String> more) {
        List<String> out = new ArrayList<>(list.size() + more.size());
        out.addAll(list);
        out.addAll(more);
        return out;
    }
}
