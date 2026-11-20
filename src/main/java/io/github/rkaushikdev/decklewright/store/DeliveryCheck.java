package io.github.rkaushikdev.decklewright.store;

import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What a fixity sweep found for one delivery on disk.
 *
 * @param accession what was checked
 * @param ok true when there was nothing to say
 * @param checked how many of the files the sidecar lists were there to hash
 * @param problems every problem, each said once, in the order found; copied
 * @see Workspace#verify(String, Folder)
 */
public record DeliveryCheck(String accession, boolean ok, int checked, List<String> problems) implements JsonValue {

    /** A check of the given parts; the problems are copied. */
    public DeliveryCheck {
        Objects.requireNonNull(accession, "accession");
        problems = List.copyOf(problems);
    }

    /** {@code {accession, ok, checked, problems}}, in a new map the caller may keep. */
    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("accession", accession);
        json.put("ok", ok);
        json.put("checked", (double) checked);
        json.put("problems", new ArrayList<Object>(problems));
        return json;
    }
}
