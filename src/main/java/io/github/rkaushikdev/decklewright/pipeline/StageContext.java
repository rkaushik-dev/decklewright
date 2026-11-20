package io.github.rkaushikdev.decklewright.pipeline;

import io.github.rkaushikdev.decklewright.detect.CaptureOptions;
import io.github.rkaushikdev.decklewright.manifest.DeliveryOptions;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.plan.PlanOptions;
import io.github.rkaushikdev.decklewright.profile.Profile;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * What every stage of a run gets beside the job: the same for every stage, and the same for every
 * accession in a batch run under one profile.
 *
 * @param profile the resolved profile the run is under
 * @param sampling a frequency the caller measured, used over the one the reading carries
 * @param version what the delivery records as the agent's version
 * @param host the stand the run is on, when a lab has several
 * @param at when the run happened, stamped on everything it delivers
 * @param level the compression level the derivatives are written at; empty for the codec's own
 * @param capture what the detector is told when it reads the capture
 * @param crop the planner's per run choices
 * @see Runner#runJob(JobInput, RunOptions)
 */
public record StageContext(
        Profile profile,
        Optional<Sampling> sampling,
        String version,
        Optional<String> host,
        Instant at,
        OptionalInt level,
        CaptureOptions capture,
        PlanOptions crop) {

    /** A context of the given parts. */
    public StageContext {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(sampling, "sampling");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(capture, "capture");
        Objects.requireNonNull(crop, "crop");
    }

    /**
     * A run under a profile at a time, with no measured frequency, version {@code 0.0.0}, no stand,
     * the codec's own level, and the detector's and the planner's defaults.
     */
    public static StageContext of(Profile profile, Instant at) {
        return new StageContext(
                profile,
                Optional.empty(),
                DeliveryOptions.UNKNOWN_VERSION,
                Optional.empty(),
                at,
                OptionalInt.empty(),
                CaptureOptions.defaults(),
                PlanOptions.defaults());
    }
}
