package io.github.rkaushikdev.decklewright.detect;

/**
 * How the projection search for skew runs. Each stage reads the options it needs: the coarse
 * sweep reads the range and step, the refinement reads the window and tolerance, and both read the
 * sample and outline. A full measurement refines over a window of one and a half steps either side
 * of the sweep's answer, whatever the window says.
 *
 * @param range how many degrees either side of zero the sweep covers
 * @param step degrees between the sweep's candidates
 * @param window how many degrees either side of its starting angle the refinement searches
 * @param tolerance the refinement stops once its bracket is no wider than this many degrees
 * @param sample take every nth set pixel
 * @param outline measure the outline of the mask rather than its fill
 * @see Deskew#measureSkew(Mask, SkewOptions)
 */
public record SkewOptions(double range, double step, double window, double tolerance, int sample, boolean outline) {
    /**
     * Twelve degrees either way in half degree steps, refined to a hundredth of a degree within
     * half a degree, over every pixel of the outline.
     */
    public static SkewOptions defaults() {
        return new SkewOptions(12, 0.5, 0.5, 0.01, 1, true);
    }

    /** These options, sweeping another range either side of zero. */
    public SkewOptions withRange(double range) {
        return new SkewOptions(range, step, window, tolerance, sample, outline);
    }

    /** These options, sweeping in another step. */
    public SkewOptions withStep(double step) {
        return new SkewOptions(range, step, window, tolerance, sample, outline);
    }

    /** These options, refining within another window. */
    public SkewOptions withWindow(double window) {
        return new SkewOptions(range, step, window, tolerance, sample, outline);
    }

    /** These options, refining to another tolerance. */
    public SkewOptions withTolerance(double tolerance) {
        return new SkewOptions(range, step, window, tolerance, sample, outline);
    }

    /** These options, taking every nth set pixel. */
    public SkewOptions withSample(int sample) {
        return new SkewOptions(range, step, window, tolerance, sample, outline);
    }

    /** These options, measuring the outline or the fill. */
    public SkewOptions withOutline(boolean outline) {
        return new SkewOptions(range, step, window, tolerance, sample, outline);
    }
}
