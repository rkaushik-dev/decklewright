package io.github.rkaushikdev.decklewright.qc;

import io.github.rkaushikdev.decklewright.detect.Neatline;
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
 * @param neatline the ruled border, when the capture was searched for one
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
        Optional<QcLimits> limits,
        Optional<Neatline> neatline) {

    private static final QcContext EMPTY = new Draft().build();

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
        Objects.requireNonNull(neatline, "neatline");
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
            case NEATLINE -> neatline.isPresent();
        };
    }

    /** This context with the one entry taken out, everything else the same. */
    public QcContext without(Need need) {
        Draft draft = new Draft(this);
        switch (need) {
            case SURFACE -> draft.surface = Optional.empty();
            case SHEET -> draft.sheet = Optional.empty();
            case AVOID -> draft.avoid = Optional.empty();
            case BED -> draft.bed = Optional.empty();
            case CROP -> draft.crop = Optional.empty();
            case TARGET -> draft.target = Optional.empty();
            case SAMPLING -> draft.sampling = Optional.empty();
            case SKEW -> draft.skew = Optional.empty();
            case SPREAD -> draft.spread = OptionalDouble.empty();
            case LIMITS -> draft.limits = Optional.empty();
            case NEATLINE -> draft.neatline = Optional.empty();
        }
        return draft.build();
    }

    /** This context holding the capture. */
    public QcContext withSurface(Surface surface) {
        Draft draft = new Draft(this);
        draft.surface = Optional.of(surface);
        return draft.build();
    }

    /** This context holding the sheet's rect. */
    public QcContext withSheet(Rect sheet) {
        Draft draft = new Draft(this);
        draft.sheet = Optional.of(sheet);
        return draft.build();
    }

    /** This context holding what to keep the bed patches off. */
    public QcContext withAvoid(List<Rect> avoid) {
        Draft draft = new Draft(this);
        draft.avoid = Optional.of(avoid);
        return draft.build();
    }

    /** This context holding the region the bed is measured over. */
    public QcContext withBed(Rect bed) {
        Draft draft = new Draft(this);
        draft.bed = Optional.of(bed);
        return draft.build();
    }

    /** This context holding the planned crop. */
    public QcContext withCrop(Rect crop) {
        Draft draft = new Draft(this);
        draft.crop = Optional.of(crop);
        return draft.build();
    }

    /** This context holding the step wedge. */
    public QcContext withTarget(TargetFinding target) {
        Draft draft = new Draft(this);
        draft.target = Optional.of(target);
        return draft.build();
    }

    /** This context holding the sampling frequency. */
    public QcContext withSampling(Sampling sampling) {
        Draft draft = new Draft(this);
        draft.sampling = Optional.of(sampling);
        return draft.build();
    }

    /** This context holding the sheet's skew. */
    public QcContext withSkew(Angle skew) {
        Draft draft = new Draft(this);
        draft.skew = Optional.of(skew);
        return draft.build();
    }

    /** This context holding the spread of the skew measures, in degrees. */
    public QcContext withSpread(double spread) {
        Draft draft = new Draft(this);
        draft.spread = OptionalDouble.of(spread);
        return draft.build();
    }

    /** This context holding the profile's quality limits. */
    public QcContext withLimits(QcLimits limits) {
        Draft draft = new Draft(this);
        draft.limits = Optional.of(limits);
        return draft.build();
    }

    /** This context holding the ruled border. */
    public QcContext withNeatline(Neatline neatline) {
        Draft draft = new Draft(this);
        draft.neatline = Optional.of(neatline);
        return draft.build();
    }

    /** A context being changed one entry at a time, so no change has to spell out the rest. */
    private static final class Draft {
        private Optional<Surface> surface = Optional.empty();
        private Optional<Rect> sheet = Optional.empty();
        private Optional<List<Rect>> avoid = Optional.empty();
        private Optional<Rect> bed = Optional.empty();
        private Optional<Rect> crop = Optional.empty();
        private Optional<TargetFinding> target = Optional.empty();
        private Optional<Sampling> sampling = Optional.empty();
        private Optional<Angle> skew = Optional.empty();
        private OptionalDouble spread = OptionalDouble.empty();
        private Optional<QcLimits> limits = Optional.empty();
        private Optional<Neatline> neatline = Optional.empty();

        Draft() {}

        Draft(QcContext from) {
            surface = from.surface;
            sheet = from.sheet;
            avoid = from.avoid;
            bed = from.bed;
            crop = from.crop;
            target = from.target;
            sampling = from.sampling;
            skew = from.skew;
            spread = from.spread;
            limits = from.limits;
            neatline = from.neatline;
        }

        QcContext build() {
            return new QcContext(surface, sheet, avoid, bed, crop, target, sampling, skew, spread, limits, neatline);
        }
    }
}
