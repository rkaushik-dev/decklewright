package io.github.rkaushikdev.decklewright.raster;

import java.util.function.DoubleUnaryOperator;

/**
 * A resampling kernel: a weight for every offset from an output sample's centre, zero beyond the
 * support.
 *
 * <p>The ones the lab uses are in {@link Kernels}; {@link #of(String, double, DoubleUnaryOperator)}
 * makes another, and anything that takes a kernel takes it as readily as a named one.
 */
public interface Kernel {
    /** The name a recipe uses, such as {@code catmullRom}. */
    String name();

    /** How far from the centre, in source samples, the weight can be other than zero. */
    double support();

    /** The weight at an offset from the centre, in source samples. */
    double weight(double t);

    /** A kernel of the given shape, which prints as its name. */
    static Kernel of(String name, double support, DoubleUnaryOperator weight) {
        return new ShapedKernel(name, support, weight);
    }
}
