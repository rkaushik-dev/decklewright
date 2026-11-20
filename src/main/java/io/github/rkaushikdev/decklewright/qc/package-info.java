/**
 * Quality control: grading a capture against the profile's floors at a star level.
 *
 * <p>One call grades a capture and one call turns the grading into something a person or a machine
 * reads: {@link io.github.rkaushikdev.decklewright.qc.Grading#gradeCapture} runs the checks and
 * says what to change on the stand, and {@link
 * io.github.rkaushikdev.decklewright.qc.Grading#reportOn} and {@link
 * io.github.rkaushikdev.decklewright.qc.Grading#gradingToJson} write the same grading out. The
 * pipeline's grade stage calls {@link io.github.rkaushikdev.decklewright.qc.QcRunner#runQc} and
 * keeps the {@link io.github.rkaushikdev.decklewright.qc.QcResult}.
 *
 * <p>Everything else is public because a lab that disagrees with a limit needs to be able to run
 * one check on its own and see the number, and because the ladders are the documentation: what a
 * {@link io.github.rkaushikdev.decklewright.qc.Check} is and how one runs, the {@link
 * io.github.rkaushikdev.decklewright.qc.QcContext} it reads, the level a set of findings reaches
 * ({@link io.github.rkaushikdev.decklewright.qc.Levels}), the reports ({@link
 * io.github.rkaushikdev.decklewright.qc.Reports}), the patch sampler the checks share ({@link
 * io.github.rkaushikdev.decklewright.qc.Patches}), and the standard checks and their ladders in
 * {@code qc.checks}.
 *
 * <p>Nothing here writes a file or decides what happens next. A capture that grades at two stars is
 * still a capture, and whether two stars is good enough to deliver is the pipeline's decision, not
 * this layer's. A check that cannot measure declines rather than fails; the only error this layer
 * throws on purpose is a {@link io.github.rkaushikdev.decklewright.QcError} for a check with no body
 * or a set holding one check twice.
 */
package io.github.rkaushikdev.decklewright.qc;
