package io.github.rkaushikdev.decklewright.codec.png;

import io.github.rkaushikdev.decklewright.CodecError;
import io.github.rkaushikdev.decklewright.codec.Bytes;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Container level edits that never touch a sample.
 *
 * <p>The archival profile says a master carries its capture time and collection identifiers and a
 * service copy carries none of them. Re-encoding to strip a text chunk would cost a generation for
 * nothing, so this works on the chunk list and leaves the image data byte for byte alone. None of it
 * checks the check values, because a file worth editing is one that is already known to be good.
 */
public final class Rewriting {
    /** The chunks a master is allowed to keep, everything else being dropped. */
    public static final List<String> MASTER_KEEP = List.of("IHDR", "pHYs", "tEXt", "tIME", "sRGB", "iCCP", "IEND");

    /** A service copy keeps its geometry and its colour intent and nothing else. */
    public static final List<String> SERVICE_KEEP = List.of("IHDR", "sRGB", "IEND");

    /** The chunks without which a file is not a png at all, in the order they sit in one. */
    private static final List<String> REQUIRED = List.of("IHDR", "IDAT", "IEND");

    private static final int TIME_LENGTH = 7;

    private Rewriting() {}

    /** A {@code tIME} chunk holding the instant in UTC, to the second. */
    public static Chunk timeChunk(Instant when) {
        LocalDateTime utc = LocalDateTime.ofInstant(when, ZoneOffset.UTC);
        int year = utc.getYear();
        byte[] data = {
            (byte) (year >> 8),
            (byte) year,
            (byte) utc.getMonthValue(),
            (byte) utc.getDayOfMonth(),
            (byte) utc.getHour(),
            (byte) utc.getMinute(),
            (byte) utc.getSecond()
        };
        return Chunk.adopt("tIME", data);
    }

    /**
     * The capture time the file carries; empty when it has no {@code tIME} chunk, or one that is not
     * the seven bytes the format says.
     *
     * @throws CodecError when the bytes are not a well formed png container
     */
    public static Optional<Instant> readTime(byte[] bytes) {
        return Chunks.findChunk(Chunks.splitChunks(bytes, false), "tIME")
                .filter(chunk -> chunk.length() == TIME_LENGTH)
                .map(Chunk::data)
                .map(data -> utc(
                        Bytes.readUint16BE(data, 0),
                        data[2] & 0xff,
                        data[3] & 0xff,
                        data[4] & 0xff,
                        data[5] & 0xff,
                        data[6] & 0xff));
    }

    /**
     * Keep the listed chunk types and drop the rest. {@code IDAT} is always kept because dropping it
     * would leave a file with no image, and {@code IHDR} and {@code IEND} because without them it is
     * not a png at all.
     *
     * @throws CodecError when the bytes are not a well formed png container
     */
    public static byte[] keepOnly(byte[] bytes, List<String> keep) {
        Set<String> allowed = new LinkedHashSet<>(keep);
        allowed.addAll(REQUIRED);
        return Chunks.assemble(Chunks.splitChunks(bytes, false).stream()
                .filter(chunk -> allowed.contains(chunk.type()))
                .toList());
    }

    /**
     * Drop every optional chunk, which is what a derivative starts from.
     *
     * @throws CodecError when the bytes are not a well formed png container
     */
    public static byte[] stripAncillary(byte[] bytes) {
        return Chunks.assemble(Chunks.splitChunks(bytes, false).stream()
                .filter(chunk -> Chunks.chunkFlags(chunk.type()).critical())
                .toList());
    }

    /**
     * Add or replace metadata before the image data, keeping everything else in the order it was
     * found. The new time goes in first, then the new text chunks.
     *
     * @throws CodecError when the bytes are not a well formed png container, or a text key or value
     *     will not go into a text chunk
     */
    public static byte[] withMetadata(byte[] bytes, Metadata additions) {
        Objects.requireNonNull(additions, "additions");
        Set<String> keys = additions.text().keySet();
        List<Chunk> head = new ArrayList<>();
        List<Chunk> tail = new ArrayList<>();
        boolean seenData = false;
        for (Chunk chunk : Chunks.splitChunks(bytes, false)) {
            String type = chunk.type();
            if (type.equals("IDAT")) {
                seenData = true;
            }
            if (type.equals("tIME") && additions.time().isPresent()) {
                continue;
            }
            if (type.equals("tEXt") && !keys.isEmpty() && keys.contains(keyOf(chunk))) {
                continue;
            }
            (seenData ? tail : head).add(chunk);
        }

        List<Chunk> inserted = new ArrayList<>();
        additions.time().ifPresent(time -> inserted.add(timeChunk(time)));
        for (Map.Entry<String, String> entry : additions.text().entrySet()) {
            inserted.add(Encoding.textChunk(entry.getKey(), entry.getValue()));
        }

        return Chunks.assemble(
                Stream.of(head, inserted, tail).flatMap(List::stream).toList());
    }

    /**
     * Say whether a file matches a chunk policy, and which chunks broke it. Used by the verify
     * command rather than by the writers, because a file that came back from a vendor is exactly the
     * one worth checking. Image data is always allowed.
     *
     * @throws CodecError when the bytes are not a well formed png container
     */
    public static PolicyCheck checkChunkPolicy(byte[] bytes, List<String> keep) {
        Set<String> present = new LinkedHashSet<>(chunkTypes(bytes));
        Set<String> allowed = new LinkedHashSet<>(keep);
        allowed.add("IDAT");
        List<String> unexpected = present.stream()
                .filter(type -> !allowed.contains(type))
                .sorted()
                .toList();
        List<String> missing =
                REQUIRED.stream().filter(type -> !present.contains(type)).toList();
        return new PolicyCheck(unexpected.isEmpty() && missing.isEmpty(), unexpected, missing);
    }

    /**
     * The type of every chunk, in order.
     *
     * @throws CodecError when the bytes are not a well formed png container
     */
    public static List<String> chunkTypes(byte[] bytes) {
        return Chunks.splitChunks(bytes, false).stream().map(Chunk::type).toList();
    }

    /**
     * The image data chunks' data end to end, unchanged: the compressed bytes, never inflated.
     *
     * @throws CodecError when the bytes are not a well formed png container, or hold no image data
     */
    public static byte[] imageDataOf(byte[] bytes) {
        List<Chunk> parts = Chunks.chunksOfType(Chunks.splitChunks(bytes, false), "IDAT");
        if (parts.isEmpty()) {
            throw new CodecError("no IDAT chunk", Map.of());
        }
        return Chunks.concatenated(parts);
    }

    /** The key of a text chunk: everything before its first zero, and empty when it has none. */
    private static String keyOf(Chunk chunk) {
        byte[] data = chunk.data();
        return Bytes.readAsciiZ(data, 0, Math.max(0, Decoding.indexOfZero(data)));
    }

    /**
     * The instant the fields name, read leniently, as the lab's files have always been read: a year
     * below a hundred is nineteen hundred and that, and a field outside its range carries into the
     * one above it, so a month of thirteen is January of the next year. A damaged chunk therefore
     * reads back as the same instant it always has.
     */
    private static Instant utc(int year, int month, int day, int hour, int minute, int second) {
        int fullYear = year <= 99 ? 1900 + year : year;
        return LocalDate.of(fullYear, 1, 1)
                .plusMonths(month - 1L)
                .plusDays(day - 1L)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .plusSeconds(hour * 3600L + minute * 60L + second);
    }
}
