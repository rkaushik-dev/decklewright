package io.github.rkaushikdev.decklewright.codec.tiff;

import io.github.rkaushikdev.decklewright.CodecError;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Tiff tags and directory entries.
 *
 * <p>The flatbed writes baseline tiff and the vendor's driver is not going to change, so this reads
 * what that driver produces and nothing more. Tags it does not know are kept as raw values rather
 * than dropped, because the provenance record quotes them back and a tag we did not recognise is
 * still a fact about the capture.
 *
 * <p>Every value is read as a number, whatever its field type, so the lookups give back doubles.
 */
public final class Tags {
    /** The names of the tags the reader knows, by tag number. */
    public static final Map<Integer, String> TAGS = names();

    /** Byte width of each field type, indexed by the type code; zero for a code that is no type. */
    private static final int[] TYPE_SIZE = {0, 1, 1, 2, 4, 8, 1, 1, 2, 4, 8, 4, 8};

    private static final int ASCII = 2;

    private Tags() {}

    /** The tag's name, or {@code tag} and its number for one the reader does not know. */
    public static String tagName(int tag) {
        String known = TAGS.get(tag);
        return known != null ? known : "tag" + tag;
    }

    /**
     * The byte order and where the first directory starts.
     *
     * @throws CodecError when the file is shorter than a header, or the byte order mark or the
     *     version word is wrong
     */
    public static TiffHeader readTiffHeader(byte[] bytes) {
        if (bytes.length < 8) {
            throw new CodecError("the file is too short to be a tiff", Map.of());
        }
        boolean little = bytes[0] == 0x49 && bytes[1] == 0x49;
        boolean big = bytes[0] == 0x4d && bytes[1] == 0x4d;
        if (!little && !big) {
            throw new CodecError(
                    "that is not a tiff, the byte order mark is wrong",
                    Map.of("head", List.of(bytes[0] & 0xff, bytes[1] & 0xff)));
        }
        ByteBuffer view = view(bytes, little);
        int magic = view.getShort(2) & 0xffff;
        if (magic != 42) {
            throw new CodecError("that is not a tiff, the version word is wrong", Map.of("magic", magic));
        }
        return new TiffHeader(little, view.getInt(4) & 0xffffffffL);
    }

    /** True when the bytes open with a byte order mark and the version word. */
    public static boolean looksLikeTiff(byte[] bytes) {
        if (bytes.length < 8) {
            return false;
        }
        boolean little = bytes[0] == 0x49 && bytes[1] == 0x49;
        boolean big = bytes[0] == 0x4d && bytes[1] == 0x4d;
        if (!little && !big) {
            return false;
        }
        return (view(bytes, little).getShort(2) & 0xffff) == 42;
    }

