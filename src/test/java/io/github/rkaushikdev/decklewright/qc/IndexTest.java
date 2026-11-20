package io.github.rkaushikdev.decklewright.qc;

import static io.github.rkaushikdev.decklewright.qc.Grading.gradeCapture;
import static io.github.rkaushikdev.decklewright.qc.Grading.gradingToJson;
import static io.github.rkaushikdev.decklewright.qc.Grading.reportOn;
import static io.github.rkaushikdev.decklewright.testing.Bench.bench;
import static io.github.rkaushikdev.decklewright.testing.Bench.paint;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;

import io.github.rkaushikdev.decklewright.detect.CaptureOptions;
import io.github.rkaushikdev.decklewright.detect.Captures;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.profile.ProfileRegistry;
import io.github.rkaushikdev.decklewright.qc.checks.GrayBalance;
import io.github.rkaushikdev.decklewright.qc.checks.Illumination;
import io.github.rkaushikdev.decklewright.qc.checks.Noise;
import io.github.rkaushikdev.decklewright.qc.checks.SamplingAndSkew;
import io.github.rkaushikdev.decklewright.qc.checks.Sharpness;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.testing.Bench;
import io.github.rkaushikdev.decklewright.util.Json;
import io.github.rkaushikdev.decklewright.util.Num;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Quality control as the rest of the tree sees it, through the public types of its two packages.
 */
class IndexTest {

    private static final ProfileRegistry REGISTRY = new ProfileRegistry();

    /** Every public type of the layer that holds functions or constants. */
    private static final List<Class<?>> LAYER = List.of(
            Grading.class,
            QcRunner.class,
            Check.class,
            Levels.class,
            Reports.class,
            Patches.class,
            SamplingAndSkew.class,
            Illumination.class,
            GrayBalance.class,
            Noise.class,
            Sharpness.class);

    private static final Pattern FINDING_LINE = Pattern.compile(": (\\*|not measured)");

    /**
     * A capture off the stand, with the wedge and the scale bar the lab lays beside every sheet.
     *
     * @param tint how much red to add to the whole frame
     * @param falloff how much darker the far side of the bed is
     */
    static Surface accession(double degrees, int tint, double falloff) {
        Surface surface = bench(Bench.Options.defaults()
                .withWidth(300)
                .withHeight(240)
                .withDegrees(degrees)
                .withHalfWidth(80)
                .withHalfHeight(56)
                .withCentre(170, 130)
                .withLayout(Layout.RGB));
        for (int i = 0; i < 8; i += 1) {
            double tone = 20 + Num.roundHalfUp((i / 7.0) * 210);
            paint(surface, Rect.of(14 + i * 10, 12, 10, 14), tone);
        }
        paint(surface, Rect.of(20, 220, 120, 7), 190);

        if (tint > 0) {
            for (int y = 0; y < surface.height(); y += 1) {
                for (int x = 0; x < surface.width(); x += 1) {
                    surface.set(x, y, 0, Math.min(255, surface.get(x, y, 0) + tint));
                }
            }
        }
        if (falloff > 0) {
            for (int y = 0; y < surface.height(); y += 1) {
                for (int x = 0; x < surface.width(); x += 1) {
                    double share = 1 - (x / (double) (surface.width() - 1)) * falloff;
                    for (int c = 0; c < 3; c += 1) {
                        surface.set(x, y, c, Num.roundHalfUp(surface.get(x, y, c) * share));
                    }
                }
            }
        }
        return surface;
    }

    static QcInput args(double degrees, int tint, double falloff) {
        Surface surface = accession(degrees, tint, falloff);
        return QcInput.of(
                        surface,
                        Captures.readCapture(surface, CaptureOptions.defaults().withSigma(4)),
                        REGISTRY.get("house"))
                .withSampling(Sampling.of(400));
    }

    /** A clean capture graded against the house profile at 400 ppi. */
    static QcInput args() {
        return args(0, 0, 0);
    }

    static GradingOptions item(String item) {
        return GradingOptions.defaults().withItem(item);
    }

    private static Optional<Method> function(String name) {
        return LAYER.stream()
                .flatMap(type -> Arrays.stream(type.getMethods()))
                .filter(method -> method.getName().equals(name) && Modifier.isStatic(method.getModifiers()))
                .findFirst();
    }

