package io.github.rkaushikdev.decklewright.codec.png;

import static io.github.rkaushikdev.decklewright.codec.png.Chunks.looksLikePng;
import static io.github.rkaushikdev.decklewright.codec.png.Chunks.splitChunks;
import static io.github.rkaushikdev.decklewright.codec.png.Decoding.decodePng;
import static io.github.rkaushikdev.decklewright.codec.png.Decoding.probePng;
import static io.github.rkaushikdev.decklewright.codec.png.Encoding.encodePng;
import static io.github.rkaushikdev.decklewright.codec.png.Encoding.physicalChunk;
import static io.github.rkaushikdev.decklewright.codec.png.Encoding.textChunk;
import static io.github.rkaushikdev.decklewright.testing.TestBytes.unsigned;
import static io.github.rkaushikdev.decklewright.testing.TestSurfaces.gray;
import static io.github.rkaushikdev.decklewright.testing.TestSurfaces.row;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.CodecError;
import io.github.rkaushikdev.decklewright.codec.ImageShape;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Samples;
import io.github.rkaushikdev.decklewright.raster.Surface;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EncodeTest {

    /** Seeded noise over every sample, the same sequence the original suite used. */
    private static Surface noisy(int width, int height, Layout layout, int depth) {
        Surface s = Surface.create(width, height, layout, depth);
        Samples data = s.data();
        int seed = 7;
        for (int i = 0; i < data.length(); i += 1) {
            // the original multiplied in doubles, which round past two to the fifty third, before
            // masking; doing the same keeps the noise identical
            seed = (int) (long) (seed * 1103515245.0 + 12345.0) & 0x7fffffff;
            data.set(i, seed % (s.maxValue() + 1));
        }
        return s;
    }

    private static Map<String, String> text(String... pairs) {
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            out.put(pairs[i], pairs[i + 1]);
        }
        return out;
    }

    private static PngOptions options() {
        return PngOptions.defaults();
    }

    @Nested
    class EncodePng {
        @Test
        void writesAFileThatStartsWithTheSignature() {
            assertThat(looksLikePng(encodePng(gray(row(1, 2))))).isTrue();
        }

        @Test
        void writesTheChunksInTheOrderTheFormatWants() {
            byte[] png =
                    encodePng(gray(row(1)), options().withPpi(Sampling.of(400)).withText(text("Title", "plate")));
            assertThat(splitChunks(png).stream().map(Chunk::type).toList())
                    .containsExactly("IHDR", "pHYs", "tEXt", "IDAT", "IEND");
        }

        @Test
        void refusesALayoutPngCannotCarry() {
            // The original forged a surface whose layout was cmyk. A Java raster can only hold a
            // Layout and png carries every one of them, so the nearest bad input is the layout name
            // itself, which is refused before any surface can be made; and every layout that can be
            // made is carried.
            assertThatThrownBy(() -> Surface.create(1, 1, Layout.parse("cmyk")))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("unknown layout cmyk");
            for (Layout layout : Layout.values()) {
                assertThat(looksLikePng(encodePng(Surface.create(1, 1, layout))))
                        .as(layout.label())
                        .isTrue();
            }
        }

        @Test
        void refusesACompressionLevelOutsideTheRange() {
            assertThatThrownBy(() -> encodePng(gray(row(1)), options().withLevel(10)))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("level must be between 0 and 9");
        }
    }

    @Nested
    class RoundTripsThroughTheDecoder {
        private void roundTrip(Surface original) {
            roundTrip(original, options());
        }

        private void roundTrip(Surface original, PngOptions options) {
            Surface surface = decodePng(encodePng(original, options)).surface();
            assertThat(surface.toJson()).isEqualTo(original.toJson());
            assertThat(surface.data().toArray()).containsExactly(original.data().toArray());
        }

        @Test
        void survivesEveryLayoutAtEightBits() {
            for (Layout layout : List.of(Layout.GRAY, Layout.GRAYA, Layout.RGB, Layout.RGBA)) {
                roundTrip(noisy(7, 5, layout, 8));
            }
        }

        @Test
        void survivesEveryLayoutAtSixteenBits() {
            for (Layout layout : List.of(Layout.GRAY, Layout.GRAYA, Layout.RGB, Layout.RGBA)) {
                roundTrip(noisy(7, 5, layout, 16));
            }
        }

        @Test
        void survivesASinglePixel() {
            roundTrip(gray(row(42)));
        }

        @Test
        void survivesAFlatCaptureAtEveryCompressionLevel() {
            Surface flat = Surface.create(16, 16, Layout.RGB, 8, 200);
            for (int level : new int[] {0, 1, 6, 9}) {
                roundTrip(flat, options().withLevel(level));
            }
        }

        @Test
        void survivesWithTheFilterHeuristicTurnedOff() {
            roundTrip(noisy(9, 9, Layout.RGB, 8), options().withFilter(FilterMode.NONE));
        }

        @Test
        void givesTheSamePixelsEitherWayRoundTheHeuristic() {
            Surface s = noisy(12, 9, Layout.RGBA, 16);
            Surface auto = decodePng(encodePng(s, options().withFilter(FilterMode.AUTO)))
                    .surface();
            Surface plain = decodePng(encodePng(s, options().withFilter(FilterMode.NONE)))
                    .surface();
            assertThat(auto.data().toArray()).containsExactly(plain.data().toArray());
        }

        @Test
        void isSmallerWithTheHeuristicOnAGradient() {
            Surface s = Surface.create(128, 128, Layout.GRAY);
            for (int y = 0; y < 128; y += 1) {
                for (int x = 0; x < 128; x += 1) {
                    s.set(x, y, 0, (x + y) & 0xff);
                }
            }
            int auto = encodePng(s, options().withFilter(FilterMode.AUTO)).length;
            int plain = encodePng(s, options().withFilter(FilterMode.NONE)).length;
            assertThat(auto).isLessThan(plain);
        }
    }

    @Nested
    class MetadataBehaviour {
        @Test
        void carriesTextThrough() {
            byte[] png = encodePng(gray(row(1)), options().withText(text("Title", "plate 14", "Source", "stand two")));
            assertThat(decodePng(png).text()).isEqualTo(Map.of("Title", "plate 14", "Source", "stand two"));
        }

        @Test
        void carriesSamplingFrequencyThroughAsPixelsPerMetre() {
            byte[] png = encodePng(gray(row(1)), options().withPpi(Sampling.of(400)));
            Optional<Physical> physical = decodePng(png).physical();
            assertThat(physical.map(Physical::unit)).contains(1);
            assertThat(physical.map(Physical::x)).contains(15748L);
            assertThat(physical.map(Physical::x).orElse(0L) * 0.0254).isCloseTo(400, byLessThan(0.5));
        }

        @Test
        void writesDifferentFrequenciesPerAxis() {
            byte[] png = encodePng(gray(row(1)), options().withPpi(new Sampling(600, 300)));
            Optional<Physical> physical = decodePng(png).physical();
            assertThat(physical.map(Physical::x)).contains(23622L);
            assertThat(physical.map(Physical::y)).contains(11811L);
        }
    }

    @Nested
    class TheChunkBuildersOnTheirOwn {
        @Test
        void laysOutATextChunkWithTheSeparator() {
            Chunk chunk = textChunk("Title", "ab");
            assertThat(unsigned(chunk.data())).containsExactly(84, 105, 116, 108, 101, 0, 97, 98);
        }

        @Test
        void refusesAKeyThatIsEmptyOrTooLong() {
            assertThatThrownBy(() -> textChunk("", "x"))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("one to seventy nine");
            assertThatThrownBy(() -> textChunk("k".repeat(80), "x"))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("one to seventy nine");
        }

        @Test
        void refusesTextOutsideLatinOne() {
            String dashed = "plate " + (char) 0x2014 + " 14";
            assertThatThrownBy(() -> textChunk("Title", dashed))
                    .isInstanceOf(CodecError.class)
                    .hasMessageFindingMatch("latin one");
        }

        @Test
        void laysOutAPhysicalChunk() {
            Chunk chunk = physicalChunk(254, 254);
            assertThat(chunk.data()).hasSize(9);
            assertThat(chunk.data()[8]).isEqualTo((byte) 1);
        }
    }

    @Nested
    class ProbeAgreesWithTheEncoder {
        @Test
        void reportsWhatWasWritten() {
            Surface s = noisy(21, 13, Layout.RGBA, 16);
            assertThat(probePng(encodePng(s))).isEqualTo(new ImageShape(21, 13, Layout.RGBA, 16));
        }
    }
}
