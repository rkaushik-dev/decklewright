package io.github.rkaushikdev.decklewright.plan;

import static io.github.rkaushikdev.decklewright.testing.Bench.bench;
import static io.github.rkaushikdev.decklewright.testing.Bench.paint;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.decklewright.detect.CaptureOptions;
import io.github.rkaushikdev.decklewright.detect.CaptureReading;
import io.github.rkaushikdev.decklewright.detect.Captures;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.plan.rules.AnchorRules;
import io.github.rkaushikdev.decklewright.plan.rules.AspectRules;
import io.github.rkaushikdev.decklewright.plan.rules.BleedMode;
import io.github.rkaushikdev.decklewright.plan.rules.BleedRules;
import io.github.rkaushikdev.decklewright.plan.rules.BoundsMode;
import io.github.rkaushikdev.decklewright.plan.rules.BoundsRules;
import io.github.rkaushikdev.decklewright.plan.rules.Landmarks;
import io.github.rkaushikdev.decklewright.plan.rules.Phase;
import io.github.rkaushikdev.decklewright.plan.rules.ResolutionRules;
import io.github.rkaushikdev.decklewright.plan.rules.Rule;
import io.github.rkaushikdev.decklewright.plan.rules.Rules;
import io.github.rkaushikdev.decklewright.plan.rules.SafeAreaMode;
import io.github.rkaushikdev.decklewright.plan.rules.SafeAreaRules;
import io.github.rkaushikdev.decklewright.plan.rules.SnapRules;
import io.github.rkaushikdev.decklewright.profile.CropOptions;
import io.github.rkaushikdev.decklewright.profile.Profile;
import io.github.rkaushikdev.decklewright.profile.ProfileRegistry;
import io.github.rkaushikdev.decklewright.profile.Settings;
import io.github.rkaushikdev.decklewright.raster.EdgeMode;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.raster.ops.Crops;
import io.github.rkaushikdev.decklewright.raster.ops.RotateOptions;
import io.github.rkaushikdev.decklewright.raster.ops.Rotation;
import io.github.rkaushikdev.decklewright.testing.Bench;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The layer as the rest of the tree sees it, through the public types of its two packages.
 */
class IndexTest {

    private static final ProfileRegistry REGISTRY = new ProfileRegistry();
    private static final CaptureOptions SIGMA_4 = CaptureOptions.defaults().withSigma(4);

    /** Every public type of the layer that holds functions or constants. */
    private static final List<Class<?>> LAYER = List.of(
            CropPlan.class,
            Engine.class,
            RuleSets.class,
            CropPlanner.class,
            Explanations.class,
            Rules.class,
            AnchorRules.class,
            BleedRules.class,
            SafeAreaRules.class,
            AspectRules.class,
            ResolutionRules.class,
            SnapRules.class,
            BoundsRules.class);

    /** The enums and records the layer's signatures are written in. */
    private static final List<Class<?>> TYPES = List.of(
            Phase.class,
            BleedMode.class,
            SafeAreaMode.class,
            BoundsMode.class,
            Rule.class,
            Landmarks.class,
            PlanInput.class,
            PlanOptions.class,
            PlannedCrop.class,
            RuleRun.class,
            StraightFrame.class,
            Straightened.class,
            Tension.class);

    /** A 320 by 260 bench with a 168 by 120 sheet centred at (170, 130), turned by the degrees, and a rule below it. */
    private static Surface accession(double degrees) {
        Surface surface = bench(Bench.Options.defaults()
                .withWidth(320)
                .withHeight(260)
                .withDegrees(degrees)
                .withHalfWidth(84)
                .withHalfHeight(60)
                .withCentre(170, 130));
        paint(surface, Rect.of(20, 230, 120, 7), 190);
        return surface;
    }

    private static boolean exportsFunction(String name) {
        return LAYER.stream()
                .filter(type -> Modifier.isPublic(type.getModifiers()))
                .flatMap(type -> Arrays.stream(type.getMethods()))
                .anyMatch(method -> method.getName().equals(name) && Modifier.isStatic(method.getModifiers()));
    }

    @Nested
    class ThePublicTypes {
        @Test
        void exportsTheOneCallThePipelineMakes() {
            assertThat(exportsFunction("planCrop")).isTrue();
        }

