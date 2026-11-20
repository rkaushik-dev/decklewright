package io.github.rkaushikdev.decklewright.manifest;

import static io.github.rkaushikdev.decklewright.manifest.Payloads.PROVENANCE_NAME;
import static io.github.rkaushikdev.decklewright.manifest.Payloads.SIDECAR_NAME;
import static io.github.rkaushikdev.decklewright.manifest.Payloads.SUMS_NAME;
import static io.github.rkaushikdev.decklewright.manifest.Payloads.describePayload;
import static io.github.rkaushikdev.decklewright.manifest.Payloads.entryNamed;
import static io.github.rkaushikdev.decklewright.manifest.Payloads.objectionsToPayload;
import static io.github.rkaushikdev.decklewright.manifest.Payloads.payloadFor;
import static io.github.rkaushikdev.decklewright.manifest.Payloads.payloadSize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.ManifestError;
import io.github.rkaushikdev.decklewright.testing.Accession;
import io.github.rkaushikdev.decklewright.util.Json;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** A delivery as a set of named blobs: assembled, sized, checked against itself and described. */
class PayloadTest {

    private static final Instant AT = Instant.parse("2025-11-18T10:30:00Z");

    private static Payload deliveryOf(Accession.Options options) {
        return Deliveries.deliveryFor(
                Accession.worked(options).run(),
                DeliveryOptions.defaults().withVersion("1.4.0").withAt(AT));
    }

    private static Payload delivery() {
        return deliveryOf(Accession.Options.defaults());
    }

