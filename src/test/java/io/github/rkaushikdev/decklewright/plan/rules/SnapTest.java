package io.github.rkaushikdev.decklewright.plan.rules;

import static io.github.rkaushikdev.decklewright.geom.Rounding.isWholePixel;
import static io.github.rkaushikdev.decklewright.plan.rules.SnapRules.evenExtentsRule;
import static io.github.rkaushikdev.decklewright.plan.rules.SnapRules.gridAlignRule;
import static io.github.rkaushikdev.decklewright.plan.rules.SnapRules.roundRule;
import static io.github.rkaushikdev.decklewright.plan.rules.SnapRules.snapFor;
import static io.github.rkaushikdev.decklewright.plan.rules.SnapRules.snapRule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.geom.RoundPolicy;
import io.github.rkaushikdev.decklewright.geom.SnapDirection;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.plan.CropPlan;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SnapTest {

    private static final Rect CAPTURE = Rect.of(0, 0, 4000, 3000);
    private static final Rect ODD = Rect.of(10, 10, 101, 99);
    private static final Rect FRACTIONAL = Rect.of(10.4, 10.6, 100.2, 100.2);
    private static final Rect SQUARE = Rect.of(100, 100, 300, 300);

    private static CropPlan start(Rect rect) {
        return CropPlan.start(CAPTURE, rect, Sampling.of(400));
    }

    private static CropPlan apply(Rule rule, CropPlan plan) {
        return rule.apply(plan, RuleContext.empty());
    }

    @Nested
    class SnapRule {
        @Test
        void growsTheExtentsToTheMultiple() {
            CropPlan out = apply(snapRule(8), start(ODD));
            assertThat(out.rect().width()).isEqualTo(104);
            assertThat(out.rect().height()).isEqualTo(104);
            assertThat(out.trail()).isEqualTo(List.of("snap"));
        }

        @Test
        void shrinksWhenToldTo() {
            CropPlan out = apply(snapRule(8, SnapDirection.DOWN), start(ODD));
            assertThat(out.rect().width()).isEqualTo(96);
            assertThat(out.rect().height()).isEqualTo(96);
        }

        @Test
        void goesTheNearerWayWhenToldTo() {
            CropPlan out = apply(snapRule(8, SnapDirection.NEAREST), start(ODD));
            assertThat(out.rect().width()).isEqualTo(104);
            assertThat(out.rect().height()).isEqualTo(96);
        }

        @Test
        void keepsTheCentreNearWhereItWas() {
            CropPlan before = start(ODD);
            CropPlan after = apply(snapRule(8), before);
            assertThat(Math.abs(
                            after.rect().centre().x() - before.rect().centre().x()))
                    .isLessThanOrEqualTo(1);
        }

        @Test
        void doesNothingAtAMultipleOfOne() {
            CropPlan plan = start(ODD);
            assertThat(apply(snapRule(1), plan)).isSameAs(plan);
        }

        @Test
        void doesNothingToACropAlreadyOnTheMultiple() {
            CropPlan plan = start(Rect.of(10, 10, 96, 96));
            assertThat(apply(snapRule(8), plan)).isSameAs(plan);
        }

        @Test
        void landsInTheSamePlaceWhenRunTwice() {
            CropPlan once = apply(snapRule(8), start(ODD));
            assertThat(apply(snapRule(8), once)).isSameAs(once);
        }

        @Test
        void refusesAMultipleThatIsNotAWholeNumberAboveZero() {
            assertThatThrownBy(() -> snapRule(0))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("above zero");
            assertThatThrownBy(() -> snapRule(1.5))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("whole number");
            // A direction is an enum, so an unknown one is refused where it is read.
            assertThatThrownBy(() -> snapRule(8, SnapDirection.parse("sideways")))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("direction must be one of");
        }
    }

    @Nested
    class RoundRule {
        @Test
        void putsAFractionalCropOnWholePixels() {
            CropPlan out = apply(roundRule(RoundPolicy.OUTWARD), start(FRACTIONAL));
            assertThat(isWholePixel(out.rect())).isTrue();
            assertThat(out.rect().left()).isEqualTo(10);
            assertThat(out.rect().right()).isEqualTo(111);
        }

        @Test
        void honoursThePolicyItWasGiven() {
            assertThat(apply(roundRule(RoundPolicy.INWARD), start(FRACTIONAL))
                            .rect()
                            .left())
                    .isEqualTo(11);
            assertThat(apply(roundRule(RoundPolicy.FLOOR), start(FRACTIONAL))
                            .rect()
                            .right())
                    .isEqualTo(110);
        }

        @Test
        void doesNothingToACropAlreadyOnWholePixels() {
            CropPlan plan = start(Rect.of(10, 10, 100, 100));
            assertThat(apply(roundRule(RoundPolicy.OUTWARD), plan)).isSameAs(plan);
        }

        @Test
        void landsInTheSamePlaceWhenRunTwice() {
            CropPlan once = apply(roundRule(RoundPolicy.OUTWARD), start(FRACTIONAL));
            assertThat(apply(roundRule(RoundPolicy.OUTWARD), once)).isSameAs(once);
        }

        @Test
        void refusesAPolicyItDoesNotKnow() {
            // A policy is an enum, so an unknown one is refused where it is read.
            assertThatThrownBy(() -> roundRule(RoundPolicy.parse("sideways")))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("policy must be one of");
        }
    }

    @Nested
    class GridAlignRule {
        @Test
        void movesTheOriginOntoTheGridAsWellAsTheExtents() {
            CropPlan out = apply(gridAlignRule(64), start(SQUARE));
            assertThat(out.rect().left() % 64).isEqualTo(0);
            assertThat(out.rect().top() % 64).isEqualTo(0);
            assertThat(out.rect().width() % 64).isEqualTo(0);
            assertThat(out.rect().height() % 64).isEqualTo(0);
        }

        @Test
        void onlyEverGrowsTheCrop() {
            CropPlan before = start(SQUARE);
            CropPlan after = apply(gridAlignRule(64), before);
            assertThat(after.rect().containsRect(before.rect())).isTrue();
        }

        @Test
        void doesNothingAtAMultipleOfOne() {
            CropPlan plan = start(SQUARE);
            assertThat(apply(gridAlignRule(1), plan)).isSameAs(plan);
        }

        @Test
        void landsInTheSamePlaceWhenRunTwice() {
            CropPlan once = apply(gridAlignRule(64), start(SQUARE));
            assertThat(apply(gridAlignRule(64), once)).isSameAs(once);
        }
    }

    @Nested
    class EvenExtentsRule {
        @Test
        void growsAnOddCropByAPixel() {
            CropPlan out = apply(evenExtentsRule(), start(ODD));
            assertThat(out.rect().width() % 2).isEqualTo(0);
            assertThat(out.rect().height() % 2).isEqualTo(0);
            assertThat(out.steps().get(1).note()).isEqualTo("grown to even extents");
        }

        @Test
        void doesNothingToACropThatIsAlreadyEven() {
            CropPlan plan = start(Rect.of(10, 10, 100, 100));
            assertThat(apply(evenExtentsRule(), plan)).isSameAs(plan);
        }
    }

    @Nested
    class SnapFor {
        @Test
        void picksThePlainSnapOrTheGridAlign() {
            Rect plain = apply(snapFor(64), start(SQUARE)).rect();
            assertThat(plain.width() % 64).isEqualTo(0);
            assertThat(plain.left() % 64).isNotEqualTo(0);

            Rect aligned =
                    apply(snapFor(64, SnapDirection.UP, true), start(SQUARE)).rect();
            assertThat(aligned.left()).isEqualTo(64);
            assertThat(aligned.width() % 64).isEqualTo(0);
        }

        @Test
        void sitsInTheGridPhaseEitherWay() {
            for (boolean alignOrigin : new boolean[] {false, true}) {
                assertThat(snapFor(8, SnapDirection.UP, alignOrigin).phase()).isEqualTo(Phase.GRID);
            }
        }
    }
}
