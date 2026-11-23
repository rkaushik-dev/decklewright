package io.github.rkaushikdev.decklewright.qc.checks;

import static io.github.rkaushikdev.decklewright.qc.Check.runCheck;
import static io.github.rkaushikdev.decklewright.qc.checks.TestContexts.LIMITS;
import static io.github.rkaushikdev.decklewright.qc.checks.TestContexts.targetOver;
import static io.github.rkaushikdev.decklewright.qc.checks.ToneResponse.TONE_LADDER;
import static io.github.rkaushikdev.decklewright.qc.checks.ToneResponse.toneErrors;
import static io.github.rkaushikdev.decklewright.qc.checks.ToneResponse.toneOf;
import static io.github.rkaushikdev.decklewright.qc.checks.ToneResponse.toneResponseCheck;
import static io.github.rkaushikdev.decklewright.testing.Bench.paint;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.QcError;
import io.github.rkaushikdev.decklewright.detect.Patch;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.profile.Fields;
import io.github.rkaushikdev.decklewright.profile.Profile;
import io.github.rkaushikdev.decklewright.qc.Check;
import io.github.rkaushikdev.decklewright.qc.Finding;
import io.github.rkaushikdev.decklewright.qc.QcContext;
import io.github.rkaushikdev.decklewright.qc.QcRunner;
import io.github.rkaushikdev.decklewright.raster.Fill;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.testing.Wedges;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ToneResponseTest {

    private static final double[] WEDGE = {40, 120, 200, 250};

    static QcContext aimedAt(List<Double> aims, double tolerance) {
        return aimedAt(8, aims, tolerance);
    }

    static QcContext aimedAt(int depth, List<Double> aims, double tolerance) {
        return QcContext.empty()
                .withSurface(Wedges.paintedAt(depth, WEDGE))
                .withTarget(Wedges.target(WEDGE.length))
                .withLimits(LIMITS.withTone(aims, tolerance));
    }

    static QcContext aimedAt(Double... aims) {
        return aimedAt(List.of(aims), 6);
    }

    @Nested
    class ToneResponseCheck {
        @Test
        void givesFourStarsToAWedgeOnItsAims() {
            Finding out = runCheck(toneResponseCheck(), aimedAt(40.0, 120.0, 200.0, 250.0));
            assertThat(out.stars()).isEqualTo(4);
            assertThat(out.measured()).isZero();
        }

        @Test
        void gradesTheWorstPatchAndSaysWhichWayItIsOff() {
            Finding out = runCheck(toneResponseCheck(), aimedAt(40.0, 124.0, 198.0, 250.0));
            assertThat(out.measured()).isEqualTo(4);
            assertThat(out.stars()).isEqualTo(3);
            assertThat(out.note()).isEqualTo("patch 2 reads 4.0 dark of its aim across 4 patches, against 6");
        }

        @Test
        void readsTheAimsBackwardsForAWedgeLaidTheOtherWayRound() {
            Finding out = runCheck(toneResponseCheck(), aimedAt(250.0, 200.0, 118.0, 40.0));
            assertThat(out.stars()).isEqualTo(4);
            assertThat(out.note()).startsWith("patch 2 reads 2.0 light");
        }

        @Test
        void givesNoStarsPastTheProfileTolerance() {
            List<Double> aims = List.of(40.0, 130.0, 200.0, 250.0);
            assertThat(runCheck(toneResponseCheck(), aimedAt(aims, 6)).stars()).isZero();
            assertThat(runCheck(toneResponseCheck(), aimedAt(aims, 12)).stars()).isEqualTo(2);
        }

        @Test
        void readsASixteenBitWedgeAgainstAimsWrittenForEightBits() {
            Finding out = runCheck(toneResponseCheck(), aimedAt(16, List.of(40.0, 124.0, 198.0, 250.0), 6));
            assertThat(out.measured()).isCloseTo(4, org.assertj.core.api.Assertions.byLessThan(0.01));
            assertThat(out.stars()).isEqualTo(3);
        }

        @Test
        void declinesWhenTheProfileSetsNoAims() {
            Finding out = runCheck(toneResponseCheck(), aimedAt(List.of(), 6));
            assertThat(out.ran()).isFalse();
            assertThat(out.note()).isEqualTo("the profile sets no tone aims");
        }

        @Test
        void declinesWhenTheAimsDoNotCoverTheWedge() {
            Finding out = runCheck(toneResponseCheck(), aimedAt(40.0, 120.0, 200.0));
            assertThat(out.ran()).isFalse();
            assertThat(out.note()).isEqualTo("the wedge has 4 patches and the profile aims 3");
        }

        @Test
        void saysWhichPatchCouldNotBeRead() {
            QcContext offTheCapture = aimedAt(40.0, 120.0, 200.0, 250.0)
                    .withTarget(targetOver(List.of(
                            Rect.of(0, 0, 30, 30),
                            Rect.of(400, 0, 30, 30),
                            Rect.of(60, 0, 30, 30),
                            Rect.of(90, 0, 30, 30))));
            Finding out = runCheck(toneResponseCheck(), offTheCapture);
            assertThat(out.ran()).isTrue();
            assertThat(out.stars()).isZero();
            assertThat(out.note()).isEqualTo("patch 2 of the wedge could not be read");
        }
    }

    @Nested
    class Readings {
        @Test
        void giveTheSignedErrorPerPatchInReadingOrder() {
            assertThat(toneErrors(List.of(42.0, 118.0, 200.0), List.of(40.0, 120.0, 200.0)))
                    .containsExactly(2.0, -2.0, 0.0);
            assertThat(toneErrors(List.of(42.0, 118.0, 200.0), List.of(200.0, 120.0, 40.0)))
                    .containsExactly(2.0, -2.0, 0.0);
        }

        @Test
        void refuseAimsThatDoNotCoverTheWedge() {
            assertThatThrownBy(() -> toneErrors(List.of(1.0, 2.0), List.of(1.0)))
                    .isInstanceOf(QcError.class)
                    .hasMessage("the wedge has 2 patches and the profile aims 1");
        }

        @Test
        void takeTheToneOfAColourPatchAsTheMeanOfItsChannels() {
            Surface surface = Surface.create(30, 30, Layout.RGB);
            paint(surface, Rect.of(0, 0, 30, 30), Fill.perChannel(100, 110, 120));
            assertThat(toneOf(surface, new Patch(Rect.of(0, 0, 30, 30), List.of(), List.of())))
                    .hasValue(110);
        }

        @Test
        void climbTheLadderBestFirst() {
            assertThat(TONE_LADDER.stream().map(rung -> rung.stars()).toList()).containsExactly(4, 3, 2, 1);
            assertThatThrownBy(() -> TONE_LADDER.add(TONE_LADDER.get(0)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class InTheStandardRun {
        private final Profile aimed =
                Fields.parseProfile(Map.of("name", "wedge", "qc", Map.of("toneAims", List.of(40, 120, 200, 250))));
        private final Profile plain = Fields.parseProfile(Map.of("name", "plain"));

        private List<String> names(List<Check> checks) {
            return checks.stream().map(Check::name).toList();
        }

        @Test
        void comeStraightAfterGrayBalanceForAProfileThatAimsItsWedge() {
            List<String> names = names(QcRunner.checksFor(aimed, QcRunner.standardChecks()));
            assertThat(names).hasSize(QcRunner.standardChecks().size() + 1);
            assertThat(names.get(names.indexOf("grayBalance") + 1)).isEqualTo("toneResponse");
        }

        @Test
        void goOnTheEndOfASetWithNoGrayBalance() {
            List<String> names = names(QcRunner.checksFor(aimed, List.of(SamplingAndSkew.skewCheck())));
            assertThat(names).containsExactly("skew", "toneResponse");
        }

        @Test
        void areLeftOutForAProfileWithNoAims() {
            List<Check> standard = QcRunner.standardChecks();
            assertThat(QcRunner.checksFor(plain, standard)).isSameAs(standard);
        }

        @Test
        void areNotAddedTwice() {
            List<Check> already = List.of(toneResponseCheck());
            assertThat(QcRunner.checksFor(aimed, already)).isSameAs(already);
        }
    }
}