    private static Optional<Field> constant(String name) {
        return LAYER.stream()
                .flatMap(type -> Arrays.stream(type.getFields()))
                .filter(field -> field.getName().equals(name))
                .filter(field -> Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers()))
                .findFirst();
    }

    @Nested
    class ThePublicTypes {
        @Test
        void exportsTheOneCallAStageMakes() {
            assertThat(function("gradeCapture")).isPresent();
        }

        @Test
        void exportsEveryCheckSoALabCanRunOneOnItsOwn() throws ReflectiveOperationException {
            for (String name : List.of(
                    "samplingCheck",
                    "samplingFloorCheck",
                    "skewCheck",
                    "skewAgreementCheck",
                    "illuminationCheck",
                    "grayBalanceCheck",
                    "noiseCheck",
                    "clippingCheck",
                    "sharpnessCheck")) {
                Method factory = function(name).orElseThrow(() -> new AssertionError(name));
                Method plain = factory.getDeclaringClass().getMethod(name);
                assertThat(plain.getReturnType()).as(name).isEqualTo(Check.class);
                assertThat(((Check) plain.invoke(null)).name()).as(name).isNotEmpty();
            }
        }

        @Test
        void exportsEveryLadderBecauseTheLaddersAreTheDocumentation() throws IllegalAccessException {
            for (String name : List.of(
                    "SAMPLING_LADDER",
                    "SKEW_LADDER",
                    "ILLUMINATION_LADDER",
                    "GRAY_BALANCE_LADDER",
                    "NOISE_LADDER",
                    "CLIPPING_LADDER",
                    "SHARPNESS_LADDER")) {
                Field field = constant(name).orElseThrow(() -> new AssertionError(name));
                assertThat(field.getType()).as(name).isEqualTo(List.class);
                List<?> ladder = (List<?>) field.get(null);
                assertThat(ladder).as(name).isNotEmpty().allMatch(Rung.class::isInstance);
                assertThatThrownBy(() -> ladder.clear()).as(name).isInstanceOf(UnsupportedOperationException.class);
            }
        }

        @Test
        void exportsTheSamplerTheChecksShare() {
            assertThat(function("patchView")).isPresent();
            assertThat(Patches.PATCH_MARGIN).isGreaterThan(0);
        }

        @Test
        void doesNotLeakACheckUnderTwoNames() {
            List<String> names =
                    QcRunner.standardChecks().stream().map(Check::name).toList();
            assertThat(new HashSet<>(names)).hasSize(names.size());
        }
    }

    @Nested
    class GradeCapture {
        @Test
        void gradesAndAdvisesInOneCall() {
            GradedCapture out = gradeCapture(args());
            assertThat(out.result().findings()).hasSize(8);
            assertThat(out.result().passed()).isTrue();
            assertThat(out.advice()).isEqualTo(List.of());
            // the run's own fields first, in their own order, with the advice after them
            assertThat(out.stars()).isEqualTo(out.result().stars());
            assertThat(out.toJson().keySet())
                    .containsExactly("findings", "stars", "worst", "missing", "passed", "wanted", "advice");
            assertThat(out.toJson().get("advice")).isEqualTo(List.of());
        }

        @Test
        void saysWhatToChangeWhenACheckFallsShort() {
            GradedCapture out = gradeCapture(args(4, 0, 0));
            assertThat(out.result().passed()).isFalse();
            assertThat(String.join(" ", out.advice())).containsPattern("square the sheet");
        }

        @Test
        void saysWhichSideIsDarkNotOnlyThatTheLightingIsUneven() {
            GradedCapture out = gradeCapture(args(0, 0, 0.5));
            assertThat(String.join(" ", out.advice())).containsPattern("move a lamp");
            assertThat(String.join(" ", out.advice())).containsPattern("dark side");
        }

        @Test
        void saysWhichWayACastGoes() {
            GradedCapture out = gradeCapture(args(0, 30, 0));
            assertThat(String.join(" ", out.advice())).containsPattern("the cast is towards red");
        }

        @Test
        void advisesNothingAboutACheckThatCouldNotRun() {
            GradedCapture out = gradeCapture(args().withoutSampling());
            assertThat(out.result().missing()).contains("sampling");
            assertThat(String.join(" ", out.advice())).doesNotContainPattern("dark side");
        }

        @Test
        void takesTheSameOptionsRunQcDoes() {
            GradedCapture out = gradeCapture(
                    args(),
                    QcOptions.defaults()
                            .withChecks(QcRunner.withoutChecks(QcRunner.standardChecks(), List.of("sharpness"))));
            assertThat(out.result().findings()).hasSize(7);
        }
    }

    @Nested
    class ReportOn {
        @Test
        void opensWithTheItemAndTheLevel() {
            assertThat(reportOn(args(), item("q17")).get(0)).containsPattern("^q17: ");
        }

        @Test
        void putsTheAdviceUnderTheFindings() {
            List<String> lines = reportOn(args(4, 0, 0), item("q17"));
            List<String> findings = lines.stream()
                    .filter(line -> FINDING_LINE.matcher(line).find())
                    .toList();
            List<String> advice = lines.subList(lines.indexOf(findings.get(findings.size() - 1)) + 1, lines.size());
            assertThat(String.join(" ", advice)).containsPattern("square the sheet");
        }

        @Test
        void saysNothingAboutRemediesWhenThereIsNothingToRemedy() {
            assertThat(String.join("\n", reportOn(args()))).doesNotContainPattern("look at the capture");
        }
    }

    @Nested
    class GradingToJson {
        @Test
        void carriesTheLevelTheMeasurementsAndTheAdvice() {
            Map<String, Object> out = gradingToJson(args(4, 0, 0), item("q17"));
            assertThat(out.get("item")).isEqualTo("q17");
            assertThat(out.get("passed")).isEqualTo(false);
            assertThat(out.get("measurements")).asInstanceOf(MAP).hasSize(8);
            assertThat(out.get("advice")).asInstanceOf(LIST).isNotEmpty();
        }

        @Test
        void namesTheRequiredChecksThatCouldNotBeRun() {
            Map<String, Object> out = gradingToJson(args().withoutSampling());
            assertThat(out.get("missing")).isEqualTo(List.of("sampling"));
            assertThat(out.get("stars")).isEqualTo(0.0);
            assertThat(out.get("level")).isEqualTo(Levels.LEVEL_NAMES.get(0));
            assertThat(out.get("passed")).isEqualTo(false);
        }

        @Test
        void isPlainDataSoItCanGoStraightIntoASidecar() {
            Map<String, Object> out = gradingToJson(args());
            assertThat(Json.parse(Json.stringify(out))).isEqualTo(out);
            // the key order is what the command line prints
            assertThat(out.keySet())
                    .containsExactly(
                            "item",
                            "stars",
                            "level",
                            "wanted",
                            "passed",
                            "worst",
                            "ran",
                            "skipped",
                            "measurements",
                            "remedies",
                            "missing",
                            "advice");
        }
    }
}
