package io.github.rkaushikdev.decklewright.manifest;

import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One thing that was done to a sheet, by what, and when, pointing at the event before it.
 *
 * <p>The fields hold what is written in the chain, so an event read back off a disk is the event
 * that was written, a kind nothing records included; {@link Provenance#breaksIn} is what judges
 * it.
 *
 * @param type the kind of event, one of {@link Provenance#EVENT_TYPES} when it was recorded here
 * @param at when, in ISO 8601
 * @param agent what did it, such as {@code decklewright 1.4.0}
 * @param detail one sentence, in a person's words
 * @param values the figures behind the sentence: strings, numbers and booleans by name, in the
 *     order given; copied. Empty when there were none, which is not the same as an empty set
 * @param previous the digest of the chain before this event
 * @see Provenance#recordEvent
 */
public record ProvenanceEvent(
        String type, String at, String agent, String detail, Optional<Map<String, Object>> values, String previous)
        implements JsonValue {

    /** An event of the given parts; the values are copied, keeping their order. */
    public ProvenanceEvent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(agent, "agent");
        Objects.requireNonNull(detail, "detail");
        values = values.map(given -> Collections.unmodifiableMap(new LinkedHashMap<>(given)));
        Objects.requireNonNull(previous, "previous");
    }

    /**
     * {@code {type, at, agent, detail, values, previous}}, in a new map the caller may keep, with the
     * values left out when there are none.
     */
    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("type", type);
        json.put("at", at);
        json.put("agent", agent);
        json.put("detail", detail);
        values.ifPresent(given -> json.put("values", new LinkedHashMap<>(given)));
        json.put("previous", previous);
        return json;
    }
}
