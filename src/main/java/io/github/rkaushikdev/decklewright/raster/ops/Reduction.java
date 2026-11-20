package io.github.rkaushikdev.decklewright.raster.ops;

import io.github.rkaushikdev.decklewright.raster.Surface;
import java.util.Objects;

/**
 * What {@link Resampling#reduceInSteps} made, and how many halvings it took on the way.
 *
 * @param surface the reduced surface, at the size asked for
 * @param halvings how many box halvings ran before the final resample
 */
public record Reduction(Surface surface, int halvings) {
    /** A reduction of the given parts. */
    public Reduction {
        Objects.requireNonNull(surface, "surface");
    }
}
