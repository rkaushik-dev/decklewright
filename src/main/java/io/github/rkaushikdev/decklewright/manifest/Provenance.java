package io.github.rkaushikdev.decklewright.manifest;

import io.github.rkaushikdev.decklewright.ManifestError;
import io.github.rkaushikdev.decklewright.derive.DerivedFile;
import io.github.rkaushikdev.decklewright.plan.CropPlan;
import io.github.rkaushikdev.decklewright.util.IsoTime;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * What was done to a sheet, in order, and by what.
 *
 * <p>A preservation copy is only as good as the story of how it was made. Ten years on, somebody
 * looking at a straightened crop needs to know that it was straightened, by how much, by which
 * version of what, and against which profile, because otherwise they cannot tell a deliberate two
 * degree turn from a scanner that was out of true.
 *
 * <p>Events are appended and never edited. Each one carries the digest of the one before it, so an
 * event quietly dropped out of the middle of a chain shows up as a break rather than as a shorter
 * list nobody counts. A chain is an immutable list of {@link ProvenanceEvent}s, oldest first.
 */
public final class Provenance {

    /** The kinds of event a run records. Anything else is a mistake, not a kind. */
    public static final List<String> EVENT_TYPES = List.of(
            "capture-read",
            "sheet-found",
            "crop-planned",
            "deskewed",
            "derivative-made",
            "quality-checked",
            "delivered",
            "note");

    /** The name a run records itself under when it is not told another. */
    private static final String SOFTWARE = "decklewright";

    private Provenance() {}

    /**
     * The name a run records itself under, such as {@code decklewright 1.4.0}. The version matters
     * more than the name: two runs of the same accession that disagree are nearly always two
     * versions.
     */
    public static String agentName(String version) {
        return agentName(SOFTWARE, version, "");
    }

    /**
     * The name a run records itself under, with the stand it ran on, such as {@code decklewright
     * 1.4.0 on stand-2}.
     *
     * @param host where it ran, when a lab has several stands; empty for no stand
     */
    public static String agentName(String version, String host) {
        return agentName(SOFTWARE, version, host);
    }

    /**
     * The name a piece of software records itself under.
     *
     * @param name what the software is called
     * @param host where it ran; empty for no stand
     */
    public static String agentName(String name, String version, String host) {
        return host.isEmpty() ? name + " " + version : name + " " + version + " on " + host;
    }

