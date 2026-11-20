package io.github.rkaushikdev.decklewright.detect;

import io.github.rkaushikdev.decklewright.geom.Angle;
import io.github.rkaushikdev.decklewright.geom.Quad;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Where the sheet is on a capture, how far it is turned, and how far to trust that.
 *
 * @param quad the sheet's corners, each moved onto the crossing of its fitted sides where that is
 *     safe
 * @param skew how far the sheet is turned, as the caliper fit measured it, between minus forty five
 *     and forty five degrees
 * @param mask the sheet's own pixels, cleaned of dust and with its holes filled
 * @param confidence between zero and one: the weakest of how well the sides fit, how well the sheet
 *     fills its rectangle and how well the three skew measures agree
 * @param spread how far apart the skew measures were, in degrees
 * @param touchesEdge true when the sheet runs off the bed
 * @param coverage the share of the capture the sheet takes
 * @param readings the three skew measures
 * @see Sheets#findSheet(io.github.rkaushikdev.decklewright.raster.Surface)
 */
public record SheetFinding(
        Quad quad,
        Angle skew,
        Mask mask,
        double confidence,
        double spread,
        boolean touchesEdge,
        double coverage,
        SkewReadings readings)
        implements JsonValue {
    /** A finding of the given parts. */
    public SheetFinding {
        Objects.requireNonNull(quad, "quad");
        Objects.requireNonNull(skew, "skew");
        Objects.requireNonNull(mask, "mask");
        Objects.requireNonNull(readings, "readings");
    }

    /**
     * {@code {quad, skew, confidence, spread, touchesEdge, coverage, readings}}, in a new map the
     * caller may keep. The mask is pixels rather than plain data and is left out.
     */
    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("quad", quad.toJson());
        json.put("skew", skew.toJson());
        json.put("confidence", confidence);
        json.put("spread", spread);
        json.put("touchesEdge", touchesEdge);
        json.put("coverage", coverage);
        json.put("readings", readings.toJson());
        return json;
    }
}
