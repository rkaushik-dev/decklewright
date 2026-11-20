package io.github.rkaushikdev.decklewright.qc;

import static io.github.rkaushikdev.decklewright.qc.QcRunner.REQUIRED;
import static io.github.rkaushikdev.decklewright.qc.QcRunner.contextFor;
import static io.github.rkaushikdev.decklewright.qc.QcRunner.runBatch;
import static io.github.rkaushikdev.decklewright.qc.QcRunner.runQc;
import static io.github.rkaushikdev.decklewright.qc.QcRunner.standardChecks;
import static io.github.rkaushikdev.decklewright.qc.QcRunner.withoutChecks;
import static io.github.rkaushikdev.decklewright.testing.Bench.bench;
import static io.github.rkaushikdev.decklewright.testing.Bench.paint;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.QcError;
import io.github.rkaushikdev.decklewright.detect.CaptureOptions;
import io.github.rkaushikdev.decklewright.detect.Captures;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.profile.ProfileRegistry;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.testing.Bench;
import io.github.rkaushikdev.decklewright.util.Json;
import io.github.rkaushikdev.decklewright.util.Num;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RunnerTest {

    private static final ProfileRegistry REGISTRY = new ProfileRegistry();

    /** A capture off the stand with the sheet turned by the given degrees, and the wedge beside it or not. */
    static Surface accession(double degrees, boolean wedge) {
        Surface surface = bench(Bench.Options.defaults()
                .withWidth(300)
                .withHeight(240)
                .withDegrees(degrees)
                .withHalfWidth(80)
                .withHalfHeight(56)
                .withCentre(170, 130)
                .withLayout(Layout.RGB));
        if (wedge) {
            for (int i = 0; i < 8; i += 1) {
                double tone = 20 + Num.roundHalfUp((i / 7.0) * 210);
                paint(surface, Rect.of(14 + i * 10, 12, 10, 14), tone);
            }
        }
        paint(surface, Rect.of(20, 220, 120, 7), 190);
        return surface;
    }

    /** A clean capture graded against the house profile at 400 ppi. */
    static QcInput args() {
        return args("house", 0, true, 400);
    }

    static QcInput args(String profile, double degrees, boolean wedge, double ppi) {
        Surface surface = accession(degrees, wedge);
        return QcInput.of(
                        surface,
                        Captures.readCapture(surface, CaptureOptions.defaults().withSigma(4)),
                        REGISTRY.get(profile))
                .withSampling(Sampling.of(ppi));
    }

    static QcInput turned(double degrees) {
        return args("house", degrees, true, 400);
    }

    @Nested
    class StandardChecks {
        @Test
        void holdsEveryCheckTheLabRunsOnceEach() {
            List<String> names = standardChecks().stream().map(Check::name).toList();
            assertThat(names)
                    .containsExactly(
                            "sampling",
                            "skew",
                            "skewAgreement",
                            "illumination",
                            "grayBalance",
                            "noise",
                            "clipping",
                            "sharpness");
            assertThat(new HashSet<>(names)).hasSize(names.size());
        }

        @Test
        void namesTheThreeADeliveryCannotBeGradedWithout() {
            assertThat(new ArrayList<>(REQUIRED)).isEqualTo(List.of("sampling", "skew", "clipping"));
        }
    }

    @Nested
    class ContextFor {
        @Test
        void gathersWhatTheChecksAskFor() {
            QcContext context = contextFor(args());
            assertThat(context.surface()).isPresent();
            assertThat(context.sheet().orElseThrow().width()).isGreaterThan(100);
            assertThat(context.skew()).isPresent();
            assertThat(context.limits().orElseThrow().sampling().x()).isEqualTo(400);
            assertThat(context.spread().orElseThrow()).isGreaterThanOrEqualTo(0);
        }

        @Test
        void takesAMeasuredFrequencyOverTheOneTheReadingCarried() {
            assertThat(contextFor(args("house", 0, true, 800))
                            .sampling()
                            .orElseThrow()
                            .x())
                    .isEqualTo(800);
        }

        @Test
        void carriesANullFrequencyRatherThanInventingOne() {
            QcContext context = contextFor(args().withoutSampling());
            assertThat(context.sampling()).isEmpty();
        }
    }

    @Nested
    class RunQc {
        @Test
        void runsEveryCheckAndReportsALevel() {
            QcResult out = runQc(args());
            assertThat(out.findings()).hasSize(8);
            assertThat(out.stars()).isGreaterThanOrEqualTo(0);
            assertThat(out.wanted()).isEqualTo(3);
        }

        @Test
        void passesACleanCapture() {
            QcResult out = runQc(args());
            assertThat(out.missing()).isEqualTo(List.of());
            assertThat(out.passed()).isTrue();
            // plain data all the way down, so it reads back as it was written
            assertThat(Json.parse(Json.stringify(out))).isEqualTo(out.toJson());
            assertThat(out.toJson().keySet())
                    .containsExactly("findings", "stars", "worst", "missing", "passed", "wanted");
        }

        @Test
        void failsACaptureThatIsWellOffSquare() {
            QcResult out = runQc(turned(4));
            assertThat(out.passed()).isFalse();
            assertThat(out.worst()).contains("skew");
        }

        @Test
        void doesNotGradeACaptureWithNoFrequency() {
            QcResult out = runQc(args().withoutSampling());
            assertThat(out.missing()).contains("sampling");
            assertThat(out.stars()).isEqualTo(0);
        }

        @Test
        void stillGradesACaptureWithNoColourTarget() {
            QcResult out = runQc(args("house", 0, false, 400));
            assertThat(out.missing()).isEqualTo(List.of());
            assertThat(out.findings().stream()
                            .filter(finding -> finding.name().equals("grayBalance"))
                            .findFirst()
                            .map(Finding::ran))
                    .contains(false);
        }

        @Test
        void measuresAgainstTheLevelTheProfileAskedFor() {
            QcResult strict = runQc(args("quadrangles", 0, true, 600));
            assertThat(strict.wanted()).isEqualTo(4);
        }

        @Test
        void takesASetOfChecksOfItsOwn() {
            QcResult out = runQc(
                    args(),
                    QcOptions.defaults().withChecks(withoutChecks(standardChecks(), List.of("sharpness", "noise"))));
            assertThat(out.findings()).hasSize(6);
        }

        @Test
        void refusesASetWithTheSameCheckTwice() {
            List<Check> twice = new ArrayList<>(standardChecks());
            twice.add(standardChecks().get(0));
            QcInput input = args();
            assertThatThrownBy(() -> runQc(input, QcOptions.defaults().withChecks(twice)))
                    .isInstanceOf(QcError.class)
                    .hasMessageFindingMatch("is in the set twice");
        }

        @Test
        void takesAnotherListOfRequiredChecks() {
            QcResult out = runQc(args().withoutSampling(), QcOptions.defaults().withRequired(List.of("skew")));
            assertThat(out.missing()).isEqualTo(List.of());
        }
    }

    @Nested
    class RunBatch {
        @Test
        void runsTheSetOverSeveralItems() {
            BatchResult out = runBatch(List.of(args(), turned(4)));
            assertThat(out.results()).hasSize(2);
            assertThat(out.passed()).isEqualTo(1);
            assertThat(out.failed()).isEqualTo(1);
            assertThat(out.toJson().keySet()).containsExactly("results", "stars", "passed", "failed");
        }

        @Test
        void gradesTheBatchAtItsWorstItem() {
            BatchResult out = runBatch(List.of(args(), turned(4)));
            assertThat(out.stars())
                    .isEqualTo(out.results().stream()
                            .mapToInt(QcResult::stars)
                            .min()
                            .orElseThrow());
        }

        @Test
        void gradesAnEmptyBatchAtNothing() {
            assertThat(runBatch(List.of()).stars()).isEqualTo(0);
        }
    }

    @Nested
    class TheReportReadsOffTheSameFindings {
        @Test
        void saysTheSameLevelTheRunnerDid() {
            QcResult out = runQc(turned(4));
            ReportOptions options =
                    ReportOptions.defaults().withWanted(out.wanted()).withItem("q17");
            assertThat(Reports.oneLine(out.findings(), options)).containsPattern("held down by skew");
        }
    }
}
