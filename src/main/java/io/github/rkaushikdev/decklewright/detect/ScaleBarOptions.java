package io.github.rkaushikdev.decklewright.detect;

import java.util.Collection;
import java.util.Set;

/**
 * What a blob must look like to be taken for the scale bar.
 *
 * @param minimumAspect how many times longer than wide it must be
 * @param minimumLength how long it must be, in pixels
 * @param minimumFill how solidly it must fill its own bounds, from zero to one
 * @param ignore the ids of components to skip, such as the sheet; copied
 * @see ScaleBars#findScaleBar(Mask, ScaleBarOptions)
 */
public record ScaleBarOptions(double minimumAspect, double minimumLength, double minimumFill, Set<Integer> ignore) {
    /** Options of the given parts. */
    public ScaleBarOptions {
        ignore = Set.copyOf(ignore);
    }

    /** At least six times longer than wide, forty pixels long and seventy per cent solid, skipping nothing. */
    public static ScaleBarOptions defaults() {
        return new ScaleBarOptions(6, 40, 0.7, Set.of());
    }

    /** These options with another smallest aspect. */
    public ScaleBarOptions withMinimumAspect(double minimumAspect) {
        return new ScaleBarOptions(minimumAspect, minimumLength, minimumFill, ignore);
    }

    /** These options with another shortest length. */
    public ScaleBarOptions withMinimumLength(double minimumLength) {
        return new ScaleBarOptions(minimumAspect, minimumLength, minimumFill, ignore);
    }

    /** These options with another smallest fill. */
    public ScaleBarOptions withMinimumFill(double minimumFill) {
        return new ScaleBarOptions(minimumAspect, minimumLength, minimumFill, ignore);
    }

    /** These options, skipping the components with these ids. */
    public ScaleBarOptions withIgnore(Collection<Integer> ignore) {
        return new ScaleBarOptions(minimumAspect, minimumLength, minimumFill, Set.copyOf(ignore));
    }
}
