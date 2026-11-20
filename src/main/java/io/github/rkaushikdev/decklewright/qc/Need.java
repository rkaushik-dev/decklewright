package io.github.rkaushikdev.decklewright.qc;

import io.github.rkaushikdev.decklewright.util.JsonValue;

/**
 * Something a check can ask the {@link QcContext} for. A check names what it needs, and {@link
 * Check#runCheck(Check, QcContext)} declines to run it, rather than failing it, when any of those is
 * missing, saying which by the label.
 */
public enum Need implements JsonValue {
    /** The capture itself. */
    SURFACE("surface"),
    /** The sheet's bounding rect, out to whole pixels. */
    SHEET("sheet"),
    /** What is on the bed and is not bed: the sheet, the target and the scale bar. */
    AVOID("avoid"),
    /** The region of the capture the bed is measured over. */
    BED("bed"),
    /** The planned crop, when there is one. */
    CROP("crop"),
    /** The step wedge, when one was found. */
    TARGET("target"),
    /** The sampling frequency, measured or claimed. */
    SAMPLING("sampling"),
    /** How far the sheet is turned. */
    SKEW("skew"),
    /** How far apart the three skew measures were, in degrees. */
    SPREAD("spread"),
    /** The profile's quality limits. */
    LIMITS("limits");

    private final String label;

    Need(String label) {
        this.label = label;
    }

    /** The name a note prints, such as {@code target}. */
    public String label() {
        return label;
    }

    /** The label. */
    @Override
    public String toJson() {
        return label;
    }

    /** The label. */
    @Override
    public String toString() {
        return label;
    }
}
