package io.github.rkaushikdev.decklewright.derive;

import io.github.rkaushikdev.decklewright.raster.Layout;
import java.util.List;

/**
 * What one format can hold, so a recipe can be refused before it is run.
 *
 * @param depths the bits per sample it holds; copied
 * @param layouts the channel layouts it holds; copied
 * @param carriesFrequency true when the file itself records the sampling frequency
 * @see Recipes#limitsFor(String)
 */
public record FormatLimits(List<Integer> depths, List<Layout> layouts, boolean carriesFrequency) {
    /** Limits of the given parts; the lists are copied. */
    public FormatLimits {
        depths = List.copyOf(depths);
        layouts = List.copyOf(layouts);
    }
}
