/**
 * Physical measurement: lengths in the units curators write, sampling frequency, which is the one
 * bridge between pixels and real sizes, and the lopsided tolerance bands the funder quotes.
 *
 * <p>A {@link io.github.rkaushikdev.decklewright.measure.Length} keeps the {@link
 * io.github.rkaushikdev.decklewright.measure.Unit} it was written in so a report can quote it back
 * the same way. A {@link io.github.rkaushikdev.decklewright.measure.Sampling} turns lengths into
 * pixels and back, and {@link io.github.rkaushikdev.decklewright.measure.PhysicalSizes} does the same
 * for whole sheets and knows the nominal sizes the lab quotes. A {@link
 * io.github.rkaushikdev.decklewright.measure.Tolerance} says how far a figure may stray from its
 * target and on which side it failed. {@code toLength}, {@code toSampling} and {@code toTolerance}
 * read the shapes a json profile holds these in.
 */
package io.github.rkaushikdev.decklewright.measure;
