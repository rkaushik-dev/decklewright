package io.github.rkaushikdev.decklewright.qc.checks;

import static io.github.rkaushikdev.decklewright.qc.Check.runCheck;
import static io.github.rkaushikdev.decklewright.qc.checks.TestContexts.targetOver;
import static io.github.rkaushikdev.decklewright.qc.checks.WedgeSteps.STEPS_LADDER;
import static io.github.rkaushikdev.decklewright.qc.checks.WedgeSteps.lostSteps;
import static io.github.rkaushikdev.decklewright.qc.checks.WedgeSteps.wedgeStepsCheck;
import static io.github.rkaushikdev.decklewright.testing.Bench.paint;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.decklewright.detect.Patch;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.qc.Finding;
import io.github.rkaushikdev.decklewright.qc.QcContext;
import io.github.rkaushikdev.decklewright.qc.Rung;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Surface;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class WedgeStepsTest {

    static List<Rect> cells(int count) {
        List<Rect> out = new ArrayList<>();
        for (int i = 0; i < count; i += 1) {
            out.add(Rect.of(i * 20, 0, 20, 20));
        }
        return out;
    }

    static QcContext wedge(double... tones) {
        Surface surface = Surface.create(tones.length * 20, 20, Layout.GRAY);
        List<Rect> rects = cells(tones.length);
        for (int i = 0; i < tones.length; i += 1) {
            paint(surface, rects.get(i), tones[i]);
        }
        return QcContext.empty().withSurface(surface).withTarget(targetOver(rects));
    }

    @Nested
    class LostSteps {
        @Test
        void findsNoneOnAWedgeThatStepsCleanly() {
            assertThat(lostSteps(List.of(new PatchTone(20, 1), new PatchTone(80, 1), new PatchTone(140, 1))))
                    .isEmpty();
        }

        @Test
        void losesAStepCloserThanTwiceTheCombinedNoise() {
            // the two patches are 5 apart and their combined noise is hypot(2, 2), about 2.83
            List<PatchTone> tones = List.of(new PatchTone(20, 2), new PatchTone(25, 2), new PatchTone(90, 2));
            assertThat(lostSteps(tones)).containsExactly(0);
        }

        @Test
        void keepsTheSameStepOnAQuieterSensor() {
            List<PatchTone> tones = List.of(new PatchTone(20, 1), new PatchTone(25, 1), new PatchTone(90, 1));
            assertThat(lostSteps(tones)).isEmpty();
        }

        @Test
        void losesAStepUnderOneSampleValueEvenWithNoNoise() {
            assertThat(lostSteps(List.of(new PatchTone(4, 0), new PatchTone(4.5, 0), new PatchTone(5.5, 0))))
                    .containsExactly(0);
        }

        @Test
        void readsAFallingWedgeTheSameAsARisingOne() {
            assertThat(lostSteps(List.of(new PatchTone(240, 0), new PatchTone(120, 0), new PatchTone(119.5, 0))))
                    .containsExactly(1);
        }
    }

    @Nested
    class WedgeStepsCheck {
        @Test
        void givesFourStarsWhenEveryStepReadsApart() {
            Finding out = runCheck(wedgeStepsCheck(), wedge(20, 50, 80, 110, 140, 170, 200, 230));
            assertThat(out.stars()).isEqualTo(4);
            assertThat(out.measured()).isZero();
            assertThat(out.note()).isEqualTo("all 7 steps read apart");
        }

        @Test
        void countsTheStepsACrushedShadowRunsTogether() {
            Finding out = runCheck(wedgeStepsCheck(), wedge(3, 3, 3, 60, 120, 180, 230, 250));
            assertThat(out.measured()).isEqualTo(2);
            assertThat(out.stars()).isEqualTo(2);
            assertThat(out.note()).isEqualTo("2 of 7 steps run together, first between patches 1 and 2");
        }

        @Test
        void saysWhichPatchCouldNotBeRead() {
            QcContext off = wedge(20, 120, 220)
                    .withTarget(targetOver(
                            List.of(Rect.of(0, 0, 20, 20), Rect.of(20, 0, 20, 20), Rect.of(300, 0, 20, 20))));
            Finding out = runCheck(wedgeStepsCheck(), off);
            assertThat(out.stars()).isZero();
            assertThat(out.note()).isEqualTo("patch 3 of the wedge could not be read");
        }

        @Test
        void hasNothingToGradeOnAWedgeOfOnePatch() {
            Finding out = runCheck(wedgeStepsCheck(), wedge(120));
            assertThat(out.measured()).isNaN();
            assertThat(out.note()).isEqualTo("a wedge of one patch has no steps");
        }

        @Test
        void readsTheToneAndNoiseOfAPatchOverItsInnerPart() {
            QcContext context = wedge(100);
            Surface surface = context.surface().orElseThrow();
            surface.set(0, 0, 0, 255);
            PatchTone read = ToneResponse.readTone(surface, new Patch(Rect.of(0, 0, 20, 20), List.of(), List.of()))
                    .orElseThrow();
            assertThat(read.tone()).isEqualTo(100);
            assertThat(read.spread()).isZero();
        }

        @Test
        void climbsItsLadderOneLostStepAtATime() {
            assertThat(STEPS_LADDER.stream().map(Rung::limit).toList()).containsExactly(0.0, 1.0, 2.0, 3.0);
        }
    }
}
