package io.github.rkaushikdev.decklewright.codec.tiff;

/** The strip compression schemes the reader knows, by the code the compression tag holds. */
public enum Compression {
    /** Samples stored as they are. */
    NONE(1),
    /** The tiff flavour of lzw. */
    LZW(5),
    /** The PackBits run length scheme the flatbed writes by default. */
    PACKBITS(32773);

    private final int code;

    Compression(int code) {
        this.code = code;
    }

    /** The value the compression tag holds for this scheme. */
    public int code() {
        return code;
    }
}
