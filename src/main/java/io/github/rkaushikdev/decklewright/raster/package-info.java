/**
 * The pixel engine: surfaces and the views onto them, their layouts and depths, sampling,
 * resampling kernels, histograms, summed area tables, statistics and conversions.
 *
 * <p>A {@link io.github.rkaushikdev.decklewright.raster.Surface} owns its samples, interleaved and
 * row by row, in {@link io.github.rkaushikdev.decklewright.raster.Samples} sized to the depth: one
 * byte a sample at eight bits and two at sixteen. A {@link
 * io.github.rkaushikdev.decklewright.raster.SurfaceView} is a live window onto part of one, and
 * both are a {@link io.github.rkaushikdev.decklewright.raster.Raster}, which is what every
 * measurement reads. The operations that make new surfaces from old ones, such as crops, pads,
 * reorientation, resampling and rotation, are in {@code raster.ops}.
 *
 * <p>Everything above this layer goes through these public types, so the split between a surface,
 * a view and their base can move again without touching the stages that use them.
 */
package io.github.rkaushikdev.decklewright.raster;