        @Test
        void exportsEveryRuleBuilder() {
            for (String name : List.of(
                    "anchorRule",
                    "bleedRule",
                    "safeAreaRule",
                    "aspectRule",
                    "resolutionRule",
                    "snapRule",
                    "boundsFor",
                    "notEmptyRule")) {
                assertThat(exportsFunction(name)).as(name).isTrue();
            }
        }

        @Test
        void holdsNoNullConstants() throws IllegalAccessException {
            for (Class<?> type : Stream.concat(LAYER.stream(), TYPES.stream()).toList()) {
                assertThat(Modifier.isPublic(type.getModifiers()))
                        .as(type.getSimpleName())
                        .isTrue();
                for (Field field : type.getFields()) {
                    if (Modifier.isStatic(field.getModifiers())) {
                        assertThat(field.get(null))
                                .as(type.getSimpleName() + "." + field.getName())
                                .isNotNull();
                    }
                }
            }
        }
    }

    @Nested
    class APlanCarriedThroughToACut {
        @Test
        void cutsAStraightSheetWhereThePlanSaid() {
            Surface surface = accession(0);
            CaptureReading reading = Captures.readCapture(surface, SIGMA_4);
            PlannedCrop out = CropPlanner.planCrop(reading, REGISTRY.get("house"), surface.bounds(), Sampling.of(400));

            Surface cut = Crops.crop(surface, out.plan().rect().intersect(surface.bounds()));
            assertThat(cut.width()).isGreaterThan(160);
            assertThat(cut.height()).isGreaterThan(110);
        }

        @Test
        void straightensFirstAndThenCutsAndTheSheetFillsTheCut() {
            Surface surface = accession(6);
            CaptureReading reading = Captures.readCapture(surface, SIGMA_4);
            PlannedCrop out = CropPlanner.planCrop(reading, REGISTRY.get("house"), surface.bounds(), Sampling.of(400));

            Surface straight = Rotation.deskew(
                            surface,
                            out.plan().deskew(),
                            RotateOptions.defaults().withEdge(EdgeMode.CLAMP))
                    .surface();
            Rect held = out.plan().rect().intersect(straight.bounds());
            assertThat(held.isEmpty()).isFalse();

            Surface cut = Crops.crop(straight, held);
            int middle = cut.get(cut.width() / 2, cut.height() / 2);
            assertThat(middle).isGreaterThan(180);
        }
    }

    @Nested
    class OverridingOneRuleForOneItem {
        @Test
        void dropsTheBleedRuleAndTheCropComesBackSmaller() {
            Surface surface = accession(0);
            CaptureReading reading = Captures.readCapture(surface, SIGMA_4);
            Profile profile = REGISTRY.get("atlas-plates");
            Rect capture = surface.bounds();
            Sampling sampling = Sampling.of(400);
            Rect anchor = reading.sheet().quad().boundingRect().outward();
            CropOptions settings = Settings.cropOptions(profile, sampling);

            List<Rule> full = RuleSets.rulesFor(settings, Landmarks.of(anchor));
            assertThat(full.stream().map(Rule::name).toList()).contains("bleed");

            PlanInput input = PlanInput.of(capture, anchor, sampling);
            CropPlan withBleed = Engine.planWith(full, input);
            CropPlan without = Engine.planWith(Engine.without(full, List.of("bleed")), input);
            assertThat(without.rect().width()).isLessThan(withBleed.rect().width());
        }

        @Test
        void replacesTheSnapAndTheCropLandsOnTheNewMultiple() {
            Rect anchor = Rect.of(100, 100, 501, 401);
            Rect capture = Rect.of(0, 0, 2000, 1600);
            List<Rule> rules =
                    List.of(AnchorRules.anchorRule(anchor, "sheet"), SnapRules.snapRule(8), BoundsRules.notEmptyRule());
            List<Rule> swapped = Engine.withRule(rules, SnapRules.snapRule(64));
            CropPlan out = Engine.planWith(swapped, PlanInput.of(capture, anchor, Sampling.of(400)));
            assertThat(out.rect().width() % 64).isEqualTo(0);
        }
    }
}
