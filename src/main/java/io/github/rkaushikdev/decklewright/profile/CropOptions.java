package io.github.rkaushikdev.decklewright.profile;

import io.github.rkaushikdev.decklewright.geom.Angle;
import io.github.rkaushikdev.decklewright.geom.AspectDirection;
import io.github.rkaushikdev.decklewright.geom.Insets;
import io.github.rkaushikdev.decklewright.geom.RoundPolicy;
import io.github.rkaushikdev.decklewright.geom.SnapDirection;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * What the planner takes from a profile, with every length already in pixels at the capture's
 * sampling frequency, so the planner works in pixels only.
 *
 * @param anchor what the crop is measured from
 * @param bleed how many pixels beyond the anchor are kept, per edge
 * @param safeArea how many pixels inside the anchor are held clear, per edge
 * @param aspect the width over height the crop is forced to; empty when it is not forced
 * @param aspectDirection whether a forced aspect grows or shrinks the crop
 * @param minimumPpi the lowest sampling frequency a crop may come out at
 * @param snapTo the multiple, in pixels, the extents are snapped to; one means no snap
 * @param snapDirection which way a snap goes
 * @param rounding how a crop is made whole pixels
 * @param background the sample value that fills whatever a crop takes from outside the capture
 * @param deskew true when the sheet is straightened before it is cut
 * @param deskewAbove the skew, in degrees, a sheet has to exceed before it is straightened
 * @see Settings#cropOptions(Profile, io.github.rkaushikdev.decklewright.measure.Sampling)
 */
public record CropOptions(
        Anchor anchor,
        Insets bleed,
        Insets safeArea,
        OptionalDouble aspect,
        AspectDirection aspectDirection,
        double minimumPpi,
        double snapTo,
        SnapDirection snapDirection,
        RoundPolicy rounding,
        int background,
        boolean deskew,
        double deskewAbove) {

    /** Crop options of the given values. */
    public CropOptions {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(bleed, "bleed");
        Objects.requireNonNull(safeArea, "safeArea");
        Objects.requireNonNull(aspect, "aspect");
        Objects.requireNonNull(aspectDirection, "aspectDirection");
        Objects.requireNonNull(snapDirection, "snapDirection");
        Objects.requireNonNull(rounding, "rounding");
    }

    /**
     * The turn to take out of a sheet read at the given skew: all of it when the profile deskews and
     * the sheet is further off square than the profile leaves alone, otherwise none.
     *
     * <p>Every rotation resamples the master, and a sheet a tenth of a degree off is not worth the
     * softening that costs. A profile that says so names the skew below which a sheet is cut as it
     * lies.
     */
    public Angle turnFor(Angle skew) {
        return deskew && Math.abs(skew.degrees()) > deskewAbove ? skew : Angle.zero();
    }

    /** These options cutting at another sampling floor, everything else the same. */
    public CropOptions withMinimumPpi(double ppi) {
        return new CropOptions(
                anchor,
                bleed,
                safeArea,
                aspect,
                aspectDirection,
                ppi,
                snapTo,
                snapDirection,
                rounding,
                background,
                deskew,
                deskewAbove);
    }
}
