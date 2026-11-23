package io.github.rkaushikdev.decklewright.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.PipelineError;
import io.github.rkaushikdev.decklewright.detect.CaptureOptions;
import io.github.rkaushikdev.decklewright.profile.Profile;
import io.github.rkaushikdev.decklewright.profile.ProfileRegistry;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** A job, a stage, and running one against the other. */
class StageTest {

    /** Something a toy stage can carry, compared by value. */
    private record Extent(int width) {}

    private static final Profile HOUSE = new ProfileRegistry().get("house");

    private static final StageContext CONTEXT = StageContext.of(HOUSE, Instant.parse("2025-11-27T09:00:00Z"));

    private static final Stage READ =
            Stages.stage("read", List.of(), List.of("surface"), (job, context) -> Map.of("surface", new Extent(10)));

    private static final Stage PLAN = Stages.stage(
            "plan",
            List.of("surface"),
            List.of("plan"),
            (job, context) ->
                    Map.of("plan", new Extent(job.need("surface", Extent.class).width())));

    private static final StageAction STARS = (job, context) -> Map.of("stars", 4);

    private static final Stage GRADE = Stages.stage("grade", List.of("surface", "target"), List.of("stars"), STARS)
            .asOptional();

    @Nested
    class JobBehaviour {
        @Test
        void startsWithAnAccessionAndNothingElse() {
            Job job = Job.start("g4181");
            assertThat(job.accession()).isEqualTo("g4181");
            assertThat(job.values()).isEqualTo(Map.of());
            assertThat(job.done()).isEqualTo(List.of());
        }

        @Test
        void refusesAJobAboutNothing() {
            assertThatThrownBy(() -> Job.start(""))
                    .isInstanceOf(PipelineError.class)
                    .hasMessageFindingMatch("has to be about something");
        }

        @Test
        void givesBackANewJobRatherThanChangingItself() {
            Job job = Job.start("a");
            Job later = job.with(Map.of("surface", 1));
            assertThat(job.has("surface")).isFalse();
            assertThat(later.has("surface")).isTrue();
        }

        @Test
        void isFrozenSoAStageCannotReachIntoIt() {
            Job job = Job.start("a", Map.of("surface", 1));
            assertThat(Arrays.stream(Job.class.getDeclaredFields())
                            .filter(field -> !field.isSynthetic())
                            .map(Field::getModifiers)
                            .allMatch(Modifier::isFinal))
                    .isTrue();
            assertThatThrownBy(() -> job.values().put("plan", 2)).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void refusesTwoStagesWritingTheSameValue() {
            assertThatThrownBy(() -> Job.start("a", Map.of("plan", 1)).with(Map.of("plan", 2)))
                    .isInstanceOf(PipelineError.class)
                    .hasMessageFindingMatch("already put that");
        }

        @Test
        void allowsADeliberateReplacement() {
            assertThat(Job.start("a", Map.of("plan", 1))
                            .replacing(Map.of("plan", 2))
                            .get("plan"))
                    .hasValue(2);
        }

        @Test
        void saysWhichValueAStageAskedForAndDidNotGet() {
            assertThatThrownBy(() -> Job.start("a").need("surface"))
                    .isInstanceOf(PipelineError.class)
                    .hasMessageFindingMatch("does not carry that yet");
        }

        @Test
        void treatsAValueSetToNullAsAbsent() {
            Map<String, Object> values = new HashMap<>();
            values.put("surface", null);
            assertThat(Job.start("a", values).has("surface")).isFalse();
        }

        @Test
        void recordsTheStagesThatRanInOrder() {
            Job job = Job.start("a").finished("read").finished("plan");
            assertThat(job.done()).isEqualTo(List.of("read", "plan"));
        }

        @Test
        void refusesToRunTheSameStageTwice() {
            assertThatThrownBy(() -> Job.start("a").finished("read").finished("read"))
                    .isInstanceOf(PipelineError.class)
                    .hasMessageFindingMatch("already run");
        }

        @Test
        void keepsNotesAndWarningsApart() {
            Job job = Job.start("a").noted("skipped grading").warned("the sheet touches the edge");
            assertThat(job.notes()).isEqualTo(List.of("skipped grading"));
            assertThat(job.warnings()).isEqualTo(List.of("the sheet touches the edge"));
        }

        @Test
        void addsAListOfWarningsAtOnceAndNothingForAnEmptyOne() {
            Job job = Job.start("a").warnedAll(List.of("one", "two"));
            assertThat(job.warnings()).isEqualTo(List.of("one", "two"));
            assertThat(job.warnedAll(List.of())).isSameAs(job);
        }

        @Test
        void namesWhatItCarriesWithoutPrintingIt() {
            Job job = Job.start("a", Map.of("surface", Map.of("huge", true), "plan", Map.of()))
                    .finished("read");
            assertThat(String.valueOf(job)).isEqualTo("a: after 1 stages, carrying plan, surface");
        }
    }

