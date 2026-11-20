package io.github.rkaushikdev.decklewright.detect;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.DetectionError;
import io.github.rkaushikdev.decklewright.geom.Angle;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.measure.Unit;
import io.github.rkaushikdev.decklewright.measure.Units;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.Num;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The scale bar that lies on the bed beside every capture.
 *
 * <p>It is a machined steel rule of a known length, and it is the only object in the frame whose
 * true size the lab is certain of. Finding it gives a second opinion on sampling frequency that does
 * not come from the scanner driver, which is worth having because the driver has been wrong twice.
 *
 * <p>It is picked out by shape: long, thin, solid, and much thinner than anything else that is not
 * bed.
 */
public final class ScaleBars {
    /** How far, as a share of the claim, a measured frequency may differ unless the caller says otherwise. */
    private static final double DEFAULT_TOLERANCE = 0.02;

    private ScaleBars() {}

    /**
     * The scale bar, judged by the default shape.
     *
     * @see #findScaleBar(Mask, ScaleBarOptions)
     */
    public static Optional<ScaleBarFinding> findScaleBar(Mask mask) {
        return findScaleBar(mask, ScaleBarOptions.defaults());
    }

    /**
     * The longest blob that is long, thin and solid enough to be the rule; the first one met wins a
     * tie.
     *
     * @return empty when no blob looks like a rule
     */
    public static Optional<ScaleBarFinding> findScaleBar(Mask mask, ScaleBarOptions options) {
        Set<Integer> skip = options.ignore();
        ScaleBarFinding best = null;
        for (Component component : Components.labelComponents(mask).components()) {
            if (skip.contains(component.id())) {
                continue;
            }
            Rect bounds = component.bounds();
            double length = Math.max(bounds.width(), bounds.height());
            double thickness = Math.min(bounds.width(), bounds.height());
            if (thickness == 0) {
                continue;
            }
            double aspect = length / thickness;
            if (aspect < options.minimumAspect()
                    || length < options.minimumLength()
                    || Components.fillRatio(component) < options.minimumFill()) {
                continue;
            }

            boolean horizontal = bounds.width() >= bounds.height();
            ScaleBarFinding finding = new ScaleBarFinding(bounds, length, aspect, Angle.degrees(horizontal ? 0 : 90));
            if (best == null || finding.lengthPixels() > best.lengthPixels()) {
                best = finding;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * The sampling frequency the rule implies, the same on both axes.
     *
     * @param length the true length of the rule
     * @param unit the unit the length is in
     * @throws BadArgumentError when the length is not a finite number above zero
     * @throws DetectionError when the bar found has no length
     */
    public static Sampling samplingFromBar(ScaleBarFinding bar, double length, Unit unit) {
        Guards.requirePositive(length, "length");
        Objects.requireNonNull(unit, "unit");
        if (bar.lengthPixels() <= 0) {
            throw new DetectionError(
                    "the scale bar has no length to measure",
                    Map.of("bar", bar.rect().toJson()));
        }
        return Sampling.of(bar.lengthPixels() / Units.toInches(length, unit));
    }

    /**
     * Compares a measured frequency with a claimed one, allowing two per cent either way.
     *
     * @see #checkAgainstClaim(Sampling, Sampling, double)
     */
    public static ClaimCheck checkAgainstClaim(Sampling measured, Sampling claimed) {
        return checkAgainstClaim(measured, claimed, DEFAULT_TOLERANCE);
    }

    /**
     * Compare a measured frequency against what the scanner claimed, across the capture. The rule is
     * the more trustworthy of the two, so a disagreement is reported as a fault in the claim rather
     * than in the measurement.
     *
     * @param tolerance as a share of the claim
     */
    public static ClaimCheck checkAgainstClaim(Sampling measured, Sampling claimed, double tolerance) {
        double error = (measured.x() - claimed.x()) / claimed.x();
        return new ClaimCheck(Math.abs(error) <= tolerance, error, measured.x(), claimed.x());
    }

    /**
     * The bar is graduated, so its own ticks give a second reading. Counting the runs of ink along
     * its middle is a cheap check that what was found really is a rule and not the spine of a
     * folder.
     *
     * @param mask the mask the bar was found in
     * @param inkMask set where a pixel is darker than the bar face
     * @return how many separate runs of inked bar pixels the walk crossed
     */
    public static int countTicks(Mask mask, ScaleBarFinding bar, Mask inkMask) {
        Rect rect = bar.rect();
        boolean horizontal = rect.width() >= rect.height();
        double along = horizontal ? rect.width() : rect.height();
        double middle =
                horizontal ? Math.floor(rect.top() + rect.height() / 2) : Math.floor(rect.left() + rect.width() / 2);

        int ticks = 0;
        boolean inRun = false;
        for (int i = 0; i < along; i += 1) {
            double x = horizontal ? rect.left() + i : middle;
            double y = horizontal ? middle : rect.top() + i;
            boolean inked = pixelAt(inkMask, x, y) == 1 && pixelAt(mask, x, y) == 1;
            if (inked && !inRun) {
                ticks += 1;
            }
            inRun = inked;
        }
        return ticks;
    }

    /**
     * One line for the log, such as {@code 400 px for 1 in, so 400 ppi}.
     *
     * @throws BadArgumentError when the length is not a finite number above zero
     * @throws DetectionError when the bar found has no length
     */
    public static String describeBar(ScaleBarFinding bar, double length, Unit unit) {
        Sampling sampling = samplingFromBar(bar, length, unit);
        return NumberText.of(Num.roundHalfUp(bar.lengthPixels()))
                + " px for "
                + NumberText.of(length)
                + " "
                + unit.label()
                + ", so "
                + sampling;
    }

    /**
     * The mask's byte at a position that need not be whole pixels, because nothing makes a bar's rect
     * whole. Off the mask reads zero. On it, the position is read at the buffer offset it works out
     * to, and an offset that is not a whole number reads zero, since no sample sits there.
     */
    private static int pixelAt(Mask mask, double x, double y) {
        if (x < 0 || y < 0 || x >= mask.width() || y >= mask.height()) {
            return 0;
        }
        double index = y * mask.width() + x;
        return index == Math.rint(index) ? mask.data[(int) index] & 0xff : 0;
    }
}
