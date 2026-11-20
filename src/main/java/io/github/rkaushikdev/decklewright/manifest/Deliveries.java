package io.github.rkaushikdev.decklewright.manifest;

import io.github.rkaushikdev.decklewright.ManifestError;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The one call that turns a finished run into a delivery ready to be written.
 *
 * <p>It refuses to hand back a delivery that does not check out against itself. That check is not
 * optional and it is not a flag. A delivery that does not agree with its own sidecar is not a
 * delivery with a small problem, and the only moment at which it is cheap to find that out is
 * before it has been written to a folder somebody else is going to trust.
 */
public final class Deliveries {

    private Deliveries() {}

    /**
     * The delivery for a run, recorded as version {@code 0.0.0} and made now.
     *
     * @see #deliveryFor(FinishedRun, DeliveryOptions)
     */
    public static Payload deliveryFor(FinishedRun run) {
        return deliveryFor(run, DeliveryOptions.defaults());
    }

    /**
     * Build the whole delivery from a finished run, and check it.
     *
     * <p>The sidecar and the provenance chain carry one timestamp between them, and the chain names
     * the agent by the version and the stand in the options.
     *
     * @throws ManifestError when the run has no files, or the delivery does not agree with itself;
     *     the message is the first objection, and the details carry the accession and how many
     *     objections there were
     */
    public static Payload deliveryFor(FinishedRun run, DeliveryOptions options) {
        Instant at = options.at().orElseGet(Instant::now);
        Map<String, Object> sidecar = Sidecars.sidecarFor(run, at);
        String agent = options.host()
                .map(host -> Provenance.agentName(options.version(), host))
                .orElseGet(() -> Provenance.agentName(options.version()));
        List<ProvenanceEvent> provenance = Provenance.chainFor(agent, run, at);

        Payload payload = Payloads.payloadFor(run.accession(), run.files(), sidecar, provenance);

        List<String> objections = Payloads.objectionsToPayload(payload);
        if (!objections.isEmpty()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("accession", run.accession());
            details.put("objections", objections.size());
            throw new ManifestError(objections.get(0), details);
        }
        return payload;
    }
}
