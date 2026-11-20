package io.github.rkaushikdev.decklewright.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.decklewright.geom.Angle;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.measure.PhysicalSize;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.measure.Unit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PlanTest {

    private static final Rect CAPTURE = Rect.of(0, 0, 1000, 800);

    private static CropPlan start() {
        return start(Rect.of(100, 100, 400, 300));
    }

    private static CropPlan start(Rect rect) {
        return CropPlan.start(CAPTURE, rect, Sampling.of(400));
    }

    @Nested
    class StartingAPlan {
        @Test
        void recordsTheAnchorAsTheFirstStep() {
            CropPlan plan = start();
            assertThat(plan.steps()).hasSize(1);
            assertThat(plan.steps().get(0).rule()).isEqualTo("start");
            assertThat(plan.trail()).isEqualTo(List.of());
        }

        @Test
        void isFrozenStepsAndWarningsIncluded() {
            CropPlan plan = start();
            // A record has no setters, and the lists it hands out refuse every change.
            assertThat(CropPlan.class.isRecord()).isTrue();
            CropPlan.Step step = plan.steps().get(0);
            assertThatThrownBy(() -> plan.steps().add(step)).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> plan.warnings().add("x")).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void hasNoDeskewUnlessOneWasGiven() {
            assertThat(start().deskew().isZero()).isTrue();
            CropPlan turned = CropPlan.start(CAPTURE, Rect.of(0, 0, 10, 10), Sampling.of(400), Angle.degrees(2));
            assertThat(turned.deskew().degrees()).isEqualTo(2);
        }
    }

    @Nested
    class Moved {
        @Test
        void recordsTheRuleAndBothRects() {
            CropPlan plan = start().moved(Rect.of(90, 90, 420, 320), "bleed", "added 10 px");
            assertThat(plan.trail()).isEqualTo(List.of("bleed"));
            assertThat(plan.steps().get(1).before().toJson())
                    .isEqualTo(Map.of("x", 100.0, "y", 100.0, "width", 400.0, "height", 300.0));
            assertThat(plan.steps().get(1).after().width()).isEqualTo(420);
            assertThat(plan.rect().width()).isEqualTo(420);
        }

        @Test
        void recordsNothingWhenTheRuleChangedNothing() {
            CropPlan plan = start();
            CropPlan same = plan.moved(plan.rect(), "bleed", "nothing to add");
            assertThat(same).isSameAs(plan);
            assertThat(same.trail()).isEqualTo(List.of());
        }

        @Test
        void leavesThePlanItCameFromAlone() {
            CropPlan first = start();
            first.moved(Rect.of(0, 0, 10, 10), "snap", "x");
            assertThat(first.rect().width()).isEqualTo(400);
            assertThat(first.steps()).hasSize(1);
        }

        @Test
        void keepsTheTrailInOrderOverSeveralRules() {
            CropPlan plan = start().moved(Rect.of(90, 90, 420, 320), "bleed", "a")
                    .moved(Rect.of(88, 88, 424, 324), "snap", "b")
                    .moved(Rect.of(88, 88, 424, 320), "aspect", "c");
            assertThat(plan.trail()).isEqualTo(List.of("bleed", "snap", "aspect"));
            assertThat(plan.touchedBy("snap")).isTrue();
            assertThat(plan.touchedBy("resolution")).isFalse();
        }
    }

    @Nested
    class Warned {
        @Test
        void collectsWarnings() {
            CropPlan plan = start().warned("the crop reaches off the bed");
            assertThat(plan.warnings()).isEqualTo(List.of("the crop reaches off the bed"));
        }

        @Test
        void saysTheSameThingOnlyOnce() {
            CropPlan plan = start().warned("same").warned("same");
            assertThat(plan.warnings()).hasSize(1);
        }

        @Test
        void keepsTheCropAndTheTrail() {
            CropPlan plan = start().moved(Rect.of(0, 0, 10, 10), "bleed", "a").warned("note");
            assertThat(plan.rect().width()).isEqualTo(10);
            assertThat(plan.trail()).isEqualTo(List.of("bleed"));
        }
    }

    @Nested
    class WithDeskew {
        @Test
        void setsTheTurnWithoutTouchingAnythingElse() {
            CropPlan plan = start().warned("x").withDeskew(Angle.degrees(-1.5));
            assertThat(plan.deskew().degrees()).isEqualTo(-1.5);
            assertThat(plan.warnings()).isEqualTo(List.of("x"));
        }
    }

    @Nested
    class WhatAPlanKnowsAboutItself {
        @Test
        void reportsItsSize() {
            assertThat(start().size().toJson()).isEqualTo(Map.of("width", 400.0, "height", 300.0));
        }

        @Test
        void saysWhenTheCropReachesPastTheCaptureAndByHowMuch() {
            assertThat(start().overhangs()).isFalse();
            assertThat(start().overhangArea()).isEqualTo(0);

            CropPlan over = start(Rect.of(900, 700, 400, 300));
            assertThat(over.overhangs()).isTrue();
            assertThat(over.overhangArea()).isEqualTo(400 * 300 - 100 * 100);
        }

        @Test
        void reportsThePhysicalSizeItWillComeOutAt() {
            PhysicalSize size = start().physical();
            assertThat(size).isEqualTo(new PhysicalSize(1, 0.75, Unit.INCH));
            assertThat(start().physical(Unit.MILLIMETRE).width()).isCloseTo(25.4, byLessThan(0.5e-9));
        }
    }

    @Nested
    class Serialisation {
        @Test
        void writesTheRectTheTrailAndTheWarnings() {
            Map<String, Object> json = start().moved(Rect.of(0, 0, 10, 10), "bleed", "a")
                    .warned("w")
                    .toJson();
            assertThat(json.get("rect")).isEqualTo(Map.of("x", 0.0, "y", 0.0, "width", 10.0, "height", 10.0));
            assertThat(json.get("sampling")).isEqualTo(Map.of("x", 400.0, "y", 400.0));
            assertThat(json.get("deskew")).isEqualTo(0.0);
            assertThat((List<?>) json.get("steps")).hasSize(2);
            assertThat(((Map<?, ?>) ((List<?>) json.get("steps")).get(1)).get("rule"))
                    .isEqualTo("bleed");
            assertThat(json.get("warnings")).isEqualTo(List.of("w"));
        }

        @Test
        void printsTheCropAndHowManyRulesTouchedIt() {
            assertThat(String.valueOf(start().moved(Rect.of(0, 0, 10, 10), "bleed", "a")))
                    .isEqualTo("10x10+0+0 after 1 rules");
        }
    }
}
