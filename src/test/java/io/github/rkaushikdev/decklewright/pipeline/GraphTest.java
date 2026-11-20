package io.github.rkaushikdev.decklewright.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.PipelineError;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The order the stages run in, worked out from what each needs and produces. */
class GraphTest {

    private static Stage step(String name, List<String> needs, List<String> produces) {
        return Stages.stage(name, needs, produces, (job, context) -> Map.of());
    }

    private static List<String> names(List<Stage> stages) {
        return stages.stream().map(Stage::name).toList();
    }

    private static final Stage READ = step("read", List.of("bytes"), List.of("surface"));
    private static final Stage DETECT = step("detect", List.of("surface"), List.of("reading"));
    private static final Stage PLAN = step("plan", List.of("surface", "reading"), List.of("plan"));
    private static final Stage DERIVE = step("derive", List.of("surface", "plan"), List.of("files"));
    private static final Stage GRADE = step("grade", List.of("surface", "reading"), List.of("quality"));
    private static final Stage PACK = step("pack", List.of("files", "quality"), List.of("payload"));

    private static final List<Stage> ALL = List.of(PACK, DERIVE, GRADE, PLAN, DETECT, READ);

    @Nested
    class Producers {
        @Test
        void saysWhichStageMakesEachValue() {
            Map<String, String> made = StageGraph.producers(List.of(READ, DETECT));
            assertThat(made.get("surface")).isEqualTo("read");
            assertThat(made.get("reading")).isEqualTo("detect");
        }

        @Test
        void refusesTwoStagesMakingTheSameValue() {
            Stage other = step("another-read", List.of(), List.of("surface"));
            assertThatThrownBy(() -> StageGraph.producers(List.of(READ, other)))
                    .isInstanceOf(PipelineError.class)
                    .hasMessageFindingMatch("produce the same value");
        }

        @Test
        void hasNothingToSayAboutStagesThatMakeNothing() {
            assertThat(StageGraph.producers(List.of(step("quiet", List.of("surface"), List.of()))))
                    .hasSize(0);
        }
    }

    @Nested
    class Dependencies {
        @Test
        void saysWhatEachStageWaitsFor() {
            StageDependencies wiring = StageGraph.dependencies(ALL);
            assertThat(wiring.edges().get("plan").stream().sorted().toList()).isEqualTo(List.of("detect", "read"));
        }

        @Test
        void namesWhatTheRunHasToSupplyItself() {
            assertThat(StageGraph.dependencies(ALL).fromOutside()).isEqualTo(List.of("bytes"));
        }

        @Test
        void doesNotMakeAStageWaitForItself() {
            Stage both = step("both", List.of("x"), List.of("x"));
            assertThat(List.copyOf(
                            StageGraph.dependencies(List.of(both)).edges().get("both")))
                    .isEqualTo(List.of());
        }

        @Test
        void hasNothingOutsideWhenEveryNeedIsProduced() {
            assertThat(StageGraph.dependencies(List.of(READ, DETECT)).fromOutside())
                    .isEqualTo(List.of("bytes"));
            assertThat(StageGraph.dependencies(List.of(DETECT, PLAN)).fromOutside())
                    .isEqualTo(List.of("surface"));
        }
    }

    @Nested
    class Order {
        @Test
        void putsEveryStageAfterTheOnesThatFeedIt() {
            List<String> names = names(StageGraph.order(ALL));
            assertThat(names.indexOf("read")).isLessThan(names.indexOf("detect"));
            assertThat(names.indexOf("detect")).isLessThan(names.indexOf("plan"));
            assertThat(names.indexOf("derive")).isLessThan(names.indexOf("pack"));
            assertThat(names.indexOf("grade")).isLessThan(names.indexOf("pack"));
        }

        @Test
        void keepsTheOrderItWasGivenAmongStagesThatCouldGoEitherWay() {
            Stage left = step("left", List.of("surface"), List.of("a"));
            Stage right = step("right", List.of("surface"), List.of("b"));
            assertThat(names(StageGraph.order(List.of(READ, left, right)))).isEqualTo(List.of("read", "left", "right"));
            assertThat(names(StageGraph.order(List.of(READ, right, left)))).isEqualTo(List.of("read", "right", "left"));
        }

