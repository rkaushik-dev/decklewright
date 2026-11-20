package io.github.rkaushikdev.decklewright.raster.ops;

import static io.github.rkaushikdev.decklewright.raster.ops.Rotation.deskew;
import static io.github.rkaushikdev.decklewright.raster.ops.Rotation.rotate;
import static io.github.rkaushikdev.decklewright.raster.ops.Rotation.rotatedBounds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.decklewright.geom.Angle;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.raster.EdgeMode;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.SampleMode;
import io.github.rkaushikdev.decklewright.raster.Surface;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RotateTest {

    private static final RotateOptions FIXED = RotateOptions.defaults().withExpand(false);
    private static final RotateOptions FIXED_CLAMPED = FIXED.withEdge(EdgeMode.CLAMP);

    static Surface block(int width, int height) {
        Surface s = Surface.create(width, height, Layout.GRAY, 8, 200);
        for (int y = 2; y < height - 2; y += 1) {
            for (int x = 2; x < width - 2; x += 1) {
                s.set(x, y, 0, 40);
            }
        }
        return s;
    }

    /** The distinct sample values, in the order they first appear. */
    static List<Integer> distinct(Surface s) {
        return Arrays.stream(s.data().toArray()).distinct().boxed().toList();
    }

    @Nested
    class RotateByWholeQuarters {
        @Test
        void doesNoResamplingAtAll() {
            Surface s = block(9, 5);
            Rotated out = rotate(s, Angle.degrees(90));
            assertThat(out.quarterTurns()).isEqualTo(1);
            assertThat(out.residual().isZero()).isTrue();
            assertThat(out.surface().size().toJson()).isEqualTo(Map.of("width", 5.0, "height", 9.0));
            assertThat(distinct(out.surface()).stream().sorted()).containsExactly(40, 200);
        }

        @Test
        void leavesAZeroAngleAloneButStillCopies() {
            Surface s = block(6, 6);
            Rotated out = rotate(s, Angle.zero());
            assertThat(out.quarterTurns()).isEqualTo(0);
            assertThat(out.surface()).isSameAs(s);
        }

        @Test
        void foldsAFullTurnBackToNothing() {
            Rotated out = rotate(block(6, 6), Angle.degrees(360));
            assertThat(out.quarterTurns()).isEqualTo(0);
            assertThat(out.residual().isZero()).isTrue();
        }
    }

    @Nested
    class RotateByASmallAngle {
        @Test
        void growsTheCanvasWhenExpanding() {
            Surface s = block(20, 20);
            Surface out = rotate(s, Angle.degrees(10)).surface();
            assertThat(out.width()).isGreaterThan(20);
            assertThat(out.height()).isGreaterThan(20);
        }

        @Test
        void keepsTheCanvasWhenToldNotToExpand() {
            Surface s = block(20, 20);
            Surface out = rotate(s, Angle.degrees(10), FIXED).surface();
            assertThat(out.size().toJson()).isEqualTo(Map.of("width", 20.0, "height", 20.0));
        }

        @Test
        void fillsTheNewCornersWithTheBackground() {
            Surface s = Surface.create(20, 20, Layout.GRAY, 8, 200);
            Surface out = rotate(s, Angle.degrees(20), RotateOptions.defaults().withBackground(7))
                    .surface();
            assertThat(out.get(0, 0)).isEqualTo(7);
            assertThat(out.get(out.width() / 2, out.height() / 2)).isEqualTo(200);
        }

        @Test
        void reportsTheTransformThatPutTheSamplesWhereTheyAre() {
            Surface s = block(20, 20);
            Rotated out = rotate(s, Angle.degrees(15));
            assertThat(out.transform().decompose().rotation().degrees()).isCloseTo(15, byLessThan(0.5e-6));
        }

        @Test
        void leavesAFlatCaptureFlat() {
            Surface flat = Surface.create(24, 24, Layout.GRAY, 8, 111);
            Surface out = rotate(flat, Angle.degrees(7), FIXED_CLAMPED).surface();
            assertThat(distinct(out)).containsExactly(111);
        }

        @Test
        void comesCloseToTheOriginalAfterTurningBack() {
            Surface s = block(30, 30);
            Surface once = rotate(s, Angle.degrees(6), FIXED_CLAMPED).surface();
            Surface back = rotate(once, Angle.degrees(-6), FIXED_CLAMPED).surface();
            int worst = 0;
            for (int y = 8; y < 22; y += 1) {
                for (int x = 8; x < 22; x += 1) {
                    worst = Math.max(worst, Math.abs(back.get(x, y) - s.get(x, y)));
                }
            }
            assertThat(worst).isLessThan(30);
        }

        @Test
        void takesTheNearestSamplerWhenAsked() {
            Surface s = block(20, 20);
            Surface out = rotate(s, Angle.degrees(12), RotateOptions.defaults().withSample(SampleMode.NEAREST))
                    .surface();
            assertThat(distinct(out)).allMatch(v -> v == 0 || v == 40 || v == 200);
        }

        @Test
        void pullsAQuarterTurnOutBeforeTheResample() {
            Surface s = block(20, 10);
            Rotated out = rotate(s, Angle.degrees(92));
            assertThat(out.quarterTurns()).isEqualTo(1);
            assertThat(out.residual().degrees()).isCloseTo(2, byLessThan(0.5e-9));
            assertThat(out.surface().height()).isGreaterThan(out.surface().width());
        }
    }

    @Nested
    class Deskew {
        @Test
        void turnsTheOtherWayFromTheMeasuredSkew() {
            Surface s = block(20, 20);
            Angle measured = Angle.degrees(3);
            Surface a = deskew(s, measured, FIXED).surface();
            Surface b = rotate(s, Angle.degrees(-3), FIXED).surface();
            assertThat(a.data().toArray()).isEqualTo(b.data().toArray());
        }
    }

    @Nested
    class RotatedBounds {
        @Test
        void agreesWithWhatRotateProduced() {
            Surface s = block(20, 14);
            Angle angle = Angle.degrees(11);
            Rect predicted = rotatedBounds(s.bounds(), angle);
            Surface out = rotate(s, angle).surface();
            assertThat(out.width()).isEqualTo((int) Math.round(predicted.width()));
            assertThat(out.height()).isEqualTo((int) Math.round(predicted.height()));
        }

        @Test
        void swapsTheExtentsOnAnOddQuarterTurn() {
            Rect out = rotatedBounds(Rect.of(0, 0, 20, 10), Angle.degrees(90));
            assertThat(out.width()).isEqualTo(10);
            assertThat(out.height()).isEqualTo(20);
        }

        @Test
        void isTheIdentityForAZeroAngle() {
            Rect bounds = Rect.of(0, 0, 20, 10);
            assertThat(rotatedBounds(bounds, Angle.zero())).isSameAs(bounds);
        }
    }
}
