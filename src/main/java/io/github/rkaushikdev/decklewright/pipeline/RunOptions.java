package io.github.rkaushikdev.decklewright.pipeline;

import io.github.rkaushikdev.decklewright.detect.CaptureOptions;
import io.github.rkaushikdev.decklewright.manifest.DeliveryOptions;
import io.github.rkaushikdev.decklewright.plan.PlanOptions;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * How a run goes, beyond the capture it is handed.
 *
 * @param stages what it runs, in any order; the graph orders them; copied
 * @param policy what it does with the result; empty for the one the profile implies
 * @param alreadyThere whether the accession has already been delivered; empty to take it as not
 *     delivered in a run on its own, and to ask the workspace in one that writes to a workspace
 * @param version what the delivery records as the agent's version
 * @param host the stand it runs on, when a lab has several
 * @param at when it happened, stamped on the delivery and the ledger; empty for the time it is now
 * @param level the compression level the derivatives are written at; empty for the codec's own
 * @param capture what the detector is told when it reads the capture
 * @param crop the planner's per run choices
 * @see Runner#runJob(JobInput, RunOptions)
 */
public record RunOptions(
        List<Stage> stages,
        Optional<Policy> policy,
        Optional<Boolean> alreadyThere,
        String version,
        Optional<String> host,
        Optional<Instant> at,
        OptionalInt level,
        CaptureOptions capture,
        PlanOptions crop) {

    /** Options of the given parts; the stages are copied. */
    public RunOptions {
        stages = List.copyOf(stages);
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(alreadyThere, "alreadyThere");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(capture, "capture");
        Objects.requireNonNull(crop, "crop");
    }

    /**
     * The standard stages under the profile's policy, version {@code 0.0.0}, no stand, now, the
     * codec's own level, and the detector's and the planner's defaults.
     */
    public static RunOptions defaults() {
        return new RunOptions(
                StandardStages.standardStages(),
                Optional.empty(),
                Optional.empty(),
                DeliveryOptions.UNKNOWN_VERSION,
                Optional.empty(),
                Optional.empty(),
                OptionalInt.empty(),
                CaptureOptions.defaults(),
                PlanOptions.defaults());
    }

    /** These options running other stages. */
    public RunOptions withStages(List<Stage> others) {
        return new RunOptions(others, policy, alreadyThere, version, host, at, level, capture, crop);
    }

    /** These options under a policy of the caller's. */
    public RunOptions withPolicy(Policy rules) {
        return new RunOptions(stages, Optional.of(rules), alreadyThere, version, host, at, level, capture, crop);
    }

    /** These options saying whether the accession has already been delivered. */
    public RunOptions withAlreadyThere(boolean delivered) {
        return new RunOptions(stages, policy, Optional.of(delivered), version, host, at, level, capture, crop);
    }

    /** These options recording another version. */
    public RunOptions withVersion(String other) {
        return new RunOptions(stages, policy, alreadyThere, other, host, at, level, capture, crop);
    }

    /** These options recording a stand. */
    public RunOptions withHost(String stand) {
        return new RunOptions(stages, policy, alreadyThere, version, Optional.of(stand), at, level, capture, crop);
    }

    /** These options stamping a time. */
    public RunOptions withAt(Instant instant) {
        return new RunOptions(stages, policy, alreadyThere, version, host, Optional.of(instant), level, capture, crop);
    }

    /** These options writing derivatives at a compression level. */
    public RunOptions withLevel(int compression) {
        return new RunOptions(
                stages, policy, alreadyThere, version, host, at, OptionalInt.of(compression), capture, crop);
    }

    /** These options telling the detector something else. */
    public RunOptions withCapture(CaptureOptions options) {
        return new RunOptions(stages, policy, alreadyThere, version, host, at, level, options, crop);
    }

    /** These options with other planner choices. */
    public RunOptions withCrop(PlanOptions options) {
        return new RunOptions(stages, policy, alreadyThere, version, host, at, level, capture, options);
    }
}