    /**
     * The digest of the chain so far: the sha256 of the last event's {@link #digestableForm}, or of
     * nothing at all for an empty chain.
     */
    public static String chainDigest(List<ProvenanceEvent> events) {
        if (events.isEmpty()) {
            return Checksums.digest(new byte[0]);
        }
        ProvenanceEvent last = events.get(events.size() - 1);
        return Checksums.digest(digestableForm(last).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The text an event hashes as. It leaves nothing out, so a value changed after the fact breaks
     * the chain.
     *
     * <p>The previous digest, the kind, the time, the agent, the detail and the values, separated by
     * single spaces. The values are written {@code name=value}, sorted by name in UTF-16 code unit
     * order so the order they were given in cannot change the digest, and joined by commas; a number
     * is written as reports write one.
     */
    public static String digestableForm(ProvenanceEvent event) {
        Map<String, Object> values = event.values().orElse(Map.of());
        String pairs = values.keySet().stream()
                .sorted()
                .map(key -> key + "=" + Documents.text(values.get(key)))
                .collect(Collectors.joining(","));
        return String.join(" ", event.previous(), event.type(), event.at(), event.agent(), event.detail(), pairs);
    }

    /**
     * Add an event to a chain, now and with no values.
     *
     * @see #recordEvent(List, String, String, String, EventOptions)
     */
    public static List<ProvenanceEvent> recordEvent(
            List<ProvenanceEvent> events, String type, String agent, String detail) {
        return recordEvent(events, type, agent, detail, EventOptions.defaults());
    }

    /**
     * Add an event to a chain, giving back a new chain. The chain it was given is not touched.
     *
     * @param type one of {@link #EVENT_TYPES}
     * @param agent what did it
     * @param detail one sentence, in a person's words
     * @param options the values behind the sentence, and the time
     * @throws ManifestError when nothing records an event of that kind; the details name it and list
     *     the kinds there are
     */
    public static List<ProvenanceEvent> recordEvent(
            List<ProvenanceEvent> events, String type, String agent, String detail, EventOptions options) {
        if (!EVENT_TYPES.contains(type)) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("type", type);
            details.put("known", String.join(", ", EVENT_TYPES));
            throw new ManifestError("nothing records an event of that kind", details);
        }
        String when = IsoTime.format(options.at().orElseGet(Instant::now));
        ProvenanceEvent event = new ProvenanceEvent(type, when, agent, detail, options.values(), chainDigest(events));

        List<ProvenanceEvent> out = new ArrayList<>(events);
        out.add(event);
        return Collections.unmodifiableList(out);
    }

    /**
     * Where the chain breaks, if it does: every event that does not point at the one before it, and
     * every event of a kind nothing records.
     *
     * @return the breaks in order; empty when every event still points at the one before it
     */
    public static List<ChainBreak> breaksIn(List<ProvenanceEvent> events) {
        List<ChainBreak> out = new ArrayList<>();
        for (int i = 0; i < events.size(); i += 1) {
            String expected = chainDigest(events.subList(0, i));
            if (!events.get(i).previous().equals(expected)) {
                out.add(new ChainBreak(i, "event " + i + " does not follow the one before it"));
            }
            if (!EVENT_TYPES.contains(events.get(i).type())) {
                out.add(new ChainBreak(i, "event " + i + " is of a kind nothing records"));
            }
        }
        return List.copyOf(out);
    }

    /**
     * The chain a normal run produces, stamped with the time it is now.
     *
     * @see #chainFor(String, FinishedRun, Instant)
     */
    public static List<ProvenanceEvent> chainFor(String agent, FinishedRun run) {
        return chainFor(agent, run, Instant.now());
    }

    /**
     * The chain a normal run produces, built from what the run already knows.
     *
     * <p>It is one call rather than eight scattered through the stages because the order of these
     * events is part of the record. A chain that says the crop was planned after the derivative was
     * made is not a chain with the events in the wrong order, it is a chain describing a different
     * and much worse process. The capture is read, the sheet found and the crop planned; the turn is
     * recorded when there was one; then one event per file, and the grading when there was one.
     *
     * @param agent what did it, usually {@link #agentName(String)}
     * @param at the one time every event is stamped with
     */
    public static List<ProvenanceEvent> chainFor(String agent, FinishedRun run, Instant at) {
        EventOptions stamped = EventOptions.defaults().withAt(at);
        CropPlan plan = run.plan();
        List<ProvenanceEvent> events = List.of();

        events = recordEvent(
                events,
                "capture-read",
                agent,
                "the capture was read off the stand",
                stamped.withValues(
                        values("warnings", (double) run.reading().warnings().size())));
        events = recordEvent(
                events,
                "sheet-found",
                agent,
                "the sheet was found on the bed",
                stamped.withValues(values(
                        "confidence", Sidecars.rounded(run.reading().sheet().confidence(), 3),
                        "skew", Sidecars.rounded(run.reading().sheet().skew().degrees(), 4))));
        events = recordEvent(
                events,
                "crop-planned",
                agent,
                "the crop was planned by " + plan.steps().size() + " rules",
                stamped.withValues(values(
                        "width", plan.rect().width(), "height", plan.rect().height())));
        if (!plan.deskew().isZero()) {
            events = recordEvent(
                    events,
                    "deskewed",
                    agent,
                    "the turn was taken out before cutting",
                    stamped.withValues(
                            values("degrees", Sidecars.rounded(plan.deskew().degrees(), 4))));
        }
        for (DerivedFile file : run.files()) {
            events = recordEvent(
                    events,
                    "derivative-made",
                    agent,
                    file.name() + " was written as " + file.format().label(),
                    stamped.withValues(values("file", file.fileName())));
        }
        if (run.quality().isPresent()) {
            int stars = run.quality().get().stars();
            events = recordEvent(
                    events,
                    "quality-checked",
                    agent,
                    "graded at " + stars + " of 4",
                    stamped.withValues(values(
                            "stars",
                            (double) stars,
                            "passed",
                            run.quality().get().passed())));
        }
        return events;
    }

    /** One line per event, oldest first, such as {@code 2025-11-11T08:00:00.000Z note: checked}. */
    public static List<String> describeChain(List<ProvenanceEvent> events) {
        return events.stream()
                .map(event -> event.at() + " " + event.type() + ": " + event.detail())
                .toList();
    }

    /**
     * Read a chain back, refusing one that has been tampered with.
     *
     * <p>Each member is read as an event: a member that is already one is taken as it is, and an
     * object read off a disk gives its fields, a field that is missing or null reading as empty
     * text. A member that is not an event at all reads as an empty one, which the chain check then
     * refuses.
     *
     * @param value what came out of a json file, or a chain built here
     * @throws ManifestError when the value is not a list, or the chain breaks; the message is the
     *     first break's reason and the details count them
     */
    public static List<ProvenanceEvent> parseChain(Object value) {
        if (!(value instanceof List<?> members)) {
            throw new ManifestError("a provenance chain has to be a list", Map.of());
        }
        List<ProvenanceEvent> events = members.stream().map(Provenance::eventOf).toList();
        List<ChainBreak> breaks = breaksIn(events);
        if (!breaks.isEmpty()) {
            throw new ManifestError(breaks.get(0).reason(), Map.of("breaks", breaks.size()));
        }
        return events;
    }

    /** One member of a written chain as an event, without judging it. */
    static ProvenanceEvent eventOf(Object member) {
        if (member instanceof ProvenanceEvent event) {
            return event;
        }
        Optional<Map<String, Object>> values = Optional.empty();
        if (Documents.field(member, "values") instanceof Map<?, ?> given) {
            Map<String, Object> copy = new LinkedHashMap<>();
            given.forEach((key, one) -> copy.put(String.valueOf(key), one));
            values = Optional.of(copy);
        }
        return new ProvenanceEvent(
                fieldText(member, "type"),
                fieldText(member, "at"),
                fieldText(member, "agent"),
                fieldText(member, "detail"),
                values,
                fieldText(member, "previous"));
    }

    private static String fieldText(Object member, String key) {
        Object value = Documents.field(member, key);
        return value == null ? "" : Documents.text(value);
    }

    private static Map<String, Object> values(String name, Object value) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(name, value);
        return out;
    }

    private static Map<String, Object> values(String first, Object one, String second, Object two) {
        Map<String, Object> out = values(first, one);
        out.put(second, two);
        return out;
    }
}
