package io.github.rkaushikdev.decklewright.raster.ops;

import io.github.rkaushikdev.decklewright.raster.EdgeMode;
import io.github.rkaushikdev.decklewright.raster.Fill;
import io.github.rkaushikdev.decklewright.raster.SampleMode;
import java.util.Objects;

/**
 * How a rotation resamples what is left after the quarter turns.
 *
 * @param expand grow the canvas so no corner is lost
 * @param sample how each output sample is read from the source
 * @param edge what a read past the edge of the source answers
 * @param background what a {@linkplain EdgeMode#CONSTANT constant} edge answers, such as the
 *     corners a rotation uncovers
 */
public record RotateOptions(boolean expand, SampleMode sample, EdgeMode edge, Fill background) {
    /** Options of the given parts. */
    public RotateOptions {
        Objects.requireNonNull(sample, "sample");
        Objects.requireNonNull(edge, "edge");
        Objects.requireNonNull(background, "background");
    }

    /** An expanded canvas, read bilinearly, with black corners. */
    public static RotateOptions defaults() {
        return new RotateOptions(true, SampleMode.BILINEAR, EdgeMode.CONSTANT, Fill.uniform(0));
    }

    /** These options, growing the canvas or not. */
    public RotateOptions withExpand(boolean expand) {
        return new RotateOptions(expand, sample, edge, background);
    }

    /** These options with another way of reading the source. */
    public RotateOptions withSample(SampleMode sample) {
        return new RotateOptions(expand, sample, edge, background);
    }

    /** These options with another edge policy. */
    public RotateOptions withEdge(EdgeMode edge) {
        return new RotateOptions(expand, sample, edge, background);
    }

    /** These options with another background. */
    public RotateOptions withBackground(Fill background) {
        return new RotateOptions(expand, sample, edge, background);
    }

    /** These options with the same background value in every channel. */
    public RotateOptions withBackground(double value) {
        return new RotateOptions(expand, sample, edge, Fill.uniform(value));
    }
}
