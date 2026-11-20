package io.github.rkaushikdev.decklewright.derive;

import io.github.rkaushikdev.decklewright.raster.Fill;
import io.github.rkaushikdev.decklewright.raster.SampleMode;
import java.util.Objects;

/**
 * How the capture is straightened and cut before any derivative is rendered.
 *
 * @param sample how the straightening reads the capture; the reductions choose their own kernel
 * @param fill what the corners a straightening uncovers, and any part of the crop past the edge of
 *     the capture, are filled with
 * @see Rendering#cutMaster
 */
public record CutOptions(SampleMode sample, Fill fill) {
    /** Options of the given parts. */
    public CutOptions {
        Objects.requireNonNull(sample, "sample");
        Objects.requireNonNull(fill, "fill");
    }

    /** Straightened bilinearly, and filled with black. */
    public static CutOptions defaults() {
        return new CutOptions(SampleMode.BILINEAR, Fill.uniform(0));
    }

    /** These options with another way of reading the capture. */
    public CutOptions withSample(SampleMode sample) {
        return new CutOptions(sample, fill);
    }

    /** These options with another fill. */
    public CutOptions withFill(Fill fill) {
        return new CutOptions(sample, fill);
    }
}