    /**
     * Read one directory. Values of four bytes or fewer live in the entry itself and anything longer
     * lives at an offset, which is the one part of the format that catches people out.
     *
     * @throws CodecError when the directory or a value it points to lies past the end of the file, or
     *     an entry has a field type that does not exist
     */
    public static Directory readIfd(byte[] bytes, long offset, boolean little) {
        ByteBuffer view = view(bytes, little);
        if (offset + 2 > bytes.length) {
            throw new CodecError("the directory offset points past the end of the file", Map.of("offset", offset));
        }
        int count = view.getShort((int) offset) & 0xffff;
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i += 1) {
            long position = offset + 2 + i * 12L;
            if (position + 12 > bytes.length) {
                throw new CodecError("the file stops inside a directory entry", Map.of("index", i));
            }
            int at = (int) position;
            int tag = view.getShort(at) & 0xffff;
            int type = view.getShort(at + 2) & 0xffff;
            long length = view.getInt(at + 4) & 0xffffffffL;
            int size = type < TYPE_SIZE.length ? TYPE_SIZE[type] : 0;
            if (size == 0) {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("type", type);
                details.put("tag", tag);
                throw new CodecError("unknown tiff field type " + type, details);
            }

            long total = size * length;
            long start = total <= 4 ? at + 8 : view.getInt(at + 8) & 0xffffffffL;
            if (start + total > bytes.length) {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("tag", tag);
                details.put("start", start);
                throw new CodecError("a directory entry points past the end of the file", details);
            }

            Object value = type == ASCII
                    ? readText(bytes, (int) start, (int) length)
                    : readValues(view, (int) start, (int) length, size, type);
            entries.add(new Entry(tag, tagName(tag), type, length, value));
        }
        long nextAt = offset + 2 + count * 12L;
        long next = nextAt + 4 <= bytes.length ? view.getInt((int) nextAt) & 0xffffffffL : 0;
        return new Directory(entries, next);
    }

    /** Every entry's value by tag name; a later entry of the same name wins. */
    public static Map<String, Object> asBag(List<Entry> entries) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Entry entry : entries) {
            out.put(entry.name(), entry.value());
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * The first value of the named tag.
     *
     * @throws CodecError when there is no such tag, or it holds text or no values
     */
    public static double scalar(List<Entry> entries, String name) {
        return scalar(entries, name, OptionalDouble.empty());
    }

    /** The first value of the named tag, or the fallback when there is no such tag, or it holds text or no values. */
    public static double scalar(List<Entry> entries, String name, double fallback) {
        return scalar(entries, name, OptionalDouble.of(fallback));
    }

    /** Every value of the named tag; empty when there is no such tag or it holds text. */
    public static List<Double> list(List<Entry> entries, String name) {
        return find(entries, name).map(Entry::value).map(Tags::numbers).orElse(List.of());
    }

    private static double scalar(List<Entry> entries, String name, OptionalDouble fallback) {
        List<Double> values = list(entries, name);
        if (values.isEmpty()) {
            return fallback.orElseThrow(() -> new CodecError("the " + name + " tag is missing", Map.of("name", name)));
        }
        return values.get(0);
    }

    private static Optional<Entry> find(List<Entry> entries, String name) {
        return entries.stream().filter(entry -> entry.name().equals(name)).findFirst();
    }

    /** The values of a numeric entry, or nothing for a text one. */
    private static List<Double> numbers(Object value) {
        if (!(value instanceof List<?> items)) {
            return List.of();
        }
        List<Double> out = new ArrayList<>(items.size());
        for (Object item : items) {
            out.add(((Number) item).doubleValue());
        }
        return Collections.unmodifiableList(out);
    }

    /** An ascii field, up to its first zero. */
    private static String readText(byte[] bytes, int start, int length) {
        StringBuilder text = new StringBuilder(length);
        for (int k = 0; k < length; k += 1) {
            int code = bytes[start + k] & 0xff;
            if (code == 0) {
                break;
            }
            text.append((char) code);
        }
        return text.toString();
    }

    private static List<Double> readValues(ByteBuffer view, int start, int length, int size, int type) {
        List<Double> values = new ArrayList<>(length);
        for (int k = 0; k < length; k += 1) {
            values.add(readOne(view, start + k * size, type));
        }
        return values;
    }

    /** One value of the field type, as a number; a rational with a zero denominator divides by one. */
    private static double readOne(ByteBuffer view, int at, int type) {
        return switch (type) {
            case 1, 2, 7 -> view.get(at) & 0xff;
            case 3 -> view.getShort(at) & 0xffff;
            case 4 -> view.getInt(at) & 0xffffffffL;
            case 6 -> view.get(at);
            case 8 -> view.getShort(at);
            case 9 -> view.getInt(at);
            case 5 -> {
                long numerator = view.getInt(at) & 0xffffffffL;
                long denominator = view.getInt(at + 4) & 0xffffffffL;
                yield (double) numerator / (denominator == 0 ? 1 : denominator);
            }
            case 10 -> {
                int numerator = view.getInt(at);
                int denominator = view.getInt(at + 4);
                yield (double) numerator / (denominator == 0 ? 1 : denominator);
            }
            case 11 -> view.getFloat(at);
            case 12 -> view.getDouble(at);
            default -> throw new CodecError("unknown tiff field type " + type, Map.of("type", type));
        };
    }

    private static ByteBuffer view(byte[] bytes, boolean little) {
        return ByteBuffer.wrap(bytes).order(little ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
    }

    private static Map<Integer, String> names() {
        Map<Integer, String> names = new LinkedHashMap<>();
        names.put(256, "imageWidth");
        names.put(257, "imageLength");
        names.put(258, "bitsPerSample");
        names.put(259, "compression");
        names.put(262, "photometricInterpretation");
        names.put(270, "imageDescription");
        names.put(271, "make");
        names.put(272, "model");
        names.put(273, "stripOffsets");
        names.put(274, "orientation");
        names.put(277, "samplesPerPixel");
        names.put(278, "rowsPerStrip");
        names.put(279, "stripByteCounts");
        names.put(282, "xResolution");
        names.put(283, "yResolution");
        names.put(284, "planarConfiguration");
        names.put(296, "resolutionUnit");
        names.put(305, "software");
        names.put(306, "dateTime");
        names.put(339, "sampleFormat");
        return Collections.unmodifiableMap(names);
    }
}
