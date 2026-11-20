package io.github.rkaushikdev.decklewright.codec;

import static io.github.rkaushikdev.decklewright.testing.MakeTiff.makeTiff;
import static io.github.rkaushikdev.decklewright.testing.TestBytes.u8;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.decklewright.codec.png.Chunks;
import io.github.rkaushikdev.decklewright.codec.png.Decoding;
import io.github.rkaushikdev.decklewright.codec.png.Encoding;
import io.github.rkaushikdev.decklewright.codec.png.Metadata;
import io.github.rkaushikdev.decklewright.codec.png.PngOptions;
import io.github.rkaushikdev.decklewright.codec.png.Rewriting;
import io.github.rkaushikdev.decklewright.codec.tiff.Lzw;
import io.github.rkaushikdev.decklewright.codec.tiff.PackBits;
import io.github.rkaushikdev.decklewright.codec.tiff.Tags;
import io.github.rkaushikdev.decklewright.codec.tiff.TiffImage;
import io.github.rkaushikdev.decklewright.codec.tiff.TiffReader;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.testing.MakeTiff;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The layer as the rest of the tree sees it, through the public types of its three packages.
 */
class IndexTest {

    /** Every public type of the layer that holds functions or constants. */
    private static final List<Class<?>> LAYER = List.of(
            Codecs.class,
            Inspections.class,
            Bytes.class,
            Pnm.class,
            Chunks.class,
            Decoding.class,
            Encoding.class,
            Rewriting.class,
            TiffReader.class,
            Tags.class,
            Lzw.class,
            PackBits.class);

    private static boolean exportsFunction(String name) {
        return LAYER.stream()
                .filter(type -> Modifier.isPublic(type.getModifiers()))
                .flatMap(type -> Arrays.stream(type.getMethods()))
                .anyMatch(method -> method.getName().equals(name) && Modifier.isStatic(method.getModifiers()));
    }

    private static Surface sample() {
        Surface s = Surface.create(5, 4, Layout.RGB, 16);
        for (int y = 0; y < 4; y += 1) {
            for (int x = 0; x < 5; x += 1) {
                s.setPixel(x, y, x * 1000, y * 2000, 30000);
            }
        }
        return s;
    }

    @Nested
    class ThePublicTypes {
        @Test
        void exportsTheThreeEntryPointsTheStagesUse() {
            for (String name : List.of("readImage", "writeImage", "probeImage", "inspect", "triage")) {
                assertThat(exportsFunction(name)).as(name).isTrue();
            }
        }

        @Test
        void holdsNoNullConstants() throws IllegalAccessException {
            for (Class<?> type : LAYER) {
                for (Field field : type.getFields()) {
                    if (Modifier.isStatic(field.getModifiers())) {
                        assertThat(field.get(null))
                                .as(type.getSimpleName() + "." + field.getName())
                                .isNotNull();
                    }
                }
            }
        }

        @Test
        void stillExposesTheIndividualDecodersForTheOddCallerThatNeedsOne() {
            assertThat(exportsFunction("decodePng")).isTrue();
            assertThat(exportsFunction("readTiff")).isTrue();
            assertThat(exportsFunction("readPnm")).isTrue();
        }
    }

    @Nested
    class AShortRunThroughTheLayer {
        @Test
        void writesAMasterReadsItBackAndChecksItsChunkPolicy() {
            Surface original = sample();
            byte[] written = Rewriting.withMetadata(
                    Encoding.encodePng(
                            original,
                            PngOptions.defaults().withPpi(Sampling.of(400)).withText(Map.of("Title", "plate 14"))),
                    Metadata.defaults().withTime(Instant.parse("2025-02-20T10:00:00Z")));

            assertThat(Codecs.formatOf(written)).contains(ImageFormat.PNG);
            assertThat(Rewriting.checkChunkPolicy(written, Rewriting.MASTER_KEEP)
                            .ok())
                    .isTrue();

            Surface back = Codecs.readImage(written, "q17.png");
            assertThat(back.toJson()).isEqualTo(original.toJson());
            assertThat(back.data().toArray()).containsExactly(original.data().toArray());
            assertThat(Rewriting.readTime(written)
                            .map(time -> time.atZone(ZoneOffset.UTC).getYear()))
                    .contains(2025);
        }

        @Test
        void stripsAMasterDownToAServiceCopyWithoutTouchingThePixels() {
            byte[] master = Encoding.encodePng(
                    sample(), PngOptions.defaults().withPpi(Sampling.of(400)).withText(Map.of("Title", "x")));
            byte[] service = Rewriting.keepOnly(master, Rewriting.SERVICE_KEEP);
            assertThat(Rewriting.chunkTypes(service)).containsExactly("IHDR", "IDAT", "IEND");
            assertThat(Rewriting.imageDataOf(service)).containsExactly(Rewriting.imageDataOf(master));
        }

        @Test
        void readsATiffOffTheFlatbedAndWritesItBackOutAsPng() {
            int[] samples = IntStream.range(0, 24).map(i -> (i * 9) % 256).toArray();
            byte[] tiff = makeTiff(MakeTiff.Options.of(4, 2, samples)
                    .withChannels(3)
                    .withPhotometric(2)
                    .withCompression(5)
                    .withResolution(400));
            assertThat(Codecs.formatOf(tiff)).contains(ImageFormat.TIFF);
            TiffImage read = TiffReader.readTiff(tiff);
            assertThat(read.resolution()).contains(new Sampling(400, 400));

            byte[] png = Codecs.writeImage(
                    read.surface(),
                    "png",
                    WriteOptions.defaults().withPpi(read.resolution().orElseThrow()));
            Surface back = Codecs.readImage(png);
            assertThat(back.data().toArray()).containsExactly(samples);
        }

        @Test
        void triagesAFolderThatHoldsOneOfEverything() {
            List<IntakeFile> files = List.of(
                    IntakeFile.of(Encoding.encodePng(sample()), "a.png"),
                    IntakeFile.of(Pnm.writePnm(Surface.create(2, 2)), "b.ppm"),
                    IntakeFile.of(makeTiff(MakeTiff.Options.of(2, 1, 1, 2)), "c.tif"),
                    IntakeFile.of(u8(0xff, 0xd8), "d.jpg"));
            Triage result = Inspections.triage(files);
            assertThat(result.good().stream()
                            .map(inspection -> inspection.format().orElseThrow())
                            .toList())
                    .containsExactly(ImageFormat.PNG, ImageFormat.PNM, ImageFormat.TIFF);
            assertThat(result.bad()).hasSize(1);
        }
    }
}
