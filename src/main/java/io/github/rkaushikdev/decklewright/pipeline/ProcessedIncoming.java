package io.github.rkaushikdev.decklewright.pipeline;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything worked out of a workspace's incoming folder in one go.
 *
 * @param done one per capture worked, in the order they were; copied
 * @param counts how many ended each way, by ledger outcome ({@code delivered}, {@code rejected},
 *     {@code failed} or {@code skipped}), in the order each outcome first came up; only outcomes that
 *     came up are there; read only
 * @param stoppedEarly true when the policy stopped the shift after a failure
 * @see WorkspaceRunner#processIncoming
 */
public record ProcessedIncoming(List<Processed> done, Map<String, Integer> counts, boolean stoppedEarly) {

    /** A result of the given parts; the list and the map are copied, keeping their order. */
    public ProcessedIncoming {
        done = List.copyOf(done);
        counts = Collections.unmodifiableMap(new LinkedHashMap<>(counts));
    }
}
