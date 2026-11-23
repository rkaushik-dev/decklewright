package io.github.rkaushikdev.decklewright.measure;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.geom.Quad;
import io.github.rkaushikdev.decklewright.geom.Size;
import io.github.rkaushikdev.decklewright.util.Guards;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Turning pixels into inches and back.
 *
 * <p>Detection works in pixels and profiles are written in millimetres, and this is the only place
 * the two meet. Keeping it in one place means there is one place to look when a crop comes out an
 * eighth of an inch wrong, which has happened twice and was a unit both times.
 */
public final class PhysicalSizes {
    /** How far, in inches, a measured sheet may be from a nominal size and still be called one. */
    private static final double NOMINAL_TOLERANCE_INCHES = 0.5;

    private PhysicalSizes() {}

    /** The real size of a pixel extent at the given frequency, in inches. */
    public static PhysicalSize physicalSize(Size pixels, Sampling sampling) {
        return physicalSize(pixels, sampling, Unit.INCH);
    }

    /** The real size of a pixel extent at the given frequency, in the given unit, each side on its own axis. */
    public static PhysicalSize physicalSize(Size pixels, Sampling sampling, Unit unit) {
        Objects.requireNonNull(sampling, "sampling");
        Objects.requireNonNull(unit, "unit");
        return new PhysicalSize(
                sampling.toLength(pixels.width(), unit, Axis.X),
                sampling.toLength(pixels.height(), unit, Axis.Y),
                unit);
    }

    /**
     * The pixel extent a real size occupies at the given frequency, each side on its own axis.
     *
     * @throws BadArgumentError when a side is not finite
     */
    public static Size pixelSize(PhysicalSize size, Sampling sampling) {
        return new Size(
                sampling.toPixels(size.width(), size.unit(), Axis.X),
                sampling.toPixels(size.height(), size.unit(), Axis.Y));
    }

    /**
     * The size with both sides in the given unit; the same size when it already is.
     *
     * @throws BadArgumentError when a side that needs converting is not finite
     */
    public static PhysicalSize inUnit(PhysicalSize size, Unit unit) {
        Objects.requireNonNull(unit, "unit");
        if (size.unit() == unit) {
            return size;
        }
        return new PhysicalSize(
                Units.convert(size.width(), size.unit(), unit), Units.convert(size.height(), size.unit(), unit), unit);
    }

    /**
     * How far a measured sheet is from a nominal one, taken as the worse of the two sides and
     * allowing for the sheet having been placed the other way round.
     */
    public static Mismatch sizeError(PhysicalSize measured, PhysicalSize nominal) {
        PhysicalSize mine = inUnit(measured, Unit.INCH);
        PhysicalSize theirs = inUnit(nominal, Unit.INCH);
        double upright = Math.max(Math.abs(mine.width() - theirs.width()), Math.abs(mine.height() - theirs.height()));
        double turned = Math.max(Math.abs(mine.width() - theirs.height()), Math.abs(mine.height() - theirs.width()));
        return upright <= turned ? new Mismatch(upright, false) : new Mismatch(turned, true);
    }

    /** Which nominal size a measured sheet is, to within half an inch. */
    public static Optional<NominalMatch> matchNominal(PhysicalSize measured) {
        return matchNominal(measured, NOMINAL_TOLERANCE_INCHES);
    }

