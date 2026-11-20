package io.github.rkaushikdev.decklewright.raster.ops;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.raster.EdgeMode;
import io.github.rkaushikdev.decklewright.raster.Kernel;
import io.github.rkaushikdev.decklewright.raster.Kernels;
import java.util.Objects;

/**
 * How a resample builds its taps.
 *
 * @param kernel the kernel both passes use
 * @param edge what the taps read past the edge of the source
 */
public record ResampleOptions(Kernel kernel, EdgeMode edge) {
    /** Options of the given parts. */
    public ResampleOptions {
        Objects.requireNonNull(kernel, "kernel");
        Objects.requireNonNull(edge, "edge");
    }

    /** {@linkplain Kernels#CATMULL_ROM Catmull Rom}, clamped at the edge: what a service copy uses. */
    public static ResampleOptions defaults() {
        return new ResampleOptions(Kernels.CATMULL_ROM, EdgeMode.CLAMP);
    }

    /** These options with another kernel. */
    public ResampleOptions withKernel(Kernel kernel) {
        return new ResampleOptions(kernel, edge);
    }

    /**
     * These options with the kernel a recipe names.
     *
     * @throws BadArgumentError when the name is empty or names no kernel
     */
    public ResampleOptions withKernel(String name) {
        return new ResampleOptions(Kernels.kernelFor(name), edge);
    }

    /** These options with another edge policy. */
    public ResampleOptions withEdge(EdgeMode edge) {
        return new ResampleOptions(kernel, edge);
    }
}
