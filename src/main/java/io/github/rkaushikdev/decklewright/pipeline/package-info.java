/**
 * The pipeline: one accession carried through named stages, and a policy deciding what is done with
 * the result.
 *
 * <p>A {@link io.github.rkaushikdev.decklewright.pipeline.Job} carries what is known about one
 * accession so far, and every {@link io.github.rkaushikdev.decklewright.pipeline.Stage} says what it
 * needs off the job and what it puts back. {@link io.github.rkaushikdev.decklewright.pipeline.Stages}
 * runs one stage against a job, and {@link io.github.rkaushikdev.decklewright.pipeline.StageGraph}
 * works out the order from the needs, so nobody writes it down. {@link
 * io.github.rkaushikdev.decklewright.pipeline.StandardStages} is the run a lab gets when it says
 * nothing: read, detect, plan, grade, derive and pack, each a thin call into the layer that does the
 * work.
 *
 * <p>{@link io.github.rkaushikdev.decklewright.pipeline.Runner} runs the stages over an accession or
 * a batch, stopping an accession at its first failure, and {@link
 * io.github.rkaushikdev.decklewright.pipeline.Policies} decides what a finished one is: delivered,
 * rejected, or held for a person, with the reason in words. A {@link
 * io.github.rkaushikdev.decklewright.pipeline.Policy} starts from the profile's quality section.
 *
 * <p>{@link io.github.rkaushikdev.decklewright.pipeline.WorkspaceRunner} is the pipeline as the
 * outside sees it: one call takes a workspace and an accession and does everything, read, cut, grade,
 * pack, write and record, and another does the same for everything waiting in the incoming folder.
 * It is the only code in the tree that both computes and writes, and it is deliberately short,
 * because everything it depends on has already been tested without a disk. A failure is recorded in
 * the ledger as carefully as a success.
 *
 * <p>A graph that cannot run, or a stage reached without what it needs, is a {@link
 * io.github.rkaushikdev.decklewright.PipelineError}. An error a lower layer throws inside a stage
 * keeps its own type and message, and is annotated with the stage and the accession.
 */
package io.github.rkaushikdev.decklewright.pipeline;
