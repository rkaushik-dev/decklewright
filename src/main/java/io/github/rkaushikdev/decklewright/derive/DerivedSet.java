package io.github.rkaushikdev.decklewright.derive;

import java.util.List;

/**
 * Everything a profile asks for, rendered, encoded and named off one plan.
 *
 * @param files one per recipe, in the order of the recipes; copied
 * @param steps what was done, in order: the straightening and the cut first, then each
 *     derivative's own steps prefixed with its name, such as {@code thumb: to gray}; copied
 * @param bytes the size of every file together
 * @see Derivatives#deriveFromPlan
 */
public record DerivedSet(List<DerivedFile> files, List<String> steps, long bytes) {
    /** A set of the given parts; the lists are copied. */
    public DerivedSet {
        files = List.copyOf(files);
        steps = List.copyOf(steps);
    }
}
