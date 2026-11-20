package io.github.rkaushikdev.decklewright.detect;

import io.github.rkaushikdev.decklewright.geom.Angle;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The steel rule found lying on the bed.
 *
 * @param rect the rule's bounds, in capture pixels
 * @param lengthPixels how long the rule is along itself, in pixels
 * @param aspect the long side over the short side
 * @param angle zero degrees for a rule lying across the capture, ninety for one lying down it
 * @see ScaleBars#findScaleBar(Mask)
 */
public record ScaleBarFinding(Rect rect, double lengthPixels, double aspect, Angle angle) implements JsonValue {
    /** A finding of the given parts. */
    public ScaleBarFinding {
        Objects.requireNonNull(rect, "rect");
        Objects.requireNonNull(angle, "angle");
    }

    /** {@code {rect, lengthPixels, aspect, angle}}, in a new map the caller may keep. */
    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("rect", rect.toJson());
        json.put("lengthPixels", lengthPixels);
        json.put("aspect", aspect);
        json.put("angle", angle.toJson());
        return json;
    }
}
