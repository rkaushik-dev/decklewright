package io.github.rkaushikdev.decklewright.raster.ops;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.geom.Size;
import io.github.rkaushikdev.decklewright.raster.Contribution;
import io.github.rkaushikdev.decklewright.raster.Kernels;
import io.github.rkaushikdev.decklewright.raster.Samples;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.Num;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Separable resampling.
 *
 * <p>Horizontal first into a float buffer, then vertical out of it. Doing both passes in one go
 * would need a two dimensional kernel and about forty times the arithmetic for a result nobody
 * could tell apart. The intermediate stays in floats so a sixteen bit master does not lose a bit to
 * the first pass. The output keeps the source's layout and depth.
 */
public final class Resampling {
    private Resampling() {}

    /**
     * Resamples with {@linkplain ResampleOptions#defaults() the defaults}.
     *
     * @see #resample(Surface, Size, ResampleOptions)
     */
    public static Surface resample(Surface surface, Size size) {
        return resample(surface, size, ResampleOptions.defaults());
    }

    /**
     * Resamples to the size, rounded half up to whole pixels. A size that is already the
     * surface's comes back as a copy.
     *
     * @throws BadArgumentError when the size rounds to less than one pixel either way
     */
    public static Surface resample(Surface surface, Size size, ResampleOptions options) {
        double wantedWidth = Num.roundHalfUp(size.width());
        double wantedHeight = Num.roundHalfUp(size.height());
        if (wantedWidth < 1 || wantedHeight < 1) {
            throw new BadArgumentError(
                    "a resample target needs at least one pixel each way", Map.of("size", size.toJson()));
        }
        Objects.requireNonNull(options, "options");
        int width = (int) wantedWidth;
        int height = (int) wantedHeight;
        int channels = surface.channels();

        if (width == surface.width() && height == surface.height()) {
            return surface.copy();
        }

        // Allocated first so a target too large to hold fails before any taps are built.
        Surface out = surface.like(width, height);
        List<Contribution> horizontal =
                Kernels.buildContributions(surface.width(), width, options.kernel(), options.edge());
        List<Contribution> vertical =
                Kernels.buildContributions(surface.height(), height, options.kernel(), options.edge());

        double[] middle = new double[Math.multiplyExact(Math.multiplyExact(width, surface.height()), channels)];
        for (int y = 0; y < surface.height(); y += 1) {
            Samples source = surface.row(y);
            int base = y * width * channels;
            for (int x = 0; x < width; x += 1) {
                Contribution tap = horizontal.get(x);
                int at = base + x * channels;
                for (int c = 0; c < channels; c += 1) {
                    double acc = 0;
                    for (int k = 0; k < tap.size(); k += 1) {
                        acc += source.get(tap.index(k) * channels + c) * tap.weight(k);
                    }
                    middle[at + c] = acc;
                }
            }
        }

        int top = out.maxValue();
        for (int y = 0; y < height; y += 1) {
            Contribution tap = vertical.get(y);
            Samples target = out.row(y);
            for (int x = 0; x < width; x += 1) {
                for (int c = 0; c < channels; c += 1) {
                    double acc = 0;
                    for (int k = 0; k < tap.size(); k += 1) {
                        acc += middle[(tap.index(k) * width + x) * channels + c] * tap.weight(k);
                    }
                    target.set(x * channels + c, (int) Num.clamp(Num.roundHalfUp(acc), 0, top));
                }
            }
        }
        return out;
    }

    /**
     * Scales both axes by the factor with {@linkplain ResampleOptions#defaults() the defaults}.
     *
     * @see #scaleBy(Surface, double, ResampleOptions)
     */
    public static Surface scaleBy(Surface surface, double factor) {
        return scaleBy(surface, factor, ResampleOptions.defaults());
    }

    /**
     * Scales both axes by the factor, rounding each extent half up and never below one pixel.
     *
     * @throws BadArgumentError when the factor is not above zero
     */
    public static Surface scaleBy(Surface surface, double factor, ResampleOptions options) {
        Guards.requirePositive(factor, "factor");
        return resample(
                surface,
                Size.of(
                        Math.max(1, Num.roundHalfUp(surface.width() * factor)),
                        Math.max(1, Num.roundHalfUp(surface.height() * factor))),
                options);
    }

    /**
     * Reduces so the long side lands on the target with {@linkplain ResampleOptions#defaults() the
     * defaults}.
     *
     * @see #fitLongestSide(Surface, double, ResampleOptions)
     */
    public static Surface fitLongestSide(Surface surface, double longest) {
        return fitLongestSide(surface, longest, ResampleOptions.defaults());
    }

    /**
     * Reduces so the long side lands on a target, which is how the thumbnail recipe is written.
     * Never enlarges, because a thumbnail larger than its master would be an odd thing to hand out;
     * a capture that is already small enough comes back as a copy.
     *
     * @throws BadArgumentError when the target is not above zero
     */
    public static Surface fitLongestSide(Surface surface, double longest, ResampleOptions options) {
        Guards.requirePositive(longest, "longest");
        int current = Math.max(surface.width(), surface.height());
        if (current <= longest) {
            return surface.copy();
        }
        return scaleBy(surface, longest / current, options);
    }

    /**
     * Reduces in steps with {@linkplain ResampleOptions#defaults() the defaults} for the final pass.
     *
     * @see #reduceInSteps(Surface, Size, ResampleOptions)
     */
    public static Reduction reduceInSteps(Surface surface, Size size) {
        return reduceInSteps(surface, size, ResampleOptions.defaults());
    }

    /**
     * Halves with a box repeatedly until one more halving would overshoot, then resamples the rest of
     * the way. A large reduction done in one pass with a wide kernel is slow, and done in one pass
     * with a narrow one aliases.
     *
     * @param options for the final pass only; the halvings always use a clamped box
     * @throws BadArgumentError when the size rounds to less than one pixel either way
     */
    public static Reduction reduceInSteps(Surface surface, Size size, ResampleOptions options) {
        ResampleOptions halving = ResampleOptions.defaults().withKernel(Kernels.BOX);
        Surface current = surface;
        int halvings = 0;
        while (current.width() >= size.width() * 2 && current.height() >= size.height() * 2) {
            Size half = Size.of(Math.max(1, current.width() / 2), Math.max(1, current.height() / 2));
            if (half.width() < size.width() || half.height() < size.height()) {
                break;
            }
            current = resample(current, half, halving);
            halvings += 1;
        }
        return new Reduction(resample(current, size, options), halvings);
    }
}
