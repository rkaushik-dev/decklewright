/**
 * Operations that make a new surface from an old one: crops, pads, the lossless reorientations,
 * separable resampling and rotation by an arbitrary angle.
 *
 * <p>Each returns a surface of its own unless it says otherwise: {@link
 * io.github.rkaushikdev.decklewright.raster.ops.Crops#cropView} hands back a live view, and a {@link
 * io.github.rkaushikdev.decklewright.raster.ops.Rotation#rotate rotation} with nothing to do hands
 * back the very surface it was given. Options are small records with a {@code defaults()} factory
 * and {@code withX} copies.
 */
package io.github.rkaushikdev.decklewright.raster.ops;
