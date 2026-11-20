package io.github.rkaushikdev.decklewright.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.PipelineError;
import io.github.rkaushikdev.decklewright.codec.png.Encoding;
import io.github.rkaushikdev.decklewright.manifest.Payload;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.profile.Profile;
import io.github.rkaushikdev.decklewright.profile.ProfileRegistry;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.store.AtomicFiles;
import io.github.rkaushikdev.decklewright.store.Folder;
import io.github.rkaushikdev.decklewright.store.Workspace;
import io.github.rkaushikdev.decklewright.testing.Accession;
import io.github.rkaushikdev.decklewright.testing.TestBytes;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Whole runs over real captures: one accession, a batch, and a workspace worked to its folders. */
class RunnerTest {

    private static final ProfileRegistry REGISTRY = new ProfileRegistry();
    private static final Instant AT = Instant.parse("2025-11-27T09:00:00Z");
    private static final RunOptions STAMPED = RunOptions.defaults().withAt(AT);

    /** An upright or turned capture off the stand, wedge and scale bar beside the sheet, as a png. */
    private static byte[] capture(double degrees) {
        return Encoding.encodePng(Accession.capture(degrees));
    }

    private static byte[] capture() {
        return capture(0);
    }

    private static JobInput args(String id, double degrees, String profile, double ppi) {
        return JobInput.of(id, capture(degrees), REGISTRY.get(profile)).withSampling(Sampling.of(ppi));
    }

    private static JobInput args() {
        return args("g4181-1907", 0, "house", 400);
    }

    private static JobInput args(String id) {
        return args(id, 0, "house", 400);
    }

    private static JobInput turned(double degrees) {
        return args("g4181-1907", degrees, "house", 400);
    }

    private static Profile house() {
        return REGISTRY.get("house");
    }

    @Nested
    class StandardStagesBehaviour {
        @Test
        void isAGraphThatCanRunGivenBytes() {
            assertThat(StageGraph.objectionsToGraph(StandardStages.standardStages(), List.of("bytes", "path")))
                    .isEqualTo(List.of());
        }

        @Test
        void readsBeforeItPlansAndPacksLast() {
            List<String> names = StageGraph.order(StandardStages.standardStages()).stream()
                    .map(Stage::name)
                    .toList();
            assertThat(names.get(0)).isEqualTo("read");
            assertThat(names.get(names.size() - 1)).isEqualTo("pack");
        }

        @Test
        void describesItselfForALabCheckingANewProfile() {
            assertThat(Runner.describeRunPlan().get(0)).containsPattern("starts with");
        }
    }

    @Nested
    class RunJob {
        @Test
        void rejectsASheetThatSatWellOffSquareWhateverTheCropCanFix() {
            RunResult out = Runner.runJob(turned(2), STAMPED);
            assertThat(out.ok()).isTrue();
            assertThat(out.decision().map(Decision::decision)).hasValue(Verdict.REJECT);
            assertThat(out.decision().orElseThrow().reason()).containsPattern("held down by skew");
        }

        @Test
        void runsTheWholeThingAndDecidesToDeliver() {
            RunResult out = Runner.runJob(args(), STAMPED.withVersion("1.4.0"));
            assertThat(out.ok()).isTrue();
            assertThat(out.decision().map(Decision::decision)).hasValue(Verdict.DELIVER);
        }

        @Test
        void carriesAPayloadThatChecksOut() {
            RunResult out = Runner.runJob(args(), STAMPED);
            Payload payload = out.job().need("payload", Payload.class);
            assertThat(payload.accession()).isEqualTo("g4181-1907");
            assertThat(payload.entries().size()).isGreaterThan(4);
        }

        @Test
        void recordsEveryStageThatRan() {
            RunResult out = Runner.runJob(args(), STAMPED);
            assertThat(out.results().stream().map(StageResult::stage).toList())
                    .isEqualTo(List.of("read", "detect", "plan", "grade", "derive", "pack"));
        }

        @Test
        void stopsAtTheFirstFailureRatherThanPilingErrorsUp() {
            JobInput bad = args().withBytes(TestBytes.u8(1, 2, 3));
            RunResult out = Runner.runJob(bad, STAMPED);
            assertThat(out.ok()).isFalse();
            assertThat(out.results()).hasSize(1);
            assertThat(out.decision()).isEmpty();
        }

