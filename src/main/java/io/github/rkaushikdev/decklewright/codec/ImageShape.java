package io.github.rkaushikdev.decklewright.codec;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.UnsupportedError;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Layouts;
import io.github.rkaushikdev.decklewright.util.Guards;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The shape of an image as its header states it, read without decoding a sample: what the intake
 * stage looks at before it decides whether a four hundred megabyte master is worth loading.
 *
 * @param width pixels across, above zero
 * @param height pixels down, above zero
 * @param layout how the channels of each pixel are laid out
 * @param depth bits per sample, eight or sixteen
 */
public record ImageShape(int width, int height, Layout layout, int depth) {
    /**
     * A shape of the given parts.
     *
     * @throws BadArgumentError when an extent is not above zero or the depth is not one the lab
     *     works in
     */
    public ImageShape {
        Guards.requirePositiveInteger(width, "width");
        Guards.requirePositiveInteger(height, "height");
        Objects.requireNonNull(layout, "layout");
        Layouts.requireDepth(depth);
    }

    /**
     * The shape a header declares, with the extent as the header's numbers.
     *
     * <p>A header is free to claim any extent, including one no surface could be made with. Such a
     * claim is refused here with the surface's own words, so a probe catches it as surely as a read
     * would.
     *
     * @throws BadArgumentError when an extent is not a whole number above zero, or the depth is not
     *     one the lab works in
     * @throws UnsupportedError when an extent is more pixels than one buffer can hold
     */
    public static ImageShape of(double width, double height, Layout layout, int depth) {
        Guards.requirePositiveInteger(width, "width");
        Guards.requirePositiveInteger(height, "height");
        Objects.requireNonNull(layout, "layout");
        if (width > Integer.MAX_VALUE || height > Integer.MAX_VALUE) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("width", width);
            details.put("height", height);
            details.put("channels", layout.channelCount());
            details.put("samples", width * height * layout.channelCount());
            throw new UnsupportedError("that surface holds more samples than one buffer can", details);
        }
        return new ImageShape((int) width, (int) height, layout, depth);
    }
}
