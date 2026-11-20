package io.github.rkaushikdev.decklewright.raster.ops;

import io.github.rkaushikdev.decklewright.raster.Samples;
import io.github.rkaushikdev.decklewright.raster.Surface;

/**
 * The lossless reorientations.
 *
 * <p>A sheet fed into the flatbed the short way comes out on its side, and the operator marks the
 * work order with a quarter turn. None of this touches a sample value, which is why it happens
 * before any resampling and why the quality checks can be run either side of it and agree. Every
 * one returns a new surface.
 */
public final class Orientation {
    private Orientation() {}

    /** Mirrors left to right. */
    public static Surface flipHorizontal(Surface surface) {
        Surface out = surface.like();
        int channels = surface.channels();
        for (int y = 0; y < surface.height(); y += 1) {
            Samples source = surface.row(y);
            Samples target = out.row(y);
            for (int x = 0; x < surface.width(); x += 1) {
                int from = x * channels;
                int to = (surface.width() - 1 - x) * channels;
                for (int c = 0; c < channels; c += 1) {
                    target.set(to + c, source.get(from + c));
                }
            }
        }
        return out;
    }

    /** Mirrors top to bottom. */
    public static Surface flipVertical(Surface surface) {
        Surface out = surface.like();
        for (int y = 0; y < surface.height(); y += 1) {
            out.row(surface.height() - 1 - y).set(surface.row(y));
        }
        return out;
    }

    /** Reflects across the main diagonal, which is the building block both quarter turns are made of. */
    public static Surface transpose(Surface surface) {
        Surface out = surface.like(surface.height(), surface.width());
        int channels = surface.channels();
        Samples target = out.data();
        for (int y = 0; y < surface.height(); y += 1) {
            Samples source = surface.row(y);
            for (int x = 0; x < surface.width(); x += 1) {
                int to = (x * out.width() + y) * channels;
                for (int c = 0; c < channels; c += 1) {
                    target.set(to + c, source.get(x * channels + c));
                }
            }
        }
        return out;
    }

    /**
     * Turns clockwise by whole quarters. Negative and out of range counts are folded, so minus one
     * is the same as three and four is a copy.
     */
    public static Surface rotateQuarters(Surface surface, int turns) {
        int folded = ((turns % 4) + 4) % 4;
        return switch (folded) {
            case 1 -> flipHorizontal(transpose(surface));
            case 2 -> flipVertical(flipHorizontal(surface));
            case 3 -> flipVertical(transpose(surface));
            default -> surface.copy();
        };
    }

    /**
     * Applies one of the eight orientations a scanner can hand back, numbered the way the exif tag
     * numbers them so a work order and a file agree. An unknown tag, one included, changes nothing
     * and comes back as a copy.
     *
     * @param orientation the exif tag value, one through eight
     */
    public static Surface applyExifOrientation(Surface surface, int orientation) {
        return switch (orientation) {
            case 2 -> flipHorizontal(surface);
            case 3 -> rotateQuarters(surface, 2);
            case 4 -> flipVertical(surface);
            case 5 -> transpose(surface);
            case 6 -> rotateQuarters(surface, 1);
            case 7 -> flipHorizontal(flipVertical(transpose(surface)));
            case 8 -> rotateQuarters(surface, 3);
            default -> surface.copy();
        };
    }
}
