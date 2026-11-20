package io.github.rkaushikdev.decklewright.testing;

import static io.github.rkaushikdev.decklewright.testing.Bench.bench;
import static io.github.rkaushikdev.decklewright.testing.Bench.paint;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.raster.Fill;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Surface;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The fixture against figures worked out independently: IEEE 754 double products for the noise
 * generator, and the buffer offsets a fractional paint writes at.
 */
class BenchTest {

    private static int count(Surface s, int value) {
        return (int) Arrays.stream(s.data().toArray()).filter(v -> v == value).count();
    }

    private static long sum(Surface s) {
        return Arrays.stream(s.data().toArray()).asLongStream().sum();
    }

    @Nested
    class TheDefaults {
        @Test
        void areTheOnesTheDetectionTestsWereWrittenAgainst() {
            Bench.Options o = Bench.Options.defaults();
            assertThat(o.width()).isEqualTo(200);
            assertThat(o.height()).isEqualTo(160);
            assertThat(o.bed()).isEqualTo(38);
            assertThat(o.paper()).isEqualTo(214);
            assertThat(o.degrees()).isEqualTo(0);
            assertThat(o.halfWidth()).isEqualTo(62);
            assertThat(o.halfHeight()).isEqualTo(44);
            assertThat(o.centreX()).isEmpty();
            assertThat(o.centreY()).isEmpty();
            assertThat(o.noise()).isEqualTo(0);
            assertThat(o.layout()).isEqualTo(Layout.GRAY);
        }

        @Test
        void makeAnEightBitGrayBedWithASquareSheetInTheMiddle() {
            Surface s = bench();
            assertThat(s.toJson()).isEqualTo(Map.of("width", 200.0, "height", 160.0, "layout", "gray", "depth", 8.0));
            // Columns 38 to 162 and rows 36 to 124, both ends included.
            assertThat(count(s, 214)).isEqualTo(125 * 89);
            assertThat(count(s, 38)).isEqualTo(200 * 160 - 125 * 89);
            assertThat(s.get(38, 36)).isEqualTo(214);
            assertThat(s.get(37, 36)).isEqualTo(38);
            assertThat(s.get(162, 124)).isEqualTo(214);
            assertThat(s.get(162, 125)).isEqualTo(38);
            assertThat(s.get(100, 80)).isEqualTo(214);
        }
    }

    @Nested
    class TheNoise {
        @Test
        void stepsTheGeneratorInDoubleArithmetic() {
            int seed = Bench.SEED;
            int[] steps = new int[6];
            for (int i = 0; i < steps.length; i += 1) {
                seed = Bench.nextSeed(seed);
                steps[i] = seed;
            }
            assertThat(steps).containsExactly(1538631380, 1092931584, 1410121728, 737013760, 1399808000, 900468736);
            // Exact integer arithmetic gives a different first step, so the rounding is what is being tested.
            assertThat((Bench.SEED * 1103515245L + 12345L) & 0x7fffffffL).isEqualTo(1538631381L);
        }

        @Test
        void jittersOnlyTheBed() {
            Surface s = bench(Bench.Options.defaults().withNoise(10));
            assertThat(Arrays.copyOf(s.row(0).toArray(), 12))
                    .containsExactly(48, 31, 43, 35, 45, 41, 39, 30, 37, 33, 41, 36);
            assertThat(sum(s)).isEqualTo(3174065);
            assertThat(count(s, 214)).isEqualTo(125 * 89);
            assertThat(Arrays.stream(s.data().toArray())).allMatch(v -> v == 214 || (v >= 28 && v <= 48));
        }

        @Test
        void matchesKnownPixelsOnATurnedNoisySheet() {
            Surface s = bench(Bench.Options.defaults().withDegrees(-8).withNoise(9));
            assertThat(Arrays.copyOf(s.row(0).toArray(), 8)).containsExactly(47, 47, 30, 46, 34, 36, 40, 31);
            assertThat(sum(s)).isEqualTo(3137521);
        }
    }

    @Nested
    class TheSheet {
        @Test
        void turnsByTheAngleAskedFor() {
            Surface s = bench(Bench.Options.defaults().withDegrees(3));
            assertThat(count(s, 214)).isEqualTo(10909);
            assertThat(sum(s)).isEqualTo(3135984);
        }

        @Test
        void sitsWhereItIsPutAndAtTheSizeAskedFor() {
            Surface s = bench(Bench.Options.defaults()
                    .withHalfWidth(50)
                    .withHalfHeight(34)
                    .withCentre(120, 100));
            // Columns 70 to 170 and rows 66 to 134, both ends included.
            assertThat(count(s, 214)).isEqualTo(101 * 69);
            assertThat(s.get(70, 66)).isEqualTo(214);
            assertThat(s.get(69, 66)).isEqualTo(38);
            assertThat(s.get(170, 134)).isEqualTo(214);
            assertThat(s.get(170, 135)).isEqualTo(38);
        }

        @Test
        void takesItsTonesAndTheCaptureShapeFromTheOptions() {
            Surface s = bench(Bench.Options.defaults()
                    .withWidth(40)
                    .withHeight(30)
                    .withBed(10)
                    .withPaper(250)
                    .withHalfWidth(5)
                    .withHalfHeight(5)
                    .withCentreX(10)
                    .withCentreY(10)
                    .withLayout(Layout.RGB));
            assertThat(s.toJson()).isEqualTo(Map.of("width", 40.0, "height", 30.0, "layout", "rgb", "depth", 8.0));
            assertThat(s.getPixel(10, 10)).containsExactly(250, 250, 250);
            assertThat(s.getPixel(30, 20)).containsExactly(10, 10, 10);
            assertThat(count(s, 250)).isEqualTo(11 * 11 * 3);
        }

