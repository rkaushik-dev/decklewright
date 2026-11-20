package io.github.rkaushikdev.decklewright.detect;

import java.util.Objects;
import java.util.Optional;

/**
 * The two patches of a wedge a gray balance check works from.
 *
 * @param light the lightest patch that is not clipped; empty when every patch is
 * @param dark the darkest patch that is not clipped; empty when every patch is
 * @see Targets#endPatches(java.util.List, double)
 */
public record EndPatches(Optional<Patch> light, Optional<Patch> dark) {
    /** The ends of the given patches. */
    public EndPatches {
        Objects.requireNonNull(light, "light");
        Objects.requireNonNull(dark, "dark");
    }
}
