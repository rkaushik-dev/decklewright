package io.github.rkaushikdev.decklewright.manifest;

import io.github.rkaushikdev.decklewright.derive.DerivedFile;
import io.github.rkaushikdev.decklewright.detect.CaptureReading;
import io.github.rkaushikdev.decklewright.plan.CropPlan;
import io.github.rkaushikdev.decklewright.profile.Profile;
import io.github.rkaushikdev.decklewright.qc.QcResult;
import io.github.rkaushikdev.decklewright.raster.Surface;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything a finished run knows about one accession that its delivery records: what was
 * photographed, how it was read and cut, what was written, and how it graded.
 *
 * @param accession what the run was of
 * @param profile the resolved profile it ran under
 * @param surface the raw capture, whose size, layout and depth the sidecar records
 * @param reading what was read off the capture
 * @param plan the crop that was cut
 * @param files the derivatives, in the order they were made; copied
 * @param quality the grading, when the run graded the capture
 * @param warnings what an operator should read before trusting the delivery; copied
 * @see Deliveries#deliveryFor(FinishedRun, DeliveryOptions)
 */
public record FinishedRun(
        String accession,
        Profile profile,
        Surface surface,
        CaptureReading reading,
        CropPlan plan,
        List<DerivedFile> files,
        Optional<QcResult> quality,
        List<String> warnings) {

    /** A run of the given parts; the lists are copied. */
    public FinishedRun {
        Objects.requireNonNull(accession, "accession");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(reading, "reading");
        Objects.requireNonNull(plan, "plan");
        files = List.copyOf(files);
        Objects.requireNonNull(quality, "quality");
        warnings = List.copyOf(warnings);
    }

    /** A run that was not graded and has nothing to warn about. */
    public static FinishedRun of(
            String accession,
            Profile profile,
            Surface surface,
            CaptureReading reading,
            CropPlan plan,
            List<DerivedFile> files) {
        return new FinishedRun(accession, profile, surface, reading, plan, files, Optional.empty(), List.of());
    }

    /** This run with its grading. */
    public FinishedRun withQuality(QcResult result) {
        return new FinishedRun(accession, profile, surface, reading, plan, files, Optional.of(result), warnings);
    }

    /** This run with other warnings. */
    public FinishedRun withWarnings(List<String> others) {
        return new FinishedRun(accession, profile, surface, reading, plan, files, quality, others);
    }
}