    /**
     * Which nominal size a measured sheet is, or nothing when it is not one of them. A sheet that
     * matches nothing is not an error: the collections hold plenty of odd sizes and the work order
     * names them by hand. Of several within the tolerance, the closest wins, and the one listed
     * first in {@link Units#SHEET_SIZES} when they are equally close.
     *
     * @param tolerance the largest {@linkplain #sizeError error} allowed, in inches
     */
    public static Optional<NominalMatch> matchNominal(PhysicalSize measured, double tolerance) {
        NominalMatch best = null;
        for (String name : Units.SHEET_SIZES.keySet()) {
            Mismatch mismatch = sizeError(measured, Units.sheetSize(name, Unit.INCH));
            if (mismatch.error() > tolerance) {
                continue;
            }
            if (best == null || mismatch.error() < best.error()) {
                best = new NominalMatch(name, mismatch.error(), mismatch.rotated());
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * The pixel extent a nominal sheet occupies at the given frequency.
     *
     * @throws BadArgumentError when there is no nominal size of that name
     */
    public static Size nominalInPixels(String name, Sampling sampling) {
        Guards.requireNonEmptyString(name, "name");
        return pixelSize(Units.sheetSize(name, Unit.INCH), sampling);
    }

    /**
     * The frequency a capture of a known object was really taken at, measured on each axis, so the
     * caller can trust whichever it trusts more.
     *
     * @throws BadArgumentError when a side of the physical size is not above zero
     */
    public static Sampling achievedFrom(Size pixels, PhysicalSize physical) {
        if (physical.width() <= 0 || physical.height() <= 0) {
            throw new BadArgumentError(
                    "a physical size needs both sides above zero", Map.of("physical", physical.toJson()));
        }
        return new Sampling(
                Sampling.ppiFrom(pixels.width(), physical.width(), physical.unit()),
                Sampling.ppiFrom(pixels.height(), physical.height(), physical.unit()));
    }

    /**
     * How big a sheet is on paper, off the quad the detector fitted round it: the mean of its top and
     * bottom edges across and of its left and right edges down, each over the frequency on its own
     * axis. Measuring the edges rather than the box round the quad keeps a turned sheet from reading
     * larger than it is.
     *
     * @return in inches
     */
    public static PhysicalSize sheetSize(Quad quad, Sampling sampling) {
        double[] edges = quad.edgeLengths();
        double across = (edges[0] + edges[2]) / 2;
        double down = (edges[1] + edges[3]) / 2;
        return new PhysicalSize(across / sampling.x(), down / sampling.y(), Unit.INCH);
    }

    /**
     * A sheet's size the way a work order quotes it, such as {@code 17.0 in by 14.0 in, an
     * atlasPlate} or {@code 30.2 in by 21.0 in, no nominal size}, one place in the given unit, with
     * {@code turned} after the name when the sheet lies the other way round from it.
     */
    public static String describeSheet(PhysicalSize size, Unit unit) {
        String measured = describeSize(inUnit(size, unit), 1);
        return matchNominal(size)
                .map(match -> measured + ", " + article(match.name()) + " " + match.name()
                        + (match.rotated() ? " turned" : ""))
                .orElse(measured + ", no nominal size");
    }

    private static String article(String name) {
        return "aeiouAEIOU".indexOf(name.charAt(0)) >= 0 ? "an" : "a";
    }

    /** Both sides at the report precision of the unit, such as {@code 17.0000 in by 14.0000 in}. */
    public static String describeSize(PhysicalSize size) {
        return Units.formatLength(size.width(), size.unit()) + " by " + Units.formatLength(size.height(), size.unit());
    }

    /** Both sides to the given places, such as {@code 431.8 mm by 355.6 mm} for one place. */
    public static String describeSize(PhysicalSize size, int places) {
        return Units.formatLength(size.width(), size.unit(), places)
                + " by "
                + Units.formatLength(size.height(), size.unit(), places);
    }

    /**
     * How far a measured sheet is from a nominal one.
     *
     * @param error the worse of the two sides' differences, in inches
     * @param rotated true when the sheet is closer to the nominal size turned a quarter
     */
    public record Mismatch(double error, boolean rotated) {}

    /**
     * The nominal size a measured sheet was taken for.
     *
     * @param name the name in {@link Units#SHEET_SIZES}, such as {@code atlasPlate}
     * @param error how far the sheet is from it, in inches, as {@link PhysicalSizes#sizeError} has it
     * @param rotated true when the sheet lies the other way round from the nominal size
     */
    public record NominalMatch(String name, double error, boolean rotated) {
        /** A match of the given parts. */
        public NominalMatch {
            Objects.requireNonNull(name, "name");
        }
    }
}
