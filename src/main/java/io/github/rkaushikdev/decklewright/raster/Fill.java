package io.github.rkaushikdev.decklewright.raster;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A flat tone for samples that have no source: the border a pad adds, the part of a crop that fell
 * off the bed, the corners a rotation uncovers and the backdrop a coverage channel is composited
 * over.
 *
 * <p>A profile states it either as one value for every channel or as one value per channel, and the
 * two are kept apart: a per channel list of the wrong length is a mistake that is reported where it
 * is used, never stretched to fit.
 */
public sealed interface Fill {
    /** The same value in every channel, such as paper white or bed black. */
    static Fill uniform(double value) {
        return new Uniform(value);
    }

    /** One value per channel, in sample order. */
    static Fill perChannel(double... values) {
        return new PerChannel(Arrays.stream(values).boxed().toList());
    }

    /**
     * The value for each of {@code channels} channels: the uniform value repeated, or the per
     * channel values exactly as given, whatever their number.
     */
    double[] forChannels(int channels);

    /**
     * The value for each of {@code channels} channels, when the fill has exactly that many.
     *
     * @throws BadArgumentError when a per channel fill has the wrong number of values
     */
    default double[] requireChannels(int channels) {
        double[] values = forChannels(channels);
        if (values.length != channels) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("given", values.length);
            details.put("expected", channels);
            throw new BadArgumentError("the fill colour has the wrong number of channels", details);
        }
        return values;
    }

    /**
     * One value for every channel.
     *
     * @param value a sample value; stored as {@link Samples#set(int, double)} stores it
     */
    record Uniform(double value) implements Fill {
        @Override
        public double[] forChannels(int channels) {
            double[] out = new double[channels];
            Arrays.fill(out, value);
            return out;
        }
    }

    /**
     * One value per channel.
     *
     * @param values sample values in channel order
     */
    record PerChannel(List<Double> values) implements Fill {
        /** A fill of the given values, copied. */
        public PerChannel {
            values = List.copyOf(Objects.requireNonNull(values, "values"));
        }

        @Override
        public double[] forChannels(int channels) {
            return values.stream().mapToDouble(Double::doubleValue).toArray();
        }
    }
}
