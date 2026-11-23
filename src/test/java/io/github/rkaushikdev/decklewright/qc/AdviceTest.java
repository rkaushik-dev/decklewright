package io.github.rkaushikdev.decklewright.qc;

import static io.github.rkaushikdev.decklewright.testing.Bench.paint;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.decklewright.detect.Neatline;
import io.github.rkaushikdev.decklewright.detect.Patch;
import io.github.rkaushikdev.decklewright.detect.Side;
import io.github.rkaushikdev.decklewright.detect.TargetFinding;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.profile.ProfileRegistry;
import io.github.rkaushikdev.decklewright.profile.QcLimits;
import io.github.rkaushikdev.decklewright.profile.Settings;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Surface;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AdviceTest {

    private static final QcLimits HOUSE = Settings.qcLimits(new ProfileRegistry().get("house"));

    /** A gray wedge of twenty pixel patches, one tone each, as a context the checks would read. */
    static QcContext wedge(double... tones) {
        Surface surface = Surface.create(tones.length * 20, 20, Layout.GRAY);
        List<Patch> patches = new ArrayList<>();
        for (int i = 0; i < tones.length; i += 1) {
            Rect cell = Rect.of(i * 20, 0, 20, 20);
            paint(surface, cell, tones[i]);
            patches.add(new Patch(cell, List.of(), List.of()));
        }
        return QcContext.empty()
                .withSurface(surface)
                .withTarget(new TargetFinding(Rect.empty(), patches, 0, true))
                .withLimits(HOUSE);
    }

    @Nested
    class Remedies {
        @Test
        void giveEveryStandardCheckOneOfItsOwn() {
            List<String> advised = Advice.advised();
            for (Check check : QcRunner.standardChecks()) {
                assertThat(advised).as(check.name()).contains(check.name());
            }
            assertThat(advised).contains("toneResponse", "neatline");
        }

        @Test
        void tellASitesOwnCheckToLookAtTheCapture() {
            assertThat(Advice.remedyFor("always4")).isEqualTo(Advice.NO_REMEDY).isEqualTo("look at the capture");
            assertThat(Advice.detailFor("always4", QcContext.empty())).isEmpty();
        }
    }

    @Nested
    class Details {
        @Test
        void nameTheSidesOfTheBorderThatWereNotFound() {
            QcContext context = QcContext.empty()
                    .withNeatline(new Neatline(Rect.of(0, 0, 9, 9), Map.of(), List.of(Side.BOTTOM, Side.LEFT)));
            assertThat(Advice.detailFor("neatline", context))
                    .contains("the border was not found on the bottom or left");
            QcContext whole = QcContext.empty().withNeatline(new Neatline(Rect.of(0, 0, 9, 9), Map.of(), List.of()));
            assertThat(Advice.detailFor("neatline", whole)).isEmpty();
        }

        @Test
        void sayWhichWayToMoveTheExposureForAPatchOffItsAim() {
            QcContext dark = wedge(40, 120, 200).withLimits(HOUSE.withTone(List.of(40.0, 130.0, 200.0), 6));
            assertThat(Advice.detailFor("toneResponse", dark)).contains("patch 2 reads dark, so open the exposure up");
            QcContext light = wedge(40, 120, 200).withLimits(HOUSE.withTone(List.of(200.0, 110.0, 40.0), 6));
            assertThat(Advice.detailFor("toneResponse", light))
                    .contains("patch 2 reads light, so stop the exposure down");
        }

        @Test
        void sayNothingOfToneWithoutAimsThatCoverTheWedge() {
            assertThat(Advice.detailFor("toneResponse", wedge(40, 120, 200))).isEmpty();
            assertThat(Advice.detailFor("toneResponse", wedge(40, 120).withLimits(HOUSE.withTone(List.of(1.0), 6))))
                    .isEmpty();
        }

        @Test
        void sayWhichEndOfTheWedgeRanTogether() {
            assertThat(Advice.detailFor("wedgeSteps", wedge(3, 3, 60, 120, 180, 240)))
                    .contains("the shadows run together");
            assertThat(Advice.detailFor("wedgeSteps", wedge(20, 80, 140, 252, 252)))
                    .contains("the highlights run together");
            assertThat(Advice.detailFor("wedgeSteps", wedge(20, 80, 140, 200))).isEmpty();
        }

        @Test
        void giveBothAxesOnlyWhenTheyDiffer() {
            assertThat(Advice.detailFor("samplingFloor", QcContext.empty().withSampling(new Sampling(600, 590))))
                    .contains("the capture is 600.0 ppi across and 590.0 ppi down, so check the stepper as well");
            assertThat(Advice.detailFor("sampling", QcContext.empty().withSampling(Sampling.of(600))))
                    .isEmpty();
        }

        @Test
        void sayNothingWhenWhatTheyReadIsMissing() {
            for (String check : Advice.advised()) {
                assertThat(Advice.detailFor(check, QcContext.empty())).as(check).isEmpty();
            }
        }
    }
}