    private static List<String> names(Payload payload) {
        return payload.entries().stream().map(PayloadEntry::name).toList();
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String textOf(Payload payload, String name) {
        return text(entryNamed(payload, name).orElseThrow());
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /** The same delivery with only the entries the test keeps. */
    private static Payload keeping(Payload payload, Predicate<PayloadEntry> kept) {
        return new Payload(
                payload.accession(), payload.entries().stream().filter(kept).toList(), payload.digest());
    }

    /** The same delivery with the bytes of the entries the test picks replaced. */
    private static Payload replacing(Payload payload, Predicate<String> which, byte[] bytes) {
        return new Payload(
                payload.accession(),
                payload.entries().stream()
                        .map(entry -> which.test(entry.name()) ? new PayloadEntry(entry.name(), bytes) : entry)
                        .toList(),
                payload.digest());
    }

    private static Map<String, Object> handWrittenSidecar() {
        Map<String, Object> sidecar = new LinkedHashMap<>();
        sidecar.put("version", 2.0);
        sidecar.put("accession", "a");
        return sidecar;
    }

    @Nested
    class PayloadFor {
        @Test
        void putsEveryDerivativeInTheFolder() {
            List<String> names = names(delivery());
            assertThat(names).contains("g4181-1907_master.png");
            assertThat(names).contains("g4181-1907_thumb.png");
        }

        @Test
        void addsTheSidecarTheProvenanceAndTheSums() {
            List<String> names = names(delivery());
            assertThat(names).contains(SIDECAR_NAME);
            assertThat(names).contains(PROVENANCE_NAME);
            assertThat(names).contains(SUMS_NAME);
        }

        @Test
        void leavesTheChecksumFileOutOfTheChecksumFile() {
            String text = textOf(delivery(), SUMS_NAME);
            assertThat(text).doesNotContain(SUMS_NAME);
            assertThat(text).doesNotContain(SIDECAR_NAME);
        }

        @Test
        void givesTheWholeDeliveryOneNumber() {
            assertThat(delivery().digest()).hasSize(64);
        }

        @Test
        void givesTheSameNumberForTheSameRunTwice() {
            assertThat(delivery().digest()).isEqualTo(delivery().digest());
        }

        @Test
        void givesADifferentNumberForADifferentAccession() {
            assertThat(deliveryOf(Accession.Options.defaults().withId("g4182")).digest())
                    .isNotEqualTo(delivery().digest());
        }

        @Test
        void refusesADeliveryWithNoFilesInIt() {
            assertThatThrownBy(() -> payloadFor("a", List.of(), handWrittenSidecar()))
                    .isInstanceOf(ManifestError.class)
                    .hasMessageFindingMatch("no files in it");
        }

        @Test
        void leavesTheProvenanceOutWhenThereIsNone() {
            Payload out = payloadFor("a", Accession.worked().files(), handWrittenSidecar());
            assertThat(entryNamed(out, PROVENANCE_NAME)).isEmpty();
        }
    }

    @Nested
    class PayloadSize {
        @Test
        void addsEveryEntryUp() {
            Payload out = delivery();
            long sum = out.entries().stream()
                    .mapToLong(entry -> entry.bytes().length)
                    .sum();
            assertThat(payloadSize(out)).isEqualTo(sum);
        }
    }

    @Nested
    class ObjectionsToPayload {
        @Test
        void hasNothingToSayAboutOneItBuiltItself() {
            assertThat(objectionsToPayload(delivery())).isEqualTo(List.of());
        }

        @Test
        void catchesAFileThatWasSwappedAfterTheSidecarWasWritten() {
            Payload out = replacing(delivery(), name -> name.endsWith("_master.png"), new byte[] {1, 2, 3});
            String said = String.join(" ", objectionsToPayload(out));
            assertThat(said).containsPattern("bytes and the sidecar says");
        }

        @Test
        void catchesAFileTheSidecarListsAndTheFolderDoesNotHave() {
            Payload out = keeping(delivery(), entry -> !entry.name().endsWith("_thumb.png"));
            assertThat(String.join(" ", objectionsToPayload(out))).containsPattern("lists .* and it is not here");
        }

        @Test
        void catchesAFileInTheFolderThatTheSumsFileDoesNotList() {
            Payload out = delivery();
            String kept = Arrays.stream(textOf(out, SUMS_NAME).split("\n", -1))
                    .filter(line -> !line.contains("_thumb"))
                    .collect(Collectors.joining("\n"));
            Payload trimmed = replacing(out, SUMS_NAME::equals, utf8(kept));
            assertThat(String.join(" ", objectionsToPayload(trimmed))).containsPattern("missing from sha256sums");
        }

        @Test
        void catchesASidecarForAnotherAccession() {
            Payload out = delivery();
            Payload other = new Payload("somebody-elses", out.entries(), out.digest());
            assertThat(String.join(" ", objectionsToPayload(other))).containsPattern("and this delivery is");
        }

        @Test
        void saysSoWhenTheSidecarIsMissingAltogether() {
            Payload out = keeping(delivery(), entry -> !entry.name().equals(SIDECAR_NAME));
            assertThat(objectionsToPayload(out)).isEqualTo(List.of("there is no " + SIDECAR_NAME));
        }

        @Test
        void saysSoWhenTheSidecarWillNotReadBack() {
            Payload out = replacing(delivery(), SIDECAR_NAME::equals, utf8("{ not json"));
            assertThat(objectionsToPayload(out).get(0)).containsPattern("will not read back");
        }
    }

    @Nested
    class DeliveryFor {
        @Test
        void buildsADeliveryThatChecksOut() {
            assertThat(objectionsToPayload(delivery())).isEqualTo(List.of());
        }

        @Test
        void recordsTheAgentVersionInTheProvenance() {
            List<?> chain = (List<?>) Json.parse(textOf(delivery(), PROVENANCE_NAME));
            assertThat(((Map<?, ?>) chain.get(0)).get("agent")).isEqualTo("decklewright 1.4.0");
        }

        @Test
        void usesOneTimestampForTheSidecarAndTheChain() {
            Payload out = delivery();
            Map<String, Object> sidecar = Sidecars.parseSidecar(textOf(out, SIDECAR_NAME));
            List<?> chain = (List<?>) Json.parse(textOf(out, PROVENANCE_NAME));
            assertThat(sidecar.get("created")).isEqualTo(((Map<?, ?>) chain.get(0)).get("at"));
        }

        @Test
        void carriesARunThatWasNeverGraded() {
            Payload out = deliveryOf(Accession.Options.defaults().withGraded(false));
            assertThat(objectionsToPayload(out)).isEqualTo(List.of());
        }
    }

    @Nested
    class DescribePayload {
        @Test
        void opensWithTheAccessionAndTheSize() {
            assertThat(describePayload(delivery()).get(0)).matches("g4181-1907, \\d+ files, \\d+ bytes");
        }

        @Test
        void showsTheEventsUnderTheFiles() {
            assertThat(String.join("\n", describePayload(delivery()))).containsPattern("capture-read:");
        }

        @Test
        void saysSoRatherThanThrowingWhenTheSidecarIsUnreadable() {
            Payload out = replacing(delivery(), SIDECAR_NAME::equals, utf8("nonsense"));
            assertThat(describePayload(out)).contains("the sidecar will not read back");
        }
    }
}