        @Test
        void holdsATonePastTheRangeAtTheEnds() {
            Surface s = bench(Bench.Options.defaults().withBed(-20).withPaper(300.7));
            assertThat(s.get(0, 0)).isEqualTo(0);
            assertThat(s.get(100, 80)).isEqualTo(255);
        }
    }

    @Nested
    class Painting {
        @Test
        void fillsAWholePixelBlockAndHandsBackTheSameSurface() {
            Surface s = Surface.create(4, 3, Layout.GRAY);
            Surface painted = paint(s, Rect.of(1, 1, 2, 2), 9);
            assertThat(painted).isSameAs(s);
            assertThat(s.data().toArray()).containsExactly(0, 0, 0, 0, 0, 9, 9, 0, 0, 9, 9, 0);
        }

        @Test
        void clipsABlockToTheCapture() {
            Surface s = Surface.create(3, 2, Layout.GRAY);
            paint(s, Rect.of(-5, 1, 20, 9), 4);
            assertThat(s.data().toArray()).containsExactly(0, 0, 0, 4, 4, 4);
        }

        @Test
        void paintsAColourOneValuePerChannel() {
            Surface s = Surface.create(2, 1, Layout.RGB);
            paint(s, s.bounds(), 10, 20, 30);
            assertThat(s.data().toArray()).containsExactly(10, 20, 30, 10, 20, 30);
            paint(s, Rect.of(1, 0, 1, 1), Fill.uniform(7));
            assertThat(s.getPixel(1, 0)).containsExactly(7, 7, 7);
        }

        @Test
        void refusesAColourWithTheWrongChannelCount() {
            Surface s = Surface.create(2, 1, Layout.RGB);
            assertThatThrownBy(() -> paint(s, s.bounds(), 1, 2))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("wrong number of channels");
            assertThatThrownBy(() -> paint(s, Rect.of(0.5, 0, 1, 1), 1, 2))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("wrong number of channels");
        }

        @Test
        void writesAFractionalBlockAtItsComputedSampleOffsets() {
            // Rows 0.5 and 1.5 of a four wide capture start at offsets 2 and 6, so columns 1 and 2
            // land on offsets 3, 4, 7 and 8, which are pixels in rows zero, one and two.
            Surface s = Surface.create(4, 4, Layout.GRAY);
            paint(s, Rect.of(1, 0.5, 2, 2), 9);
            assertThat(s.data().toArray()).containsExactly(0, 0, 0, 9, 9, 0, 0, 9, 9, 0, 0, 0, 0, 0, 0, 0);

            // A third of a pixel across three channels is a whole sample offset of one.
            Surface colour = Surface.create(4, 4, Layout.RGB);
            paint(colour, Rect.of(1.0 / 3, 0, 1, 1), 7, 8, 9);
            assertThat(Arrays.copyOf(colour.data().toArray(), 5)).containsExactly(0, 7, 8, 9, 0);
        }

        @Test
        void leavesTheStepsOfAFractionalWedgeThatStartBetweenPixelsUnpainted() {
            Surface s = bench();
            double step = 80.0 / 6;
            for (int i = 0; i < 6; i += 1) {
                paint(s, Rect.of(12 + i * step, 12, step, 16), 100 + i);
            }
            Map<Integer, Integer> painted = new LinkedHashMap<>();
            for (int tone = 100; tone < 106; tone += 1) {
                painted.put(tone, count(s, tone));
            }
            assertThat(painted)
                    .containsExactly(
                            Map.entry(100, 14 * 16),
                            Map.entry(101, 0),
                            Map.entry(102, 0),
                            Map.entry(103, 14 * 16),
                            Map.entry(104, 0),
                            Map.entry(105, 0));
        }
    }

    @Nested
    class TheOptions {
        @Test
        void changeOneThingAtATime() {
            Bench.Options o = Bench.Options.defaults();
            assertThat(o.withWidth(7).width()).isEqualTo(7);
            assertThat(o.withHeight(8).height()).isEqualTo(8);
            assertThat(o.withBed(1).bed()).isEqualTo(1);
            assertThat(o.withPaper(2).paper()).isEqualTo(2);
            assertThat(o.withDegrees(3).degrees()).isEqualTo(3);
            assertThat(o.withHalfWidth(4).halfWidth()).isEqualTo(4);
            assertThat(o.withHalfHeight(5).halfHeight()).isEqualTo(5);
            assertThat(o.withCentreX(6).centreX()).isEqualTo(OptionalDouble.of(6));
            assertThat(o.withCentreY(7).centreY()).isEqualTo(OptionalDouble.of(7));
            assertThat(o.withNoise(9).noise()).isEqualTo(9);
            assertThat(o.withLayout(Layout.RGBA).layout()).isEqualTo(Layout.RGBA);
            assertThat(o.withNoise(9).withLayout(Layout.RGB))
                    .isEqualTo(new Bench.Options(
                            200,
                            160,
                            38,
                            214,
                            0,
                            62,
                            44,
                            OptionalDouble.empty(),
                            OptionalDouble.empty(),
                            9,
                            Layout.RGB));
        }
    }
}
