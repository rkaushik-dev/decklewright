package io.github.rkaushikdev.decklewright.detect;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.GeometryError;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.raster.ChannelStats;
import io.github.rkaushikdev.decklewright.raster.Stats;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.raster.SurfaceView;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.Num;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The colour target that sits on the bed next to every capture.
 *
 * <p>The lab uses a step wedge with a row of grey patches running from paper white to near black.
 * The quality checks read tone off it, so it has to be found before any of them can run, and it has
 * to be found in a way that does not confuse it with the sheet, a stray envelope, or the scale bar.
 *
 * <p>The rule that works is the strip test: a target is a rectangle whose long axis divides into
 * patches that step monotonically. Nothing else on the bed does that.
 */
public final class Targets {
    /** The share of each cell ignored at its edges unless the caller says otherwise. */
    private static final double DEFAULT_MARGIN = 0.2;

    /** How many steps may go the wrong way unless the caller says otherwise. */
    private static final double DEFAULT_SLACK = 1;

    /** A patch averaging below this share of full scale is clipped dark. */
    private static final double CLIPPED_DARK = 0.02;

    /** A patch averaging above this share of full scale is clipped light. */
    private static final double CLIPPED_LIGHT = 0.98;

    private Targets() {}

    /**
     * The patches of a rect, ignoring a fifth of each cell at its edges.
     *
     * @see #readPatches(Surface, Rect, int, double)
     */
    public static List<Patch> readPatches(Surface surface, Rect rect, int count) {
        return readPatches(surface, rect, count, DEFAULT_MARGIN);
    }

    /**
     * Divide a rect along its long axis and measure each cell. A margin is taken off every cell so a
     * patch boundary that is a pixel out does not pull the reading. A cell that falls off the capture
     * comes back unread, with its whole rect and no means.
     *
     * @param count how many cells to divide the rect into
     * @param margin the share of each cell ignored at its edges
     * @throws BadArgumentError when the count is not a whole number above zero
     * @throws GeometryError when the margin leaves a cell no room across the rect
     */
    public static List<Patch> readPatches(Surface surface, Rect rect, int count, double margin) {
        Guards.requirePositiveInteger(count, "count");
        boolean horizontal = rect.width() >= rect.height();
        double span = horizontal ? rect.width() : rect.height();
        double origin = horizontal ? rect.left() : rect.top();
        double step = span / count;

        List<Patch> out = new ArrayList<>(count);
        for (int i = 0; i < count; i += 1) {
            double inset = step * margin;
            double from = Num.roundHalfUp(origin + i * step + inset);
            double to = Num.roundHalfUp(origin + (i + 1) * step - inset);
            double thin = Math.max(1, Num.roundHalfUp((horizontal ? rect.height() : rect.width()) * margin));

            Rect cell = horizontal
                    ? Rect.fromEdges(from, rect.top() + thin, Math.max(from + 1, to), rect.bottom() - thin)
                    : Rect.fromEdges(rect.left() + thin, from, rect.right() - thin, Math.max(from + 1, to));

            Rect clipped = cell.intersect(surface.bounds());
            if (clipped.isEmpty()) {
                out.add(new Patch(cell, List.of(), List.of()));
                continue;
            }
            List<ChannelStats> stats = Stats.allChannelStats(new SurfaceView(surface, clipped));
            out.add(new Patch(
                    clipped,
                    stats.stream().map(ChannelStats::mean).toList(),
                    stats.stream().map(ChannelStats::stdDev).toList()));
        }
        return List.copyOf(out);
    }

    /**
     * True when the first channel steps one way, allowing one step the other way.
     *
     * @see #stepsMonotonically(List, int, double)
     */
    public static boolean stepsMonotonically(List<Patch> patches) {
        return stepsMonotonically(patches, 0, DEFAULT_SLACK);
    }

