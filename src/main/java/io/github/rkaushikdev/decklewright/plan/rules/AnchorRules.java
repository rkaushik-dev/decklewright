package io.github.rkaushikdev.decklewright.plan.rules;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.PlanError;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.profile.Anchor;
import io.github.rkaushikdev.decklewright.util.Guards;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What the crop is measured from.
 *
 * <p>Two of the collections crop to the paper edge and two to the neatline, and the choice is the
 * single biggest difference between them. It is a rule rather than a setting the engine reads
 * directly, so a site with a third anchor can add one without touching the engine.
 *
 * <p>It runs first, and it always sets the crop rather than adjusting it, so a second pass lands in
 * the same place as the first.
 */
public final class AnchorRules {
    /** What every anchor rule is called in the trail. */
    private static final String NAME = "anchor";

    private AnchorRules() {}

    /**
     * Sets the crop to the rect and records it in the context as the anchor the later rules measure
     * from.
     *
     * @param name what to call the anchor in the trail, such as {@code neatline}
     * @throws BadArgumentError when the name is empty
     * @throws PlanError when the rect has no area
     */
    public static Rule anchorRule(Rect rect, String name) {
        Guards.requireNonEmptyString(name, "name");
        if (rect.isEmpty()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("anchor", rect.toJson());
            details.put("name", name);
            throw new PlanError("the " + name + " anchor has no area", details);
        }
        return Rules.rule(NAME, Phase.ANCHOR, (plan, context) -> {
            context.recordAnchor(rect, name);
            return plan.moved(rect, NAME, "measured from the " + name);
        });
    }

    /**
     * The anchor a profile asked for, out of what the detector found. A profile that wants the
     * neatline and a capture that has none is a fault, not a quiet fall back to the paper edge: the
     * crop would be an inch and a half out and nobody would see it until the plates were bound.
     *
     * @throws PlanError when the neatline is asked for and none was found, or the one found has no
     *     area
     */
    public static Rule anchorFor(Anchor anchor, Landmarks found) {
        return switch (Objects.requireNonNull(anchor, "anchor")) {
            case SHEET -> anchorRule(found.sheet(), Anchor.SHEET.label());
            case NEATLINE -> {
                Optional<Rect> neatline = found.neatline().filter(rect -> !rect.isEmpty());
                if (neatline.isEmpty()) {
                    throw new PlanError(
                            "the profile crops to the neatline and none was found", Map.of("anchor", anchor.label()));
                }
                yield anchorRule(neatline.get(), Anchor.NEATLINE.label());
            }
        };
    }

    /**
     * The anchor named in words, such as an operator's override, out of what the detector found.
     *
     * @param anchor the name of the anchor, {@code sheet} or {@code neatline}
     * @throws PlanError when there is no anchor of that name, or as {@link #anchorFor(Anchor,
     *     Landmarks)} does
     */
    public static Rule anchorFor(String anchor, Landmarks found) {
        for (Anchor known : Anchor.values()) {
            if (known.label().equals(anchor)) {
                return anchorFor(known, found);
            }
        }
        throw new PlanError("there is no anchor called " + anchor, Collections.singletonMap("anchor", anchor));
    }

    /**
     * An anchor that falls back to the paper edge when the neatline asked for is not there, for the
     * collections that would rather have a crop than a held item. It says loudly what it did.
     */
    public static Rule anchorOrSheetRule(Anchor anchor, Landmarks found) {
        boolean hasNeatline = found.neatline().filter(rect -> !rect.isEmpty()).isPresent();
        if (anchor != Anchor.NEATLINE || hasNeatline) {
            return anchorFor(anchor, found);
        }
        Rule inner = anchorRule(found.sheet(), Anchor.SHEET.label());
        return Rules.rule(NAME, Phase.ANCHOR, (plan, context) -> inner.apply(plan, context)
                .warned("no neatline was found, so the crop fell back to the paper edge"));
    }
}
