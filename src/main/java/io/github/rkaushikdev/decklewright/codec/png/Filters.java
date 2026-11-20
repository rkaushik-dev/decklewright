package io.github.rkaushikdev.decklewright.codec.png;

import io.github.rkaushikdev.decklewright.CodecError;
import java.util.List;
import java.util.Map;

/**
 * The five png row filters.
 *
 * <p>They are cheap and they matter: a scan of a paper sheet is mostly a slow gradient, and Paeth
 * over that gives about a third smaller file than no filter at all. Everything here works on one row
 * at a time against the row above, which is all the format allows.
 *
 * <p>Rows are raw bytes, whatever the sample depth, and {@code step} is the number of bytes in one
 * pixel, at least one. The row above is null for the first row of an image.
 */
public final class Filters {
    /** The row goes out as it is. */
    public static final int FILTER_NONE = 0;
    /** Each byte less the one a pixel to its left. */
    public static final int FILTER_SUB = 1;
    /** Each byte less the one above it. */
    public static final int FILTER_UP = 2;
    /** Each byte less the mean of the one to its left and the one above. */
    public static final int FILTER_AVERAGE = 3;
    /** Each byte less whichever neighbour the Paeth estimate lands nearest. */
    public static final int FILTER_PAETH = 4;

    /** The filters by name, indexed by filter number. */
    public static final List<String> FILTER_NAMES = List.of("none", "sub", "up", "average", "paeth");

    private static final int[] ALL = {FILTER_NONE, FILTER_SUB, FILTER_UP, FILTER_AVERAGE, FILTER_PAETH};

    private Filters() {}

    /**
     * Whichever of the three neighbours is nearest the estimate {@code a + b - c}, breaking a tie
     * towards the left and then towards the one above.
     *
     * @param a the byte to the left
     * @param b the byte above
     * @param c the byte above and to the left
     */
    public static int paethPredictor(int a, int b, int c) {
        int p = a + b - c;
        int pa = Math.abs(p - a);
        int pb = Math.abs(p - b);
        int pc = Math.abs(p - c);
        if (pa <= pb && pa <= pc) {
            return a;
        }
        if (pb <= pc) {
            return b;
        }
        return c;
    }

    /**
     * Undo one filtered row in place. The row handed in already has its filter byte stripped off, and
     * the previous row is the reconstructed one.
     *
     * @param previous the reconstructed row above, or null for the first row
     * @return the same row, now holding raw bytes
     * @throws CodecError when the filter number is not one of the five
     */
    public static byte[] undoFilter(int filter, byte[] row, byte[] previous, int step) {
        undoFilter(filter, row, 0, row.length, previous, 0, step);
        return row;
    }

    /**
     * Undo one filtered row in place where it sits inside a larger buffer, which is how the decoder
     * restores a whole image without copying a row out.
     *
     * @param data holds the row at {@code at}
     * @param above holds the reconstructed row above at {@code aboveAt}, or is null for the first row
     * @throws CodecError when the filter number is not one of the five
     */
    static void undoFilter(int filter, byte[] data, int at, int length, byte[] above, int aboveAt, int step) {
        switch (filter) {
            case FILTER_NONE -> {}
            case FILTER_SUB -> {
                for (int i = step; i < length; i += 1) {
                    data[at + i] = (byte) (data[at + i] + data[at + i - step]);
                }
            }
            case FILTER_UP -> {
                if (above != null) {
                    for (int i = 0; i < length; i += 1) {
                        data[at + i] = (byte) (data[at + i] + above[aboveAt + i]);
                    }
                }
            }
            case FILTER_AVERAGE -> {
                for (int i = 0; i < length; i += 1) {
                    int left = i >= step ? data[at + i - step] & 0xff : 0;
                    int up = above != null ? above[aboveAt + i] & 0xff : 0;
                    data[at + i] = (byte) (data[at + i] + ((left + up) >> 1));
                }
            }
            case FILTER_PAETH -> {
                for (int i = 0; i < length; i += 1) {
                    int left = i >= step ? data[at + i - step] & 0xff : 0;
                    int up = above != null ? above[aboveAt + i] & 0xff : 0;
                    int upLeft = above != null && i >= step ? above[aboveAt + i - step] & 0xff : 0;
                    data[at + i] = (byte) (data[at + i] + paethPredictor(left, up, upLeft));
                }
            }
            default -> throw unknownFilter(filter);
        }
    }