        @Test
        void saysWhichStageFailed() {
            RunResult out = Runner.runJob(args().withBytes(TestBytes.u8(1)), STAMPED);
            assertThat(out.results().get(0).stage()).isEqualTo("read");
            assertThat(out.error()).isPresent();
        }

        @Test
        void rejectsWorkUnderTheLevelThePolicyAsksFor() {
            RunResult out = Runner.runJob(
                    turned(5),
                    STAMPED.withPolicy(Policies.policy().withMinimumStars(4).withHoldOn(List.of())));
            assertThat(out.decision().map(Decision::decision)).hasValue(Verdict.REJECT);
            assertThat(out.decision().map(Decision::folder)).hasValue(Folder.REJECTED);
        }

        @Test
        void holdsOneThatHasAlreadyBeenDelivered() {
            RunResult out = Runner.runJob(args(), STAMPED.withAlreadyThere(true));
            assertThat(out.decision().map(Decision::decision)).hasValue(Verdict.HOLD);
        }

        @Test
        void takesAShorterListOfStages() {
            List<Stage> stages = Stages.without(StandardStages.standardStages(), List.of("grade", "pack"));
            RunResult out = Runner.runJob(args(), STAMPED.withStages(stages));
            assertThat(out.ok()).isTrue();
            assertThat(out.job().has("files")).isTrue();
            assertThat(out.job().has("payload")).isFalse();
        }

        @Test
        void refusesASetOfStagesTheRunCannotFeed() {
            List<Stage> stages = Stages.without(StandardStages.standardStages(), List.of("read"));
            assertThatThrownBy(() -> Runner.runJob(args(), RunOptions.defaults().withStages(stages)))
                    .isInstanceOf(PipelineError.class)
                    .hasMessageFindingMatch("nothing produces surface");
        }

        @Test
        void takesTheProfileThroughToTheCrop() {
            RunResult out = Runner.runJob(args("g4181-1907", 0, "rolled-drawings", 300), STAMPED);
            assertThat((List<?>) out.job().need("files")).hasSize(2);
        }

        @Test
        void timesTheRun() {
            assertThat(Runner.runJob(args(), STAMPED).ms()).isGreaterThanOrEqualTo(0);
        }
    }

    @Nested
    class RunBatch {
        @Test
        void runsSeveralAndCountsWhatHappened() {
            BatchRun out = Runner.runBatch(List.of(args("a"), args("b")), STAMPED);
            assertThat(out.runs()).hasSize(2);
            assertThat(out.delivered()).isEqualTo(2);
            assertThat(out.failed()).isEqualTo(0);
        }

        @Test
        void carriesOnPastAFailureByDefault() {
            BatchRun out =
                    Runner.runBatch(List.of(args("a"), args("b").withBytes(TestBytes.u8(1)), args("c")), STAMPED);
            assertThat(out.runs()).hasSize(3);
            assertThat(out.failed()).isEqualTo(1);
            assertThat(out.delivered()).isEqualTo(2);
        }

        @Test
        void stopsWhenThePolicySaysTo() {
            BatchRun out = Runner.runBatch(
                    List.of(args("a").withBytes(TestBytes.u8(1)), args("b")),
                    STAMPED.withPolicy(Policies.policy().withStopOnFailure(true)));
            assertThat(out.stoppedEarly()).isTrue();
            assertThat(out.runs()).hasSize(1);
        }

        @Test
        void hasNothingToReportForAnEmptyBatch() {
            BatchRun out = Runner.runBatch(List.of(), STAMPED);
            assertThat(out).isEqualTo(new BatchRun(List.of(), 0, 0, 0, 0, false));
        }
    }

    @Nested
    class DescribeRun {
        @Test
        void opensWithTheDecision() {
            List<String> lines = Runner.describeRun(Runner.runJob(args(), STAMPED));
            assertThat(lines.get(0)).containsPattern("^g4181-1907: deliver, ");
        }

        @Test
        void saysSoWhenTheRunFailed() {
            RunResult out = Runner.runJob(args().withBytes(TestBytes.u8(1)), STAMPED);
            assertThat(Runner.describeRun(out).get(0)).containsPattern("failed after 1 stages");
        }

        @Test
        void listsALinePerStage() {
            List<String> lines = Runner.describeRun(Runner.runJob(args(), STAMPED));
            assertThat(lines.stream().filter(line -> line.startsWith("  "))).hasSize(6);
        }
    }

