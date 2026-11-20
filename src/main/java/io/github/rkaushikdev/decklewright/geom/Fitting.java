package io.github.rkaushikdev.decklewright.geom;

import java.util.Objects;

/**
 * Placing one box inside another.
 *
 * <p>A service copy has a target box and the crop has an aspect that rarely matches it. Which of
 * the four answers is right depends on the collection, so the choice is a named {@link FitMode}
 * and the profile picks one.
 */
public final class Fitting {
    private Fitting() {}

    /** A rect of the given size centred in the bound on both axes. */
    public static Rect alignIn(Size size, Rect bound) {
        return alignIn(size, bound, Align.CENTRE, Align.CENTRE);
    }

    /** A rect of the given size placed in the bound by the two alignments, measured from the bound's own edges. */
    public static Rect alignIn(Size size, Rect bound, Align alignX, Align alignY) {
        Objects.requireNonNull(alignX, "alignX");
        Objects.requireNonNull(alignY, "alignY");
        double x = bound.left() + alignX.offset(size.width(), bound.width());
        double y = bound.top() + alignY.offset(size.height(), bound.height());
        return Rect.fromOrigin(new Point(x, y), size);
    }

    /** The source {@linkplain FitMode#CONTAIN contained} in the bound and centred. */
    public static Rect fitIn(Size source, Rect bound) {
        return fitIn(source, bound, FitOptions.defaults());
    }

    /**
     * Scales a source size to a bound under the named mode, then places it. An empty source or
     * bound gives an empty rect at the bound's origin.
     */
    public static Rect fitIn(Size source, Rect bound, FitOptions options) {
        if (source.isEmpty() || bound.isEmpty()) {
            return Rect.fromOrigin(bound.origin(), Size.empty());
        }
        Size sized =
                switch (options.mode()) {
                    case CONTAIN -> source.containedIn(bound.size());
                    case COVER -> source.covering(bound.size());
                    case STRETCH -> bound.size();
                    case NONE -> source;
                };
        if (!options.allowUpscale() && options.mode() == FitMode.CONTAIN && !sized.fitsInside(source)) {
            sized = source;
        }
        return alignIn(sized, bound, options.alignX(), options.alignY());
    }

    /** The scale a {@linkplain FitMode#CONTAIN contain} fit would apply. */
    public static double fitScale(Size source, Size bound) {
        return fitScale(source, bound, FitMode.CONTAIN);
    }

    /**
     * The scale factor a fit would apply, which the render stage needs before it commits to a
     * resample kernel. Zero when either side is empty; a stretch reports the smaller of its two
     * axis scales.
     */
    public static double fitScale(Size source, Size bound, FitMode mode) {
        Objects.requireNonNull(mode, "mode");
        if (source.isEmpty() || bound.isEmpty()) {
            return 0;
        }
        return switch (mode) {
            case COVER -> Math.max(bound.width() / source.width(), bound.height() / source.height());
            case NONE -> 1;
            case CONTAIN, STRETCH -> Math.min(bound.width() / source.width(), bound.height() / source.height());
        };
    }

    /**
     * The rect grown to the aspect about its centre.
     *
     * @see #forceAspect(Rect, double, AspectDirection)
     */
    public static Rect forceAspect(Rect rect, double aspect) {
        return forceAspect(rect, aspect, AspectDirection.GROW);
    }

    /**
     * Grows or shrinks a rect to a target aspect while keeping its centre. Growing is the safe
     * direction for a crop that still has image around it, so that is the default. An empty rect
     * comes back as it is.
     *
     * @param aspect width over height
     */
    public static Rect forceAspect(Rect rect, double aspect, AspectDirection direction) {
        Objects.requireNonNull(direction, "direction");
        if (rect.isEmpty()) {
            return rect;
        }
        double current = rect.width() / rect.height();
        boolean wider = current > aspect;
        boolean takeHeight = direction == AspectDirection.GROW ? wider : !wider;
        Size size = takeHeight
                ? new Size(rect.width(), rect.width() / aspect)
                : new Size(rect.height() * aspect, rect.height());
        return Rect.fromCentre(rect.centre(), size);
    }
}
