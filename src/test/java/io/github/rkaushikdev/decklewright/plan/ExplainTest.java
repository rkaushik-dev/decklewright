package io.github.rkaushikdev.decklewright.plan;

import static io.github.rkaushikdev.decklewright.plan.Explanations.biggestMove;
import static io.github.rkaushikdev.decklewright.plan.Explanations.describeMove;
import static io.github.rkaushikdev.decklewright.plan.Explanations.explain;
import static io.github.rkaushikdev.decklewright.plan.Explanations.explainSteps;
import static io.github.rkaushikdev.decklewright.plan.Explanations.explainWithRules;
import static io.github.rkaushikdev.decklewright.plan.Explanations.summarise;
import static io.github.rkaushikdev.decklewright.plan.rules.AspectRules.aspectRule;
import static io.github.rkaushikdev.decklewright.plan.rules.BleedRules.bleedRule;
import static io.github.rkaushikdev.decklewright.plan.rules.BoundsRules.notEmptyRule;
import static io.github.rkaushikdev.decklewright.plan.rules.ResolutionRules.resolutionRule;
import static io.github.rkaushikdev.decklewright.plan.rules.SnapRules.snapRule;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.decklewright.geom.Angle;
import io.github.rkaushikdev.decklewright.geom.Insets;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.measure.Unit;
import io.github.rkaushikdev.decklewright.plan.rules.Rule;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ExplainTest {

    private static final Rect CAPTURE = Rect.of(0, 0, 4000, 3200);
    private static final Rect ANCHOR = Rect.of(400, 400, 2000, 1600);

    private static CropPlan run(List<Rule> rules) {
        return Engine.planWith(rules, PlanInput.of(CAPTURE, ANCHOR, Sampling.of(400)));
    }

    @Nested
    class DescribeMove {
        @Test
        void saysNothingChangedWhenNothingDid() {
            assertThat(describeMove(ANCHOR, ANCHOR)).isEqualTo("no change");
        }

        @Test
        void saysHowMuchItGrew() {
            assertThat(describeMove(Rect.of(0, 0, 10, 10), Rect.of(0, 0, 20, 30)))
                    .isEqualTo("grew by 10 by 20");
        }

        @Test
        void saysHowMuchItShrank() {
            assertThat(describeMove(Rect.of(0, 0, 20, 20), Rect.of(0, 0, 10, 10)))
                    .isEqualTo("shrank by 10 by 10");
        }

        @Test
        void saysHowFarItMoved() {
            assertThat(describeMove(Rect.of(0, 0, 10, 10), Rect.of(5, -3, 10, 10)))
                    .isEqualTo("moved by 5 and -3");
        }

        @Test
        void saysBothWhenBothHappened() {
            assertThat(describeMove(Rect.of(0, 0, 10, 10), Rect.of(-5, -5, 20, 20)))
                    .isEqualTo("grew by 10 by 10, moved by -5 and -5");
        }
    }

    @Nested
    class ExplainSteps {
        @Test
        void leavesOutTheRulesThatChangedNothing() {
            List<String> lines = explainSteps(run(List.of(bleedRule(Insets.uniform(20)), notEmptyRule())));
            assertThat(lines).hasSize(1);
            assertThat(lines.get(0)).containsPattern("^bleed: kept 20 all round beyond the anchor \\(grew by 40 by 40");
        }

        @Test
        void givesNothingBackForAPlanNothingTouched() {
            assertThat(explainSteps(run(List.of(notEmptyRule())))).isEqualTo(List.of());
        }
    }

    @Nested
    class Explain {
        @Test
        void opensWithTheCropAndItsPhysicalSize() {
            List<String> lines = explain(run(List.of(notEmptyRule())));
            assertThat(lines.get(0)).isEqualTo("crop 2000x1600+400+400 at 400 ppi");
            assertThat(lines.get(1)).isEqualTo("which is 5.00 in by 4.00 in");
        }

        @Test
        void answersInAnotherUnitWhenAsked() {
            List<String> lines = explain(run(List.of(notEmptyRule())), Unit.MILLIMETRE);
            assertThat(lines.get(1)).containsPattern("^which is 127.0 mm by 101.6 mm$");
        }

        @Test
        void mentionsTheDeskewOnlyWhenThereIsOne() {
            assertThat(String.join("\n", explain(run(List.of(notEmptyRule()))))).doesNotContainPattern("skew");
            CropPlan turned = CropPlan.start(CAPTURE, ANCHOR, Sampling.of(400), Angle.degrees(1.4));
            assertThat(String.join("\n", explain(turned))).containsPattern("after taking out 1.4 deg of skew");
        }

        @Test
        void saysHowManyRulesMovedIt() {
            List<String> lines = explain(run(List.of(bleedRule(Insets.uniform(20)), snapRule(64), notEmptyRule())));
            assertThat(String.join("\n", lines)).containsPattern("2 rules moved it:");
        }

        @Test
        void saysSoWhenNoneDid() {
            assertThat(explain(run(List.of(notEmptyRule())))).contains("no rule moved it");
        }

        @Test
        void printsTheWarningsAtTheEnd() {
            List<String> lines = explain(run(List.of(resolutionRule(800), notEmptyRule())));
            assertThat(lines.get(lines.size() - 1)).containsPattern("^warning: the capture is 400 ppi");
        }
    }

    @Nested
    class ExplainWithRules {
        private final List<Rule> rules =
                List.of(bleedRule(Insets.uniform(20)), snapRule(64), resolutionRule(300), notEmptyRule());

        @Test
        void namesTheRulesThatRanAndChangedNothing() {
            List<String> lines = explainWithRules(run(rules), rules);
            Optional<String> quiet = lines.stream()
                    .filter(line -> line.startsWith("rules that changed nothing:"))
                    .findFirst();
            assertThat(quiet).hasValueSatisfying(line -> assertThat(line).containsPattern("resolution"));
            assertThat(quiet).hasValueSatisfying(line -> assertThat(line).containsPattern("notEmpty"));
        }

        @Test
        void saysNothingAboutQuietRulesWhenEveryRuleMovedSomething() {
            List<Rule> busy = List.of(bleedRule(Insets.uniform(20)), snapRule(64));
            List<String> lines = explainWithRules(run(busy), busy);
            assertThat(String.join("\n", lines)).doesNotContainPattern("changed nothing");
        }

        @Test
        void notesThePairsThatAreKnownToArgue() {
            List<Rule> tense = List.of(aspectRule(1.5), snapRule(64), notEmptyRule());
            List<String> lines = explainWithRules(run(tense), tense);
            assertThat(String.join("\n", lines))
                    .containsPattern("note: a forced aspect and a snap pull against each other");
        }
    }

    @Nested
    class Summarise {
        @Test
        void writesOneLineForABatchLog() {
            String out = summarise(run(List.of(bleedRule(Insets.uniform(20)), snapRule(64), notEmptyRule())));
            assertThat(out).containsPattern("^\\d+x\\d+\\+-?\\d+\\+-?\\d+ after bleed then snap$");
        }

        @Test
        void saysNothingHappenedWhenNothingDid() {
            assertThat(summarise(run(List.of(notEmptyRule())))).containsPattern("after nothing$");
        }

        @Test
        void countsTheWarnings() {
            assertThat(summarise(run(List.of(resolutionRule(800), notEmptyRule()))))
                    .containsPattern(", 1 warnings$");
        }
    }

    @Nested
    class BiggestMove {
        @Test
        void namesTheRuleThatChangedTheMostArea() {
            Optional<Explanations.Move> out =
                    biggestMove(run(List.of(bleedRule(Insets.uniform(100)), snapRule(8), notEmptyRule())));
            assertThat(out.map(Explanations.Move::rule)).hasValue("bleed");
            assertThat(out.map(Explanations.Move::area))
                    .hasValueSatisfying(area -> assertThat(area).isGreaterThan(0));
        }

        @Test
        void givesNothingBackWhenNothingMoved() {
            assertThat(biggestMove(run(List.of(notEmptyRule())))).isEmpty();
        }
    }
}
