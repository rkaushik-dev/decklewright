package io.github.rkaushikdev.decklewright.raster;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.JsonValue;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How the channels of one pixel are laid out.
 *
 * <p>The lab captures three channel colour at sixteen bits for masters and hands out eight bit
 * derivatives. Grayscale turns up in the mask and profile work and in the odd microfilm scan. Alpha
 * only exists so a rotated master can mark the corners that fell outside the capture it was cut from.
 *
 * @see Layouts
 */
public enum Layout implements JsonValue {
    /** One gray channel. */
    GRAY("gray", -1, List.of("gray")),
    /** Gray, then its coverage. */
    GRAYA("graya", 1, List.of("gray", "alpha")),
    /** Red, green and blue. */
    RGB("rgb", -1, List.of("red", "green", "blue")),
    /** Red, green and blue, then their coverage. */
    RGBA("rgba", 3, List.of("red", "green", "blue", "alpha"));

    /** Every label, in declaration order. */
    private static final List<String> LABELS =
            Arrays.stream(values()).map(Layout::label).toList();

    private final String label;
    private final int alphaIndex;
    private final List<String> channelNames;

    Layout(String label, int alphaIndex, List<String> channelNames) {
        this.label = label;
        this.alphaIndex = alphaIndex;
        this.channelNames = channelNames;
    }

    /**
     * The layout a file header or a profile names.
     *
     * @throws BadArgumentError when the name is empty, or names no layout; the details list every
     *     known layout
     */
    public static Layout parse(String name) {
        Guards.requireNonEmptyString(name, "layout");
        int index = LABELS.indexOf(name);
        if (index < 0) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("layout", name);
            details.put("known", LABELS);
            throw new BadArgumentError("unknown layout " + name, details);
        }
        return values()[index];
    }

    /** The name a profile writes, such as {@code rgba}. */
    public String label() {
        return label;
    }

    /** How many samples make up one pixel. */
    public int channelCount() {
        return channelNames.size();
    }

    /** True when the last channel is coverage rather than image. */
    public boolean hasAlpha() {
        return alphaIndex >= 0;
    }

    /** The index of the coverage channel, or minus one when there is none. */
    public int alphaIndex() {
        return alphaIndex;
    }

    /** The channels by name, in sample order, such as {@code red, green, blue, alpha}. */
    public List<String> channelNames() {
        return channelNames;
    }

    /** How many channels carry image rather than coverage. */
    public int colourCount() {
        return channelCount() - (hasAlpha() ? 1 : 0);
    }

    /** True for the three channel layouts, with or without alpha. */
    public boolean isColour() {
        return colourCount() == 3;
    }

    /** The layout with the same colour channels and alpha added; this layout when it already has one. */
    public Layout withAlpha() {
        return switch (this) {
            case GRAY -> GRAYA;
            case RGB -> RGBA;
            default -> this;
        };
    }

    /** The layout with the same colour channels and no alpha; this layout when it has none. */
    public Layout withoutAlpha() {
        return switch (this) {
            case GRAYA -> GRAY;
            case RGBA -> RGB;
            default -> this;
        };
    }

    /** The {@linkplain #label() label}, which is how a layout is written in json. */
    @Override
    public String toJson() {
        return label;
    }

    /** The {@linkplain #label() label}, which is how a layout reads in a message. */
    @Override
    public String toString() {
        return label;
    }
}
