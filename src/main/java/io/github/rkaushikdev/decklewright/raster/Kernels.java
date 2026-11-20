package io.github.rkaushikdev.decklewright.raster;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.Num;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Resampling kernels and the tap lists built from them.
 *
 * <p>The lab uses three of these in anger. Box for the thumbnail, because the reduction is large
 * and anything else just costs time. Catmull Rom for the service copy, because it holds an edge
 * without ringing badly. Lanczos for the rare case where a master has to be resized at all, which
 * the policy says should not happen but occasionally does.
 */
public final class Kernels {
    /** Every source sample within half a sample, equally. */
    public static final Kernel BOX = Kernel.of("box", 0.5, t -> Math.abs(t) <= 0.5 ? 1 : 0);

    /** Linear falloff to zero one sample out. */
    public static final Kernel TRIANGLE = Kernel.of("triangle", 1, t -> {
        double a = Math.abs(t);
        return a < 1 ? 1 - a : 0;
    });

    /** The cubic that passes through the samples and holds an edge. */
    public static final Kernel CATMULL_ROM = cubicFamily(0, 0.5, "catmullRom");

    /** The softer cubic that never goes negative near the centre. */
    public static final Kernel MITCHELL = cubicFamily(1.0 / 3, 1.0 / 3, "mitchell");

    private static final Map<String, Kernel> REGISTRY = registry();

    /** Every kernel {@link #kernelFor(String)} knows by name. */
    public static final List<String> KERNEL_NAMES = List.copyOf(REGISTRY.keySet());

    private Kernels() {}

    /**
     * The windowed sinc with the given number of lobes either side.
     *
     * @throws BadArgumentError when the count is not above zero
     */
    public static Kernel lanczos(int lobes) {
        Guards.requirePositiveInteger(lobes, "lobes");
        return Kernel.of("lanczos" + lobes, lobes, t -> {
            double a = Math.abs(t);
            if (a < 1e-12) {
                return 1;
            }
            if (a >= lobes) {
                return 0;
            }
            double pi = Math.PI * a;
            return (lobes * StrictMath.sin(pi) * StrictMath.sin(pi / lobes)) / (pi * pi);
        });
    }

    /**
     * The kernel a recipe names.
     *
     * @throws BadArgumentError when the name is empty or names no kernel; the details list every
     *     known name
     */
    public static Kernel kernelFor(String name) {
        Guards.requireNonEmptyString(name, "kernel");
        Kernel found = REGISTRY.get(name);
        if (found == null) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("kernel", name);
            details.put("known", KERNEL_NAMES);
            throw new BadArgumentError("unknown kernel " + name, details);
        }
        return found;
    }

    /** The kernel itself, so a caller holding either a name or a kernel can resolve it the same way. */
    public static Kernel kernelFor(Kernel kernel) {
        return Objects.requireNonNull(kernel, "kernel");
    }

    /**
     * The taps for every output sample along one axis, with the {@linkplain EdgeMode#CLAMP clamp}
     * edge policy.
     *
     * @see #buildContributions(int, int, Kernel, EdgeMode)
     */
    public static List<Contribution> buildContributions(int sourceExtent, int targetExtent, Kernel kernel) {
        return buildContributions(sourceExtent, targetExtent, kernel, EdgeMode.CLAMP);
    }

    /**
     * Builds the taps that make each output row or column.
     *
     * <p>When the image is being reduced the kernel has to be stretched to cover the samples that
     * are being merged, or the result aliases. When it is being enlarged the kernel keeps its own
     * width. That asymmetry is the whole reason this is not a two line loop. A tap list the edge
     * policy empties falls back on the one nearest sample.
     *
     * @return one tap list per output sample, in order
     * @throws BadArgumentError when either extent is not above zero
     */
    public static List<Contribution> buildContributions(
            int sourceExtent, int targetExtent, Kernel kernel, EdgeMode edge) {
        Guards.requirePositiveInteger(sourceExtent, "sourceExtent");
        Guards.requirePositiveInteger(targetExtent, "targetExtent");
        Objects.requireNonNull(kernel, "kernel");
        Objects.requireNonNull(edge, "edge");
        double scale = (double) targetExtent / sourceExtent;
        double stretch = scale < 1 ? 1 / scale : 1;
        double support = kernel.support() * stretch;

        List<Contribution> out = new ArrayList<>(targetExtent);
        for (int i = 0; i < targetExtent; i += 1) {
            double centre = (i + 0.5) / scale - 0.5;
            int first = (int) Math.ceil(centre - support);
            int last = (int) Math.floor(centre + support);

            int[] indices = new int[Math.max(1, last - first + 1)];
            double[] weights = new double[indices.length];
            int taps = 0;
            double total = 0;
            for (int j = first; j <= last; j += 1) {
                double weight = kernel.weight((j - centre) / stretch);
                if (weight == 0) {
                    continue;
                }
                int index = Sampler.resolveIndex(j, sourceExtent, edge);
                if (index < 0) {
                    continue;
                }
                indices[taps] = index;
                weights[taps] = weight;
                taps += 1;
                total += weight;
            }
            if (taps == 0) {
                indices[0] = Sampler.resolveIndex((int) Num.roundHalfUp(centre), sourceExtent, EdgeMode.CLAMP);
                weights[0] = 1;
                taps = 1;
                total = 1;
            }
            for (int k = 0; k < taps; k += 1) {
                weights[k] /= total;
            }
            out.add(new Contribution(first, Arrays.copyOf(indices, taps), Arrays.copyOf(weights, taps)));
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * How wide a halo a kernel needs when it is used at a given scale, which the tiled renderer has
     * to know before it reads anything.
     *
     * @throws BadArgumentError when the scale is not above zero
     */
    public static int haloFor(Kernel kernel, double scale) {
        Guards.requirePositive(scale, "scale");
        double stretch = scale < 1 ? 1 / scale : 1;
        return (int) Math.ceil(kernel.support() * stretch);
    }

    /** The Mitchell Netravali family, where {@code b} and {@code c} pick the member. */
    private static Kernel cubicFamily(double b, double c, String name) {
        return Kernel.of(name, 2, t -> {
            double a = Math.abs(t);
            double a2 = a * a;
            double a3 = a2 * a;
            if (a < 1) {
                return ((12 - 9 * b - 6 * c) * a3 + (-18 + 12 * b + 6 * c) * a2 + (6 - 2 * b)) / 6;
            }
            if (a < 2) {
                return ((-b - 6 * c) * a3 + (6 * b + 30 * c) * a2 + (-12 * b - 48 * c) * a + (8 * b + 24 * c)) / 6;
            }
            return 0;
        });
    }

    private static Map<String, Kernel> registry() {
        Map<String, Kernel> kernels = new LinkedHashMap<>();
        kernels.put("box", BOX);
        kernels.put("triangle", TRIANGLE);
        kernels.put("catmullRom", CATMULL_ROM);
        kernels.put("mitchell", MITCHELL);
        kernels.put("lanczos2", lanczos(2));
        kernels.put("lanczos3", lanczos(3));
        return Collections.unmodifiableMap(kernels);
    }
}