    @Nested
    class MissingFrom {
        @Test
        void namesWhatAJobDoesNotCarry() {
            assertThat(Job.missingFrom(Job.start("a", Map.of("surface", 1)), List.of("surface", "plan")))
                    .isEqualTo(List.of("plan"));
        }
    }

    @Nested
    class StageBehaviour {
        @Test
        void freezesWhatItWasGiven() {
            assertThat(Stage.class.isRecord()).isTrue();
            assertThatThrownBy(() -> READ.produces().add("plan")).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void refusesOneWithNoName() {
            assertThatThrownBy(() -> Stages.stage("", (job, context) -> Map.of()))
                    .isInstanceOf(PipelineError.class)
                    .hasMessageFindingMatch("has to have a name");
        }

        @Test
        void refusesOneThatDoesNothing() {
            assertThatThrownBy(() -> Stages.stage("x", null))
                    .isInstanceOf(PipelineError.class)
                    .hasMessageFindingMatch("has to do something");
        }
    }

    @Nested
    class RunStage {
        @Test
        void runsAStageAndPutsWhatItMadeOnTheJob() {
            StageResult out = Stages.runStage(READ, Job.start("a"), CONTEXT);
            assertThat(out.ok()).isTrue();
            assertThat(out.ran()).isTrue();
            assertThat(out.job().get("surface")).hasValue(new Extent(10));
        }

        @Test
        void recordsThatTheStageRan() {
            StageResult out = Stages.runStage(READ, Job.start("a"), CONTEXT);
            assertThat(out.job().done()).isEqualTo(List.of("read"));
        }

        @Test
        void givesAStageWhatEarlierOnesProduced() {
            StageResult first = Stages.runStage(READ, Job.start("a"), CONTEXT);
            StageResult second = Stages.runStage(PLAN, first.job(), CONTEXT);
            assertThat(second.job().get("plan")).hasValue(new Extent(10));
        }

        @Test
        void failsARequiredStageThatWasReachedWithoutWhatItNeeds() {
            StageResult out = Stages.runStage(PLAN, Job.start("a"), CONTEXT);
            assertThat(out.ok()).isFalse();
            assertThat(out.missing()).isEqualTo(List.of("surface"));
            assertThat(out.error().map(Throwable::getMessage).orElseThrow()).containsPattern("without what it needs");
        }

        @Test
        void skipsAnOptionalOneAndSaysSo() {
            StageResult first = Stages.runStage(READ, Job.start("a"), CONTEXT);
            StageResult out = Stages.runStage(GRADE, first.job(), CONTEXT);
            assertThat(out.ok()).isTrue();
            assertThat(out.ran()).isFalse();
            assertThat(out.missing()).isEqualTo(List.of("target"));
            assertThat(out.job().notes().get(0)).containsPattern("grade was skipped, no target");
        }

        @Test
        void leavesTheJobAloneWhenAStageFails() {
            Job job = Job.start("a");
            StageResult out = Stages.runStage(PLAN, job, CONTEXT);
            assertThat(out.job()).isSameAs(job);
        }

        @Test
        void catchesWhatAStageThrewAndSaysWhichStageItWas() {
            Stage broken = Stages.stage("broken", (job, context) -> {
                throw new IllegalStateException("the lamp went out");
            });
            StageResult out = Stages.runStage(broken, Job.start("a"), CONTEXT);
            assertThat(out.ok()).isFalse();
            assertThat(out.stage()).isEqualTo("broken");
            assertThat(out.error().map(Throwable::getMessage).orElseThrow()).containsPattern("the lamp went out");
        }

        @Test
        void failsAStageThatDidNotProduceWhatItPromised() {
            Stage liar = Stages.stage("liar", List.of(), List.of("plan"), (job, context) -> Map.of());
            StageResult out = Stages.runStage(liar, Job.start("a"), CONTEXT);
            assertThat(out.error().map(Throwable::getMessage).orElseThrow())
                    .containsPattern("did not produce what it said");
        }

        @Test
        void allowsAStageThatPromisesNothingToReturnNothing() {
            Stage quiet = Stages.stage("quiet", (job, context) -> null);
            assertThat(Stages.runStage(quiet, Job.start("a"), CONTEXT).ok()).isTrue();
        }

        @Test
        void waitsForAStageThatTakesItsTime() {
            StageAction later = (job, context) ->
                    CompletableFuture.supplyAsync(() -> Map.of("x", 1)).join();
            Stage slow = Stages.stage("slow", List.of(), List.of("x"), later);
            assertThat(Stages.runStage(slow, Job.start("a"), CONTEXT).job().get("x"))
                    .hasValue(1);
        }

