package io.github.rkaushikdev.decklewright.codec.tiff;

/** What a sample value means, by the code the photometric interpretation tag holds. */
public enum Photometric {
    /** Zero is white, so the reader inverts the samples. */
    WHITE_IS_ZERO(0),
    /** Zero is black: the ordinary gray capture. */
    BLACK_IS_ZERO(1),
    /** Red, green and blue samples. */
    RGB(2),
    /** Indexes into a colour map, which no capture is. */
    PALETTE(3);

    private final int code;

    Photometric(int code) {
        this.code = code;
    }

    /** The value the photometric interpretation tag holds for this meaning. */
    public int code() {
        return code;
    }
}
