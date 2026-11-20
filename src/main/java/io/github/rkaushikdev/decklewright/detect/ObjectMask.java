package io.github.rkaushikdev.decklewright.detect;

import java.util.Objects;

/**
 * Everything on a capture that stands out from the bed, and the bed it was measured against.
 *
 * @param mask set wherever a pixel is far enough from the bed to be object
 * @param background the bed the pixels were compared with: the one the caller gave, or the one
 *     estimated from the capture
 * @see Backgrounds#objectMask(io.github.rkaushikdev.decklewright.raster.Surface)
 */
public record ObjectMask(Mask mask, Background background) {
    /** A result of the given parts. */
    public ObjectMask {
        Objects.requireNonNull(mask, "mask");
        Objects.requireNonNull(background, "background");
    }
}
