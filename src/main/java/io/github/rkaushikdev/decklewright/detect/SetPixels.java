package io.github.rkaushikdev.decklewright.detect;

/**
 * The positions of a sample of a mask's set pixels, which the skew projection scores.
 *
 * <p>Two parallel runs of coordinates, one pair per pixel, in the row by row order the pixels were
 * met. The runs are held privately and handed out as copies.
 *
 * @see Deskew#sampleSetPixels(Mask)
 */
public final class SetPixels {
    private final double[] xs;
    private final double[] ys;

    /** Takes ownership of the two runs, which must be the same length. */
    SetPixels(double[] xs, double[] ys) {
        this.xs = xs;
        this.ys = ys;
    }

    /** How many pixels were taken. */
    public int count() {
        return xs.length;
    }

    /** The column of every pixel taken, in a new array. */
    public double[] xs() {
        return xs.clone();
    }

    /** The row of every pixel taken, in a new array. */
    public double[] ys() {
        return ys.clone();
    }

    /** The column of the pixel at the index, without a copy. */
    double x(int index) {
        return xs[index];
    }

    /** The row of the pixel at the index, without a copy. */
    double y(int index) {
        return ys[index];
    }
}
