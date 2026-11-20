package io.github.rkaushikdev.decklewright.measure;

import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A real size: two sides and the unit they are in. A sheet is measured as one of these and a crop
 * comes out at one.
 *
 * <p>The sides are not checked here, because a measurement is whatever the bed saw; each function
 * that takes one checks what it needs, as {@link PhysicalSizes#achievedFrom} does.
 *
 * @param width the side across
 * @param height the side down
 * @param unit the unit both sides are in
 */
public record PhysicalSize(double width, double height, Unit unit) implements JsonValue {

    /** A size of the given sides. */
    public PhysicalSize {
        Objects.requireNonNull(unit, "unit");
    }

    /** {@code {width, height, unit}}, in a new map the caller may keep, with the unit as its label. */
    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("width", width);
        json.put("height", height);
        json.put("unit", unit.label());
        return json;
    }

    /** Both sides at the report precision of the unit, such as {@code 17.0000 in by 14.0000 in}. */
    @Override
    public String toString() {
        return PhysicalSizes.describeSize(this);
    }
}
