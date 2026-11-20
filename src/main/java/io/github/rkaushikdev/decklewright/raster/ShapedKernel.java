package io.github.rkaushikdev.decklewright.raster;

import java.util.Objects;
import java.util.function.DoubleUnaryOperator;

/** A kernel defined by its weight function, printed by name. */
record ShapedKernel(String name, double support, DoubleUnaryOperator shape) implements Kernel {
    ShapedKernel {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(shape, "shape");
    }

    @Override
    public double weight(double t) {
        return shape.applyAsDouble(t);
    }

    @Override
    public String toString() {
        return name;
    }
}
