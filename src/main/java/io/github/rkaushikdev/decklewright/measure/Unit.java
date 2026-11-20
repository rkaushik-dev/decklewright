package io.github.rkaushikdev.decklewright.measure;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A physical unit of length.
 *
 * <p>Pixels are deliberately not in here, because a pixel has no length until you say at what
 * sampling frequency it was taken, and mixing the two is how a service copy ends up at the wrong
 * size. The constants are declared in the order a report lists them.
 *
 * @see Units
 */
public enum Unit implements JsonValue {
    /** The millimetre, reported to two places. */
    MILLIMETRE("mm", 1 / 25.4, 2),
    /** The centimetre, reported to three places. */
    CENTIMETRE("cm", 1 / 2.54, 3),
    /** The metre, reported to five places. */
    METRE("m", 1 / 0.0254, 5),
    /** The inch, reported to four places. The inch is the base every conversion goes through, because ppi is. */
    INCH("in", 1, 4),
    /** The printer's point, a seventy second of an inch, reported to two places. */
    POINT("pt", 1 / 72.0, 2),
    /** The pica, twelve points, reported to three places. */
    PICA("pc", 1 / 6.0, 3);

    /** Every label, in declaration order. */
    private static final List<String> LABELS =
            Arrays.stream(values()).map(Unit::label).toList();

    private final String label;
    private final double inchesPer;
    private final int reportPlaces;

    Unit(String label, double inchesPer, int reportPlaces) {
        this.label = label;
        this.inchesPer = inchesPer;
        this.reportPlaces = reportPlaces;
    }

    /**
     * The unit a profile or an operator names, such as {@code mm}. Labels are lower case and
     * matched exactly.
     *
     * @throws BadArgumentError when the label is none of them, with the label as {@code unit} and
     *     every label as {@code known} in the details
     */
    public static Unit parse(String label) {
        for (Unit unit : values()) {
            if (unit.label.equals(label)) {
                return unit;
            }
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("unit", label);
        details.put("known", new ArrayList<>(LABELS));
        throw new BadArgumentError("unknown unit " + label, details);
    }

    /**
     * The unit a json document names, checked the way a profile field is checked.
     *
     * @throws BadArgumentError saying {@code unit must be one of mm, cm, m, in, pt, pc} when the
     *     value is not one of the labels
     */
    static Unit fromJson(Object value) {
        return values()[LABELS.indexOf(Guards.requireOneOf(value, LABELS, "unit"))];
    }

    /** True when the value is the label of a unit. */
    static boolean isLabel(Object value) {
        return value instanceof String text && LABELS.contains(text);
    }

    /** The name a profile writes, such as {@code mm}. */
    public String label() {
        return label;
    }

    /** Inches in one of this unit. */
    double inchesPer() {
        return inchesPer;
    }

    /** How many decimal places a report keeps for a figure in this unit. */
    int reportPlaces() {
        return reportPlaces;
    }

    /** The {@linkplain #label() label}, which is how a unit is written in json. */
    @Override
    public String toJson() {
        return label;
    }

    /** The {@linkplain #label() label}, which is how a unit reads in a message. */
    @Override
    public String toString() {
        return label;
    }
}