        @Test
        void passesTheContextToEveryStage() {
            Stage reader = Stages.stage(
                    "reader", List.of(), List.of("seen"), (job, context) -> Map.of("seen", context.profile()));
            StageResult out = Stages.runStage(reader, Job.start("a"), StageContext.of(HOUSE, Instant.EPOCH));
            assertThat(out.job().get("seen")).containsSame(HOUSE);
        }

        @Test
        void timesWhatItRan() {
            assertThat(Stages.runStage(READ, Job.start("a"), CONTEXT).ms()).isGreaterThanOrEqualTo(0);
        }
    }

    @Nested
    class RequireDistinct {
        @Test
        void isHappyWithDistinctNames() {
            assertThatCode(() -> Stages.requireDistinct(List.of(READ, PLAN))).doesNotThrowAnyException();
        }

        @Test
        void refusesTheSameNameTwice() {
            assertThatThrownBy(() -> Stages.requireDistinct(List.of(READ, READ)))
                    .isInstanceOf(PipelineError.class)
                    .hasMessageFindingMatch("same name");
        }
    }

    @Nested
    class Without {
        @Test
        void dropsTheOnesItWasToldTo() {
            assertThat(Stages.without(List.of(READ, PLAN, GRADE), List.of("grade")).stream()
                            .map(Stage::name)
                            .toList())
                    .isEqualTo(List.of("read", "plan"));
        }

        @Test
        void ignoresANameThatIsNotThere() {
            assertThat(Stages.without(List.of(READ), List.of("nothing"))).hasSize(1);
        }
    }

    @Nested
    class InsertBefore {
        @Test
        void putsAStageInFrontOfAnother() {
            Stage extra = Stages.stage("extra", (job, context) -> Map.of());
            assertThat(Stages.insertBefore(List.of(READ, PLAN), "plan", extra).stream()
                            .map(Stage::name)
                            .toList())
                    .isEqualTo(List.of("read", "extra", "plan"));
        }

        @Test
        void refusesANameThatIsNotInTheList() {
            Stage extra = Stages.stage("extra", (job, context) -> Map.of());
            assertThatThrownBy(() -> Stages.insertBefore(List.of(READ), "nowhere", extra))
                    .isInstanceOf(PipelineError.class)
                    .hasMessageFindingMatch("no stage by that name");
        }
    }

    @Nested
    class DescribeResult {
        @Test
        void saysAStageRanAndHowLongItTook() {
            assertThat(Stages.describeResult(Stages.runStage(READ, Job.start("a"), CONTEXT)))
                    .matches("read ran in \\d+ ms");
        }

        @Test
        void saysWhyOneWasSkipped() {
            StageResult first = Stages.runStage(READ, Job.start("a"), CONTEXT);
            assertThat(Stages.describeResult(Stages.runStage(GRADE, first.job(), CONTEXT)))
                    .isEqualTo("grade was skipped, no target");
        }

        @Test
        void saysWhyOneFailed() {
            assertThat(Stages.describeResult(Stages.runStage(PLAN, Job.start("a"), CONTEXT)))
                    .containsPattern("^plan failed: ");
        }
    }

    @Nested
    class CaptureOptionsFor {
        @Test
        void tellsTheDetectorWhatTheProfileSays() {
            CaptureOptions rolled = StandardStages.captureOptionsFor(new ProfileRegistry().get("rolled-drawings"));
            assertThat(rolled.sigma()).isEqualTo(4);
            assertThat(rolled.gap()).isEqualTo(6);
            assertThat(rolled.expectNeatline()).isFalse();
            assertThat(StandardStages.captureOptionsFor(new ProfileRegistry().get("quadrangles"))
                            .expectNeatline())
                    .isTrue();
        }

        @Test
        void leavesTheRuleAndTheClaimAsTheDetectorHasThem() {
            CaptureOptions house = StandardStages.captureOptionsFor(new ProfileRegistry().get("house"));
            assertThat(house.barLength()).isEqualTo(CaptureOptions.defaults().barLength());
            assertThat(house.claimed()).isEmpty();
        }

        @Test
        void standsInForARunThatSaysNothing() {
            assertThat(RunOptions.defaults().capture()).isEmpty();
            CaptureOptions told = CaptureOptions.defaults().withSigma(9);
            assertThat(RunOptions.defaults().withCapture(told).capture()).contains(told);
            assertThat(StageContext.of(new ProfileRegistry().get("microfilm"), Instant.EPOCH)
                            .capture()
                            .speckle())
                    .isEqualTo(1);
        }
    }
}
