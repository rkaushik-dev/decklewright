package io.github.rkaushikdev.decklewright.detect;

import java.util.Collection;
import java.util.Set;

/**
 * What a blob must look like to be taken for the step wedge.
 *
 * @param patches how many steps the wedge has
 * @param minimumArea how large it must be, in pixels
 * @param minimumRange how far apart its lightest and darkest patches must be, in sample values
 * @param minimumFill how solidly it must fill its own bounds, from zero to one
 * @param ignore the ids of components to skip, such as the sheet; copied
 * @see Targets#locateTarget(io.github.rkaushikdev.decklewright.raster.Surface, Mask, TargetOptions)
 */
public record TargetOptions(
        int patches, double minimumArea, double minimumRange, double minimumFill, Set<Integer> ignore) {
    /** Options of the given parts. */
    public TargetOptions {
        ignore = Set.copyOf(ignore);
    }

    /** Eight steps, two hundred pixels, a range of sixty and sixty per cent solid, skipping nothing. */
    public static TargetOptions defaults() {
        return new TargetOptions(8, 200, 60, 0.6, Set.of());
    }

    /** These options for a wedge of another number of steps. */
    public TargetOptions withPatches(int patches) {
        return new TargetOptions(patches, minimumArea, minimumRange, minimumFill, ignore);
    }

    /** These options with another smallest area. */
    public TargetOptions withMinimumArea(double minimumArea) {
        return new TargetOptions(patches, minimumArea, minimumRange, minimumFill, ignore);
    }

    /** These options with another smallest range. */
    public TargetOptions withMinimumRange(double minimumRange) {
        return new TargetOptions(patches, minimumArea, minimumRange, minimumFill, ignore);
    }

    /** These options with another smallest fill. */
    public TargetOptions withMinimumFill(double minimumFill) {
        return new TargetOptions(patches, minimumArea, minimumRange, minimumFill, ignore);
    }

    /** These options, skipping the components with these ids. */
    public TargetOptions withIgnore(Collection<Integer> ignore) {
        return new TargetOptions(patches, minimumArea, minimumRange, minimumFill, Set.copyOf(ignore));
    }
}
