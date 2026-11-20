package io.github.rkaushikdev.decklewright.plan;

import static io.github.rkaushikdev.decklewright.plan.Engine.planWith;
import static io.github.rkaushikdev.decklewright.plan.RuleSets.KNOWN_TENSIONS;
import static io.github.rkaushikdev.decklewright.plan.RuleSets.describeSet;
import static io.github.rkaushikdev.decklewright.plan.RuleSets.houseRules;
import static io.github.rkaushikdev.decklewright.plan.RuleSets.rulesFor;
import static io.github.rkaushikdev.decklewright.plan.RuleSets.tensionsIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.PlanError;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.plan.rules.BoundsMode;
import io.github.rkaushikdev.decklewright.plan.rules.Landmarks;
import io.github.rkaushikdev.decklewright.plan.rules.Rule;
import io.github.rkaushikdev.decklewright.profile.CropOptions;
import io.github.rkaushikdev.decklewright.profile.Profile;
import io.github.rkaushikdev.decklewright.profile.ProfileRegistry;
import io.github.rkaushikdev.decklewright.profile.Settings;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RulesetTest {

    private static final ProfileRegistry REGISTRY = new ProfileRegistry();
    private static final Rect CAPTURE = Rect.of(0, 0, 4000, 3200);
    private static final Rect SHEET = Rect.of(400, 400, 3000, 2200);
    private static final Rect NEATLINE = Rect.of(600, 600, 2600, 1700);

    private static CropOptions cropFor(String name) {
        Profile profile = REGISTRY.get(name);
        return Settings.cropOptions(profile, Sampling.of(profile.capture().claimedPpi()));
    }

    /** The crop options of a site profile written over the house style. */
    private static CropOptions siteCrop(String name, String base, Map<String, Object> crop, double ppi) {
        ProfileRegistry site = new ProfileRegistry().add(Map.of("name", name, "extends", base, "crop", crop));
        return Settings.cropOptions(site.get(name), Sampling.of(ppi));
    }

    private static List<String> names(List<Rule> rules) {
        return rules.stream().map(Rule::name).toList();
    }

    private static PlanInput input(Rect anchor, double ppi) {
        return PlanInput.of(CAPTURE, anchor, Sampling.of(ppi));
    }

    @Nested
    class RulesFor {
        @Test
        void buildsTheHouseSet() {
            List<Rule> rules = rulesFor(cropFor("house"), Landmarks.of(SHEET));
            assertThat(names(rules)).isEqualTo(List.of("anchor", "snap", "round", "bounds", "resolution", "notEmpty"));
        }

        @Test
        void addsABleedRuleOnlyWhenTheProfileHasOne() {
            assertThat(names(rulesFor(cropFor("house"), Landmarks.of(SHEET)))).doesNotContain("bleed");
            assertThat(names(rulesFor(cropFor("atlas-plates"), Landmarks.of(SHEET))))
                    .contains("bleed");
        }

        @Test
        void addsASafeAreaRuleOnlyWhenTheProfileHasOne() {
            CropOptions crop = siteCrop("gutter", "house", Map.of("safeArea", Map.of("left", "10mm")), 400);
            assertThat(names(rulesFor(crop, Landmarks.of(SHEET)))).contains("safeArea");
        }

        @Test
        void addsAnAspectRuleOnlyWhenTheProfileSetOne() {
            CropOptions crop = siteCrop("square", "house", Map.of("aspect", 1.0), 400);
            assertThat(names(rulesFor(crop, Landmarks.of(SHEET)))).contains("aspect");
        }

        @Test
        void skipsTheSnapWhenTheProfileSnapsToOnePixel() {
            CropOptions crop = siteCrop("nosnap", "house", Map.of("snapTo", 1.0), 400);
            assertThat(names(rulesFor(crop, Landmarks.of(SHEET)))).doesNotContain("snap");
        }

        @Test
        void anchorsAQuadrangleToTheNeatline() {
            List<Rule> rules = rulesFor(cropFor("quadrangles"), Landmarks.of(SHEET, NEATLINE));
            CropPlan out = planWith(rules, input(SHEET, 600));
            assertThat(out.rect().left()).isLessThan(NEATLINE.left());
            assertThat(out.rect().top()).isLessThan(NEATLINE.top());
            assertThat(out.trail().get(0)).isEqualTo("anchor");
        }

        @Test
        void refusesToBuildANeatlineSetWhenThereIsNoNeatline() {
            assertThatThrownBy(() -> rulesFor(cropFor("quadrangles"), Landmarks.of(SHEET)))
                    .isInstanceOf(PlanError.class)
                    .hasMessageFindingMatch("none was found");
        }

        @Test
        void fallsBackWhenItIsToldItMay() {
            List<Rule> rules = rulesFor(
                    cropFor("quadrangles"),
                    Landmarks.of(SHEET),
                    PlanOptions.defaults().withFallBackToSheet(true));
            CropPlan out = planWith(rules, input(SHEET, 600));
            assertThat(String.join(" ", out.warnings())).containsPattern("fell back to the paper edge");
        }

        @Test
        void alwaysEndsWithNotEmpty() {
            for (String name : REGISTRY.names()) {
                List<Rule> rules = rulesFor(cropFor(name), Landmarks.of(SHEET, NEATLINE));
                assertThat(rules.get(rules.size() - 1).name()).as(name).isEqualTo("notEmpty");
            }
        }

        @Test
        void neverPutsTheSameRuleInTwice() {
            for (String name : REGISTRY.names()) {
                assertThatCode(() -> rulesFor(cropFor(name), Landmarks.of(SHEET, NEATLINE)))
                        .as(name)
                        .doesNotThrowAnyException();
            }
        }
    }

    @Nested
    class TheBoundsMode {
        private final Rect wide = Rect.of(3900, 3100, 400, 300);

        @Test
        void warnsByDefault() {
            List<Rule> rules = rulesFor(cropFor("house"), Landmarks.of(wide));
            CropPlan out = planWith(rules, input(wide, 400));
            assertThat(String.join(" ", out.warnings())).containsPattern("falls off the capture");
            assertThat(out.rect().width()).isGreaterThan(100);
        }

        @Test
        void clampsWhenAsked() {
            List<Rule> rules = rulesFor(
                    cropFor("house"), Landmarks.of(wide), PlanOptions.defaults().withBoundsMode(BoundsMode.CLAMP));
            CropPlan out = planWith(rules, input(wide, 400));
            assertThat(CAPTURE.containsRect(out.rect())).isTrue();
        }

        @Test
        void refusesWhenAsked() {
            List<Rule> rules = rulesFor(
                    cropFor("house"), Landmarks.of(wide), PlanOptions.defaults().withBoundsMode(BoundsMode.REFUSE));
            assertThatThrownBy(() -> planWith(rules, input(wide, 400)))
                    .isInstanceOf(PlanError.class)
                    .hasMessageFindingMatch("will not pad");
        }

        @Test
        void usesATrimmedBleedUnderTheClampMode() {
            List<Rule> rules = rulesFor(
                    cropFor("atlas-plates"),
                    Landmarks.of(wide),
                    PlanOptions.defaults().withBoundsMode(BoundsMode.CLAMP));
            CropPlan out = planWith(rules, input(wide, 600));
            assertThat(CAPTURE.containsRect(out.rect())).isTrue();
        }
    }

    @Nested
    class TheResolutionMode {
        @Test
        void warnsByDefaultAndStopsWhenStrict() {
            CropOptions house = cropFor("house");
            CropOptions crop = new CropOptions(
                    house.anchor(),
                    house.bleed(),
                    house.safeArea(),
                    house.aspect(),
                    house.aspectDirection(),
                    800,
                    house.snapTo(),
                    house.snapDirection(),
                    house.rounding(),
                    house.background(),
                    house.deskew());
            CropPlan warn = planWith(rulesFor(crop, Landmarks.of(SHEET)), input(SHEET, 400));
            assertThat(String.join(" ", warn.warnings())).containsPattern("against a floor of 800");

            List<Rule> strict =
                    rulesFor(crop, Landmarks.of(SHEET), PlanOptions.defaults().withStrictResolution(true));
            assertThatThrownBy(() -> planWith(strict, input(SHEET, 400)))
                    .isInstanceOf(PlanError.class)
                    .hasMessageFindingMatch("will not take under 800");
        }
    }

    @Nested
    class TensionsIn {
        @Test
        void namesThePairsThatAreKnownToArgue() {
            CropOptions crop = siteCrop("busy", "quadrangles", Map.of("aspect", 1.4, "safeArea", "2mm"), 600);
            List<Tension> found = tensionsIn(rulesFor(crop, Landmarks.of(SHEET, NEATLINE)));
            List<String> pairs =
                    found.stream().map(t -> String.join("+", t.between())).toList();
            assertThat(pairs).contains("aspect+snap");
            assertThat(pairs).contains("bleed+safeArea");
        }

        @Test
        void findsNothingInAPlainSet() {
            assertThat(tensionsIn(rulesFor(cropFor("house"), Landmarks.of(SHEET))))
                    .isEqualTo(List.of());
        }

        @Test
        void hasAFrozenTable() {
            Tension any = KNOWN_TENSIONS.get(0);
            assertThatThrownBy(() -> KNOWN_TENSIONS.add(any)).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> any.between().add("x")).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class DescribeSet {
        @Test
        void listsTheRulesByPhaseInTheOrderTheyRun() {
            List<String> lines = describeSet(rulesFor(cropFor("quadrangles"), Landmarks.of(SHEET, NEATLINE)));
            assertThat(lines.get(0)).isEqualTo("anchor: anchor");
            assertThat(lines).contains("grid: snap");
            assertThat(lines.get(lines.size() - 1)).isEqualTo("check: notEmpty");
        }
    }

    @Nested
    class HouseRules {
        @Test
        void isTheWarningSet() {
            assertThat(names(houseRules(cropFor("house"), Landmarks.of(SHEET)))).contains("bounds");
        }
    }
}
