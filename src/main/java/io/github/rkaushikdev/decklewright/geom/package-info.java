/**
 * Geometry in image space: points, sizes, rects, insets, angles, quads, polygons and affine
 * transforms, measured in pixels of the capture with x to the right and y down.
 *
 * <p>Every coordinate is a real number, because the planner works between pixels; {@link
 * io.github.rkaushikdev.decklewright.geom.Rounding} is the one step that turns a planned crop into
 * whole pixels. The shapes are immutable values with an exact {@code equals}, and each also has an
 * {@code approxEquals} that compares within a tolerance, which is what measurements want.
 */
package io.github.rkaushikdev.decklewright.geom;
