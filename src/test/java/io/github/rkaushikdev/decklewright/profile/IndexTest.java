package io.github.rkaushikdev.decklewright.profile;

import static io.github.rkaushikdev.decklewright.profile.Documents.bag;
import static io.github.rkaushikdev.decklewright.testing.Bench.bench;
import static io.github.rkaushikdev.decklewright.testing.Bench.paint;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.decklewright.ProfileError;
import io.github.rkaushikdev.decklewright.detect.CaptureReading;
import io.github.rkaushikdev.decklewright.detect.Captures;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.testing.Bench;
import io.github.rkaushikdev.decklewright.util.Num;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The layer as the rest of the tree sees it. The original checked its barrel module; the Java layer
 * has no barrel, so this checks the public types of the package instead.
 */
class IndexTest {

    /** Every public type of the layer that holds functions or constants, as the barrel exported them. */
    private static final List<Class<?>> LAYER = List.of(
            ProfileRegistry.class,
            ProfileLoader.class,
            Fields.class,
            BuiltIn.class,
            Overlays.class,
            Validation.class,
            Migrations.class,
            Settings.class,
            Diagnostics.class,
            Schema.class);

    /** A 260 by 220 bench with a 140 by 100 sheet turned by three degrees, a step wedge and a rule. */
    private static Surface accession() {
        Surface surface = bench(Bench.Options.defaults()
                .withWidth(260)
                .withHeight(220)
                .withDegrees(3)
                .withHalfWidth(70)
                .withHalfHeight(50)
                .withCentre(140, 110));
        for (int i = 0; i < 8; i += 1) {
            paint(surface, Rect.of(14 + i * 10, 12, 10, 14), 20 + Num.roundHalfUp((i / 7.0) * 210));
        }
        paint(surface, Rect.of(20, 200, 120, 7), 190);
        return surface;
    }

    /** What the detection stage takes, read off the options the profile layer hands it. */
    private static io.github.rkaushikdev.decklewright.detect.CaptureOptions forDetection(CaptureOptions options) {
        return new io.github.rkaushikdev.decklewright.detect.CaptureOptions(
                options.sigma(),
                options.speckle(),
                options.gap(),
                OptionalDouble.of(options.barLength()),
                options.barUnit(),
                Optional.of(options.claimed()),
                options.expectNeatline());
    }

    private static boolean exportsFunction(String name) {
        return LAYER.stream()
                .filter(type -> Modifier.isPublic(type.getModifiers()))
                .flatMap(type -> Arrays.stream(type.getMethods()))
                .anyMatch(method -> method.getName().equals(name) && Modifier.isStatic(method.getModifiers()));
    }

    @Nested
    class TheBarrel {
        @Test
        void exportsTheRegistryAndTheOneCallTheCliMakes() {
            assertThat(Modifier.isPublic(ProfileRegistry.class.getModifiers())).isTrue();
            assertThat(exportsFunction("loadProfile")).isTrue();
            assertThat(exportsFunction("loadAll")).isTrue();
        }

        @Test
        void exportsNoUndefinedBindings() throws IllegalAccessException {
            for (Class<?> type : LAYER) {
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
    class LoadProfile {
        @Test
        void takesAnOldDocumentAllTheWayToStageSettings() {
            Profile profile = ProfileLoader.loadProfile(bag(
                    "name",
                    "plat-books",
                    "extends",
                    "atlas-plates",
                    "crop",
                    bag("margin", "4mm", "roundTo", 4.0),
                    "qc",
                    bag("dpi", 600.0)));
            assertThat(profile.version()).isEqualTo(2);
            assertThat(profile.crop().bleed().top().value()).isEqualTo(4);
            assertThat(profile.crop().snapTo()).isEqualTo(4);
            assertThat(profile.qc().samplingPpi()).isEqualTo(600);
            assertThat(profile.derivatives()).hasSize(3);
        }

        @Test
        void refusesADocumentThatCannotBeUsedAndSaysWhy() {
            assertThatThrownBy(() -> ProfileLoader.loadProfile(
                            bag("name", "bad", "extends", "house", "crop", bag("anchor", "neatline"))))
                    .isInstanceOf(ProfileError.class)
                    .hasMessageFindingMatch("the bad profile cannot be used");
        }

        @Test
        void takesARegistryToLookTheParentsUpIn() {
            ProfileRegistry registry = ProfileRegistry.registryWith(
                    List.of(bag("name", "site", "extends", "house", "crop", bag("minimumPpi", 200.0))));
            Profile profile = ProfileLoader.loadProfile(bag("name", "child", "extends", "site"), registry);
            assertThat(profile.crop().minimumPpi()).isEqualTo(200);
            assertThat(registry.names()).contains("child");
        }
    }

    @Nested
    class LoadAll {
        @Test
        void registersAFolderOfDocumentsInOneGo() {
            ProfileRegistry registry = ProfileLoader.loadAll(List.of(
                    bag("name", "a", "extends", "house"),
                    bag("name", "b", "extends", "a", "crop", bag("margin", "1mm"))));
            assertThat(registry.get("b").crop().bleed().top().value()).isEqualTo(1);
        }

        @Test
        void leavesTheShippedProfilesInPlace() {
            ProfileRegistry registry = ProfileLoader.loadAll(List.of(bag("name", "a")));
            assertThat(registry.names()).contains("quadrangles");
        }
    }

    @Nested
    class AProfileDrivingARealCapture {
        @Test
        void handsTheDetectionStageItsArguments() {
            Profile profile = ProfileLoader.loadProfile(
                    bag("name", "bench-test", "extends", "house", "capture", bag("sigma", 4.0, "claimedPpi", 20.0)));
            CaptureOptions options = Settings.captureOptions(profile);
            CaptureReading reading = Captures.readCapture(accession(), forDetection(options));
            assertThat(Math.abs(reading.sheet().skew().degrees() - 3)).isLessThan(1);
            assertThat(reading.scaleBar()).isPresent();
            assertThat(String.join(" ", reading.warnings())).doesNotContainPattern("against a claimed");
        }

        @Test
        void turnsAMillimetreBleedIntoThePixelsThePlannerWillUse() {
            Profile profile = ProfileLoader.loadProfile(bag(
                    "name",
                    "bleedy",
                    "extends",
                    "house",
                    "capture",
                    bag("claimedPpi", 254.0),
                    "crop",
                    bag("bleed", "10mm")));
            StageSettings settings = Settings.settingsFor(profile);
            assertThat(settings.crop().bleed().top()).isCloseTo(100, byLessThan(0.5e-6));
        }

        @Test
        void saysTheSameThingThroughTheReportAsThroughTheSettings() {
            Profile profile = ProfileLoader.loadProfile(bag("name", "reported", "extends", "quadrangles"));
            List<String> lines = Diagnostics.report(profile);
            StageSettings settings = Settings.settingsFor(profile);
            assertThat(String.join("\n", lines))
                    .containsPattern("bleed .*"
                            + NumberText.of(
                                    Num.roundHalfUp(settings.crop().bleed().top())));
        }
    }
}