    @Nested
    class ProcessBehaviour {
        @Test
        void writesTheDeliveryAndTheLedgerLine(@TempDir Path root) {
            Workspace space = Workspace.open(root);
            Processed out = WorkspaceRunner.process(space, args(), STAMPED.withVersion("1.4.0"));
            assertThat(out.directory()).isPresent();
            assertThat(out.entry().outcome()).isEqualTo("delivered");
            assertThat(space.list()).isEqualTo(List.of("g4181-1907"));
        }

        @Test
        void writesADeliveryThatVerifiesOffTheDisk(@TempDir Path root) {
            Workspace space = Workspace.open(root);
            WorkspaceRunner.process(space, args(), STAMPED);
            assertThat(space.verify("g4181-1907").ok()).isTrue();
        }

        @Test
        void recordsAFailureWithoutWritingAnything(@TempDir Path root) {
            Workspace space = Workspace.open(root);
            Processed out = WorkspaceRunner.process(space, args().withBytes(TestBytes.u8(1)), STAMPED);
            assertThat(out.directory()).isEmpty();
            assertThat(out.entry().outcome()).isEqualTo("failed");
            assertThat(space.list()).isEqualTo(List.of());
        }

        @Test
        void recordsAHoldAsSkippedAndWritesNothing(@TempDir Path root) {
            Workspace space = Workspace.open(root);
            WorkspaceRunner.process(space, args(), STAMPED);
            Processed again = WorkspaceRunner.process(space, args(), STAMPED);
            assertThat(again.entry().outcome()).isEqualTo("skipped");
            assertThat(again.entry().reason().orElseThrow()).containsPattern("already been delivered");
        }

        @Test
        void putsARejectionInTheRejectedFolder(@TempDir Path root) {
            Workspace space = Workspace.open(root);
            Processed out = WorkspaceRunner.process(
                    space,
                    turned(5),
                    STAMPED.withPolicy(Policies.policy().withMinimumStars(4).withHoldOn(List.of())));
            assertThat(out.entry().outcome()).isEqualTo("rejected");
            assertThat(space.list(Folder.REJECTED)).isEqualTo(List.of("g4181-1907"));
        }
    }

    @Nested
    class ProcessIncoming {
        @Test
        void worksEverythingWaiting(@TempDir Path root) {
            Workspace space = Workspace.open(root);
            AtomicFiles.writeAtomic(space.paths().incoming().resolve("g4181.png"), capture());
            AtomicFiles.writeAtomic(space.paths().incoming().resolve("g4182.png"), capture());

            ProcessedIncoming out =
                    WorkspaceRunner.processIncoming(space, house(), Optional.of(Sampling.of(400)), STAMPED);
            assertThat(out.done()).hasSize(2);
            assertThat(out.counts().get("delivered")).isEqualTo(2);
        }

        @Test
        void leavesTheSpreadsheetAlone(@TempDir Path root) {
            Workspace space = Workspace.open(root);
            AtomicFiles.writeAtomic(space.paths().incoming().resolve("notes.txt"), "nothing to report");
            ProcessedIncoming out = WorkspaceRunner.processIncoming(space, house(), Optional.empty(), STAMPED);
            assertThat(out.done()).isEqualTo(List.of());
        }

        @Test
        void recordsACaptureItCouldNotReadAndCarriesOn(@TempDir Path root) {
            Workspace space = Workspace.open(root);
            AtomicFiles.writeAtomic(space.paths().incoming().resolve("broken.png"), TestBytes.u8(1, 2, 3));
            AtomicFiles.writeAtomic(space.paths().incoming().resolve("g4181.png"), capture());

            ProcessedIncoming out =
                    WorkspaceRunner.processIncoming(space, house(), Optional.of(Sampling.of(400)), STAMPED);
            assertThat(out.counts()).isEqualTo(Map.of("failed", 1, "delivered", 1));
        }

        @Test
        void doesNotReadACaptureItCannotGradeIntoTheLedgerTwice(@TempDir Path root) {
            Workspace space = Workspace.open(root);
            Surface surface = Surface.create(60, 40, Layout.RGB, 8, 40);
            AtomicFiles.writeAtomic(space.paths().incoming().resolve("blank.png"), Encoding.encodePng(surface));

            ProcessedIncoming out =
                    WorkspaceRunner.processIncoming(space, house(), Optional.of(Sampling.of(400)), STAMPED);
            assertThat(out.done()).hasSize(1);
            assertThat(out.done().get(0).entry().outcome()).isEqualTo("failed");
        }
    }
}
