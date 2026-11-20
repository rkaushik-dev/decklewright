package io.github.rkaushikdev.decklewright.plan.rules;

import io.github.rkaushikdev.decklewright.geom.Rect;
import java.util.Objects;
import java.util.Optional;

/**
 * What the detector found that a crop can be measured from, in the frame the plan is drawn in: the
 * paper edge, and the neatline when there is one.
 *
 * @param sheet the paper edge
 * @param neatline the printed border; empty when none was found or none was looked for
 * @see AnchorRules#anchorFor(io.github.rkaushikdev.decklewright.profile.Anchor, Landmarks)
 */
public record Landmarks(Rect sheet, Optional<Rect> neatline) {

    /** Landmarks of the given parts. */
    public Landmarks {
        Objects.requireNonNull(sheet, "sheet");
        Objects.requireNonNull(neatline, "neatline");
    }

    /** A paper edge and no neatline. */
    public static Landmarks of(Rect sheet) {
        return new Landmarks(sheet, Optional.empty());
    }

    /** A paper edge and the neatline found inside it. */
    public static Landmarks of(Rect sheet, Rect neatline) {
        return new Landmarks(sheet, Optional.of(neatline));
    }
}
