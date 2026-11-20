package io.github.rkaushikdev.decklewright.qc;

import io.github.rkaushikdev.decklewright.detect.TargetFinding;
import io.github.rkaushikdev.decklewright.geom.Angle;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.profile.QcLimits;
import io.github.rkaushikdev.decklewright.raster.Surface;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Everything a check might ask for, gathered once so a check never has to know how to get at a
 * reading. That is what makes the checks testable with a context built by hand.
 *
 * <p>{@link QcRunner#contextFor(QcInput)} fills it off the capture reading and the profile. Each
 * entry is one {@link Need}; any of them may be absent, and a check that needs an absent one is
 * declined rather than run.
 *
 * @param surface the capture
 * @param sheet the sheet's bounding rect, out to whole pixels
 * @param avoid what is on the bed and is not bed, which the illumination check keeps its patches
 *     off; copied
 * @param bed the region the bed is measured over
 * @param crop the planned crop, which the clipping check measures inside
 * @param target the step wedge
 * @param sampling the sampling frequency, measured or claimed
 * @param skew how far the sheet is turned
 * @param spread how far apart the three skew measures were, in degrees
 * @param limits the profile's quality limits
 */
public record QcContext(
        Optional<Surface> surface,
        Optional<Rect> sheet,
        Optional<List<Rect>> avoid,
        Optional<Rect> bed,
        Optional<Rect> crop,
        Optional<TargetFinding> target,
        Optional<Sampling> sampling,
        Optional<Angle> skew,
        OptionalDouble spread,
        Optional<QcLimits> limits) {

    private static final QcContext EMPTY = new QcContext(
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            OptionalDouble.empty(),
            Optional.empty());

    /** A context of the given parts; the list of rects to avoid is copied. */
    public QcContext {
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(sheet, "sheet");
        avoid = Objects.requireNonNull(avoid, "avoid").map(List::copyOf);
        Objects.requireNonNull(bed, "bed");
        Objects.requireNonNull(crop, "crop");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(sampling, "sampling");
        Objects.requireNonNull(skew, "skew");
        Objects.requireNonNull(spread, "spread");
        Objects.requireNonNull(limits, "limits");
    }

    /** A context holding nothing, to build one up from with the {@code withX} methods. */
    public static QcContext empty() {
        return EMPTY;
    }

    /** True when the context holds what the check asked for. */
    public boolean has(Need need) {
        return switch (need) {
            case SURFACE -> surface.isPresent();
            case SHEET -> sheet.isPresent();
            case AVOID -> avoid.isPresent();
            case BED -> bed.isPresent();
            case CROP -> crop.isPresent();
            case TARGET -> target.isPresent();
            case SAMPLING -> sampling.isPresent();
            case SKEW -> skew.isPresent();
            case SPREAD -> spread.isPresent();
            case LIMITS -> limits.isPresent();
        };
    }

    /** This context with the one entry taken out, everything else the same. */
    public QcContext without(Need need) {
        return switch (need) {
            case SURFACE ->
                new QcContext(Optional.empty(), sheet, avoid, bed, crop, target, sampling, skew, spread, limits);
            case SHEET ->
                new QcContext(surface, Optional.empty(), avoid, bed, crop, target, sampling, skew, spread, limits);
            case AVOID ->
                new QcContext(surface, sheet, Optional.empty(), bed, crop, target, sampling, skew, spread, limits);
            case BED ->
                new QcContext(surface, sheet, avoid, Optional.empty(), crop, target, sampling, skew, spread, limits);
            case CROP ->
                new QcContext(surface, sheet, avoid, bed, Optional.empty(), target, sampling, skew, spread, limits);
            case TARGET ->
                new QcContext(surface, sheet, avoid, bed, crop, Optional.empty(), sampling, skew, spread, limits);
            case SAMPLING ->
                new QcContext(surface, sheet, avoid, bed, crop, target, Optional.empty(), skew, spread, limits);
            case SKEW ->
                new QcContext(surface, sheet, avoid, bed, crop, target, sampling, Optional.empty(), spread, limits);
            case SPREAD ->
                new QcContext(surface, sheet, avoid, bed, crop, target, sampling, skew, OptionalDouble.empty(), limits);
            case LIMITS ->
                new QcContext(surface, sheet, avoid, bed, crop, target, sampling, skew, spread, Optional.empty());
        };
    }

    /** This context holding the capture. */
    public QcContext withSurface(Surface surface) {
        return new QcContext(Optional.of(surface), sheet, avoid, bed, crop, target, sampling, skew, spread, limits);
    }

    /** This context holding the sheet's rect. */
    public QcContext withSheet(Rect sheet) {
        return new QcContext(surface, Optional.of(sheet), avoid, bed, crop, target, sampling, skew, spread, limits);
    }

    /** This context holding what to keep the bed patches off. */
    public QcContext withAvoid(List<Rect> avoid) {
        return new QcContext(surface, sheet, Optional.of(avoid), bed, crop, target, sampling, skew, spread, limits);
    }

    /** This context holding the region the bed is measured over. */
    public QcContext withBed(Rect bed) {
        return new QcContext(surface, sheet, avoid, Optional.of(bed), crop, target, sampling, skew, spread, limits);
    }

    /** This context holding the planned crop. */
    public QcContext withCrop(Rect crop) {
        return new QcContext(surface, sheet, avoid, bed, Optional.of(crop), target, sampling, skew, spread, limits);
    }

    /** This context holding the step wedge. */
    public QcContext withTarget(TargetFinding target) {
        return new QcContext(surface, sheet, avoid, bed, crop, Optional.of(target), sampling, skew, spread, limits);
    }

    /** This context holding the sampling frequency. */
    public QcContext withSampling(Sampling sampling) {
        return new QcContext(surface, sheet, avoid, bed, crop, target, Optional.of(sampling), skew, spread, limits);
    }

    /** This context holding the sheet's skew. */
    public QcContext withSkew(Angle skew) {
        return new QcContext(surface, sheet, avoid, bed, crop, target, sampling, Optional.of(skew), spread, limits);
    }

    /** This context holding the spread of the skew measures, in degrees. */
    public QcContext withSpread(double spread) {
        return new QcContext(
                surface, sheet, avoid, bed, crop, target, sampling, skew, OptionalDouble.of(spread), limits);
    }

    /** This context holding the profile's quality limits. */
    public QcContext withLimits(QcLimits limits) {
        return new QcContext(surface, sheet, avoid, bed, crop, target, sampling, skew, spread, Optional.of(limits));
    }
}
