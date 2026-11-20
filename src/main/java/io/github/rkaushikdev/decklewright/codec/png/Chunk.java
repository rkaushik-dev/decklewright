package io.github.rkaushikdev.decklewright.codec.png;

import io.github.rkaushikdev.decklewright.CodecError;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * One png chunk: a four letter type and its data.
 *
 * <p>A chunk is either read out of a file by {@link Chunks#splitChunks(byte[])}, in which case it
 * also knows where it started and the check value it carried, or made here to be written, with
 * {@link #of(String, byte[])} or one of the builders. A read chunk is a window onto the file's bytes
 * rather than a copy of them, as it always was, so splitting a four hundred megabyte master costs
 * nothing; {@link #data()} always hands out a copy, so nothing can write back through a chunk.
 *
 * <p>Two chunks are equal when their type, data, offset and check value all are.
 */
public final class Chunk {
    private final String type;
    private final byte[] bytes;
    private final int start;
    private final int length;
    private final OptionalInt offset;
    private final OptionalLong crc;

    private Chunk(String type, byte[] bytes, int start, int length, OptionalInt offset, OptionalLong crc) {
        this.type = type;
        this.bytes = bytes;
        this.start = start;
        this.length = length;
        this.offset = offset;
        this.crc = crc;
    }

    /**
     * A chunk to write, holding a copy of the data.
     *
     * @throws CodecError when the type is not four characters
     */
    public static Chunk of(String type, byte[] data) {
        return adopt(type, data.clone());
    }

    /** A chunk to write that takes the array as its own, for data this package has just made. */
    static Chunk adopt(String type, byte[] data) {
        requireType(type);
        return new Chunk(type, data, 0, data.length, OptionalInt.empty(), OptionalLong.empty());
    }

    /** A chunk found in a file: a window onto the file's bytes, never a copy. */
    static Chunk found(String type, byte[] file, int start, int length, int offset, long crc) {
        return new Chunk(type, file, start, length, OptionalInt.of(offset), OptionalLong.of(crc));
    }

    /**
     * Checks that a chunk type is four characters.
     *
     * @throws CodecError naming the type when it is not
     */
    static void requireType(String type) {
        if (type.length() != 4) {
            throw new CodecError("a chunk type is four characters", Map.of("type", type));
        }
    }

    /** The four letter type, such as {@code IHDR}; its letter cases carry {@linkplain ChunkFlags meaning}. */
    public String type() {
        return type;
    }

    /** A copy of the chunk's data. */
    public byte[] data() {
        return Arrays.copyOfRange(bytes, start, start + length);
    }

    /** How many bytes of data the chunk holds. */
    public int length() {
        return length;
    }

    /** Where the chunk's length field sits in the file it was read from; empty for a chunk made here. */
    public OptionalInt offset() {
        return offset;
    }

    /** The check value the chunk carried in the file it was read from; empty for a chunk made here. */
    public OptionalLong crc() {
        return crc;
    }

    /** Copies the data into {@code out} from {@code at}, without an intermediate copy. */
    void copyTo(byte[] out, int at) {
        System.arraycopy(bytes, start, out, at, length);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Chunk that
                && type.equals(that.type)
                && Arrays.equals(bytes, start, start + length, that.bytes, that.start, that.start + that.length)
                && offset.equals(that.offset)
                && crc.equals(that.crc);
    }

    @Override
    public int hashCode() {
        int hash = 1;
        for (int i = start; i < start + length; i += 1) {
            hash = 31 * hash + bytes[i];
        }
        return Objects.hash(type, hash, offset, crc);
    }

    /** The type and size, such as {@code Chunk[IDAT, 4096 bytes, at 33]}. */
    @Override
    public String toString() {
        String at = offset.isPresent() ? ", at " + offset.getAsInt() : "";
        return "Chunk[" + type + ", " + length + " bytes" + at + "]";
    }
}
