package io.github.rkaushikdev.decklewright.raster;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.decklewright.geom.Angle;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.geom.Size;
import io.github.rkaushikdev.decklewright.raster.ops.Crops;
import io.github.rkaushikdev.decklewright.raster.ops.Orientation;
import io.github.rkaushikdev.decklewright.raster.ops.PadMode;
import io.github.rkaushikdev.decklewright.raster.ops.PadOptions;
import io.github.rkaushikdev.decklewright.raster.ops.PaddedCrop;
import io.github.rkaushikdev.decklewright.raster.ops.Padding;
import io.github.rkaushikdev.decklewright.raster.ops.ResampleOptions;
import io.github.rkaushikdev.decklewright.raster.ops.Resampling;
import io.github.rkaushikdev.decklewright.raster.ops.RotateOptions;
import io.github.rkaushikdev.decklewright.raster.ops.Rotated;
import io.github.rkaushikdev.decklewright.raster.ops.Rotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The layer as the rest of the tree sees it, through the public types of its two packages.
 */
class IndexTest {

    /** Every public type of the layer that holds functions or constants. */
    private static final List<Class<?>> LAYER = List.of(
            Layouts.class,
            Sampler.class,
            Kernels.class,
            Histogram.class,
            IntegralImage.class,
            Stats.class,
            Conversions.class,
            Crops.class,
            Padding.class,
            Orientation.class,
            Resampling.class,
            Rotation.class);

    /**
     * A stand in for a capture: a grey bed with a lighter sheet on it, turned by a degree and a half
     * so nothing lines up with the pixel grid.
     */
    private static Surface capture() {
        Surface s = Surface.create(80, 60, Layout.RGB, 8, 30);
        for (int y = 10; y < 50; y += 1) {
            for (int x = 12; x < 68; x += 1) {
                s.setPixel(x, y, 220, 215, 205);
            }
        }
        return Rotation.rotate(
                        s,
                        Angle.degrees(1.5),
                        RotateOptions.defaults().withExpand(false).withEdge(EdgeMode.CLAMP))
                .surface();
    }

    private static boolean exportsFunction(String name) {
        return LAYER.stream()
                .flatMap(type -> Arrays.stream(type.getMethods()))
                .anyMatch(method -> method.getName().equals(name) && Modifier.isStatic(method.getModifiers()));
    }

    @Nested
    class ThePublicTypes {
        @Test
        void exportsTheTwoRegionClassesAndTheirBase() {
            for (Class<?> type : List.of(Surface.class, SurfaceView.class, Raster.class)) {
                assertThat(Modifier.isPublic(type.getModifiers())).isTrue();
            }
            assertThat(Raster.class.getPermittedSubclasses())
                    .containsExactlyInAnyOrder(Surface.class, SurfaceView.class);
        }

        @Test
        void exportsTheLayoutTable() {
            assertThat(Layouts.LAYOUTS).contains(Layout.RGBA);
            assertThat(Layout.RGB.channelCount()).isEqualTo(3);
            assertThat(Layouts.maxValue(16)).isEqualTo(65535);
        }

        @Test
        void exportsEveryOperationTheStagesAboveUse() {
            for (String name : List.of(
                    "crop",
                    "cropPadded",
                    "pad",
                    "resample",
                    "rotate",
                    "deskew",
                    "toGray",
                    "toDepth",
                    "histogramOf",
                    "integralOf",
                    "channelStats",
                    "kernelFor",
                    "samplerFor")) {
                assertThat(exportsFunction(name)).as(name).isTrue();
            }
        }

        @Test
        void holdsNoNullConstants() throws IllegalAccessException {
            for (Class<?> type : Stream.concat(
                            LAYER.stream(),
                            Stream.of(PadMode.class, PadOptions.class, ResampleOptions.class, RotateOptions.class))
                    .toList()) {
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
    class AShortRunThroughTheLayer {
        @Test
        void cropsDeskewsAndReducesWithoutLeavingTheRange() {
            Surface raw = capture();
            PaddedCrop cut = Crops.cropPadded(raw, Rect.of(8, 6, 64, 48), Fill.uniform(0));
            assertThat(cut.surface().size().toJson()).isEqualTo(Map.of("width", 64.0, "height", 48.0));

            Rotated straight = Rotation.deskew(
                    cut.surface(), Angle.degrees(1.5), RotateOptions.defaults().withExpand(false));
            assertThat(straight.surface().size().toJson()).isEqualTo(Map.of("width", 64.0, "height", 48.0));

            Surface small = Resampling.resample(
                    straight.surface(),
                    Size.of(16, 12),
                    ResampleOptions.defaults().withKernel("catmullRom"));
            assertThat(small.size().toJson()).isEqualTo(Map.of("width", 16.0, "height", 12.0));
            for (int value : small.data().toArray()) {
                assertThat(value).isGreaterThanOrEqualTo(0);
                assertThat(value).isLessThanOrEqualTo(255);
            }
        }

        @Test
        void keepsTheSheetBrighterThanTheBedAllTheWayDown() {
            Surface raw = capture();
            Surface gray = Conversions.toGray(raw);
            ChannelStats bed = Stats.channelStats(new SurfaceView(gray, Rect.of(0, 0, 8, 8)), 0);
            ChannelStats sheet = Stats.channelStats(new SurfaceView(gray, Rect.of(30, 25, 8, 8)), 0);
            assertThat(sheet.mean()).isGreaterThan(bed.mean() + 100);

            Surface thumb = Resampling.fitLongestSide(
                    gray, 20, ResampleOptions.defaults().withKernel("box"));
            assertThat(Math.max(thumb.width(), thumb.height())).isEqualTo(20);
            ChannelStats thumbStats = Stats.channelStats(thumb, 0);
            assertThat(thumbStats.max()).isGreaterThan(thumbStats.min() + 100);
        }

        @Test
        void findsAThresholdBetweenBedAndSheetFromTheHistogramAlone() {
            Surface gray = Conversions.toGray(capture());
            double threshold = Histogram.histogramOf(gray).otsu();
            assertThat(threshold).isGreaterThan(30);
            assertThat(threshold).isLessThan(220);
        }

        @Test
        void promotesToSixteenBitsAndBackWithoutDrift() {
            Surface raw = capture();
            Surface round = Conversions.toDepth(Conversions.toDepth(raw, 16), 8);
            assertThat(Stats.difference(raw, round).peak()).isEqualTo(0);
        }
    }
}
