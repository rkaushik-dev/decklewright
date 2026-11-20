package io.github.rkaushikdev.decklewright.derive;

import io.github.rkaushikdev.decklewright.raster.Surface;
import java.util.List;
import java.util.Objects;

/**
 * Every derivative a profile asks for, rendered off one cut.
 *
 * @param master the cut every derivative came off
 * @param cut what was done to the capture to make the cut, in order; copied
 * @param filled true when the crop reached past the capture and the rest was filled
 * @param rendered one per recipe, in the order of the recipes; copied
 * @see Rendering#renderAll
 */
public record RenderedSet(Surface master, List<String> cut, boolean filled, List<Rendered> rendered) {
    /** A rendered set of the given parts; the lists are copied. */
    public RenderedSet {
        Objects.requireNonNull(master, "master");
        cut = List.copyOf(cut);
        rendered = List.copyOf(rendered);
    }
}
