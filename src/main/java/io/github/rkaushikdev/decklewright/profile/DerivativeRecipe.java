package io.github.rkaushikdev.decklewright.profile;

import io.github.rkaushikdev.decklewright.raster.Layout;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * What the derive stage takes from a profile for one derivative. It is the profile's derivative with
 * the depth turned into the number the raster layer takes.
 *
 * @param name what the derivative is called, which goes into its file name
 * @param format the format it is written in
 * @param longestSide the pixels its longest side is reduced to; empty when it is not
 * @param scale the factor it is resampled by; empty when it is not
 * @param ppi the sampling frequency it is reduced to, against the master's; empty when it is not
 * @param depth the bits per sample it is written at, 8 or 16
 * @param layout the channels it is written with
 * @param kernel the name of the resampling kernel, such as {@code catmullRom}
 * @param keepMetadata true when the master's text metadata is carried into the file
 * @see Settings#derivativeRecipes(Profile)
 */
public record DerivativeRecipe(
        String name,
        OutputFormat format,
        OptionalDouble longestSide,
        OptionalDouble scale,
        OptionalDouble ppi,
        int depth,
        Layout layout,
        String kernel,
        boolean keepMetadata) {

    /** A recipe of the given values. */
    public DerivativeRecipe {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(longestSide, "longestSide");
        Objects.requireNonNull(scale, "scale");
        Objects.requireNonNull(ppi, "ppi");
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(kernel, "kernel");
    }
}
