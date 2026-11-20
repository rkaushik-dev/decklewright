package io.github.rkaushikdev.decklewright.detect;

import static io.github.rkaushikdev.decklewright.testing.Bench.bench;
import static io.github.rkaushikdev.decklewright.testing.Bench.paint;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.decklewright.codec.Codecs;
import io.github.rkaushikdev.decklewright.codec.png.Encoding;
import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.raster.EdgeMode;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.raster.ops.Crops;
import io.github.rkaushikdev.decklewright.raster.ops.RotateOptions;
import io.github.rkaushikdev.decklewright.raster.ops.Rotation;
import io.github.rkaushikdev.decklewright.testing.Bench;
import io.github.rkaushikdev.decklewright.util.Num;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The layer as the rest of the tree sees it, through the public types of its package.
 */
class IndexTest {

    private static final CaptureOptions SIGMA_4 = CaptureOptions.defaults().withSigma(4);

    /** Every public type of the layer that holds functions or constants. */
    private static final List<Class<?>> LAYER = List.of(
            Captures.class,
            Sheets.class,
            Mask.class,
            Backgrounds.class,
            Morphology.class,
            Components.class,
            Edges.class,
            Corners.class,
            QuadFit.class,
            Deskew.class,
            Neatlines.class,
            Targets.class,
            ScaleBars.class);

    /**
     * A 260 by 220 rgb bench with a 140 by 100 sheet centred at (140, 110), turned by the degrees, an
     * eight step wedge above it and a steel rule below.
     */
    private static Surface accession(double degrees) {
        Surface surface = bench(Bench.Options.defaults()
                .withWidth(260)
                .withHeight(220)
                .withDegrees(degrees)
                .withHalfWidth(70)
                .withHalfHeight(50)
                .withCentre(140, 110)
                .withLayout(Layout.RGB));
        for (int i = 0; i < 8; i += 1) {
            double tone = 20 + Num.roundHalfUp((i / 7.0) * 210);
            paint(surface, Rect.of(14 + i * 10, 12, 10, 14), tone);
        }
        paint(surface, Rect.of(20, 200, 120, 7), 190);
        return surface;
    }

    private static boolean exportsFunction(String name) {
        return LAYER.stream()
                .filter(type -> Modifier.isPublic(type.getModifiers()))
                .flatMap(type -> Arrays.stream(type.getMethods()))
                .anyMatch(method -> method.getName().equals(name) && Modifier.isStatic(method.getModifiers()));
    }

    @Nested
    class ThePublicTypes {
        @Test
        void exportsTheOneCallThePlannerMakes() {
            assertThat(exportsFunction("readCapture")).isTrue();
        }

        @Test
        void exportsEveryStepSoAFailureCanBeTakenApart() {
            for (String name : List.of(
                    "objectMask",
                    "open",
                    "close",
                    "labelComponents",
                    "fitQuad",
                    "edgeLines",
                    "measureSkew",
                    "refineCorners",
                    "findNeatline",
                    "locateTarget",
                    "findScaleBar")) {
                assertThat(exportsFunction(name)).as(name).isTrue();
            }
        }

        @Test
        void holdsNoNullConstants() throws IllegalAccessException {
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
    class ACaptureReadFromAFile {
        @Test
        void survivesARoundTripThroughPng() {
            Surface original = accession(4);
            Surface back = Codecs.readImage(Encoding.encodePng(original));
            CaptureReading reading = Captures.readCapture(back, SIGMA_4);
            assertThat(Math.abs(reading.sheet().skew().degrees() - 4)).isLessThan(1);
            assertThat(reading.target()).isPresent();
            assertThat(reading.scaleBar()).isPresent();
        }
    }

    @Nested
    class DetectAndStraighten {
        @Test
        void leavesAlmostNoSkewBehind() {
            Surface surface = accession(6);
            CaptureReading reading = Captures.readCapture(surface, SIGMA_4);
            Surface straight = Rotation.deskew(
                            surface,
                            reading.sheet().skew(),
                            RotateOptions.defaults().withExpand(false).withEdge(EdgeMode.CLAMP))
                    .surface();
            SheetFinding after =
                    Sheets.findSheet(straight, SheetOptions.defaults().withSigma(4));
            assertThat(Math.abs(after.skew().degrees())).isLessThan(1);
        }

        @Test
        void leavesTheSheetWhereTheQuadSaidItWas() {
            Surface surface = accession(0);
            CaptureReading reading = Captures.readCapture(surface, SIGMA_4);
            Rect box = reading.sheet().quad().boundingRect().outward();
            Surface cut = Crops.crop(surface, box);
            assertThat(cut.width()).isGreaterThan(135);
            assertThat(cut.width()).isLessThan(145);
            assertThat(cut.height()).isGreaterThan(95);
            assertThat(cut.height()).isLessThan(105);
        }
    }

    @Nested
    class TheStepsAgreeWithTheWhole {
        @Test
        void theMaskTheFinderKeptIsTheLargestBlobOfTheMaskTheEstimatorMade() {
            Surface surface = accession(0);
            CaptureReading reading = Captures.readCapture(surface, SIGMA_4);
            Mask mask = Backgrounds.objectMask(surface, 4).mask();
            Optional<ComponentMask> largest =
                    Components.largestComponent(Morphology.fillHoles(Morphology.close(mask, 3)));
            assertThat(largest).hasValueSatisfying(found -> assertThat(
                            (double) found.component().area())
                    .isGreaterThan(reading.sheet().mask().count() * 0.9));
        }

        @Test
        void theCaliperAngleAndTheProjectionAngleAgreeOnTheSameMask() {
            CaptureReading reading = Captures.readCapture(accession(3), SIGMA_4);
            double calipers = QuadFit.fitQuad(reading.sheet().mask()).angle().degrees();
            double projection =
                    Deskew.measureSkew(reading.sheet().mask()).angle().degrees();
            assertThat(Math.abs(calipers - projection)).isLessThan(1);
        }
    }
}
