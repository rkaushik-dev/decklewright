package io.github.rkaushikdev.decklewright.plan.rules;

import static io.github.rkaushikdev.decklewright.plan.rules.BleedRules.bleedFor;
import static io.github.rkaushikdev.decklewright.plan.rules.BleedRules.bleedRule;
import static io.github.rkaushikdev.decklewright.plan.rules.BleedRules.bleedWithinSheetRule;
import static io.github.rkaushikdev.decklewright.plan.rules.BleedRules.noBleedRule;
import static io.github.rkaushikdev.decklewright.plan.rules.BleedRules.trimmedBleedRule;
import static io.github.rkaushikdev.decklewright.plan.rules.Rules.PHASES;
import static io.github.rkaushikdev.decklewright.plan.rules.Rules.inPhase;
import static io.github.rkaushikdev.decklewright.plan.rules.Rules.isRule;
import static io.github.rkaushikdev.decklewright.plan.rules.Rules.ordered;
import static io.github.rkaushikdev.decklewright.plan.rules.Rules.requireNoDuplicates;
import static io.github.rkaushikdev.decklewright.plan.rules.Rules.rule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.PlanError;
import io.github.rkaushikdev.decklewright.geom.Insets;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.plan.CropPlan;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BleedTest {

    private static final Rect CAPTURE = Rect.of(0, 0, 1000, 800);
    private static final Rect ANCHOR = Rect.of(100, 100, 400, 300);
    private static final Map<String, Object> BLED = Map.of("x", 80.0, "y", 80.0, "width", 440.0, "height", 340.0);

    private static CropPlan start() {
        return start(ANCHOR);
    }

    private static CropPlan start(Rect rect) {
        return CropPlan.start(CAPTURE, rect, Sampling.of(400));
    }

    private static RuleContext fromAnchor() {
        return RuleContext.of(ANCHOR);
    }

    /** A rule that hands back the plan it was given. */
    private static Rule still(String name, Phase phase) {
        return rule(name, phase, (plan, context) -> plan);
    }

    private static List<String> names(List<Rule> rules) {
        return rules.stream().map(Rule::name).toList();
    }

    @Nested
    class TheRuleProtocol {
        @Test
        void buildsARuleAndFreezesIt() {
            Rule made = still("x", Phase.SHAPE);
            // Every field of a rule is final and none is ever handed out to be changed.
            assertThat(Modifier.isFinal(Rule.class.getModifiers())).isTrue();
            assertThat(Arrays.stream(Rule.class.getDeclaredFields()).map(Field::getModifiers))
                    .allMatch(Modifier::isFinal);
            assertThat(isRule(made)).isTrue();
            assertThat(isRule(Map.of())).isFalse();
        }

        @Test
        void refusesAPhaseItDoesNotKnowOrARuleWithNoBody() {
            // A phase is an enum, so an unknown one is refused where it is read.
            assertThatThrownBy(() -> rule("x", Phase.parse("later"), (p, c) -> p))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("phase must be one of");
            assertThatThrownBy(() -> rule("x", Phase.SHAPE, null))
                    .isInstanceOf(PlanError.class)
                    .hasMessageFindingMatch("nothing to apply");
            assertThatThrownBy(() -> rule("", Phase.SHAPE, (p, c) -> p))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("non empty string");
        }

        @Test
        void filtersAndOrdersByPhase() {
            List<Rule> set = List.of(
                    still("c", Phase.CHECK), still("a", Phase.ANCHOR), still("g", Phase.GRID), still("s", Phase.SHAPE));
            assertThat(names(ordered(set))).isEqualTo(List.of("a", "s", "g", "c"));
            assertThat(names(inPhase(set, Phase.GRID))).isEqualTo(List.of("g"));
            assertThat(PHASES).hasSize(4);
        }

        @Test
        void keepsTheWrittenOrderInsideAPhase() {
            List<Rule> set = List.of(still("first", Phase.SHAPE), still("second", Phase.SHAPE));
            assertThat(names(ordered(set))).isEqualTo(List.of("first", "second"));
        }

        @Test
        void catchesTheSameRuleTwice() {
            List<Rule> set = List.of(still("bleed", Phase.ANCHOR), still("bleed", Phase.SHAPE));
            assertThatThrownBy(() -> requireNoDuplicates(set))
                    .isInstanceOf(PlanError.class)
                    .hasMessageFindingMatch("the rule bleed is in the set twice");
            assertThatCode(() -> requireNoDuplicates(set.subList(0, 1))).doesNotThrowAnyException();
        }
    }

    @Nested
    class BleedRule {
        @Test
        void pushesTheAnchorOutByTheBleed() {
            CropPlan out = bleedRule(Insets.uniform(20)).apply(start(), fromAnchor());
            assertThat(out.rect().toJson()).isEqualTo(BLED);
            assertThat(out.trail()).isEqualTo(List.of("bleed"));
        }

        @Test
        void doesNothingAtAllForAZeroBleed() {
            CropPlan plan = start();
            assertThat(noBleedRule().apply(plan, fromAnchor())).isSameAs(plan);
        }

        @Test
        void measuresFromTheAnchorNotFromTheCropAsItStands() {
            CropPlan moved = start().moved(Rect.of(0, 0, 900, 700), "other", "x");
            CropPlan out = bleedRule(Insets.uniform(20)).apply(moved, fromAnchor());
            assertThat(out.rect().toJson()).isEqualTo(BLED);
        }

        @Test
        void givesTheSameAnswerTwiceSoTheEngineMayRunItAgain() {
            CropPlan once = bleedRule(Insets.uniform(20)).apply(start(), fromAnchor());
            CropPlan twice = bleedRule(Insets.uniform(20)).apply(once, fromAnchor());
            assertThat(twice.rect().approxEquals(once.rect())).isTrue();
            assertThat(twice).isSameAs(once);
        }

        @Test
        void namesWhatItWasMeasuredFrom() {
            CropPlan out = bleedRule(Insets.uniform(5)).apply(start(), RuleContext.of(ANCHOR, "neatline"));
            assertThat(out.steps().get(1).note()).containsPattern("beyond the neatline");
        }
    }

    @Nested
    class TrimmedBleedRule {
        @Test
        void isThePlainBleedWhenThereIsRoom() {
            CropPlan out = trimmedBleedRule(Insets.uniform(20)).apply(start(), fromAnchor());
            assertThat(out.rect().toJson()).isEqualTo(BLED);
            assertThat(out.warnings()).isEqualTo(List.of());
        }

        @Test
        void trimsAtTheCaptureEdgeAndSaysSo() {
            Rect edge = Rect.of(0, 0, 200, 200);
            CropPlan out = trimmedBleedRule(Insets.uniform(20)).apply(start(edge), RuleContext.of(edge));
            assertThat(out.rect().left()).isEqualTo(0);
            assertThat(out.warnings().get(0)).containsPattern("trimmed at the edge of the capture");
        }
    }

    @Nested
    class BleedWithinSheetRule {
        private final Rect sheet = Rect.of(110, 110, 380, 280);

        @Test
        void holdsTheBleedInsideThePaper() {
            CropPlan out = bleedWithinSheetRule(Insets.uniform(20), sheet).apply(start(), fromAnchor());
            assertThat(out.rect().approxEquals(sheet.intersect(Rect.of(80, 80, 440, 340))))
                    .isTrue();
            assertThat(out.warnings().get(0)).containsPattern("not have enough paper");
        }

        @Test
        void saysNothingWhenThePaperReachesFarEnough() {
            Rect wide = Rect.of(0, 0, 900, 700);
            CropPlan out = bleedWithinSheetRule(Insets.uniform(20), wide).apply(start(), fromAnchor());
            assertThat(out.warnings()).isEqualTo(List.of());
        }
    }

    @Nested
    class BleedFor {
        @Test
        void picksTheRuleTheModeNames() {
            Rect edge = Rect.of(0, 0, 200, 200);
            assertThat(bleedFor(Insets.uniform(20))
                            .apply(start(edge), RuleContext.of(edge))
                            .rect()
                            .left())
                    .isEqualTo(-20);
            assertThat(bleedFor(Insets.uniform(20), BleedMode.TRIMMED)
                            .apply(start(edge), RuleContext.of(edge))
                            .rect()
                            .left())
                    .isEqualTo(0);
        }

        @Test
        void fallsBackToThePlainRuleWhenASheetWasNotGiven() {
            CropPlan out = bleedFor(Insets.uniform(20), BleedMode.WITHIN_SHEET).apply(start(), fromAnchor());
            assertThat(out.rect().left()).isEqualTo(80);
        }

        @Test
        void everyShapeOfItIsCalledBleedAndSitsInTheAnchorPhase() {
            for (BleedMode mode : List.of(BleedMode.PLAIN, BleedMode.TRIMMED, BleedMode.WITHIN_SHEET)) {
                Rule made = bleedFor(Insets.uniform(1), mode, CAPTURE);
                assertThat(made.name()).isEqualTo("bleed");
                assertThat(made.phase()).isEqualTo(Phase.ANCHOR);
            }
        }
    }
}