        @Test
        void givesTheSameAnswerWhateverOrderItWasHanded() {
            List<String> one = names(StageGraph.order(ALL));
            List<String> two = names(StageGraph.order(List.of(READ, DETECT, PLAN, DERIVE, GRADE, PACK)));
            assertThat(Set.copyOf(one)).isEqualTo(Set.copyOf(two));
            assertThat(one.indexOf("read")).isEqualTo(0);
        }

        @Test
        void refusesACircle() {
            Stage a = step("a", List.of("y"), List.of("x"));
            Stage b = step("b", List.of("x"), List.of("y"));
            assertThatThrownBy(() -> StageGraph.order(List.of(a, b)))
                    .isInstanceOf(PipelineError.class)
                    .hasMessageFindingMatch("wait on each other in a circle");
        }

        @Test
        void refusesTwoStagesWithTheSameName() {
            assertThatThrownBy(() -> StageGraph.order(List.of(READ, READ)))
                    .isInstanceOf(PipelineError.class)
                    .hasMessageFindingMatch("same name");
        }

        @Test
        void isHappyWithNothingAtAll() {
            assertThat(StageGraph.order(List.of())).isEqualTo(List.of());
        }
    }

    @Nested
    class ObjectionsToGraph {
        @Test
        void hasNothingToSayAboutAGraphTheRunCanFeed() {
            assertThat(StageGraph.objectionsToGraph(ALL, List.of("bytes"))).isEqualTo(List.of());
        }

        @Test
        void saysWhatNothingProducesAndTheRunDoesNotSupply() {
            assertThat(StageGraph.objectionsToGraph(ALL).get(0)).containsPattern("nothing produces bytes");
        }

        @Test
        void saysSoWhenThereIsNothingToRun() {
            assertThat(StageGraph.objectionsToGraph(List.of())).isEqualTo(List.of("there are no stages to run"));
        }

        @Test
        void reportsACircleRatherThanThrowing() {
            Stage a = step("a", List.of("y"), List.of("x"));
            Stage b = step("b", List.of("x"), List.of("y"));
            assertThat(StageGraph.objectionsToGraph(List.of(a, b)).get(0)).containsPattern("circle");
        }

        @Test
        void reportsTwoStagesMakingTheSameValue() {
            Stage other = step("another", List.of(), List.of("surface"));
            assertThat(StageGraph.objectionsToGraph(List.of(READ, other), List.of("bytes"))
                            .get(0))
                    .containsPattern("same value");
        }
    }

    @Nested
    class DescribeGraph {
        @Test
        void saysWhatTheRunStartsWith() {
            assertThat(StageGraph.describeGraph(ALL).get(0)).isEqualTo("starts with bytes");
        }

        @Test
        void numbersTheStagesInTheOrderTheyRun() {
            List<String> lines = StageGraph.describeGraph(ALL).stream()
                    .filter(line -> line.matches("\\d+\\..*"))
                    .toList();
            assertThat(lines.get(0)).isEqualTo("1. read making surface");
            assertThat(lines).hasSize(6);
        }

        @Test
        void saysWhatEachOneWaitsFor() {
            String said = String.join("\n", StageGraph.describeGraph(ALL));
            assertThat(said).containsPattern("plan after read, detect making plan");
        }

        @Test
        void saysNothingAboutWaitingForAStageThatWaitsForNothing() {
            assertThat(StageGraph.describeGraph(List.of(step("alone", List.of(), List.of("x")))))
                    .isEqualTo(List.of("1. alone making x"));
        }
    }

    @Nested
    class Waves {
        @Test
        void groupsTheOnesThatCouldGoAtTheSameTime() {
            List<List<String>> out = StageGraph.waves(ALL);
            assertThat(out.get(0)).isEqualTo(List.of("read"));
            assertThat(out.get(1)).isEqualTo(List.of("detect"));
            assertThat(out.get(out.size() - 1)).isEqualTo(List.of("pack"));
        }

        @Test
        void putsTwoIndependentStagesInOneWave() {
            List<List<String>> out = StageGraph.waves(List.of(READ, DETECT, PLAN, GRADE));
            assertThat(out.get(2).stream().sorted().toList()).isEqualTo(List.of("grade", "plan"));
        }

        @Test
        void hasOneWaveForOneStage() {
            assertThat(StageGraph.waves(List.of(step("alone", List.of(), List.of("x")))))
                    .isEqualTo(List.of(List.of("alone")));
        }
    }
}
