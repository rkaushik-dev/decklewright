package io.github.rkaushikdev.decklewright.qc.checks;

import static io.github.rkaushikdev.decklewright.qc.Check.starsFor;

import io.github.rkaushikdev.decklewright.detect.TargetFinding;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.qc.Check;
import io.github.rkaushikdev.decklewright.qc.Measurement;
import io.github.rkaushikdev.decklewright.qc.Need;
import io.github.rkaushikdev.decklewright.qc.Patches;
import io.github.rkaushikdev.decklewright.qc.QcContext;
import io.github.rkaushikdev.decklewright.qc.Rung;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.util.Num;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Illumination uniformity.
 *
 * <p>Two lamps either side of a copy stand never light a thirty six inch sheet evenly. The
 * guidelines allow a spread and the collections that deliver into a viewer care about it more than
 * the ones that deliver plates, because a viewer shows two sheets side by side and a difference in
 * brightness between them is the first thing anybody sees.
 *
 * <p>It is measured on the bed rather than on the sheet: paper has its own tone and a sheet that is
 * genuinely darker at one end would fail a check that cannot tell the two apart. Any patch that
 * touches the sheet or the colour target is dropped for the same reason, and dropping them is not
 * optional: a grid over the whole capture measures the difference between paper and board, which is
 * two hundred percent and means nothing. The caller says what to keep off, because it is the caller
 * that knows what else was on the bed.
 */
public final class Illumination {
    /** Spread as a share of the mean, per star. */
    public static final List<Rung> ILLUMINATION_LADDER =
            List.of(new Rung(4, 0.05), new Rung(3, 0.1), new Rung(2, 0.2), new Rung(1, 0.35));

    private static final int ACROSS = 5;
    private static final int DOWN = 4;
    private static final List<String> CORNERS = List.of("top left", "top right", "bottom left", "bottom right");

    private Illumination() {}

    /**
     * The check over a grid five patches across and four down.
     *
     * @see #illuminationCheck(int, int)
     */
    public static Check illuminationCheck() {
        return illuminationCheck(ACROSS, DOWN);
    }

    /**
     * The spread of brightness across the bed, as a share of its mean: the darkest and lightest
     * patches of a grid over the bed, kept off everything in the context's {@code avoid}, held to the
     * profile's illumination tolerance. The bed is the whole capture when the context names none,
     * and without an {@code avoid} the sheet and the target are kept off.
     *
     * @param across how many grid cells across the bed
     * @param down how many down
     */
    public static Check illuminationCheck(int across, int down) {
        return Check.of("illumination", List.of(Need.SURFACE, Need.LIMITS), context -> measure(context, across, down));
    }

    /**
     * Which corner of the whole capture is darkest.
     *
     * @see #darkestCorner(Surface, Rect)
     */
    public static DarkCorner darkestCorner(Surface surface) {
        return darkestCorner(surface, surface.bounds());
    }

    /**
     * Which corner is darkest, which is what an operator needs in order to move a lamp. It is not a
     * check, it is the thing a failed check should have said. The region is split into a two by two
     * grid and the first channel read over each patch; on a tie the first corner, top left, wins.
     *
     * @return the darkest corner; {@code top left} with a mean that is not a number when no part of
     *     the region lies on the capture
     */
    public static DarkCorner darkestCorner(Surface surface, Rect region) {
        double[] means = Patches.patchMeans(surface, Patches.patchGrid(region, 2, 2));
        int at = 0;
        for (int i = 1; i < means.length; i += 1) {
            if (means[i] < means[at]) {
                at = i;
            }
        }
        return new DarkCorner(CORNERS.get(at), means.length == 0 ? Double.NaN : means[at]);
    }

    private static Measurement measure(QcContext context, int across, int down) {
        Surface surface = context.surface().orElseThrow();
        Rect region = context.bed().orElseGet(surface::bounds);
        List<Rect> avoid = context.avoid()
                .orElseGet(() -> Stream.of(context.sheet(), context.target().map(TargetFinding::rect))
                        .flatMap(Optional::stream)
                        .toList());
        double[] means = Patches.patchMeans(surface, Patches.gridOver(region, across, down, avoid));
        if (means.length < 2) {
            return new Measurement(Double.NaN, 0, "not enough of the bed to measure");
        }

        double low = Double.POSITIVE_INFINITY;
        double high = Double.NEGATIVE_INFINITY;
        for (double mean : means) {
            low = Math.min(low, mean);
            high = Math.max(high, mean);
        }
        double middle = Num.sum(means) / means.length;
        double spread = middle == 0 ? 0 : (high - low) / middle;

        double allowed = context.limits().orElseThrow().illuminationTolerance();
        int stars = spread <= allowed ? starsFor(spread, ILLUMINATION_LADDER) : 0;
        return new Measurement(
                spread,
                stars,
                NumberText.fixed(spread * 100, 1) + " percent across " + NumberText.of(means.length)
                        + " patches, against a limit of " + NumberText.fixed(allowed * 100, 1));
    }
}