    /**
     * Filter one row of raw bytes into a new array.
     *
     * @param previous the raw bytes of the row above, or null for the first row
     * @throws CodecError when the filter number is not one of the five
     */
    public static byte[] applyFilter(int filter, byte[] row, byte[] previous, int step) {
        return applyFilter(filter, row, previous, step, new byte[row.length]);
    }

    /**
     * Filter one row of raw bytes into a buffer the caller already has.
     *
     * @param previous the raw bytes of the row above, or null for the first row
     * @param into at least as long as the row
     * @return {@code into}, holding the filtered row
     * @throws CodecError when the filter number is not one of the five
     */
    public static byte[] applyFilter(int filter, byte[] row, byte[] previous, int step, byte[] into) {
        switch (filter) {
            case FILTER_NONE -> System.arraycopy(row, 0, into, 0, row.length);
            case FILTER_SUB -> {
                for (int i = 0; i < row.length; i += 1) {
                    into[i] = (byte) (row[i] - (i >= step ? row[i - step] : 0));
                }
            }
            case FILTER_UP -> {
                for (int i = 0; i < row.length; i += 1) {
                    into[i] = (byte) (row[i] - (previous != null ? previous[i] : 0));
                }
            }
            case FILTER_AVERAGE -> {
                for (int i = 0; i < row.length; i += 1) {
                    int left = i >= step ? row[i - step] & 0xff : 0;
                    int up = previous != null ? previous[i] & 0xff : 0;
                    into[i] = (byte) (row[i] - ((left + up) >> 1));
                }
            }
            case FILTER_PAETH -> {
                for (int i = 0; i < row.length; i += 1) {
                    int left = i >= step ? row[i - step] & 0xff : 0;
                    int up = previous != null ? previous[i] & 0xff : 0;
                    int upLeft = previous != null && i >= step ? previous[i - step] & 0xff : 0;
                    into[i] = (byte) (row[i] - paethPredictor(left, up, upLeft));
                }
            }
            default -> throw unknownFilter(filter);
        }
        return into;
    }

    /**
     * The minimum sum of absolute differences heuristic from the specification. It is not optimal and
     * it is not close to slow, which is the right trade for a master that is written once and read for
     * the next hundred years. A tie goes to the lower filter number.
     *
     * @param previous the raw bytes of the row above, or null for the first row
     */
    public static ChosenFilter chooseFilter(byte[] row, byte[] previous, int step) {
        int best = FILTER_NONE;
        long bestScore = Long.MAX_VALUE;
        byte[] bestData = null;
        byte[] scratch = new byte[row.length];

        for (int filter : ALL) {
            byte[] candidate = applyFilter(filter, row, previous, step, scratch);
            long score = 0;
            for (byte value : candidate) {
                int unsigned = value & 0xff;
                score += unsigned < 128 ? unsigned : 256 - unsigned;
            }
            if (score < bestScore) {
                bestScore = score;
                best = filter;
                bestData = candidate.clone();
            }
        }
        return new ChosenFilter(best, bestData);
    }

    /**
     * The name of a filter number, such as {@code paeth}.
     *
     * @throws CodecError when the number is not one of the five
     */
    public static String filterName(int filter) {
        if (filter < 0 || filter >= FILTER_NAMES.size()) {
            throw unknownFilter(filter);
        }
        return FILTER_NAMES.get(filter);
    }

    private static CodecError unknownFilter(int filter) {
        return new CodecError("unknown row filter " + filter, Map.of("filter", filter));
    }
}
