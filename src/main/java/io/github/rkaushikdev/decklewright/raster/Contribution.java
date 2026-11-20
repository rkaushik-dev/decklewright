package io.github.rkaushikdev.decklewright.raster;

import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.Arrays;

/**
 * The taps that make one output sample: which source samples it reads and how much of each.
 *
 * <p>The weights add up to one. A tap list is a value, compared by its contents, and never
 * changes once built; {@link #indices()} and {@link #weights()} hand out copies, while {@link
 * #index(int)} and {@link #weight(int)} read one tap without copying, which is what a resampler's
 * inner loop wants.
 */
public final class Contribution {
    private final int start;
    private final int[] indices;
    private final double[] weights;

    /** Takes ownership of the two arrays, which must be the same length. */
    Contribution(int start, int[] indices, double[] weights) {
        this.start = start;
        this.indices = indices;
        this.weights = weights;
    }

    /** The first source position the kernel reached, before any edge policy moved it. */
    public int start() {
        return start;
    }

    /** How many taps there are. */
    public int size() {
        return indices.length;
    }

    /** The source sample the {@code k}th tap reads. */
    public int index(int k) {
        return indices[k];
    }

    /** How much of its source sample the {@code k}th tap takes. */
    public double weight(int k) {
        return weights[k];
    }

    /** The source sample of every tap, in a new array. */
    public int[] indices() {
        return indices.clone();
    }

    /** The weight of every tap, in a new array. */
    public double[] weights() {
        return weights.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Contribution that
                && start == that.start
                && Arrays.equals(indices, that.indices)
                && Arrays.equals(weights, that.weights);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * start + Arrays.hashCode(indices)) + Arrays.hashCode(weights);
    }

    /** The start and the taps, such as {@code from 0: 0 x 0.5, 1 x 0.5}. */
    @Override
    public String toString() {
        StringBuilder out = new StringBuilder("from ").append(start).append(':');
        for (int k = 0; k < indices.length; k += 1) {
            out.append(k == 0 ? " " : ", ").append(indices[k]).append(" x ").append(NumberText.of(weights[k]));
        }
        return out.toString();
    }
}
