package io.github.rkaushikdev.decklewright.derive;

import static io.github.rkaushikdev.decklewright.derive.Derivatives.deriveFromPlan;
import static io.github.rkaushikdev.decklewright.derive.Derivatives.preservationCopy;
import static io.github.rkaushikdev.decklewright.testing.Bench.bench;
import static io.github.rkaushikdev.decklewright.testing.Bench.paint;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.decklewright.PipelineError;
import io.github.rkaushikdev.decklewright.codec.ImageFormat;
import io.github.rkaushikdev.decklewright.detect.CaptureReading;
import io.github.rkaushikdev.decklewright.detect.Captures;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.plan.CropPlan;
import io.github.rkaushikdev.decklewright.plan.CropPlanner;
import io.github.rkaushikdev.decklewright.profile.DerivativeRecipe;
import io.github.rkaushikdev.decklewright.profile.Profile;
import io.github.rkaushikdev.decklewright.profile.ProfileRegistry;
import io.github.rkaushikdev.decklewright.profile.Settings;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.testing.Bench;
import io.github.rkaushikdev.decklewright.util.Num;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The derivative layer as the rest of the tree sees it: one call that derives a set from a plan,
 * and each of its steps public on its own, so a bad file can be traced to one of them.
 */
class IndexTest {

    private static final ProfileRegistry REGISTRY = new ProfileRegistry();

    /** Every public type of the layer that holds functions or constants. */
    private static final List<Class<?>> LAYER =
            List.of(Derivatives.class, Rendering.class, Recipes.class, FileNames.class);

    /** A capture off the stand, with the wedge and the scale bar the lab lays beside every sheet. */
    static Surface accession(double degrees) {
        Surface surface = bench(Bench.Options.defaults()
                .withWidth(300)
                .withHeight(240)
                .withDegrees(degrees)
                .withHalfWidth(80)
                .withHalfHeight(56)
                .withCentre(170, 130)
                .withLayout(Layout.RGB));
        for (int i = 0; i < 8; i += 1) {
            paint(surface, Rect.of(14 + i * 10, 12, 10, 14), 20 + Num.roundHalfUp((i / 7.0) * 210));
        }
        paint(surface, Rect.of(20, 220, 120, 7), 190);
        return surface;
    }

    /** Everything a stage hands the derive call: a capture read and planned under a profile. */
    record Planned(Surface surface, CropPlan plan, Profile profile, String accession, List<DerivativeRecipe> recipes) {

        Planned withPlan(CropPlan other) {
            return new Planned(surface, other, profile, accession, recipes);
        }

        Planned withProfile(Profile other) {
            return new Planned(surface, plan, other, accession, recipes);
        }

        DerivedSet derive() {
            return deriveFromPlan(surface, plan, profile, accession, recipes);
        }
    }

    static Planned planned(String profileName, double degrees, double ppi) {
        Surface surface = accession(degrees);
        Profile profile = REGISTRY.get(profileName);
        CaptureReading reading = Captures.readCapture(surface);
        CropPlan plan = CropPlanner.planCrop(reading, profile, surface.bounds(), Sampling.of(ppi))
                .plan();
        return new Planned(surface, plan, profile, "g4181-1907", Settings.derivativeRecipes(profile));
    }

    /** A square capture planned under the house profile at 400 ppi. */
    static Planned planned() {
        return planned("house", 0, 400);
    }

    /** A file that has nothing but its name to go on. */
    static DerivedFile fileNamed(String name) {
        return new DerivedFile(name, name + ".png", new byte[0], ImageFormat.PNG, Sampling.of(400), List.of());
    }

    private static Optional<Method> function(String name) {
        return LAYER.stream()
                .filter(type -> Modifier.isPublic(type.getModifiers()))
                .flatMap(type -> Arrays.stream(type.getMethods()))
                .filter(method -> method.getName().equals(name) && Modifier.isStatic(method.getModifiers()))
                .findFirst();
    }

