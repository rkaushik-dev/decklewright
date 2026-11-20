package io.github.rkaushikdev.decklewright.detect;

import io.github.rkaushikdev.decklewright.geom.Angle;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The three independent measures of a sheet's skew. They fail in different ways, so how far apart
 * they are says whether any of them can be trusted.
 *
 * @param calipers the skew of the minimum area rectangle over the sheet's outline
 * @param projection the skew at which the outline projects most sharply
 * @param sides the skew the four fitted sides agree on
 * @see Sheets#spreadOf(SkewReadings)
 */
public record SkewReadings(Angle calipers, Angle projection, Angle sides) implements JsonValue {
    /** Readings of the given measures. */
    public SkewReadings {
        Objects.requireNonNull(calipers, "calipers");
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(sides, "sides");
    }

    /** {@code {calipers, projection, sides}}, each in degrees, in a new map the caller may keep. */
    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("calipers", calipers.toJson());
        json.put("projection", projection.toJson());
        json.put("sides", sides.toJson());
        return json;
    }
}
