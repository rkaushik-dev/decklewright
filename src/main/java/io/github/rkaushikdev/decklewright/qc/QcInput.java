package io.github.rkaushikdev.decklewright.qc;

import io.github.rkaushikdev.decklewright.detect.CaptureReading;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.profile.Profile;
import io.github.rkaushikdev.decklewright.raster.Surface;
import java.util.Objects;
import java.util.Optional;

/**
 * One capture to grade, with everything the checks are read off.
 *
 * @param surface the capture
 * @param reading what the detector read off it
 * @param profile the collection profile, whose quality section sets the limits and the level wanted
 * @param sampling a measured frequency, used over the one the reading carries
 * @param crop the planned crop, which clipping is measured inside
 * @see QcRunner#runQc(QcInput)
 */
public record QcInput(
        Surface surface, CaptureReading reading, Profile profile, Optional<Sampling> sampling, Optional<Rect> crop) {

    /** An input of the given parts. */
    public QcInput {
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(reading, "reading");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(sampling, "sampling");
        Objects.requireNonNull(crop, "crop");
    }

    /** A capture with no measured frequency of its own and no planned crop. */
    public static QcInput of(Surface surface, CaptureReading reading, Profile profile) {
        return new QcInput(surface, reading, profile, Optional.empty(), Optional.empty());
    }

    /** This input with a measured frequency. */
    public QcInput withSampling(Sampling sampling) {
        return new QcInput(surface, reading, profile, Optional.of(sampling), crop);
    }

    /** This input with no measured frequency, so the reading's own is used if it has one. */
    public QcInput withoutSampling() {
        return new QcInput(surface, reading, profile, Optional.empty(), crop);
    }

    /** This input with a planned crop. */
    public QcInput withCrop(Rect crop) {
        return new QcInput(surface, reading, profile, sampling, Optional.of(crop));
    }
}