    /**
     * True when the patches step one way, up or down, with at most {@code slack} steps going the
     * other way. A run with an unreadable patch never does.
     *
     * @param channel the channel to read
     * @param slack how many steps may go the wrong way
     */
    public static boolean stepsMonotonically(List<Patch> patches, int channel, double slack) {
        double[] values =
                patches.stream().mapToDouble(patch -> meanOf(patch, channel)).toArray();
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }

        int up = 0;
        int down = 0;
        for (int i = 1; i < values.length; i += 1) {
            if (values[i] > values[i - 1]) {
                up += 1;
            } else if (values[i] < values[i - 1]) {
                down += 1;
            }
        }
        return Math.min(up, down) <= slack;
    }

    /**
     * The range of the first channel.
     *
     * @see #patchRange(List, int)
     */
    public static double patchRange(List<Patch> patches) {
        return patchRange(patches, 0);
    }

    /**
     * The difference between the lightest and the darkest readable patch; zero when none could be
     * read.
     *
     * @param channel the channel to read
     */
    public static double patchRange(List<Patch> patches, int channel) {
        double[] values = patches.stream()
                .mapToDouble(patch -> meanOf(patch, channel))
                .filter(Double::isFinite)
                .toArray();
        if (values.length == 0) {
            return 0;
        }
        double lightest = Double.NEGATIVE_INFINITY;
        double darkest = Double.POSITIVE_INFINITY;
        for (double value : values) {
            lightest = Math.max(lightest, value);
            darkest = Math.min(darkest, value);
        }
        return lightest - darkest;
    }

    /**
     * The wedge, judged by the default shape.
     *
     * @see #locateTarget(Surface, Mask, TargetOptions)
     */
    public static Optional<TargetFinding> locateTarget(Surface surface, Mask mask) {
        return locateTarget(surface, mask, TargetOptions.defaults());
    }

    /**
     * Look through the components of a mask for the one that behaves like a step wedge, ignoring
     * anything the caller has already claimed as the sheet. Of several, the one with the widest range
     * wins, and the first one met wins a tie.
     *
     * @param mask everything that is not bed
     * @return empty when no blob behaves like a wedge
     * @throws BadArgumentError when the patch count is not a whole number above zero
     */
    public static Optional<TargetFinding> locateTarget(Surface surface, Mask mask, TargetOptions options) {
        Set<Integer> skip = options.ignore();
        TargetFinding best = null;
        for (Component component : Components.labelComponents(mask).components()) {
            if (skip.contains(component.id())
                    || component.area() < options.minimumArea()
                    || Components.fillRatio(component) < options.minimumFill()) {
                continue;
            }

            List<Patch> patches = readPatches(surface, component.bounds(), options.patches());
            double range = patchRange(patches);
            if (range < options.minimumRange() || !stepsMonotonically(patches)) {
                continue;
            }
            if (best == null || range > best.range()) {
                best = new TargetFinding(component.bounds(), patches, range, true);
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * The two patches a gray balance check works from: the lightest and the darkest whose first
     * channel is not clipped, within two per cent of either end of full scale.
     *
     * @param fullScale the largest sample value at the capture's depth
     * @return both empty when every patch is clipped or unreadable
     */
    public static EndPatches endPatches(List<Patch> patches, double fullScale) {
        List<Patch> usable = new ArrayList<>();
        for (Patch patch : patches) {
            if (!patch.mean().isEmpty()
                    && patch.mean().getFirst() > fullScale * CLIPPED_DARK
                    && patch.mean().getFirst() < fullScale * CLIPPED_LIGHT) {
                usable.add(patch);
            }
        }
        if (usable.isEmpty()) {
            return new EndPatches(Optional.empty(), Optional.empty());
        }
        usable.sort(Comparator.comparingDouble(patch -> patch.mean().getFirst()));
        return new EndPatches(Optional.of(usable.getLast()), Optional.of(usable.getFirst()));
    }

    /** The patch's mean in the channel, or not a number when it has none there. */
    private static double meanOf(Patch patch, int channel) {
        List<Double> mean = patch.mean();
        return channel >= 0 && channel < mean.size() ? mean.get(channel) : Double.NaN;
    }
}
