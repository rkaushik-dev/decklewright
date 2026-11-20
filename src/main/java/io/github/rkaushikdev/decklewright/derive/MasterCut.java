package io.github.rkaushikdev.decklewright.derive;

import io.github.rkaushikdev.decklewright.raster.Surface;
import java.util.List;
import java.util.Objects;

/**
 * The capture straightened and cut to the plan, which every derivative starts from.
 *
 * @param surface the cut, at the size the plan asked for
 * @param steps what was done, in order, in words; copied
 * @param filled true when the crop reached past the capture and the rest was filled
 * @see Rendering#cutMaster
 */
public record MasterCut(Surface surface, List<String> steps, boolean filled) {
    /** A cut of the given parts; the steps are copied. */
    public MasterCut {
        Objects.requireNonNull(surface, "surface");
        steps = List.copyOf(steps);
    }
}
