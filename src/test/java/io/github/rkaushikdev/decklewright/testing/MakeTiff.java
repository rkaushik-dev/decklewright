package io.github.rkaushikdev.decklewright.testing;

import io.github.rkaushikdev.decklewright.codec.tiff.Lzw;
import io.github.rkaushikdev.decklewright.codec.tiff.PackBits;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.stream.IntStream;

/**
 * Build a baseline tiff by hand.
 *
 * <p>The reader has to be measured against the format, not against a writer we also wrote, so this
 * lays out the header, one directory and the strips directly. It is deliberately literal: every
 * value is written exactly where the specification puts it, and nothing is checked.
 */
public final class MakeTiff {
    private static final int TYPE_SHORT = 3;
    private static final int TYPE_LONG = 4;
    private static final int TYPE_RATIONAL = 5;

    private static final int COMPRESSION_LZW = 5;
    private static final int COMPRESSION_PACKBITS = 32773;

    private MakeTiff() {}

    /** The tiff the options describe: little endian unless told otherwise, one directory, then the strips. */
    public static byte[] makeTiff(Options options) {
        int width = options.width();
        int height = options.height();
        int channels = options.channels();
        int depth = options.depth();
        boolean little = options.little();
        int perStrip = options.rowsPerStrip().orElse(height);
        int bytesPerRow = width * channels * (depth / 8);

        List<byte[]> strips = new ArrayList<>();
        for (int row = 0; row < height; row += perStrip) {
            int rows = Math.min(perStrip, height - row);
            byte[] raw = new byte[bytesPerRow * rows];
            int at = 0;
            for (int r = 0; r < rows; r += 1) {
                for (int i = 0; i < width * channels; i += 1) {
                    int index = (row + r) * width * channels + i;
                    int value =
                            index < options.samples().size() ? options.samples().get(index) : 0;
                    if (depth == 8) {
                        raw[at] = (byte) value;
                        at += 1;
                    } else if (little) {
                        raw[at] = (byte) (value & 0xff);
                        raw[at + 1] = (byte) ((value >> 8) & 0xff);
                        at += 2;
                    } else {
                        raw[at] = (byte) ((value >> 8) & 0xff);
                        raw[at + 1] = (byte) (value & 0xff);
                        at += 2;
                    }
                }
            }
            if (options.compression() == COMPRESSION_PACKBITS) {
                strips.add(PackBits.packBits(raw));
            } else if (options.compression() == COMPRESSION_LZW) {
                strips.add(Lzw.lzwEncode(raw));
            } else {
                strips.add(raw);
            }
        }

        List<Field> fields = new ArrayList<>(List.of(
                new Field(256, TYPE_LONG, List.of((long) width)),
                new Field(257, TYPE_LONG, List.of((long) height)),
                new Field(258, TYPE_SHORT, repeated(depth, channels)),
                new Field(259, TYPE_SHORT, List.of((long) options.compression())),
                new Field(262, TYPE_SHORT, List.of((long) options.photometric())),
                new Field(273, TYPE_LONG, repeated(0, strips.size())),
                new Field(277, TYPE_SHORT, List.of((long) channels)),
                new Field(278, TYPE_LONG, List.of((long) perStrip)),
                new Field(
                        279,
                        TYPE_LONG,
                        strips.stream().map(strip -> (long) strip.length).toList()),
                new Field(284, TYPE_SHORT, List.of((long) options.planar()))));
        if (options.resolution() > 0) {
            fields.add(new Field(282, TYPE_RATIONAL, List.of((long) options.resolution())));
            fields.add(new Field(283, TYPE_RATIONAL, List.of((long) options.resolution())));
            fields.add(new Field(296, TYPE_SHORT, List.of((long) options.resolutionUnit())));
        }
        fields.sort(Comparator.comparingInt(Field::tag));

        int dirSize = 2 + fields.size() * 12 + 4;
        int heapSize = 0;
        for (Field field : fields) {
            int total = sizeOf(field.type()) * field.values().size();
            if (total > 4) {
                heapSize += total;
            }
        }
        int stripBytes = 0;
        for (byte[] strip : strips) {
            stripBytes += strip.length;
        }

        byte[] bytes = new byte[8 + dirSize + heapSize + stripBytes];
        ByteBuffer view = ByteBuffer.wrap(bytes).order(little ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        bytes[0] = (byte) (little ? 0x49 : 0x4d);
        bytes[1] = (byte) (little ? 0x49 : 0x4d);
        view.putShort(2, (short) 42);
        view.putInt(4, 8);

        int stripAt = 8 + dirSize + heapSize;
        List<Long> offsets = new ArrayList<>();
        for (byte[] strip : strips) {
            System.arraycopy(strip, 0, bytes, stripAt, strip.length);
            offsets.add((long) stripAt);
            stripAt += strip.length;
        }
        fields.replaceAll(field -> field.tag() == 273 ? new Field(273, field.type(), offsets) : field);

        view.putShort(8, (short) fields.size());
        int heap = 8 + dirSize;
        for (int i = 0; i < fields.size(); i += 1) {
            Field field = fields.get(i);
            int at = 10 + i * 12;
            view.putShort(at, (short) field.tag());
            view.putShort(at + 2, (short) field.type());
            view.putInt(at + 4, field.values().size());
            int size = sizeOf(field.type());
            int total = size * field.values().size();
            int target = total <= 4 ? at + 8 : heap;
            if (total > 4) {
                view.putInt(at + 8, heap);
                heap += total;
            }
            for (int k = 0; k < field.values().size(); k += 1) {
                long value = field.values().get(k);
                if (field.type() == TYPE_SHORT) {
                    view.putShort(target + k * 2, (short) value);
                } else if (field.type() == TYPE_LONG) {
                    view.putInt(target + k * 4, (int) value);
                } else {
                    view.putInt(target + k * 8, (int) value);
                    view.putInt(target + k * 8 + 4, 1);
                }
            }
        }
        view.putInt(10 + fields.size() * 12, 0);
        return bytes;
    }

    private static int sizeOf(int type) {
        return switch (type) {
            case TYPE_SHORT -> 2;
            case TYPE_LONG -> 4;
            default -> 8;
        };
    }

    private static List<Long> repeated(long value, int count) {
        return IntStream.range(0, count).mapToObj(i -> value).toList();
    }

    /** One directory entry as it will be written. */
    private record Field(int tag, int type, List<Long> values) {}

    /**
     * What the tiff holds. Only the extent and the samples are required; {@link #of(int, int, int...)}
     * gives every other option the default the reader tests were written against, and each {@code
     * withX} method changes one.
     *
     * @param width pixels across
     * @param height pixels down
     * @param samples raw sample values, row major; a missing one is written as zero
     * @param depth bits per sample, eight by default
     * @param channels samples per pixel, one by default
     * @param photometric the photometric interpretation code, one (black is zero) by default
     * @param compression one for none, five for lzw, 32773 for packbits; any other code writes the
     *     strips uncompressed under that code
     * @param rowsPerStrip rows in each strip; the whole height in one strip when empty
     * @param little little endian, the default, or big endian
     * @param resolution pixels per unit on both axes; no resolution tags at all when zero, the default
     * @param resolutionUnit the resolution unit code, two (inches) by default
     * @param planar the planar configuration code, one (interleaved) by default
     */
    public record Options(
            int width,
            int height,
            List<Integer> samples,
            int depth,
            int channels,
            int photometric,
            int compression,
            OptionalInt rowsPerStrip,
            boolean little,
            int resolution,
            int resolutionUnit,
            int planar) {
        /** Options of the given parts; the samples are copied. */
        public Options {
            samples = List.copyOf(samples);
            Objects.requireNonNull(rowsPerStrip, "rowsPerStrip");
        }

        /** An eight bit, one channel, uncompressed, little endian tiff of these samples in one strip. */
        public static Options of(int width, int height, int... samples) {
            return new Options(
                    width,
                    height,
                    Arrays.stream(samples).boxed().toList(),
                    8,
                    1,
                    1,
                    1,
                    OptionalInt.empty(),
                    true,
                    0,
                    2,
                    1);
        }

        /** These options with another sample depth. */
        public Options withDepth(int depth) {
            return new Options(
                    width,
                    height,
                    samples,
                    depth,
                    channels,
                    photometric,
                    compression,
                    rowsPerStrip,
                    little,
                    resolution,
                    resolutionUnit,
                    planar);
        }

        /** These options with another number of samples per pixel. */
        public Options withChannels(int channels) {
            return new Options(
                    width,
                    height,
                    samples,
                    depth,
                    channels,
                    photometric,
                    compression,
                    rowsPerStrip,
                    little,
                    resolution,
                    resolutionUnit,
                    planar);
        }

        /** These options with another photometric interpretation code. */
        public Options withPhotometric(int photometric) {
            return new Options(
                    width,
                    height,
                    samples,
                    depth,
                    channels,
                    photometric,
                    compression,
                    rowsPerStrip,
                    little,
                    resolution,
                    resolutionUnit,
                    planar);
        }

        /** These options with another compression code. */
        public Options withCompression(int compression) {
            return new Options(
                    width,
                    height,
                    samples,
                    depth,
                    channels,
                    photometric,
                    compression,
                    rowsPerStrip,
                    little,
                    resolution,
                    resolutionUnit,
                    planar);
        }

        /** These options with the image split into strips of this many rows. */
        public Options withRowsPerStrip(int rowsPerStrip) {
            return new Options(
                    width,
                    height,
                    samples,
                    depth,
                    channels,
                    photometric,
                    compression,
                    OptionalInt.of(rowsPerStrip),
                    little,
                    resolution,
                    resolutionUnit,
                    planar);
        }

        /** These options with the other byte order. */
        public Options withLittle(boolean little) {
            return new Options(
                    width,
                    height,
                    samples,
                    depth,
                    channels,
                    photometric,
                    compression,
                    rowsPerStrip,
                    little,
                    resolution,
                    resolutionUnit,
                    planar);
        }

        /** These options with resolution tags of this many pixels per unit on both axes. */
        public Options withResolution(int resolution) {
            return new Options(
                    width,
                    height,
                    samples,
                    depth,
                    channels,
                    photometric,
                    compression,
                    rowsPerStrip,
                    little,
                    resolution,
                    resolutionUnit,
                    planar);
        }

        /** These options with another resolution unit code. */
        public Options withResolutionUnit(int resolutionUnit) {
            return new Options(
                    width,
                    height,
                    samples,
                    depth,
                    channels,
                    photometric,
                    compression,
                    rowsPerStrip,
                    little,
                    resolution,
                    resolutionUnit,
                    planar);
        }

        /** These options with another planar configuration code. */
        public Options withPlanar(int planar) {
            return new Options(
                    width,
                    height,
                    samples,
                    depth,
                    channels,
                    photometric,
                    compression,
                    rowsPerStrip,
                    little,
                    resolution,
                    resolutionUnit,
                    planar);
        }
    }
}
