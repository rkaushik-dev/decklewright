package io.github.rkaushikdev.decklewright.detect;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.Num;

/**
 * Morphology on a mask.
 *
 * <p>A square structuring element, done through a running sum, so the cost does not grow with the
 * radius. The shapes here are sheets of paper and the difference between a square element and a
 * disc is not measurable on one, but the difference between a linear pass and a quadratic one is
 * very measurable on a three hundred megapixel capture.
 *
 * <p>Outside the mask counts as background by default, so an erode eats into the border. A closing
 * has to say otherwise. It is meant to mend a gap and put everything else back, and with the
 * default it quietly shaves three pixels off every edge instead, which loses exactly the evidence
 * that a sheet ran off the bed.
 *
 * <p>A radius is rounded half up to whole pixels, and a radius that rounds to zero leaves a copy of
 * the mask.
 */
public final class Morphology {
    private Morphology() {}

    /** What a window pass decides for one pixel from how many of its window's pixels are set. */
    @FunctionalInterface
    private interface Rule {
        boolean setsPixel(double count, double area);
    }

    /**
     * Keeps only the pixels whose whole square neighbourhood is set, with outside the mask counting
     * as background.
     *
     * @see #erode(Mask, double, boolean)
     */
    public static Mask erode(Mask mask, double radius) {
        return erode(mask, radius, false);
    }

    /**
     * Keeps only the pixels whose whole square neighbourhood is set.
     *
     * @param radius half the side of the square, less the centre pixel
     * @param outsideIsSet treat beyond the border as object, so a block that reaches the border is
     *     not eaten from that side
     * @throws BadArgumentError when the radius is negative or not finite
     */
    public static Mask erode(Mask mask, double radius, boolean outsideIsSet) {
        return windowPass(mask, radius, (count, area) -> count == area, outsideIsSet);
    }

    /**
     * Sets every pixel with any set pixel in its square neighbourhood, clipped at the border.
     *
     * @throws BadArgumentError when the radius is negative or not finite
     */
    public static Mask dilate(Mask mask, double radius) {
        return windowPass(mask, radius, (count, area) -> count > 0, false);
    }

    /**
     * Erode then dilate, which removes speckle without shrinking what survives.
     *
     * @throws BadArgumentError when the radius is negative or not finite
     */
    public static Mask open(Mask mask, double radius) {
        return dilate(erode(mask, radius), radius);
    }

    /**
     * Dilate then erode, which closes the gap a fold or a tear leaves in the outline of a sheet. The
     * erode treats beyond the border as object, so nothing that reaches the border is shaved off.
     *
     * @throws BadArgumentError when the radius is negative or not finite
     */
    public static Mask close(Mask mask, double radius) {
        return erode(dilate(mask, radius), radius, true);
    }

    /** The set pixels that have an unset neighbour, which is the outline the quad fitter walks. */
    public static Mask boundary(Mask mask) {
        Mask out = new Mask(mask.width(), mask.height());
        for (int y = 0; y < mask.height(); y += 1) {
            for (int x = 0; x < mask.width(); x += 1) {
                if (mask.get(x, y) == 0) {
                    continue;
                }
                boolean surrounded = mask.get(x - 1, y) != 0
                        && mask.get(x + 1, y) != 0
                        && mask.get(x, y - 1) != 0
                        && mask.get(x, y + 1) != 0;
                out.data[y * mask.width() + x] = (byte) (surrounded ? 0 : 1);
            }
        }
        return out;
    }

    /**
     * Fills anything unset that cannot reach the border. A sheet with a light patch in the middle
     * comes out of the threshold with a hole in it, and the hole is not a hole in the paper.
     */
    public static Mask fillHoles(Mask mask) {
        int width = mask.width();
        int height = mask.height();
        Mask reachable = new Mask(width, height);
        IntStack stack = new IntStack();

        for (int x = 0; x < width; x += 1) {
            reach(mask, reachable, stack, x, 0);
            reach(mask, reachable, stack, x, height - 1);
        }
        for (int y = 0; y < height; y += 1) {
            reach(mask, reachable, stack, 0, y);
            reach(mask, reachable, stack, width - 1, y);
        }

        while (!stack.isEmpty()) {
            int y = stack.pop();
            int x = stack.pop();
            reach(mask, reachable, stack, x - 1, y);
            reach(mask, reachable, stack, x + 1, y);
            reach(mask, reachable, stack, x, y - 1);
            reach(mask, reachable, stack, x, y + 1);
        }

        Mask out = new Mask(width, height);
        for (int i = 0; i < mask.data.length; i += 1) {
            out.data[i] = (byte) (mask.data[i] != 0 || reachable.data[i] == 0 ? 1 : 0);
        }
        return out;
    }

    /** Marks an unset pixel as reachable from the border and queues it, once. */
    private static void reach(Mask mask, Mask reachable, IntStack stack, int x, int y) {
        if (x < 0 || y < 0 || x >= mask.width() || y >= mask.height()) {
            return;
        }
        if (mask.get(x, y) != 0 || reachable.get(x, y) != 0) {
            return;
        }
        reachable.set(x, y, 1);
        stack.push(x, y);
    }

    /**
     * A summed area table one row and column larger than the mask, so the count of set pixels in any
     * window is four lookups.
     */
    private static int[] prefixSum(Mask mask) {
        int stride = mask.width() + 1;
        int[] sums = new int[stride * (mask.height() + 1)];
        for (int y = 0; y < mask.height(); y += 1) {
            int start = y * mask.width();
            int running = 0;
            int here = (y + 1) * stride;
            int above = y * stride;
            for (int x = 0; x < mask.width(); x += 1) {
                running += mask.data[start + x] & 0xff;
                sums[here + x + 1] = sums[above + x + 1] + running;
            }
        }
        return sums;
    }

    private static Mask windowPass(Mask mask, double radius, Rule rule, boolean outsideIsSet) {
        Guards.requireNonNegative(radius, "radius");
        double r = Num.roundHalfUp(radius);
        if (r == 0) {
            return mask.copy();
        }

        int width = mask.width();
        int height = mask.height();
        int[] sums = prefixSum(mask);
        int stride = width + 1;
        Mask out = new Mask(width, height);
        double side = 2 * r + 1;
        double fullArea = side * side;

        for (int y = 0; y < height; y += 1) {
            int top = (int) Math.max(0, y - r);
            int bottom = (int) Math.min(height, y + r + 1);
            for (int x = 0; x < width; x += 1) {
                int left = (int) Math.max(0, x - r);
                int right = (int) Math.min(width, x + r + 1);
                double count = (double) sums[bottom * stride + right]
                        - sums[bottom * stride + left]
                        - sums[top * stride + right]
                        + sums[top * stride + left];
                if (outsideIsSet) {
                    count += fullArea - (double) (right - left) * (bottom - top);
                }
                out.data[y * width + x] = (byte) (rule.setsPixel(count, fullArea) ? 1 : 0);
            }
        }
        return out;
    }
}
