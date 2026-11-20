package io.github.rkaushikdev.decklewright.codec.png;

import static io.github.rkaushikdev.decklewright.codec.png.Decoding.decodePng;
import static io.github.rkaushikdev.decklewright.codec.png.Encoding.encodePng;
import static io.github.rkaushikdev.decklewright.codec.png.Rewriting.MASTER_KEEP;
import static io.github.rkaushikdev.decklewright.codec.png.Rewriting.SERVICE_KEEP;
import static io.github.rkaushikdev.decklewright.codec.png.Rewriting.checkChunkPolicy;
import static io.github.rkaushikdev.decklewright.codec.png.Rewriting.chunkTypes;
import static io.github.rkaushikdev.decklewright.codec.png.Rewriting.imageDataOf;
import static io.github.rkaushikdev.decklewright.codec.png.Rewriting.keepOnly;
import static io.github.rkaushikdev.decklewright.codec.png.Rewriting.readTime;
import static io.github.rkaushikdev.decklewright.codec.png.Rewriting.stripAncillary;
import static io.github.rkaushikdev.decklewright.codec.png.Rewriting.timeChunk;
import static io.github.rkaushikdev.decklewright.codec.png.Rewriting.withMetadata;
import static io.github.rkaushikdev.decklewright.testing.TestBytes.unsigned;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.raster.Layout;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.util.IsoTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RewriteTest {

    private static byte[] master() {
        Surface s = Surface.create(8, 6, Layout.RGB, 16, 30000);
        Map<String, String> text = new LinkedHashMap<>();
        text.put("Title", "plate 14");
        text.put("Rights", "public");
        return withMetadata(
                encodePng(s, PngOptions.defaults().withPpi(Sampling.of(400)).withText(text)),
                Metadata.defaults().withTime(Instant.parse("2025-03-14T09:30:15Z")));
    }

    private static long count(List<String> types, String type) {
        return types.stream().filter(type::equals).count();
    }

    @Nested
    class TimeChunkAndReadTime {
        @Test
        void roundTripsACaptureTimeToTheSecond() {
            Instant when = Instant.parse("2025-05-02T13:45:07Z");
            byte[] file = withMetadata(
                    encodePng(Surface.create(1, 1)), Metadata.defaults().withTime(when));
            assertThat(readTime(file).map(IsoTime::format)).contains(IsoTime.format(when));
        }

        @Test
        void laysOutTheSevenBytes() {
            Chunk chunk = timeChunk(Instant.parse("2025-01-01T00:00:00Z"));
            assertThat(unsigned(chunk.data())).containsExactly(7, 233, 1, 1, 0, 0, 0);
        }

        @Test
        void reportsNoTimeChunkAsNull() {
            assertThat(readTime(encodePng(Surface.create(1, 1)))).isEmpty();
        }
    }

    @Nested
    class WithMetadata {
        @Test
        void putsTheNewChunksBeforeTheImageData() {
            List<String> types = chunkTypes(master());
            assertThat(types.indexOf("tIME")).isLessThan(types.indexOf("IDAT"));
            assertThat(types.get(0)).isEqualTo("IHDR");
            assertThat(types.get(types.size() - 1)).isEqualTo("IEND");
        }

        @Test
        void replacesATextKeyRatherThanAddingASecondOne() {
            byte[] once = master();
            byte[] twice = withMetadata(once, Metadata.defaults().withText(Map.of("Title", "plate 15")));
            assertThat(count(chunkTypes(twice), "tEXt")).isEqualTo(2);
            assertThat(decodePng(twice).text().get("Title")).isEqualTo("plate 15");
            assertThat(decodePng(twice).text().get("Rights")).isEqualTo("public");
        }

        @Test
        void replacesTheTimeRatherThanAddingASecondOne() {
            byte[] twice = withMetadata(master(), Metadata.defaults().withTime(Instant.parse("2026-01-01T00:00:00Z")));
            assertThat(count(chunkTypes(twice), "tIME")).isEqualTo(1);
            assertThat(readTime(twice).map(time -> time.atZone(ZoneOffset.UTC).getYear()))
                    .contains(2026);
        }

        @Test
        void leavesTheSamplesUntouched() {
            Surface before = decodePng(master()).surface();
            Surface after = decodePng(withMetadata(master(), Metadata.defaults().withText(Map.of("Note", "rescanned"))))
                    .surface();
            assertThat(after.data().toArray()).containsExactly(before.data().toArray());
        }

        @Test
        void doesNotTouchTheCompressedDataItself() {
            assertThat(imageDataOf(withMetadata(master(), Metadata.defaults().withText(Map.of("Note", "x")))))
                    .containsExactly(imageDataOf(master()));
        }
    }

    @Nested
    class KeepOnly {
        @Test
        void dropsWhatIsNotOnTheList() {
            byte[] cut = keepOnly(master(), SERVICE_KEEP);
            assertThat(chunkTypes(cut)).containsExactly("IHDR", "IDAT", "IEND");
        }

        @Test
        void keepsTheMasterListIntact() {
            List<String> kept = chunkTypes(keepOnly(master(), MASTER_KEEP));
            assertThat(kept).contains("pHYs");
            assertThat(kept).contains("tIME");
            assertThat(count(kept, "tEXt")).isEqualTo(2);
        }

        @Test
        void neverDropsTheThreeChunksAPngNeeds() {
            byte[] cut = keepOnly(master(), List.of());
            assertThat(chunkTypes(cut)).containsExactly("IHDR", "IDAT", "IEND");
        }

        @Test
        void leavesADecodableFileBehind() {
            Surface before = decodePng(master()).surface();
            Surface after = decodePng(keepOnly(master(), SERVICE_KEEP)).surface();
            assertThat(after.toJson()).isEqualTo(before.toJson());
            assertThat(after.data().toArray()).containsExactly(before.data().toArray());
        }
    }

    @Nested
    class StripAncillary {
        @Test
        void keepsOnlyTheCriticalChunks() {
            assertThat(chunkTypes(stripAncillary(master()))).containsExactly("IHDR", "IDAT", "IEND");
        }

        @Test
        void makesTheFileSmaller() {
            assertThat(stripAncillary(master()).length).isLessThan(master().length);
        }
    }

    @Nested
    class CheckChunkPolicy {
        @Test
        void passesAFileThatMatches() {
            assertThat(checkChunkPolicy(keepOnly(master(), SERVICE_KEEP), SERVICE_KEEP))
                    .isEqualTo(new PolicyCheck(true, List.of(), List.of()));
        }

        @Test
        void namesTheChunksThatBrokeThePolicy() {
            PolicyCheck result = checkChunkPolicy(master(), SERVICE_KEEP);
            assertThat(result.ok()).isFalse();
            assertThat(result.unexpected()).containsExactly("pHYs", "tEXt", "tIME");
            assertThat(result.missing()).isEqualTo(List.of());
        }

        @Test
        void acceptsAMasterAgainstTheMasterPolicy() {
            assertThat(checkChunkPolicy(master(), MASTER_KEEP).ok()).isTrue();
        }
    }

    @Nested
    class ImageDataOf {
        @Test
        void handsBackTheCompressedBytes() {
            assertThat(imageDataOf(master()).length).isGreaterThan(0);
        }
    }
}
