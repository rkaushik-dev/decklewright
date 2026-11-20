package io.github.rkaushikdev.decklewright.detect;

import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The ruled border a map sheet is drawn inside, as found on a capture.
 *
 * @param rect the border, in capture pixels; a side that was not found falls back to the paper edge
 * @param contrast how strong each side's ruled line was, in sample values, top then clockwise
 * @param missing the sides where nothing convincing was found, top then clockwise
 * @see Neatlines#findNeatline(io.github.rkaushikdev.decklewright.raster.Surface, Rect)
 */
public record Neatline(Rect rect, Map<Side, Double> contrast, List<Side> missing) implements JsonValue {
    /** A neatline of the given parts; the contrast and the missing sides are copied. */
    public Neatline {
        Objects.requireNonNull(rect, "rect");
        Map<Side, Double> bySide = new EnumMap<>(Side.class);
        bySide.putAll(contrast);
        contrast = Collections.unmodifiableMap(bySide);
        missing = List.copyOf(missing);
    }

    /** True when the ruled line was found on all four sides. */
    public boolean complete() {
        return missing.isEmpty();
    }

    /**
     * {@code {rect, contrast: {top, right, bottom, left}, missing, complete}}, in a new map the
     * caller may keep.
     */
    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> sides = new LinkedHashMap<>();
        contrast.forEach((side, value) -> sides.put(side.label(), value));
        List<Object> absent = new ArrayList<>();
        missing.forEach(side -> absent.add(side.label()));
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("rect", rect.toJson());
        json.put("contrast", sides);
        json.put("missing", absent);
        json.put("complete", complete());
        return json;
    }
}
