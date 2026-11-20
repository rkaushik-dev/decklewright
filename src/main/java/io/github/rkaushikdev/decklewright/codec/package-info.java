/**
 * The codec layer as the rest of the tree sees it.
 *
 * <p>Above this layer nothing imports a format by name. The intake stage calls {@link
 * io.github.rkaushikdev.decklewright.codec.Codecs#readImage(byte[], String)}, the derivative stage
 * calls {@link io.github.rkaushikdev.decklewright.codec.Codecs#writeImage(
 * io.github.rkaushikdev.decklewright.raster.Raster, String,
 * io.github.rkaushikdev.decklewright.codec.WriteOptions)}, and the verify command calls {@link
 * io.github.rkaushikdev.decklewright.codec.Inspections#inspect(byte[], String)}. Formats are an
 * {@link io.github.rkaushikdev.decklewright.codec.ImageFormat}, and the shared byte helpers are in
 * {@link io.github.rkaushikdev.decklewright.codec.Bytes}.
 *
 * <p>The individual codecs are here for the tests and for the one or two places that genuinely need
 * a chunk list: netpbm in {@link io.github.rkaushikdev.decklewright.codec.Pnm}, png in {@code
 * codec.png} and tiff in {@code codec.tiff}.
 */
package io.github.rkaushikdev.decklewright.codec;
