package io.github.rkaushikdev.decklewright.profile;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.measure.Length;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Four lengths, one per edge, as a profile writes a bleed or a safe area.
 *
 * <p>The lengths keep the units the curator wrote them in; {@link Settings#edgesInPixels(Edges,
 * io.github.rkaushikdev.decklewright.measure.Sampling)} is where they become pixels.
 *
 * @param top the length at the top edge
 * @param right the length at the right edge
 * @param bottom the length at the bottom edge
 * @param left the length at the left edge
 */
public record Edges(Length top, Length right, Length bottom, Length left) implements JsonValue {

    /** The edge names, in css order, which is the order a profile and a report list them in. */
    public static final List<String> SIDES = List.of("top", "right", "bottom", "left");

    /** Edges of the given lengths, in css order. */
    public Edges {
        Objects.requireNonNull(top, "top");
        Objects.requireNonNull(right, "right");
        Objects.requireNonNull(bottom, "bottom");
        Objects.requireNonNull(left, "left");
    }

    /** The one length on every edge. */
    public static Edges uniform(Length length) {
        return new Edges(length, length, length, length);
    }

    /**
     * The length at the named edge.
     *
     * @throws BadArgumentError when the name is not one of {@link #SIDES}
     */
    public Length side(String name) {
        return switch (Guards.requireOneOf(name, SIDES, "side")) {
            case "top" -> top;
            case "right" -> right;
            case "bottom" -> bottom;
            default -> left;
        };
    }

    /** True when every edge is exactly zero, whatever its unit. */
    public boolean isZero() {
        return top.isZero() && right.isZero() && bottom.isZero() && left.isZero();
    }

    /** {@code {top, right, bottom, left}}, each a {@link Length}, in a new map the caller may keep. */
    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("top", top);
        json.put("right", right);
        json.put("bottom", bottom);
        json.put("left", left);
        return json;
    }
}
