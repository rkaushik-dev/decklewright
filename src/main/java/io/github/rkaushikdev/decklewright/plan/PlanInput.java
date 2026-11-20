package io.github.rkaushikdev.decklewright.plan;

import io.github.rkaushikdev.decklewright.geom.Angle;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import java.util.Objects;

/**
 * What a rule set plans from: the capture, the anchor the crop starts at, the frequency the capture
 * was taken at, and the turn to take out before cropping. Every rect is in the frame the render will
 * cut in.
 *
 * @param capture the whole capture, in pixels
 * @param anchor where the crop starts, which needs some area
 * @param sampling the frequency the capture was taken at
 * @param deskew the turn to take out before cropping
 * @see Engine#planWith(java.util.List, PlanInput)
 */
public record PlanInput(Rect capture, Rect anchor, Sampling sampling, Angle deskew) {

    /** An input of the given parts. */
    public PlanInput {
        Objects.requireNonNull(capture, "capture");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(sampling, "sampling");
        Objects.requireNonNull(deskew, "deskew");
    }

    /** An input with no turn to take out. */
    public static PlanInput of(Rect capture, Rect anchor, Sampling sampling) {
        return new PlanInput(capture, anchor, sampling, Angle.zero());
    }

    /** This input with another turn to take out. */
    public PlanInput withDeskew(Angle deskew) {
        return new PlanInput(capture, anchor, sampling, deskew);
    }
}