    @Nested
    class TheLayer {
        @Test
        void exportsTheOneCallAStageMakes() {
            assertThat(function("deriveFromPlan")).isPresent();
        }

        @Test
        void exportsEachStepSoABadFileCanBeTracedToOneOfThem() {
            for (String name : List.of("cutMaster", "renderDerivative", "encodeRendered", "nameFor")) {
                assertThat(function(name)).as(name).isPresent();
            }
        }

        @Test
        void exportsOneWayToDeriveASetAndNotTwo() {
            assertThat(function("deriveFromPlan")).isPresent();
            assertThat(function("deriveOne")).isEmpty();
        }

        @Test
        void exportsTheReadBackHelpersWhichIsHowADeliveryIsCheckedLater() {
            assertThat(function("frequencyOf")).isPresent();
            assertThat(function("textOf")).isPresent();
        }
    }

    @Nested
    class DeriveFromPlan {
        @Test
        void rendersEncodesAndNamesGivenAPlanSomebodyElseMade() {
            DerivedSet out = planned().derive();
            assertThat(out.files())
                    .extracting(DerivedFile::fileName)
                    .containsExactly("g4181-1907_master.png", "g4181-1907_service.png", "g4181-1907_thumb.png");
        }

        @Test
        void doesNotReadTheCaptureAgain() {
            Planned args = planned();
            Rect before = args.plan().rect();
            args.derive();
            assertThat(args.plan().rect()).isSameAs(before);
        }

        @Test
        void takesThePlanItWasGivenRatherThanMakingABetterOne() {
            Planned args = planned();
            CropPlan moved = args.plan().moved(Rect.of(100, 100, 40, 30), "test", "by hand");
            DerivedSet out = args.withPlan(moved).derive();
            assertThat(Recipes.frequencyOf(out.files().get(0).bytes()))
                    .hasValueSatisfying(sampling -> assertThat(sampling.x()).isCloseTo(400, byLessThan(0.005)));
            assertThat(String.join(" ", out.steps())).containsPattern("cut 40 by 30");
        }

        @Test
        void addsTheSetUp() {
            DerivedSet out = planned().derive();
            long sum =
                    out.files().stream().mapToLong(file -> file.bytes().length).sum();
            assertThat(out.bytes()).isEqualTo(sum);
        }

        @Test
        void givesTheSameAnswerTwiceForTheSamePlan() {
            Planned args = planned();
            DerivedSet one = args.derive();
            DerivedSet two = args.derive();
            assertThat(two.files())
                    .extracting(DerivedFile::fileName)
                    .isEqualTo(one.files().stream().map(DerivedFile::fileName).toList());
            assertThat(two.bytes()).isEqualTo(one.bytes());
        }

        @Test
        void refusesASetWhoseNamesWouldCollide() {
            Planned args = planned();
            Planned clashing = args.withProfile(args.profile().withNaming(new Profile.Naming("{accession}", true)));
            assertThatThrownBy(clashing::derive)
                    .isInstanceOf(PipelineError.class)
                    .hasMessageFindingMatch("differ only in case");
        }
    }

    @Nested
    class PreservationCopy {
        @Test
        void picksTheMasterOutOfTheSet() {
            DerivedSet out = planned().derive();
            assertThat(preservationCopy(out.files()).name()).isEqualTo("master");
        }

        @Test
        void fallsBackToTheFirstFileWhenNothingIsCalledMaster() {
            List<DerivedFile> files = List.of(fileNamed("archive"), fileNamed("access"));
            assertThat(preservationCopy(files).name()).isEqualTo("archive");
        }

        @Test
        void refusesAnEmptySetRatherThanHandingBackNothing() {
            assertThatThrownBy(() -> preservationCopy(List.of()))
                    .isInstanceOf(PipelineError.class)
                    .hasMessageFindingMatch("nothing was derived");
        }
    }
}
