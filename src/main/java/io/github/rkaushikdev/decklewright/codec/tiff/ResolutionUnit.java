package io.github.rkaushikdev.decklewright.codec.tiff;

/** What the resolution tags are measured in, by the code the resolution unit tag holds. */
public enum ResolutionUnit {
    /** The ratio is known but the scale is not, which is the same as no resolution. */
    NONE(1),
    /** Pixels per inch, the default. */
    INCH(2),
    /** Pixels per centimetre. */
    CENTIMETRE(3);

    private final int code;

    ResolutionUnit(int code) {
        this.code = code;
    }

    /** The value the resolution unit tag holds for this unit. */
    public int code() {
        return code;
    }
}
