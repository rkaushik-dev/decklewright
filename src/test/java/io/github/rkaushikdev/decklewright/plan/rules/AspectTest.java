package io.github.rkaushikdev.decklewright.plan.rules;

import static io.github.rkaushikdev.decklewright.plan.rules.AspectRules.aspectFor;
import static io.github.rkaushikdev.decklewright.plan.rules.AspectRules.aspectRule;
import static io.github.rkaushikdev.decklewright.plan.rules.AspectRules.nearestAspectRule;
import static io.github.rkaushikdev.decklewright.plan.rules.AspectRules.nudgeAspectRule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.PlanError;
import io.github.rkaushikdev.decklewright.geom.AspectDirection;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.plan.CropPlan;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AspectTest {

    private static final Rect CAPTURE = Rect.of(0, 0, 2000, 2000);

    private static CropPlan start(double width, double height) {
        return start(Rect.of(100, 100, width, height));
    }

    private static CropPlan start(Rect rect) {
        return CropPlan.start(CAPTURE, rect, Sampling.of(400));
    }

    private static double aspectOf(CropPlan plan) {
        return plan.rect().width() / plan.rect().height();
    }

    @Nested
    class AspectRule {
        @Test
        void growsTheShortSideByDefault() {
            CropPlan out = aspectRule(1).apply(start(400, 300), RuleContext.empty());
            assertThat(out.rect().width()).isEqualTo(400);
            assertThat(out.rect().height()).isEqualTo(400);
            assertThat(out.trail()).isEqualTo(List.of("aspect"));
        }

        @Test
        void shrinksTheLongSideWhenToldTo() {
            CropPlan out = aspectRule(1, AspectDirection.SHRINK).apply(start(400, 300), RuleContext.empty());
            assertThat(out.rect().width()).isEqualTo(300);
            assertThat(out.rect().height()).isEqualTo(300);
        }

        @Test
        void keepsTheCentreWhereItWas() {
            CropPlan before = start(400, 300);
            CropPlan after = aspectRule(1).apply(before, RuleContext.empty());
            assertThat(after.rect().centre().approxEquals(before.rect().centre()))
                    .isTrue();
        }

        @Test
        void doesNothingWhenTheCropAlreadyHasTheRatio() {
            CropPlan plan = start(400, 200);
            assertThat(aspectRule(2).apply(plan, RuleContext.empty())).isSameAs(plan);
        }

        @Test
        void landsInTheSamePlaceWhenRunTwice() {
            CropPlan once = aspectRule(1.5).apply(start(400, 300), RuleContext.empty());
            assertThat(aspectRule(1.5).apply(once, RuleContext.empty())).isSameAs(once);
        }

        @Test
        void leavesAnEmptyCropAloneRatherThanDividingByZero() {
            CropPlan empty = start(Rect.of(10, 10, 0, 10));
            assertThat(aspectRule(1).apply(empty, RuleContext.empty())).isSameAs(empty);
        }

        @Test
        void refusesARatioOrDirectionItCannotUse() {
            assertThatThrownBy(() -> aspectRule(0))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("aspect must be greater than zero");
            // A direction is an enum, so an unknown one is refused where it is read.
            assertThatThrownBy(() -> aspectRule(1, AspectDirection.parse("sideways")))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("direction must be one of");
        }
    }

    @Nested
    class NearestAspectRule {
        @Test
        void picksTheClosestAllowedRatio() {
            CropPlan out = nearestAspectRule(List.of(1.0, 1.5, 2.0)).apply(start(400, 260), RuleContext.empty());
            assertThat(aspectOf(out)).isCloseTo(1.5, byLessThan(0.5e-9));
            assertThat(out.steps().get(1).note()).containsPattern("nearest allowed ratio, 1.5");
        }

        @Test
        void picksTheSameRatioForACropTheOtherSideOfIt() {
            CropPlan out = nearestAspectRule(List.of(1.0, 1.5, 2.0)).apply(start(400, 280), RuleContext.empty());
            assertThat(aspectOf(out)).isCloseTo(1.5, byLessThan(0.5e-9));
        }

        @Test
        void takesASingleRatioAsWellAsAList() {
            CropPlan out = nearestAspectRule(List.of(1.0)).apply(start(400, 300), RuleContext.empty());
            assertThat(out.rect().width()).isEqualTo(out.rect().height());
        }

        @Test
        void refusesAnEmptyListOrABadRatio() {
            assertThatThrownBy(() -> nearestAspectRule(List.of()))
                    .isInstanceOf(PlanError.class)
                    .hasMessageFindingMatch("at least one ratio");
            assertThatThrownBy(() -> nearestAspectRule(List.of(1.0, -1.0)))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("aspect must be greater than zero");
        }

        @Test
        void landsInTheSamePlaceWhenRunTwice() {
            CropPlan once = nearestAspectRule(List.of(1.0, 1.5, 2.0)).apply(start(400, 260), RuleContext.empty());
            assertThat(nearestAspectRule(List.of(1.0, 1.5, 2.0)).apply(once, RuleContext.empty()))
                    .isSameAs(once);
        }
    }

    @Nested
    class NudgeAspectRule {
        @Test
        void forcesACropThatIsAlreadyClose() {
            CropPlan out = nudgeAspectRule(1.5, 0.1).apply(start(400, 260), RuleContext.empty());
            assertThat(aspectOf(out)).isCloseTo(1.5, byLessThan(0.5e-9));
            assertThat(out.warnings()).isEqualTo(List.of());
        }

        @Test
        void leavesACropThatIsGenuinelyAnotherShapeAndSaysSo() {
            CropPlan out = nudgeAspectRule(1.5, 0.05).apply(start(400, 400), RuleContext.empty());
            assertThat(out.rect().width()).isEqualTo(400);
            assertThat(out.rect().height()).isEqualTo(400);
            assertThat(out.warnings().get(0)).containsPattern("off the 1.5 ratio and was left alone");
        }

        @Test
        void leavesAnEmptyCropAlone() {
            CropPlan empty = CropPlan.start(CAPTURE, Rect.of(0, 0, 10, 0), Sampling.of(400));
            assertThat(nudgeAspectRule(1).apply(empty, RuleContext.empty())).isSameAs(empty);
        }
    }

    @Nested
    class AspectFor {
        @Test
        void givesNothingBackWhenTheProfileSetNoAspect() {
            assertThat(aspectFor(OptionalDouble.empty())).isEmpty();
        }

        @Test
        void givesAnAspectRuleWhenItDid() {
            Optional<Rule> made = aspectFor(OptionalDouble.of(1.5), AspectDirection.SHRINK);
            assertThat(made.map(Rule::name)).hasValue("aspect");
            assertThat(made.map(Rule::phase)).hasValue(Phase.SHAPE);
        }
    }
}
