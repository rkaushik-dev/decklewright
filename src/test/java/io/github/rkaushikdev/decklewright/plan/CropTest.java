package io.github.rkaushikdev.decklewright.plan;

import static io.github.rkaushikdev.decklewright.plan.CropPlanner.intoStraightFrame;
import static io.github.rkaushikdev.decklewright.plan.CropPlanner.planCrop;
import static io.github.rkaushikdev.decklewright.plan.CropPlanner.pointsIntoStraightFrame;
import static io.github.rkaushikdev.decklewright.plan.CropPlanner.straightFrame;
import static io.github.rkaushikdev.decklewright.testing.Bench.bench;
import static io.github.rkaushikdev.decklewright.testing.Bench.paint;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.decklewright.PlanError;
import io.github.rkaushikdev.decklewright.detect.CaptureOptions;
import io.github.rkaushikdev.decklewright.detect.CaptureReading;
import io.github.rkaushikdev.decklewright.detect.Captures;
import io.github.rkaushikdev.decklewright.geom.Angle;
import io.github.rkaushikdev.decklewright.geom.Point;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.profile.Profile;
import io.github.rkaushikdev.decklewright.profile.ProfileLoader;
import io.github.rkaushikdev.decklewright.profile.ProfileRegistry;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.testing.Bench;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CropTest {

    private static final ProfileRegistry REGISTRY = new ProfileRegistry();
    private static final CaptureOptions SIGMA_4 = CaptureOptions.defaults().withSigma(4);

    /**
     * A 300 by 240 bench with a 160 by 112 sheet centred at (160, 120), turned by the degrees, a rule
     * below it, and a neatline drawn on it when asked.
     */
    private static Surface accession(double degrees, boolean ruled) {
        Surface surface = bench(Bench.Options.defaults()
                .withWidth(300)
                .withHeight(240)
                .withDegrees(degrees)
                .withHalfWidth(80)
                .withHalfHeight(56)
                .withCentre(160, 120));
        paint(surface, Rect.of(20, 210, 120, 7), 190);
        if (ruled) {
            paint(surface, Rect.of(95, 78, 130, 2), 30);
            paint(surface, Rect.of(95, 155, 130, 2), 30);
            paint(surface, Rect.of(95, 78, 2, 79), 30);
            paint(surface, Rect.of(223, 78, 2, 79), 30);
        }
        return surface;
    }

    private static Surface accession() {
        return accession(0, false);
    }

    /** The corners of a 200 by 100 rect centred at (200, 150), turned by ten degrees. */
    private static List<Point> turnedCorners() {
        double radians = (10 * Math.PI) / 180;
        double cos = StrictMath.cos(radians);
        double sin = StrictMath.sin(radians);
        double[][] offsets = {{-100, -50}, {100, -50}, {100, 50}, {-100, 50}};
        return Arrays.stream(offsets)
                .map(p -> new Point(200 + p[0] * cos - p[1] * sin, 150 + p[0] * sin + p[1] * cos))
                .toList();
    }

    @Nested
    class IntoStraightFrame {
        private final Rect capture = Rect.of(0, 0, 400, 300);
        private final Rect rect = Rect.of(100, 100, 200, 100);

        @Test
        void leavesEverythingAloneWhenThereIsNoSkew() {
            Straightened out = intoStraightFrame(rect, capture, Angle.zero());
            assertThat(out.rect()).isSameAs(rect);
            assertThat(out.capture()).isSameAs(capture);
        }

        @Test
        void movesTheRectAndGrowsTheCapture() {
            Straightened out = intoStraightFrame(rect, capture, Angle.degrees(10));
            assertThat(out.capture().width()).isGreaterThan(400);
            assertThat(out.capture().left()).isEqualTo(0);
            assertThat(out.rect().approxEquals(rect)).isFalse();
        }

        @Test
        void keepsTheRectCentredWhereItWasRelativeToTheCapture() {
            Rect middle = Rect.of(150, 100, 100, 100);
            Straightened out = intoStraightFrame(middle, capture, Angle.degrees(8));
            Point before = middle.centre();
            Point after = out.rect().centre();
            assertThat(Math.abs(after.x() - out.capture().width() / 2))
                    .isCloseTo(Math.abs(before.x() - capture.width() / 2), byLessThan(0.5));
        }

        @Test
        void growsTheRectSinceAnAxisAlignedBoxOfATurnedRectIsBigger() {
            Straightened out = intoStraightFrame(rect, capture, Angle.degrees(20));
            assertThat(out.rect().area()).isGreaterThan(rect.area());
        }

        @Test
        void undoesItselfWhenTheSkewGoesTheOtherWay() {
            Straightened there = intoStraightFrame(rect, capture, Angle.degrees(6));
            Straightened back = intoStraightFrame(there.rect(), there.capture(), Angle.degrees(-6));
            assertThat(Math.abs(back.rect().centre().x() - there.capture().width() / 2))
                    .isLessThan(there.capture().width());
        }
    }

    @Nested
    class StraightFrameBehaviour {
        private final Rect capture = Rect.of(0, 0, 400, 300);

        @Test
        void isTheIdentityWhenThereIsNoSkew() {
            StraightFrame frame = straightFrame(capture, Angle.zero());
            assertThat(frame.point(10, 20)).isEqualTo(new Point(10, 20));
            assertThat(frame.capture()).isSameAs(capture);
        }

        @Test
        void growsTheCanvasAndPutsItAtTheOrigin() {
            StraightFrame frame = straightFrame(capture, Angle.degrees(10));
            assertThat(frame.capture().left()).isEqualTo(0);
            assertThat(frame.capture().top()).isEqualTo(0);
            assertThat(frame.capture().width()).isGreaterThan(400);
        }

        @Test
        void leavesTheCentreOfTheCaptureAtTheCentreOfTheNewCanvas() {
            StraightFrame frame = straightFrame(capture, Angle.degrees(12));
            Point middle = frame.point(200, 150);
            assertThat(middle.x()).isCloseTo(frame.capture().width() / 2, byLessThan(0.5e-6));
            assertThat(middle.y()).isCloseTo(frame.capture().height() / 2, byLessThan(0.5e-6));
        }
    }

    @Nested
    class PointsIntoStraightFrame {
        private final Rect capture = Rect.of(0, 0, 400, 300);

        @Test
        void keepsATurnedQuadTheSizeItReallyIs() {
            Straightened out = pointsIntoStraightFrame(turnedCorners(), capture, Angle.degrees(10));
            assertThat(out.rect().width()).isCloseTo(200, byLessThan(0.5e-6));
            assertThat(out.rect().height()).isCloseTo(100, byLessThan(0.5e-6));
        }

        @Test
        void isBiggerWhenTheBoxIsTurnedRatherThanTheCorners() {
            List<Point> corners = turnedCorners();
            Rect boxed = Rect.bounding(corners);

            Rect tight =
                    pointsIntoStraightFrame(corners, capture, Angle.degrees(10)).rect();
            Rect loose = intoStraightFrame(boxed, capture, Angle.degrees(10)).rect();
            assertThat(loose.width()).isGreaterThan(tight.width());
        }
    }

    @Nested
    class PlanCrop {
        private PlannedCrop plan(String name) {
            return plan(name, 0, false, PlanOptions.defaults());
        }

        private PlannedCrop plan(String name, double degrees) {
            return plan(name, degrees, false, PlanOptions.defaults());
        }

        private PlannedCrop plan(String name, double degrees, boolean ruled, PlanOptions options) {
            Surface surface = accession(degrees, ruled);
            Profile profile = REGISTRY.get(name);
            CaptureReading reading = Captures.readCapture(
                    surface, SIGMA_4.withExpectNeatline(profile.capture().expectNeatline()));
            return planCrop(reading, profile, surface.bounds(), Optional.of(Sampling.of(400)), options);
        }

        @Test
        void plansAStraightSheetToThePaperEdge() {
            PlannedCrop out = plan("house");
            assertThat(out.settled()).isTrue();
            assertThat(out.plan().rect().width()).isGreaterThan(150);
            assertThat(out.plan().rect().width()).isLessThan(175);
            assertThat(out.plan().deskew().isZero()).isTrue();
        }

        @Test
        void keepsABleedTheProfileAskedFor() {
            Rect plain = plan("house").plan().rect();
            Rect bled = plan("atlas-plates").plan().rect();
            assertThat(bled.width()).isGreaterThan(plain.width());
        }

        @Test
        void recordsTheSkewItWillTakeOut() {
            PlannedCrop out = plan("house", 5);
            assertThat(Math.abs(out.plan().deskew().degrees() - 5)).isLessThan(1);
        }

        @Test
        void cutsASheetAsItLiesWhenItIsInsideWhatTheProfileLeavesAlone() {
            Profile lenient = ProfileLoader.loadProfile(
                    Map.of("name", "lenient", "extends", "house", "crop", Map.of("deskewAbove", 6)), REGISTRY);
            Surface surface = accession(5, false);
            CaptureReading reading = Captures.readCapture(surface, SIGMA_4);
            PlannedCrop out =
                    planCrop(reading, lenient, surface.bounds(), Optional.of(Sampling.of(400)), PlanOptions.defaults());
            assertThat(out.plan().deskew().isZero()).isTrue();
            assertThat(out.plan().rect().width())
                    .isGreaterThan(plan("house", 5).plan().rect().width());
        }

        @Test
        void stillStraightensASheetPastWhatTheProfileLeavesAlone() {
            Profile lenient = ProfileLoader.loadProfile(
                    Map.of("name", "lenient", "extends", "house", "crop", Map.of("deskewAbove", 4)), REGISTRY);
            Surface surface = accession(5, false);
            CaptureReading reading = Captures.readCapture(surface, SIGMA_4);
            PlannedCrop out =
                    planCrop(reading, lenient, surface.bounds(), Optional.of(Sampling.of(400)), PlanOptions.defaults());
            assertThat(out.plan().deskew().degrees())
                    .isEqualTo(reading.sheet().skew().degrees());
        }

        @Test
        void plansAgainstTheStraightenedFrameNotTheRawOne() {
            Rect straight = plan("house").plan().rect();
            Rect turned = plan("house", 6).plan().rect();
            // the anchor is an axis aligned box round a turned sheet, so it is a little
            // larger, but it is the same crop and not one rotated away from it
            assertThat(Math.abs(turned.width() - straight.width())).isLessThan(30);
            assertThat(Math.abs(turned.height() - straight.height())).isLessThan(30);
        }

        @Test
        void carriesTheDetectorWarningsOntoThePlan() {
            PlannedCrop out = plan("house");
            assertThat(String.join(" ", out.plan().warnings())).containsPattern("no colour target on the bed");
        }

        @Test
        void namesTheRulesItRan() {
            assertThat(plan("house").rules()).contains("notEmpty");
        }

        @Test
        void cropsAQuadrangleToTheNeatline() {
            PlannedCrop out =
                    plan("quadrangles", 0, true, PlanOptions.defaults().withFallBackToSheet(true));
            assertThat(out.plan().rect().width()).isGreaterThan(0);
            assertThat(out.rules()).contains("anchor");
        }

        @Test
        void refusesWhenThereIsNoFrequencyToPlanAgainst() {
            Surface surface = accession();
            CaptureReading reading = Captures.readCapture(surface, SIGMA_4);
            assertThatThrownBy(() -> planCrop(reading, REGISTRY.get("house"), surface.bounds()))
                    .isInstanceOf(PlanError.class)
                    .hasMessageFindingMatch("no sampling frequency to plan against");
        }

        @Test
        void takesAMeasuredFrequencyOverTheOneTheReadingCarried() {
            Surface surface = accession();
            CaptureReading reading = Captures.readCapture(surface, SIGMA_4.withBarLength(6));
            PlannedCrop out = planCrop(reading, REGISTRY.get("house"), surface.bounds(), Sampling.of(800));
            assertThat(out.plan().sampling().x()).isEqualTo(800);
        }
    }
}
