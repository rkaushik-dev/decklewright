package io.github.rkaushikdev.decklewright.derive;

import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.raster.Surface;
import java.util.List;
import java.util.Objects;

/**
 * One derivative rendered off the cut master, before it is encoded.
 *
 * <p>The frequency travels with the pixels: a service copy at half the linear size of the master is
 * at half its frequency, and that is the frequency its file and its sidecar entry carry.
 *
 * @param name the derivative's name in the profile, such as {@code master} or {@code thumb}
 * @param surface the pixels
 * @param sampling the frequency the pixels are at
 * @param steps what was done to the master to make it, in order; copied
 * @param scale its linear size against the cut master, 1 when it was not resized
 * @see Rendering#renderDerivative
 */
public record Rendered(String name, Surface surface, Sampling sampling, List<String> steps, double scale) {
    /** A rendered derivative of the given parts; the steps are copied. */
    public Rendered {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(sampling, "sampling");
        steps = List.copyOf(steps);
    }
}
