package io.github.rkaushikdev.decklewright.plan.rules;

import static io.github.rkaushikdev.decklewright.plan.rules.ResolutionRules.achievedPpi;
import static io.github.rkaushikdev.decklewright.plan.rules.ResolutionRules.minimumSizeRule;
import static io.github.rkaushikdev.decklewright.plan.rules.ResolutionRules.resolutionFor;
import static io.github.rkaushikdev.decklewright.plan.rules.ResolutionRules.resolutionRule;
import static io.github.rkaushikdev.decklewright.plan.rules.ResolutionRules.strictResolutionRule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.PlanError;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.measure.Unit;
import io.github.rkaushikdev.decklewright.plan.CropPlan;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ResolutionTest {

    private static final Rect CAPTURE = Rect.of(0, 0, 4000, 3000);

    private static CropPlan start(double ppi) {
        return start(ppi, Rect.of(100, 100, 2000, 1500));
    }

    private static CropPlan start(double ppi, Rect rect) {
        return CropPlan.start(CAPTURE, rect, Sampling.of(ppi));
    }

    private static CropPlan apply(Rule rule, CropPlan plan) {
        return rule.apply(plan, RuleContext.empty());
    }

    @Nested
    class ResolutionRule {
        @Test
        void saysNothingWhenTheCaptureIsOverTheFloor() {
            CropPlan plan = start(600);
            assertThat(apply(resolutionRule(400), plan)).isSameAs(plan);
        }

        @Test
        void acceptsACaptureExactlyOnTheFloor() {
            assertThat(apply(resolutionRule(400), start(400)).warnings()).isEqualTo(List.of());
        }

        @Test
        void warnsWhenItIsUnder() {
            CropPlan out = apply(resolutionRule(400), start(300));
            assertThat(out.warnings().get(0)).isEqualTo("the capture is 300 ppi against a floor of 400");
        }

        @Test
        void readsTheWorseAxisOfANonSquareCapture() {
            CropPlan plan = CropPlan.start(CAPTURE, Rect.of(0, 0, 100, 100), new Sampling(600, 300));
            assertThat(apply(resolutionRule(400), plan).warnings().get(0)).containsPattern("300 ppi");
        }

        @Test
        void doesNotMoveTheCropBecauseCroppingCannotFixIt() {
            CropPlan before = start(200);
            CropPlan after = apply(resolutionRule(400), before);
            assertThat(after.rect().approxEquals(before.rect())).isTrue();
            assertThat(after.trail()).isEqualTo(List.of());
        }

        @Test
        void warnsOnceEvenWhenRunTwice() {
            CropPlan once = apply(resolutionRule(400), start(300));
            assertThat(apply(resolutionRule(400), once).warnings()).hasSize(1);
        }

        @Test
        void refusesAFloorOfZero() {
            assertThatThrownBy(() -> resolutionRule(0))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("minimumPpi must be greater than zero");
        }
    }

    @Nested
    class StrictResolutionRule {
        @Test
        void letsACaptureOverTheFloorThrough() {
            CropPlan plan = start(600);
            assertThat(apply(strictResolutionRule(400), plan)).isSameAs(plan);
        }

        @Test
        void refusesOneUnderIt() {
            assertThatThrownBy(() -> apply(strictResolutionRule(400), start(300)))
                    .isInstanceOf(PlanError.class)
                    .hasMessageFindingMatch("is 300 ppi and this collection will not take under 400");
        }

        @Test
        void carriesTheNumbersInTheError() {
            assertThatThrownBy(() -> apply(strictResolutionRule(400), start(299.5)))
                    .isInstanceOfSatisfying(PlanError.class, error -> {
                        assertThat(error.details().get("achieved")).isEqualTo(299.5);
                        assertThat(error.details().get("minimumPpi")).isEqualTo(400.0);
                    });
        }
    }

    @Nested
    class MinimumSizeRule {
        @Test
        void saysNothingAboutALargeCrop() {
            CropPlan plan = start(400);
            assertThat(apply(minimumSizeRule(1000), plan)).isSameAs(plan);
        }

        @Test
        void warnsAboutASmallOneAndQuotesTheLongSide() {
            CropPlan small = start(400, Rect.of(0, 0, 300, 200));
            assertThat(apply(minimumSizeRule(1000), small).warnings().get(0))
                    .isEqualTo("the crop is 300 px on its long side against a floor of 1000");
        }

        @Test
        void measuresTheLongSideWhicheverWayRoundItIs() {
            CropPlan tall = start(400, Rect.of(0, 0, 200, 900));
            assertThat(apply(minimumSizeRule(500), tall).warnings()).isEqualTo(List.of());
        }

        @Test
        void isACheckSoItNeverMovesAnything() {
            CropPlan small = start(400, Rect.of(0, 0, 300, 200));
            assertThat(apply(minimumSizeRule(1000), small).rect().approxEquals(small.rect()))
                    .isTrue();
        }
    }

    @Nested
    class ResolutionFor {
        @Test
        void picksTheShapeTheCollectionAskedFor() {
            assertThat(apply(resolutionFor(400), start(300)).warnings()).hasSize(1);
            assertThatThrownBy(() -> apply(resolutionFor(400, true), start(300)))
                    .isInstanceOf(PlanError.class);
        }

        @Test
        void isCalledResolutionInTheCheckPhaseEitherWay() {
            for (boolean strict : new boolean[] {false, true}) {
                Rule made = resolutionFor(400, strict);
                assertThat(made.name()).isEqualTo("resolution");
                assertThat(made.phase()).isEqualTo(Phase.CHECK);
            }
        }
    }

    @Nested
    class AchievedPpi {
        @Test
        void dividesTheCropByTheObjectItHolds() {
            CropPlan plan = start(400, Rect.of(0, 0, 6800, 100));
            assertThat(achievedPpi(plan, 17, Unit.INCH)).isEqualTo(400);
            assertThat(achievedPpi(plan, 431.8, Unit.MILLIMETRE)).isCloseTo(400, byLessThan(0.5e-6));
        }

        @Test
        void refusesAPhysicalWidthOfZero() {
            assertThatThrownBy(() -> achievedPpi(start(400), 0, Unit.INCH))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("physicalWidth must be greater");
        }

        @Test
        void refusesAUnitItDoesNotKnow() {
            // A unit is an enum, so the unknown label is refused where it is read.
            assertThatThrownBy(() -> achievedPpi(start(400), 1, Unit.parse("px")))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("unknown unit px");
        }
    }
}
