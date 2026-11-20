package io.github.rkaushikdev.decklewright.detect;

import java.util.Objects;

/**
 * The four sides of a sheet, each fitted on its own.
 *
 * @param top the line along the top side
 * @param right the line along the right side
 * @param bottom the line along the bottom side
 * @param left the line along the left side
 * @see Edges#edgeLines(Mask)
 */
public record SideLines(Line top, Line right, Line bottom, Line left) {
    /** The lines of the given sides. */
    public SideLines {
        Objects.requireNonNull(top, "top");
        Objects.requireNonNull(right, "right");
        Objects.requireNonNull(bottom, "bottom");
        Objects.requireNonNull(left, "left");
    }

    /** The line along the given side. */
    public Line get(Side side) {
        return switch (side) {
            case TOP -> top;
            case RIGHT -> right;
            case BOTTOM -> bottom;
            case LEFT -> left;
        };
    }
}
