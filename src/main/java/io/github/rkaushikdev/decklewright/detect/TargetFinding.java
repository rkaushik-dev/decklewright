package io.github.rkaushikdev.decklewright.detect;

import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The step wedge found lying on the bed.
 *
 * @param rect the wedge's bounds, in capture pixels
 * @param patches every patch of the wedge, read along its long axis; copied
 * @param range the difference between the lightest and the darkest patch, in sample values
 * @param monotone true when the patches step one way, which every wedge that is found does
 * @see Targets#locateTarget(io.github.rkaushikdev.decklewright.raster.Surface, Mask)
 */
public record TargetFinding(Rect rect, List<Patch> patches, double range, boolean monotone) implements JsonValue {
    /** A finding of the given parts. */
    public TargetFinding {
        Objects.requireNonNull(rect, "rect");
        patches = List.copyOf(patches);
    }

    /** {@code {rect, patches, range, monotone}}, in a new map the caller may keep. */
    @Override
    public Map<String, Object> toJson() {
        List<Object> cells = new ArrayList<>(patches.size());
        patches.forEach(patch -> cells.add(patch.toJson()));
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("rect", rect.toJson());
        json.put("patches", cells);
        json.put("range", range);
        json.put("monotone", monotone);
        return json;
    }
}
