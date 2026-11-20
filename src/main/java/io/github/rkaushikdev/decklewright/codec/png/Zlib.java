package io.github.rkaushikdev.decklewright.codec.png;

import io.github.rkaushikdev.decklewright.UnsupportedError;
import io.github.rkaushikdev.decklewright.codec.Bytes;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * The zlib streams a png keeps its image data in, through the platform's {@link Deflater} and
 * {@link Inflater}.
 */
final class Zlib {
    private static final int CHUNK = 64 * 1024;

    private Zlib() {}

    /** The data as one zlib stream compressed at the level, zero through nine. */
    static byte[] deflate(byte[] data, int level) {
        Deflater deflater = new Deflater(level);
        try {
            deflater.setInput(data);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(CHUNK);
            byte[] buffer = new byte[CHUNK];
            while (!deflater.finished()) {
                int written = deflater.deflate(buffer);
                out.write(buffer, 0, written);
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    /**
     * Inflates a whole zlib stream and keeps at most {@code keep} bytes of what comes out.
     *
     * <p>The rest of a stream that runs long is still inflated, so a corrupt tail is refused, but it
     * is not kept. The buffer grows only as data arrives, so a header that claims a huge image over a
     * few bytes of data costs a few bytes.
     *
     * @return the first {@code keep} bytes, or every byte when the stream inflates to fewer
     * @throws DataFormatException when the stream is corrupt, needs a preset dictionary, or stops
     *     before its end
     * @throws UnsupportedError when more must be kept than one buffer can hold
     */
    static byte[] inflate(byte[] data, long keep) throws DataFormatException {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(data);
            byte[] kept = new byte[(int) Math.min(keep, CHUNK)];
            int count = 0;
            byte[] spill = null;
            while (!inflater.finished()) {
                int inflated;
                if (count < keep) {
                    if (count == kept.length) {
                        kept = grown(kept, keep);
                    }
                    inflated = inflater.inflate(kept, count, kept.length - count);
                    count += inflated;
                } else {
                    if (spill == null) {
                        spill = new byte[CHUNK];
                    }
                    inflated = inflater.inflate(spill);
                }
                if (inflated == 0 && inflater.needsDictionary()) {
                    throw new DataFormatException("the stream needs a preset dictionary");
                }
                if (inflated == 0 && inflater.needsInput()) {
                    throw new DataFormatException("unexpected end of file");
                }
            }
            return count == kept.length ? kept : Arrays.copyOf(kept, count);
        } finally {
            inflater.end();
        }
    }

    /** The buffer copied into one twice as long, but never longer than {@code keep}. */
    private static byte[] grown(byte[] buffer, long keep) {
        byte[] larger = Bytes.allocate(Math.min(keep, 2L * buffer.length));
        System.arraycopy(buffer, 0, larger, 0, buffer.length);
        return larger;
    }
}
