/**
 * The detection layer: finding the sheet on a capture, its corners and skew, the colour target, the
 * neatline and the scale bar.
 *
 * <p>The planner calls {@link io.github.rkaushikdev.decklewright.detect.Captures#readCapture} and
 * nothing else, and gets back a {@link io.github.rkaushikdev.decklewright.detect.CaptureReading}.
 * Everything else here is public because the steps are separately useful when something has gone
 * wrong and somebody is trying to work out which one: the {@link
 * io.github.rkaushikdev.decklewright.detect.Mask} and the bed estimate it is thresholded against
 * ({@link io.github.rkaushikdev.decklewright.detect.Backgrounds}), the cleanup ({@link
 * io.github.rkaushikdev.decklewright.detect.Morphology}), the blobs ({@link
 * io.github.rkaushikdev.decklewright.detect.Components}), the three skew measures ({@link
 * io.github.rkaushikdev.decklewright.detect.QuadFit}, {@link
 * io.github.rkaushikdev.decklewright.detect.Deskew} and {@link
 * io.github.rkaushikdev.decklewright.detect.Edges}) and the corners crossed from the fitted sides
 * ({@link io.github.rkaushikdev.decklewright.detect.Corners}), all arranged by {@link
 * io.github.rkaushikdev.decklewright.detect.Sheets}, and the detectors for what lies beside the
 * sheet ({@link io.github.rkaushikdev.decklewright.detect.Targets}, {@link
 * io.github.rkaushikdev.decklewright.detect.ScaleBars} and {@link
 * io.github.rkaushikdev.decklewright.detect.Neatlines}).
 *
 * <p>A detector that finds nothing it was looking for answers an empty optional. One that cannot do
 * its job at all, such as a sheet finder on an empty bed, throws a {@link
 * io.github.rkaushikdev.decklewright.DetectionError}.
 */
package io.github.rkaushikdev.decklewright.detect;
