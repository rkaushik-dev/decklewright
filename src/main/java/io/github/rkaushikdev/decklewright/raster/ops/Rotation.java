package io.github.rkaushikdev.decklewright.raster.ops;

import io.github.rkaushikdev.decklewright.geom.Angle;
import io.github.rkaushikdev.decklewright.geom.Matrix;
import io.github.rkaushikdev.decklewright.geom.Point;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.raster.Sampler;
import io.github.rkaushikdev.decklewright.raster.Samples;
import io.github.rkaushikdev.decklewright.raster.Surface;
import java.util.Objects;

/**
 * Rotation by an arbitrary angle.
 *
 * <p>A deskew is nearly always under three degrees, and the quarter turns are pulled out first so a
 * sheet fed sideways does not pay for a resample it does not need. What is left goes through the
 * sampler once. Twice would be one more generation of loss than the policy allows on a master.
 */
public final class Rotation {
    private Rotation() {}

    /**
     * Rotates with {@linkplain RotateOptions#defaults() the defaults}.
     *
     * @see #rotate(Surface, Angle, RotateOptions)
     */
    public static Rotated rotate(Surface surface, Angle angle) {
        return rotate(surface, angle, RotateOptions.defaults());
    }

    /**
     * Rotates clockwise by the angle: whole quarter turns losslessly, then the residual through the
     * sampler. When there is no residual nothing is resampled, and when there is nothing to do at
     * all the very surface handed in comes back.
     */
    public static Rotated rotate(Surface surface, Angle angle, RotateOptions options) {
        Objects.requireNonNull(options, "options");
        Angle.Split split = angle.split();
        int quarterTurns = split.quarterTurns();
        Angle residual = split.residual();
        Surface turned = quarterTurns == 0 ? surface : Orientation.rotateQuarters(surface, quarterTurns);
        if (residual.isZero()) {
            return new Rotated(turned, Matrix.identity(), residual, quarterTurns);
        }

        Rect bounds = turned.bounds();
        Matrix spin = Matrix.rotation(residual, bounds.centre());
        Rect box = options.expand() ? spin.applyToRect(bounds).outward() : bounds;
        int width = (int) Math.max(1, Math.round(box.width()));
        int height = (int) Math.max(1, Math.round(box.height()));

        Matrix place = spin.then(Matrix.translation(-box.left(), -box.top()));
        Matrix back = place.inverse();
        Sampler sampler = new Sampler(turned, options.edge(), options.background());

        Surface out = turned.like(width, height);
        int channels = turned.channels();
        for (int y = 0; y < height; y += 1) {
            Samples row = out.row(y);
            for (int x = 0; x < width; x += 1) {
                Point source = back.apply(new Point(x, y));
                int base = x * channels;
                for (int c = 0; c < channels; c += 1) {
                    row.set(base + c, sampler.quantised(source.x(), source.y(), c, options.sample()));
                }
            }
        }
        return new Rotated(out, place, residual, quarterTurns);
    }

    /**
     * Takes a measured skew back out with {@linkplain RotateOptions#defaults() the defaults}.
     *
     * @see #deskew(Surface, Angle, RotateOptions)
     */
    public static Rotated deskew(Surface surface, Angle skew) {
        return deskew(surface, skew, RotateOptions.defaults());
    }

    /**
     * Takes a measured skew back out. The angle handed in is the one the detector reported, so the
     * rotation applied is its negation and callers stop having to remember which way round that
     * goes.
     */
    public static Rotated deskew(Surface surface, Angle skew, RotateOptions options) {
        return rotate(surface, skew.negated(), options);
    }

    /**
     * The canvas a rotation would need, worked out without touching a sample. The planner asks this
     * before it commits to a crop, because a deskew that grows the canvas moves every coordinate the
     * plan was written in.
     *
     * @return the bounds themselves when the angle is a whole number of half turns
     */
    public static Rect rotatedBounds(Rect bounds, Angle angle) {
        Angle.Split split = angle.split();
        Rect turned = split.quarterTurns() % 2 == 0
                ? bounds
                : Rect.of(bounds.x(), bounds.y(), bounds.height(), bounds.width());
        if (split.residual().isZero()) {
            return turned;
        }
        return Matrix.rotation(split.residual(), turned.centre())
                .applyToRect(turned)
                .outward();
    }
}
