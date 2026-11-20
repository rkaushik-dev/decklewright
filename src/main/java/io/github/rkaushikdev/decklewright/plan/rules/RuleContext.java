package io.github.rkaushikdev.decklewright.plan.rules;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.util.Guards;
import java.util.Objects;
import java.util.Optional;

/**
 * What one run of a rule set shares between its rules: the anchor the crop is measured from, and
 * what to call it.
 *
 * <p>The anchor rules record it and the rules after them read it. A bleed or a safe area works from
 * the anchor held here rather than from wherever the crop has got to, which is what makes running
 * it a second time land in the same place. A rule that finds no anchor here measures from the crop
 * as it stands.
 *
 * <p>A context is mutable on purpose, because the anchor rule writes into the one the engine hands
 * every rule of a run. It is not safe for use from several threads at once.
 */
public final class RuleContext {
    private Rect anchor;
    private String anchorName;

    private RuleContext(Rect anchor, String anchorName) {
        this.anchor = anchor;
        this.anchorName = anchorName;
    }

    /** A new context holding no anchor. */
    public static RuleContext empty() {
        return new RuleContext(null, null);
    }

    /** A new context measuring from the anchor, with no name for it. */
    public static RuleContext of(Rect anchor) {
        return new RuleContext(Objects.requireNonNull(anchor, "anchor"), null);
    }

    /**
     * A new context measuring from the anchor, under the name the trail calls it by.
     *
     * @throws BadArgumentError when the name is empty
     */
    public static RuleContext of(Rect anchor, String anchorName) {
        return new RuleContext(
                Objects.requireNonNull(anchor, "anchor"), Guards.requireNonEmptyString(anchorName, "anchorName"));
    }

    /** The rect the crop is measured from, when an anchor has been recorded. */
    public Optional<Rect> anchor() {
        return Optional.ofNullable(anchor);
    }

    /** What the trail calls the anchor, such as {@code neatline}, when a name has been recorded. */
    public Optional<String> anchorName() {
        return Optional.ofNullable(anchorName);
    }

    /**
     * Records what the crop is measured from, for the rules that run after this one.
     *
     * @throws BadArgumentError when the name is empty
     */
    public void recordAnchor(Rect anchor, String anchorName) {
        Guards.requireNonEmptyString(anchorName, "anchorName");
        this.anchor = Objects.requireNonNull(anchor, "anchor");
        this.anchorName = anchorName;
    }
}
