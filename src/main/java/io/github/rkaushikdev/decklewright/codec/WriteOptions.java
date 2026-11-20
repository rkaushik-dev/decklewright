package io.github.rkaushikdev.decklewright.codec;

import io.github.rkaushikdev.decklewright.codec.png.FilterMode;
import io.github.rkaushikdev.decklewright.codec.png.PngOptions;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import java.util.Map;
import java.util.Objects;

/**
 * How an image is written, whatever format it is written in.
 *
 * <p>Each codec reads the part it understands and ignores the rest, so a recipe can set the
 * sampling frequency, the text and the level once and then name any format: a netpbm file simply
 * goes out without them.
 *
 * @param png what the png writer reads
 * @param pnm what the netpbm writer reads
 * @see Codecs#writeImage(io.github.rkaushikdev.decklewright.raster.Raster, String, WriteOptions)
 */
public record WriteOptions(PngOptions png, PnmOptions pnm) {
    /** Options of the given parts. */
    public WriteOptions {
        Objects.requireNonNull(png, "png");
        Objects.requireNonNull(pnm, "pnm");
    }

    /** Every writer's defaults. */
    public static WriteOptions defaults() {
        return new WriteOptions(PngOptions.defaults(), PnmOptions.defaults());
    }

    /** These options with other png options. */
    public WriteOptions withPng(PngOptions png) {
        return new WriteOptions(png, pnm);
    }

    /** These options with other netpbm options. */
    public WriteOptions withPnm(PnmOptions pnm) {
        return new WriteOptions(png, pnm);
    }

    /** These options with text chunks for a png. */
    public WriteOptions withText(Map<String, String> text) {
        return withPng(png.withText(text));
    }

    /** These options with a sampling frequency for a png to carry. */
    public WriteOptions withPpi(Sampling ppi) {
        return withPng(png.withPpi(ppi));
    }

    /** These options with a way of filtering a png's rows. */
    public WriteOptions withFilter(FilterMode filter) {
        return withPng(png.withFilter(filter));
    }

    /** These options with a png compression level. */
    public WriteOptions withLevel(int level) {
        return withPng(png.withLevel(level));
    }

    /** These options with netpbm's plain text variants on or off. */
    public WriteOptions withAscii(boolean ascii) {
        return withPnm(pnm.withAscii(ascii));
    }

    /** These options with a netpbm header comment. */
    public WriteOptions withComment(String comment) {
        return withPnm(pnm.withComment(comment));
    }
}
