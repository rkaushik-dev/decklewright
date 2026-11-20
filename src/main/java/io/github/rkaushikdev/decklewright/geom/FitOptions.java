package io.github.rkaushikdev.decklewright.geom;

import java.util.Objects;

/**
 * How {@link Fitting#fitIn(Size, Rect, FitOptions)} scales a source to a bound and where it places
 * the result. Start from {@link #defaults()} and change what the profile asks for.
 *
 * @param mode how the source is scaled
 * @param alignX where the result sits across the bound
 * @param alignY where the result sits down the bound
 * @param allowUpscale whether {@link FitMode#CONTAIN} may make the source larger than it is
 */
public record FitOptions(FitMode mode, Align alignX, Align alignY, boolean allowUpscale) {

    /** Options of the given parts. */
    public FitOptions {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(alignX, "alignX");
        Objects.requireNonNull(alignY, "alignY");
    }

    /** Contain, centred on both axes, upscaling allowed. */
    public static FitOptions defaults() {
        return new FitOptions(FitMode.CONTAIN, Align.CENTRE, Align.CENTRE, true);
    }

    /** These options with another mode. */
    public FitOptions withMode(FitMode mode) {
        return new FitOptions(mode, alignX, alignY, allowUpscale);
    }

    /** These options with another alignment across the bound. */
    public FitOptions withAlignX(Align alignX) {
        return new FitOptions(mode, alignX, alignY, allowUpscale);
    }

    /** These options with another alignment down the bound. */
    public FitOptions withAlignY(Align alignY) {
        return new FitOptions(mode, alignX, alignY, allowUpscale);
    }

    /** These options with upscaling allowed or not. */
    public FitOptions withAllowUpscale(boolean allowUpscale) {
        return new FitOptions(mode, alignX, alignY, allowUpscale);
    }
}
