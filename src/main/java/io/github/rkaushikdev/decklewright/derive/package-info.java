/**
 * The derivative layer as the rest of the tree sees it: a capture, a plan and a profile in, named
 * bytes out.
 *
 * <p>A stage calls {@link io.github.rkaushikdev.decklewright.derive.Derivatives#deriveFromPlan} and
 * gets back a {@link io.github.rkaushikdev.decklewright.derive.DerivedSet}: one {@link
 * io.github.rkaushikdev.decklewright.derive.DerivedFile} per recipe, each with its file name, bytes,
 * format and frequency, the steps that made them, and their total size. {@link
 * io.github.rkaushikdev.decklewright.derive.Derivatives#preservationCopy} picks the master out of
 * the files.
 *
 * <p>Below that there are four steps, cutting, rendering, encoding and naming, and each is public on
 * its own, because the question an operator asks about a bad derivative is which step went wrong,
 * and answering it means being able to run them apart. {@link
 * io.github.rkaushikdev.decklewright.derive.Rendering} straightens the capture once, cuts it once
 * and renders every derivative off that cut; {@link
 * io.github.rkaushikdev.decklewright.derive.Recipes} encodes each in the format its recipe names,
 * with its frequency and provenance text, and reads both back out of written bytes; {@link
 * io.github.rkaushikdev.decklewright.derive.FileNames} names the files from the profile's pattern
 * and objects to names that would collide.
 *
 * <p>Nothing here touches a disk. That is deliberate and it is the reason the whole layer is
 * testable without a temporary directory: a derivative is a pure function of a capture, a plan and
 * a profile, and anything that is not belongs in the store. A set that cannot be made is a {@link
 * io.github.rkaushikdev.decklewright.PipelineError}; a format that cannot hold what it is handed is
 * an {@link io.github.rkaushikdev.decklewright.UnsupportedError}.
 */
package io.github.rkaushikdev.decklewright.derive;
