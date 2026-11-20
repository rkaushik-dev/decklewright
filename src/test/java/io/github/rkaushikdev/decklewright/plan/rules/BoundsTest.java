package io.github.rkaushikdev.decklewright.plan.rules;

import static io.github.rkaushikdev.decklewright.plan.rules.BoundsRules.BOUNDS_MODES;
import static io.github.rkaushikdev.decklewright.plan.rules.BoundsRules.boundsFor;
import static io.github.rkaushikdev.decklewright.plan.rules.BoundsRules.clampToCaptureRule;
import static io.github.rkaushikdev.decklewright.plan.rules.BoundsRules.notEmptyRule;
import static io.github.rkaushikdev.decklewright.plan.rules.BoundsRules.nudgeIntoCaptureRule;
import static io.github.rkaushikdev.decklewright.plan.rules.BoundsRules.refuseOverhangRule;
import static io.github.rkaushikdev.decklewright.plan.rules.BoundsRules.warnOnOverhangRule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.PlanError;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.plan.CropPlan;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BoundsTest {

    private static final Rect CAPTURE = Rect.of(0, 0, 1000, 800);
    private static final CropPlan INSIDE = start(Rect.of(100, 100, 400, 300));
    private static final CropPlan OVER = start(Rect.of(900, 700, 400, 300));

    private static CropPlan start(Rect rect) {
        return CropPlan.start(CAPTURE, rect, Sampling.of(400));
    }

    private static CropPlan apply(Rule rule, CropPlan plan) {
        return rule.apply(plan, RuleContext.empty());
    }

    @Nested
    class WarnOnOverhangRule {
        @Test
        void saysNothingAboutACropThatIsInside() {
            assertThat(apply(warnOnOverhangRule(), INSIDE)).isSameAs(INSIDE);
        }

        @Test
        void warnsAndChangesNothing() {
            CropPlan out = apply(warnOnOverhangRule(), OVER);
            assertThat(out.rect().approxEquals(OVER.rect())).isTrue();
            assertThat(out.warnings().get(0)).isEqualTo("110000 px of the crop falls off the capture");
        }

        @Test
        void warnsOnceWhenRunTwice() {
            CropPlan once = apply(warnOnOverhangRule(), OVER);
            assertThat(apply(warnOnOverhangRule(), once).warnings()).hasSize(1);
        }
    }

    @Nested
    class ClampToCaptureRule {
        @Test
        void leavesACropThatIsInsideAlone() {
            assertThat(apply(clampToCaptureRule(), INSIDE)).isSameAs(INSIDE);
        }

        @Test
        void cutsTheCropBackToTheCaptureAndSaysSo() {
            CropPlan out = apply(clampToCaptureRule(), OVER);
            assertThat(out.rect().toJson()).isEqualTo(Map.of("x", 900.0, "y", 700.0, "width", 100.0, "height", 100.0));
            assertThat(out.warnings().get(0)).containsPattern("trimmed at the edge of the capture");
            assertThat(out.trail()).isEqualTo(List.of("bounds"));
        }

        @Test
        void refusesACropThatMissesTheCaptureEntirely() {
            CropPlan away = start(Rect.of(2000, 2000, 100, 100));
            assertThatThrownBy(() -> apply(clampToCaptureRule(), away))
                    .isInstanceOf(PlanError.class)
                    .hasMessageFindingMatch("does not touch the capture");
        }

        @Test
        void landsInTheSamePlaceWhenRunTwice() {
            CropPlan once = apply(clampToCaptureRule(), OVER);
            assertThat(apply(clampToCaptureRule(), once)).isSameAs(once);
        }
    }

    @Nested
    class NudgeIntoCaptureRule {
        @Test
        void leavesACropThatIsInsideAlone() {
            assertThat(apply(nudgeIntoCaptureRule(), INSIDE)).isSameAs(INSIDE);
        }

        @Test
        void slidesTheCropBackOnAndKeepsItsSize() {
            CropPlan out = apply(nudgeIntoCaptureRule(), OVER);
            assertThat(out.rect().size().toJson()).isEqualTo(Map.of("width", 400.0, "height", 300.0));
            assertThat(CAPTURE.containsRect(out.rect())).isTrue();
            assertThat(out.warnings()).isEqualTo(List.of());
        }

        @Test
        void centresACropLargerThanTheCaptureAndSaysSo() {
            CropPlan huge = start(Rect.of(0, 0, 1400, 1000));
            CropPlan out = apply(nudgeIntoCaptureRule(), huge);
            assertThat(out.rect().centre().approxEquals(CAPTURE.centre())).isTrue();
            assertThat(out.warnings().get(0)).containsPattern("larger than the capture and was centred");
        }

        @Test
        void landsInTheSamePlaceWhenRunTwice() {
            CropPlan once = apply(nudgeIntoCaptureRule(), OVER);
            assertThat(apply(nudgeIntoCaptureRule(), once)).isSameAs(once);
        }
    }

    @Nested
    class RefuseOverhangRule {
        @Test
        void letsACropThatIsInsideThrough() {
            assertThat(apply(refuseOverhangRule(), INSIDE)).isSameAs(INSIDE);
        }

        @Test
        void stopsOnACropThatIsNot() {
            assertThatThrownBy(() -> apply(refuseOverhangRule(), OVER))
                    .isInstanceOf(PlanError.class)
                    .hasMessageFindingMatch("will not pad");
        }

        @Test
        void carriesTheOverhangInTheError() {
            assertThatThrownBy(() -> apply(refuseOverhangRule(), OVER))
                    .isInstanceOfSatisfying(
                            PlanError.class,
                            error -> assertThat(error.details().get("overhang")).isEqualTo(110000.0));
        }
    }

    @Nested
    class BoundsFor {
        @Test
        void givesEveryNamedMode() {
            assertThat(BOUNDS_MODES).hasSize(4);
            for (BoundsMode mode : BOUNDS_MODES) {
                assertThat(boundsFor(mode).name()).isEqualTo("bounds");
            }
        }

        @Test
        void behavesDifferentlyUnderEachOne() {
            assertThat(apply(boundsFor(BoundsMode.WARN), OVER).rect().width()).isEqualTo(400);
            assertThat(apply(boundsFor(BoundsMode.CLAMP), OVER).rect().width()).isEqualTo(100);
            assertThat(apply(boundsFor(BoundsMode.NUDGE), OVER).rect().width()).isEqualTo(400);
            assertThatThrownBy(() -> apply(boundsFor(BoundsMode.REFUSE), OVER)).isInstanceOf(PlanError.class);
        }

        @Test
        void warnsByDefault() {
            assertThat(apply(boundsFor(), OVER).warnings()).hasSize(1);
        }

        @Test
        void refusesAModeItDoesNotKnow() {
            // A mode is an enum, so an unknown one is refused where it is read.
            assertThatThrownBy(() -> boundsFor(BoundsMode.parse("shrug")))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("mode must be one of");
        }
    }

    @Nested
    class NotEmptyRule {
        @Test
        void letsACropWithAreaThrough() {
            assertThat(apply(notEmptyRule(), INSIDE)).isSameAs(INSIDE);
        }

        @Test
        void stopsOnACropWithNoneAndNamesTheRulesThatGotThere() {
            CropPlan empty = start(Rect.of(10, 10, 400, 300)).moved(Rect.of(10, 10, 0, 300), "safeArea", "x");
            assertThatThrownBy(() -> apply(notEmptyRule(), empty)).isInstanceOfSatisfying(PlanError.class, error -> {
                assertThat(error.getMessage()).containsPattern("left no crop at all");
                assertThat(error.details().get("trail")).isEqualTo(List.of("safeArea"));
            });
        }
    }
}
