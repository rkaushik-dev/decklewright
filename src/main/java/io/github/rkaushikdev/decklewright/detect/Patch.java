package io.github.rkaushikdev.decklewright.detect;

import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One grey patch of a step wedge, as read off the capture.
 *
 * @param rect the part of the patch that was read, in capture pixels, or the whole cell when it fell
 *     off the capture
 * @param mean the mean of each channel, in channel order; empty when nothing could be read
 * @param stdDev the spread of each channel, in channel order; empty when nothing could be read
 * @see Targets#readPatches(io.github.rkaushikdev.decklewright.raster.Surface, Rect, int)
 */
public record Patch(Rect rect, List<Double> mean, List<Double> stdDev) implements JsonValue {
    /** A patch of the given parts; the lists are copied. */
    public Patch {
        Objects.requireNonNull(rect, "rect");
        mean = List.copyOf(mean);
        stdDev = List.copyOf(stdDev);
    }

    /** {@code {rect, mean, stdDev}}, in a new map the caller may keep. */
    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("rect", rect.toJson());
        json.put("mean", new ArrayList<Object>(mean));
        json.put("stdDev", new ArrayList<Object>(stdDev));
        return json;
    }
}
