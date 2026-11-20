package io.github.rkaushikdev.decklewright.manifest;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * What a delivery records about the run that made it, beyond the run itself.
 *
 * @param version what to record as the agent's version
 * @param host the stand it ran on, when a lab has several
 * @param at when the delivery was made, stamped on the sidecar and on every event; empty for now
 * @see Deliveries#deliveryFor(FinishedRun, DeliveryOptions)
 */
public record DeliveryOptions(String version, Optional<String> host, Optional<Instant> at) {

    /** The version a run records when it is not told one. */
    public static final String UNKNOWN_VERSION = "0.0.0";

    /** Options of the given parts. */
    public DeliveryOptions {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(at, "at");
    }

    /** Version {@code 0.0.0}, no stand, and the time it is now. */
    public static DeliveryOptions defaults() {
        return new DeliveryOptions(UNKNOWN_VERSION, Optional.empty(), Optional.empty());
    }

    /** These options with another version. */
    public DeliveryOptions withVersion(String other) {
        return new DeliveryOptions(other, host, at);
    }

    /** These options with a stand. */
    public DeliveryOptions withHost(String stand) {
        return new DeliveryOptions(version, Optional.of(stand), at);
    }

    /** These options with a time. */
    public DeliveryOptions withAt(Instant instant) {
        return new DeliveryOptions(version, host, Optional.of(instant));
    }
}
