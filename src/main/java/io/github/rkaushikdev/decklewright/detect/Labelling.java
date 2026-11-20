package io.github.rkaushikdev.decklewright.detect;

import java.util.List;
import java.util.Objects;

/**
 * A mask split into its connected components: a label for every pixel and a summary of every
 * blob.
 *
 * <p>Labels start at one. Zero means background, which lets the label buffer double as a mask.
 *
 * @see Components#labelComponents(Mask)
 */
public final class Labelling {
    private final int[] labels;
    private final List<Component> components;

    Labelling(int[] labels, List<Component> components) {
        this.labels = Objects.requireNonNull(labels, "labels");
        this.components = List.copyOf(components);
    }

    /** Every blob, in label order, in a list that cannot be changed. */
    public List<Component> components() {
        return components;
    }

    /**
     * The label of one pixel: the id of the blob it belongs to, or zero for background.
     *
     * @param index the pixel's position in the mask's buffer, {@code y * width + x}
     * @throws IndexOutOfBoundsException when the index is outside the mask
     */
    public int label(int index) {
        return labels[index];
    }

    /** The labels themselves, for the passes in this layer that walk every pixel. */
    int[] labels() {
        return labels;
    }
}
