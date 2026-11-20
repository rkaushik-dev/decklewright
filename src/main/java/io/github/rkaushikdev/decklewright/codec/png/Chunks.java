package io.github.rkaushikdev.decklewright.codec.png;

import io.github.rkaushikdev.decklewright.CodecError;
import io.github.rkaushikdev.decklewright.codec.Bytes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The png container: a signature and then a run of length, type, data, check.
 *
 * <p>The lab cares about the container as much as the pixels, because the archival profile says a
 * master carries its capture date and its collection identifier in text chunks, and a service copy
 * carries neither. Splitting and reassembling without decoding is how that gets enforced.
 */
public final class Chunks {
    /** The eight bytes every png opens with. */
    public static final List<Integer> PNG_SIGNATURE = List.of(137, 80, 78, 71, 13, 10, 26, 10);

    private static final byte[] SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

    /** Length, type and check: what a chunk costs on top of its data. */
    private static final int FRAMING = 12;

    private Chunks() {}

    /** True when the bytes open with the eight signature bytes. */
    public static boolean looksLikePng(byte[] bytes) {
        if (bytes.length < SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < SIGNATURE.length; i += 1) {
            if (bytes[i] != SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Every chunk in the file, in order, each check value checked.
     *
     * @see #splitChunks(byte[], boolean)
     */
    public static List<Chunk> splitChunks(byte[] bytes) {
        return splitChunks(bytes, true);
    }

    /**
     * Every chunk in the file, in order, up to and including {@code IEND}; anything after it is
     * ignored.
     *
     * @param checkCrc false to skip the check values, for a caller that only wants the layout
     * @throws CodecError when the signature is wrong, the file stops inside a chunk, a check value
     *     does not match, or the file does not open with {@code IHDR} and close with {@code IEND}
     */
    public static List<Chunk> splitChunks(byte[] bytes, boolean checkCrc) {
        if (!looksLikePng(bytes)) {
            throw new CodecError("that is not a png, the signature is wrong", Map.of());
        }
        List<Chunk> chunks = new ArrayList<>();
        int at = SIGNATURE.length;
        while (at < bytes.length) {
            if (at + 8 > bytes.length) {
                throw new CodecError("the file stops inside a chunk header", Map.of("at", at));
            }
            long length = Bytes.readUint32BE(bytes, at);
            String type = Bytes.readAscii(bytes, at + 4, 4);
            int start = at + 8;
            if (start + length + 4 > bytes.length) {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("type", type);
                details.put("at", at);
                details.put("length", length);
                throw new CodecError("the file stops inside a chunk body", details);
            }
            int size = (int) length;
            long crc = Bytes.readUint32BE(bytes, start + size);
            if (checkCrc) {
                long expected = Bytes.crc32(bytes, at + 4, size + 4);
                if (expected != crc) {
                    Map<String, Object> details = new LinkedHashMap<>();
                    details.put("type", type);
                    details.put("expected", expected);
                    details.put("found", crc);
                    throw new CodecError("the check value on the " + type + " chunk does not match", details);
                }
            }
            chunks.add(Chunk.found(type, bytes, start, size, at, crc));
            at = start + size + 4;
            if (type.equals("IEND")) {
                break;
            }
        }
        if (chunks.isEmpty() || !chunks.get(0).type().equals("IHDR")) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("first", chunks.isEmpty() ? null : chunks.get(0).type());
            throw new CodecError("a png has to open with an IHDR chunk", details);
        }
        if (!chunks.get(chunks.size() - 1).type().equals("IEND")) {
            throw new CodecError("a png has to close with an IEND chunk", Map.of());
        }
        return List.copyOf(chunks);
    }

    /**
     * One chunk laid out as it sits in a file: length, type, data, check.
     *
     * @throws CodecError when the type is not four characters
     */
    public static byte[] buildChunk(String type, byte[] data) {
        Chunk chunk = Chunk.adopt(type, data);
        byte[] out = Bytes.allocate((long) FRAMING + chunk.length());
        write(out, 0, chunk);
        return out;
    }

    /** A whole file: the signature, then each chunk laid out in order. */
    public static byte[] assemble(List<Chunk> chunks) {
        long total = SIGNATURE.length;
        for (Chunk chunk : chunks) {
            total += FRAMING + chunk.length();
        }
        byte[] out = Bytes.allocate(total);
        System.arraycopy(SIGNATURE, 0, out, 0, SIGNATURE.length);
        int at = SIGNATURE.length;
        for (Chunk chunk : chunks) {
            at = write(out, at, chunk);
        }
        return out;
    }

    /** The first chunk of the type. */
    public static Optional<Chunk> findChunk(List<Chunk> chunks, String type) {
        return chunks.stream().filter(chunk -> chunk.type().equals(type)).findFirst();
    }

    /** Every chunk of the type, in order. */
    public static List<Chunk> chunksOfType(List<Chunk> chunks, String type) {
        return chunks.stream().filter(chunk -> chunk.type().equals(type)).toList();
    }

    /**
     * The four case bits carry meaning. A lower case first letter means the chunk is optional, and a
     * lower case last letter means a rewriter that does not understand it may keep it. That is exactly
     * the rule the derivative stage needs when it strips a master down to a service copy.
     */
    public static ChunkFlags chunkFlags(String type) {
        return new ChunkFlags((type.charAt(0) & 0x20) == 0, (type.charAt(1) & 0x20) == 0, (type.charAt(3) & 0x20) != 0);
    }

    /** The data of every chunk, end to end, which is how a run of image data chunks is read. */
    static byte[] concatenated(List<Chunk> chunks) {
        long total = 0;
        for (Chunk chunk : chunks) {
            total += chunk.length();
        }
        byte[] out = Bytes.allocate(total);
        int at = 0;
        for (Chunk chunk : chunks) {
            chunk.copyTo(out, at);
            at += chunk.length();
        }
        return out;
    }

    /** Lays the chunk out at {@code at} and gives back where the next one starts. */
    private static int write(byte[] out, int at, Chunk chunk) {
        int length = chunk.length();
        Bytes.writeUint32BE(out, at, length);
        for (int i = 0; i < 4; i += 1) {
            out[at + 4 + i] = (byte) chunk.type().charAt(i);
        }
        chunk.copyTo(out, at + 8);
        Bytes.writeUint32BE(out, at + 8 + length, Bytes.crc32(out, at + 4, length + 4));
        return at + FRAMING + length;
    }
}
