package io.github.rkaushikdev.decklewright.detect;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.util.Guards;
import java.util.List;

/** Which neighbours of a pixel count as touching it when the mask is split into components. */
public enum Connectivity {
    /** The four pixels that share an edge with it. */
    FOUR(4),
    /** The eight pixels that share an edge or a corner with it, so diagonal neighbours join. */
    EIGHT(8);

    private static final List<Integer> COUNTS = List.of(4, 8);

    private final int neighbours;

    Connectivity(int neighbours) {
        this.neighbours = neighbours;
    }

    /**
     * The connectivity of the given number of neighbours.
     *
     * @throws BadArgumentError when the count is neither four nor eight
     */
    public static Connectivity of(int neighbours) {
        return Guards.requireOneOf(neighbours, COUNTS, "connectivity") == 4 ? FOUR : EIGHT;
    }

    /** How many neighbours touch a pixel: four or eight. */
    public int neighbours() {
        return neighbours;
    }
}
