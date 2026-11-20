package io.github.rkaushikdev.decklewright.detect;

import java.util.Objects;

/**
 * One component lifted out of a mask on its own.
 *
 * @param mask set exactly where the component's pixels are
 * @param component the component's summary
 * @see Components#largestComponent(Mask)
 */
public record ComponentMask(Mask mask, Component component) {
    /** A result of the given parts. */
    public ComponentMask {
        Objects.requireNonNull(mask, "mask");
        Objects.requireNonNull(component, "component");
    }
}
