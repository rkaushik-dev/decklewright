package io.github.rkaushikdev.decklewright.plan;

import static io.github.rkaushikdev.decklewright.plan.Engine.planInDetail;
import static io.github.rkaushikdev.decklewright.plan.Engine.planWith;
import static io.github.rkaushikdev.decklewright.plan.Engine.runRules;
import static io.github.rkaushikdev.decklewright.plan.Engine.withRule;
import static io.github.rkaushikdev.decklewright.plan.Engine.without;
import static io.github.rkaushikdev.decklewright.plan.rules.AnchorRules.anchorRule;
import static io.github.rkaushikdev.decklewright.plan.rules.AspectRules.aspectRule;
import static io.github.rkaushikdev.decklewright.plan.rules.BleedRules.bleedRule;
import static io.github.rkaushikdev.decklewright.plan.rules.BoundsRules.boundsFor;
import static io.github.rkaushikdev.decklewright.plan.rules.BoundsRules.notEmptyRule;
import static io.github.rkaushikdev.decklewright.plan.rules.ResolutionRules.resolutionRule;
import static io.github.rkaushikdev.decklewright.plan.rules.Rules.rule;
import static io.github.rkaushikdev.decklewright.plan.rules.SnapRules.roundRule;
import static io.github.rkaushikdev.decklewright.plan.rules.SnapRules.snapRule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.PlanError;
import io.github.rkaushikdev.decklewright.geom.Insets;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.geom.RoundPolicy;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.plan.rules.BoundsMode;
import io.github.rkaushikdev.decklewright.plan.rules.Phase;
import io.github.rkaushikdev.decklewright.plan.rules.Rule;
import io.github.rkaushikdev.decklewright.plan.rules.RuleContext;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EngineTest {

    private static final Rect CAPTURE = Rect.of(0, 0, 2000, 1600);
    private static final Rect ANCHOR = Rect.of(200, 200, 800, 600);

    private static PlanInput input() {
        return input(ANCHOR);
    }

    private static PlanInput input(Rect anchor) {
        return PlanInput.of(CAPTURE, anchor, Sampling.of(400));
    }

    private static CropPlan run(List<Rule> rules) {
        return planWith(rules, input());
    }

    /** A shape rule that widens the crop by a pixel all round, every time it runs. */
    private static Rule grow() {
        return rule(
                "grow", Phase.SHAPE, (plan, context) -> plan.moved(plan.rect().outset(1), "grow", "wider"));
    }

    @Nested
    class PlanWith {
        @Test
        void givesTheAnchorBackWhenTheSetIsEmpty() {
            assertThat(run(List.of()).rect().approxEquals(ANCHOR)).isTrue();
        }

        @Test
        void appliesTheRulesInPhaseOrderWhateverOrderTheyWereGiven() {
            CropPlan out = run(
                    List.of(snapRule(16), bleedRule(Insets.uniform(10)), anchorRule(ANCHOR, "sheet"), notEmptyRule()));
            assertThat(out.trail().get(0)).isEqualTo("bleed");
            assertThat(out.trail()).contains("snap");
        }

        @Test
        void refusesAnAnchorWithNoArea() {
            assertThatThrownBy(() -> planWith(List.of(), input(Rect.empty())))
                    .isInstanceOf(PlanError.class)
                    .hasMessageFindingMatch("the anchor has no area");
        }

        @Test
        void refusesASetWithTheSameRuleTwice() {
            assertThatThrownBy(() -> run(List.of(snapRule(8), snapRule(16))))
                    .isInstanceOf(PlanError.class)
                    .hasMessageFindingMatch("the rule snap is in the set twice");
        }
    }

    @Nested
    class RunningUntilItSettles {
        @Test
        void settlesASetWithNothingToFightAbout() {
            PlannedCrop out =
                    planInDetail(List.of(bleedRule(Insets.uniform(10)), roundRule(RoundPolicy.OUTWARD)), input());
            assertThat(out.settled()).isTrue();
            assertThat(out.passes()).isEqualTo(1);
        }

        @Test
        void runsTheAnchorRulesOnceSoABleedCannotUndoASnap() {
            PlannedCrop out = planInDetail(List.of(bleedRule(Insets.uniform(10)), snapRule(64)), input());
            assertThat(out.settled()).isTrue();
            assertThat(out.plan().trail()).isEqualTo(List.of("bleed", "snap"));
        }

        @Test
        void settlesTheAspectAndTheSnapTogetherWhichTheLinearOrderCouldNot() {
            PlannedCrop out = planInDetail(List.of(aspectRule(1), snapRule(8)), input(Rect.of(0, 0, 101, 90)));
            assertThat(out.settled()).isTrue();
            assertThat(out.plan().rect().width()).isEqualTo(out.plan().rect().height());
            assertThat(out.plan().rect().width() % 8).isEqualTo(0);
        }

        @Test
        void saysSoWhenASetWillNotSettle() {
            Rule shrink = rule(
                    "shrink",
                    Phase.GRID,
                    (plan, context) -> plan.moved(plan.rect().inset(2), "shrink", "narrower"));
            PlannedCrop out = planInDetail(List.of(grow(), shrink), input());
            assertThat(out.settled()).isFalse();
            assertThat(out.plan().warnings().get(0)).containsPattern("did not settle in 6 passes");
        }

        @Test
        void honoursAPassLimit() {
            PlannedCrop out = planInDetail(List.of(grow()), input(), 2);
            assertThat(out.passes()).isEqualTo(2);
            assertThat(out.settled()).isFalse();
        }

        @Test
        void refusesAPassLimitThatIsNotAWholeNumberAboveZero() {
            CropPlan start = CropPlan.start(CAPTURE, ANCHOR, Sampling.of(400));
            assertThatThrownBy(() -> runRules(List.of(), start, RuleContext.empty(), 0))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("above zero");
        }
    }

    @Nested
    class TheCheckPhase {
        @Test
        void runsAfterEverythingHasSettled() {
            CropPlan out = run(List.of(snapRule(8), resolutionRule(600)));
            assertThat(out.warnings().get(0)).containsPattern("against a floor of 600");
        }

        @Test
        void runsOnceEvenWhenTheMovingRulesRanSeveralTimes() {
            PlannedCrop out = planInDetail(
                    List.of(aspectRule(1), snapRule(8), resolutionRule(600)), input(Rect.of(0, 0, 101, 90)));
            assertThat(out.plan().warnings().stream()
                            .filter(w -> w.contains("floor of 600"))
                            .toList())
                    .hasSize(1);
        }

        @Test
        void canStopThePlanOutright() {
            assertThatThrownBy(
                            () -> planWith(List.of(boundsFor(BoundsMode.REFUSE)), input(Rect.of(1900, 1500, 400, 300))))
                    .isInstanceOf(PlanError.class)
                    .hasMessageFindingMatch("will not pad");
        }
    }

    @Nested
    class PlanInDetail {
        @Test
        void namesTheRulesItRanInTheOrderItRanThem() {
            PlannedCrop out = planInDetail(List.of(notEmptyRule(), snapRule(8), bleedRule(Insets.uniform(4))), input());
            assertThat(out.rules()).isEqualTo(List.of("bleed", "snap", "notEmpty"));
        }

        @Test
        void refusesAnEmptyAnchorBeforeItStarts() {
            assertThatThrownBy(() -> planInDetail(List.of(), input(Rect.empty())))
                    .isInstanceOf(PlanError.class)
                    .hasMessageFindingMatch("the anchor has no area");
        }
    }

    @Nested
    class EditingASet {
        private final List<Rule> set = List.of(bleedRule(Insets.uniform(10)), snapRule(8), notEmptyRule());

        @Test
        void dropsRulesByName() {
            assertThat(without(set, List.of("snap")).stream().map(Rule::name).toList())
                    .isEqualTo(List.of("bleed", "notEmpty"));
            assertThat(without(set, List.of("nothing")).stream().map(Rule::name).toList())
                    .hasSize(3);
        }

        @Test
        void replacesARuleOfTheSameNameInPlace() {
            List<Rule> swapped = withRule(set, snapRule(64));
            assertThat(swapped.stream().map(Rule::name).toList()).isEqualTo(List.of("bleed", "snap", "notEmpty"));
            assertThat(planWith(swapped, input()).rect().width() % 64).isEqualTo(0);
        }

        @Test
        void addsARuleThatWasNotThere() {
            assertThat(withRule(set, resolutionRule(300)).stream()
                            .map(Rule::name)
                            .toList())
                    .contains("resolution");
        }
    }
}
