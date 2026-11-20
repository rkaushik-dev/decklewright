package io.github.rkaushikdev.decklewright.plan.rules;

import static io.github.rkaushikdev.decklewright.plan.rules.AnchorRules.anchorFor;
import static io.github.rkaushikdev.decklewright.plan.rules.AnchorRules.anchorOrSheetRule;
import static io.github.rkaushikdev.decklewright.plan.rules.AnchorRules.anchorRule;
import static io.github.rkaushikdev.decklewright.plan.rules.SafeAreaRules.edgeSafeAreaRule;
import static io.github.rkaushikdev.decklewright.plan.rules.SafeAreaRules.safeAreaFor;
import static io.github.rkaushikdev.decklewright.plan.rules.SafeAreaRules.safeAreaRule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.PlanError;
import io.github.rkaushikdev.decklewright.geom.Insets;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.plan.CropPlan;
import io.github.rkaushikdev.decklewright.profile.Anchor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AnchorTest {

    private static final Rect CAPTURE = Rect.of(0, 0, 1000, 800);
    private static final Rect SHEET = Rect.of(100, 100, 600, 500);
    private static final Rect NEATLINE = Rect.of(150, 150, 500, 380);

    private static CropPlan start() {
        return CropPlan.start(CAPTURE, SHEET, Sampling.of(400));
    }

    /** The plan after a bleed has pushed it out past the sheet. */
    private static CropPlan bled() {
        return start().moved(Rect.of(80, 80, 640, 540), "bleed", "x");
    }

    private static RuleContext fromSheet() {
        return RuleContext.of(SHEET);
    }

    @Nested
    class AnchorRule {
        @Test
        void setsTheCropToTheRectAndNamesItInTheTrail() {
            CropPlan out = anchorRule(NEATLINE, "neatline").apply(start(), RuleContext.empty());
            assertThat(out.rect().approxEquals(NEATLINE)).isTrue();
            assertThat(out.steps().get(1).note()).isEqualTo("measured from the neatline");
        }

        @Test
        void recordsTheAnchorInTheContextForTheLaterRules() {
            RuleContext context = RuleContext.empty();
            anchorRule(NEATLINE, "neatline").apply(start(), context);
            assertThat(context.anchor()).containsSame(NEATLINE);
            assertThat(context.anchorName()).contains("neatline");
        }

        @Test
        void landsInTheSamePlaceWhenRunTwice() {
            CropPlan once = anchorRule(NEATLINE, "neatline").apply(start(), RuleContext.empty());
            CropPlan twice = anchorRule(NEATLINE, "neatline").apply(once, RuleContext.empty());
            assertThat(twice).isSameAs(once);
        }

        @Test
        void refusesAnAnchorWithNoArea() {
            assertThatThrownBy(() -> anchorRule(Rect.empty(), "neatline"))
                    .isInstanceOf(PlanError.class)
                    .hasMessageFindingMatch("the neatline anchor has no area");
        }
    }

    @Nested
    class AnchorFor {
        @Test
        void picksTheSheetOrTheNeatlineAsTheProfileSays() {
            Landmarks found = Landmarks.of(SHEET, NEATLINE);
            assertThat(anchorFor(Anchor.SHEET, found)
                            .apply(start(), RuleContext.empty())
                            .rect()
                            .approxEquals(SHEET))
                    .isTrue();
            assertThat(anchorFor(Anchor.NEATLINE, found)
                            .apply(start(), RuleContext.empty())
                            .rect()
                            .approxEquals(NEATLINE))
                    .isTrue();
        }

        @Test
        void refusesToFallBackQuietlyWhenTheNeatlineIsMissing() {
            assertThatThrownBy(() -> anchorFor(Anchor.NEATLINE, Landmarks.of(SHEET)))
                    .isInstanceOf(PlanError.class)
                    .hasMessageFindingMatch("crops to the neatline and none");
            assertThatThrownBy(() -> anchorFor(Anchor.NEATLINE, Landmarks.of(SHEET, Rect.empty())))
                    .isInstanceOf(PlanError.class)
                    .hasMessageFindingMatch("none was found");
        }

        @Test
        void refusesAnAnchorItDoesNotKnow() {
            // An Anchor is an enum, so an unknown anchor can only arrive by name.
            assertThatThrownBy(() -> anchorFor("collar", Landmarks.of(SHEET)))
                    .isInstanceOfSatisfying(PlanError.class, error -> assertThat(error.details())
                            .isEqualTo(Map.of("anchor", "collar")))
                    .hasMessageFindingMatch("there is no anchor called collar");
        }
    }

    @Nested
    class AnchorOrSheetRule {
        @Test
        void usesTheNeatlineWhenThereIsOne() {
            CropPlan out = anchorOrSheetRule(Anchor.NEATLINE, Landmarks.of(SHEET, NEATLINE))
                    .apply(start(), RuleContext.empty());
            assertThat(out.rect().approxEquals(NEATLINE)).isTrue();
            assertThat(out.warnings()).isEqualTo(List.of());
        }

        @Test
        void fallsBackToThePaperEdgeAndSaysSoLoudly() {
            CropPlan out =
                    anchorOrSheetRule(Anchor.NEATLINE, Landmarks.of(SHEET)).apply(start(), RuleContext.empty());
            assertThat(out.rect().approxEquals(SHEET)).isTrue();
            assertThat(out.warnings().get(0)).containsPattern("fell back to the paper edge");
        }

        @Test
        void behavesLikeAnchorForWhenTheAnchorIsTheSheet() {
            CropPlan out = anchorOrSheetRule(Anchor.SHEET, Landmarks.of(SHEET)).apply(start(), RuleContext.empty());
            assertThat(out.warnings()).isEqualTo(List.of());
        }
    }

    @Nested
    class SafeAreaRule {
        @Test
        void pullsTheCropInFromTheAnchor() {
            CropPlan out = safeAreaRule(Insets.uniform(50)).apply(start(), fromSheet());
            assertThat(out.rect().toJson()).isEqualTo(Map.of("x", 150.0, "y", 150.0, "width", 500.0, "height", 400.0));
        }

        @Test
        void doesNothingAtAllForAZeroSafeArea() {
            CropPlan plan = start();
            assertThat(safeAreaRule(Insets.zero()).apply(plan, fromSheet())).isSameAs(plan);
        }

        @Test
        void doesNotLetABleedPutTheCropBackOutsideTheSafeArea() {
            CropPlan out = safeAreaRule(Insets.uniform(50)).apply(bled(), fromSheet());
            assertThat(out.rect().toJson()).isEqualTo(Map.of("x", 150.0, "y", 150.0, "width", 500.0, "height", 400.0));
        }

        @Test
        void warnsWhenItConsumesTheAnchorEntirely() {
            CropPlan out = safeAreaRule(Insets.uniform(400)).apply(start(), fromSheet());
            assertThat(out.warnings().get(0)).containsPattern("leaves no crop at all");
        }

        @Test
        void landsInTheSamePlaceWhenRunTwice() {
            RuleContext context = fromSheet();
            CropPlan once = safeAreaRule(Insets.uniform(50)).apply(start(), context);
            CropPlan twice = safeAreaRule(Insets.uniform(50)).apply(once, context);
            assertThat(twice).isSameAs(once);
        }
    }

    @Nested
    class EdgeSafeAreaRule {
        @Test
        void pullsInOnlyTheEdgesItWasGiven() {
            CropPlan out = edgeSafeAreaRule(new Insets(0, 0, 0, 60)).apply(bled(), fromSheet());
            assertThat(out.rect().left()).isEqualTo(160);
            assertThat(out.rect().top()).isEqualTo(80);
            assertThat(out.rect().right()).isEqualTo(720);
        }

        @Test
        void doesNothingForAZeroSafeArea() {
            CropPlan plan = start();
            assertThat(edgeSafeAreaRule(Insets.zero()).apply(plan, fromSheet())).isSameAs(plan);
        }

        @Test
        void warnsRatherThanInvertingWhenTheEdgesMeet() {
            CropPlan out = edgeSafeAreaRule(Insets.uniform(400)).apply(start(), fromSheet());
            assertThat(out.warnings().get(0)).containsPattern("leaves no crop at all");
            assertThat(out.rect().approxEquals(SHEET)).isTrue();
        }
    }

    @Nested
    class SafeAreaFor {
        @Test
        void picksTheShapeTheModeNames() {
            assertThat(safeAreaFor(new Insets(0, 0, 0, 60))
                            .apply(bled(), fromSheet())
                            .rect()
                            .top())
                    .isEqualTo(100);
            assertThat(safeAreaFor(new Insets(0, 0, 0, 60), SafeAreaMode.EDGES)
                            .apply(bled(), fromSheet())
                            .rect()
                            .top())
                    .isEqualTo(80);
        }

        @Test
        void isCalledSafeAreaInTheShapePhaseEitherWay() {
            for (SafeAreaMode mode : List.of(SafeAreaMode.WHOLE, SafeAreaMode.EDGES)) {
                Rule made = safeAreaFor(Insets.uniform(1), mode);
                assertThat(made.name()).isEqualTo("safeArea");
                assertThat(made.phase()).isEqualTo(Phase.SHAPE);
            }
        }
    }
}
