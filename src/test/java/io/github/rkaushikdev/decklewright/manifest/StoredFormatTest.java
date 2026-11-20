package io.github.rkaushikdev.decklewright.manifest;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.decklewright.derive.DerivedFile;
import io.github.rkaushikdev.decklewright.testing.StoredDelivery;
import io.github.rkaushikdev.decklewright.util.Json;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Sidecars, sum files and provenance chains written by earlier releases still read, and the writers
 * still produce the same text from the same input, byte for byte, because every one of those files
 * is digested and the digests are delivered.
 */
class StoredFormatTest {

    private static final String AT = StoredDelivery.AT;

    private static Map<String, Object> object(Object... namesAndValues) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            out.put((String) namesAndValues[i], namesAndValues[i + 1]);
        }
        return out;
    }

    private static Object at(Object document, Object... path) {
        Object value = document;
        for (Object step : path) {
            value = step instanceof Integer index ? ((List<?>) value).get(index) : ((Map<?, ?>) value).get(step);
        }
        return value;
    }

    /** The stored sidecar's document, built field by field in the order the writer keeps. */
    private static Map<String, Object> sidecarDocument() {
        List<Object> derivatives = List.of(
                derivative(
                        "master",
                        "g4181-1907_master.png",
                        400,
                        13,
                        StoredDelivery.MASTER_SHA256,
                        "acb3d59aded008677852fb0f28ab9756",
                        List.of()),
                derivative(
                        "service",
                        "g4181-1907_service.png",
                        200,
                        14,
                        StoredDelivery.SERVICE_SHA256,
                        "bccab74061f599da60dddadd1a7646e6",
                        List.of()),
                derivative(
                        "thumb",
                        "g4181-1907_thumb.png",
                        37.5,
                        12,
                        StoredDelivery.THUMB_SHA256,
                        "9452eb46abcff3c3031402566a55bf31",
                        List.of("the text chunks were not carried")));
        Map<String, Object> quality = object(
                "stars",
                3.0,
                "wanted",
                3.0,
                "passed",
                true,
                "worst",
                "noise",
                "missing",
                List.of(),
                "checks",
                List.of(
                        object(
                                "name",
                                "sampling",
                                "ran",
                                true,
                                "stars",
                                4.0,
                                "measured",
                                400.0,
                                "note",
                                "400 ppi against 400"),
                        object(
                                "name",
                                "noise",
                                "ran",
                                false,
                                "stars",
                                0.0,
                                "measured",
                                null,
                                "note",
                                "no flat patch to measure")));
        return object(
                "version",
                2.0,
                "accession",
                "g4181-1907",
                "profile",
                object("name", "house", "title", "house style"),
                "created",
                AT,
                "capture",
                object(
                        "pixels",
                        object("width", 300.0, "height", 240.0),
                        "layout",
                        "rgb",
                        "depth",
                        8.0,
                        "skewDegrees",
                        0.0,
                        "confidence",
                        0.986,
                        "spreadDegrees",
                        0.0625,
                        "target",
                        true,
                        "neatline",
                        false,
                        "scaleBar",
                        true),
                "crop",
                object(
                        "rect", object("x", 84.5, "y", 70.0, "width", 171.0, "height", 120.0),
                        "deskewDegrees", 0.0,
                        "ppi", object("x", 400.0, "y", 400.0),
                        "rules", List.of("start", "bleed"),
                        "overhangs", false),
                "derivatives",
                derivatives,
                "quality",
                quality,
                "warnings",
                List.of("no neatline was asked for"));
    }

    private static Map<String, Object> derivative(
            String name, String file, double ppi, double bytes, String sha256, String md5, List<String> notes) {
        return object(
                "name", name,
                "file", file,
                "format", "png",
                "ppi", object("x", ppi, "y", ppi),
                "bytes", bytes,
                "digests", object("sha256", sha256, "md5", md5),
                "notes", notes);
    }

    /** The stored chain, recorded event by event as the run recorded it. */
    private static List<ProvenanceEvent> chain() {
        EventOptions stamped = EventOptions.defaults().withAt(Instant.parse(AT));
        String agent = StoredDelivery.AGENT;
        List<ProvenanceEvent> events = List.of();
        events = Provenance.recordEvent(
                events,
                "capture-read",
                agent,
                "the capture was read off the stand",
                stamped.withValues(object("warnings", 1.0)));
        events = Provenance.recordEvent(
                events,
                "sheet-found",
                agent,
                "the sheet was found on the bed",
                stamped.withValues(object("confidence", 0.986, "skew", 0.0)));
        events = Provenance.recordEvent(
                events,
                "crop-planned",
                agent,
                "the crop was planned by 2 rules",
                stamped.withValues(object("width", 171.0, "height", 120.0)));
        for (DerivedFile file : StoredDelivery.files()) {
            events = Provenance.recordEvent(
                    events,
                    "derivative-made",
                    agent,
                    file.name() + " was written as png",
                    stamped.withValues(object("file", file.fileName())));
        }
        return Provenance.recordEvent(
                events,
                "quality-checked",
                agent,
                "graded at 3 of 4",
                stamped.withValues(object("stars", 3.0, "passed", true)));
    }

    @Test
    void readsASidecarWrittenBeforeThisCodeExisted() {
        Map<String, Object> document = Sidecars.parseSidecar(StoredDelivery.SIDECAR);
        assertThat(document).isEqualTo(sidecarDocument());
        assertThat(Sidecars.objectionsToSidecar(document)).isEmpty();
        assertThat(Sidecars.describeSidecar(document))
                .containsExactly(
                        "g4181-1907, profile house",
                        "3 of 4 stars, passed",
                        "  g4181-1907_master.png, png, 13 bytes, 400 ppi",
                        "  g4181-1907_service.png, png, 14 bytes, 200 ppi",
                        "  g4181-1907_thumb.png, png, 12 bytes, 37.5 ppi",
                        "  warning: no neatline was asked for");
    }

    @Test
    void readsASidecarAnEditorSavedWithAByteOrderMark() {
        byte[] text = StoredDelivery.SIDECAR.getBytes(StandardCharsets.UTF_8);
        byte[] marked = new byte[text.length + 3];
        marked[0] = (byte) 0xEF;
        marked[1] = (byte) 0xBB;
        marked[2] = (byte) 0xBF;
        System.arraycopy(text, 0, marked, 3, text.length);
        assertThat(Sidecars.parseSidecar(marked)).isEqualTo(sidecarDocument());
    }

    @Test
    void writesTheStoredSidecarTextFromTheSameDocument() {
        assertThat(Sidecars.writeSidecar(sidecarDocument())).isEqualTo(StoredDelivery.SIDECAR);
        assertThat(Sidecars.writeSidecar(Sidecars.parseSidecar(StoredDelivery.SIDECAR)))
                .isEqualTo(StoredDelivery.SIDECAR);
    }

    @Test
    void writesTheFixedKeysFirstWhateverOrderTheyWereGivenIn() {
        List<Map.Entry<String, Object>> backwards =
                new ArrayList<>(sidecarDocument().entrySet());
        Collections.reverse(backwards);
        Map<String, Object> shuffled = new LinkedHashMap<>();
        backwards.forEach(entry -> shuffled.put(entry.getKey(), entry.getValue()));
        assertThat(Sidecars.writeSidecar(shuffled)).isEqualTo(StoredDelivery.SIDECAR);
    }

    @Test
    void readsASumFileWrittenBeforeThisCodeExisted() {
        List<SumLine> expected = List.of(
                new SumLine(StoredDelivery.MASTER_SHA256, "g4181-1907_master.png"),
                new SumLine(StoredDelivery.SERVICE_SHA256, "g4181-1907_service.png"),
                new SumLine(StoredDelivery.THUMB_SHA256, "g4181-1907_thumb.png"));
        assertThat(Checksums.fromSumFile(StoredDelivery.SUMS)).isEqualTo(expected);
        assertThat(Checksums.fromSumFile(StoredDelivery.SUMS.replace("\n", "\r\n")))
                .isEqualTo(expected);
    }

    @Test
    void writesTheStoredSumFileTextForTheSameFiles() {
        List<SumEntry> entries = StoredDelivery.files().stream()
                .map(file -> new SumEntry(file.fileName(), Checksums.digests(file.bytes())))
                .toList();
        assertThat(Checksums.toSumFile(entries) + "\n").isEqualTo(StoredDelivery.SUMS);
    }

    @Test
    void readsAProvenanceChainWrittenBeforeThisCodeExisted() {
        List<ProvenanceEvent> events = Provenance.parseChain(Json.parse(StoredDelivery.PROVENANCE));
        assertThat(events).hasSize(7);
        assertThat(events).isEqualTo(chain());
        assertThat(Provenance.chainDigest(events)).isEqualTo(StoredDelivery.CHAIN_DIGEST);
        assertThat(at(events.get(1).toJson(), "values")).isEqualTo(object("confidence", 0.986, "skew", 0.0));
        assertThat(Provenance.describeChain(events).get(0))
                .isEqualTo(AT + " capture-read: the capture was read off the stand");
    }

    @Test
    void writesTheStoredChainTextForTheSameEvents() {
        assertThat(Json.stringify(chain(), 2) + "\n").isEqualTo(StoredDelivery.PROVENANCE);
        assertThat(Provenance.chainDigest(chain())).isEqualTo(StoredDelivery.CHAIN_DIGEST);
    }

    @Test
    void packsTheStoredFilesIntoTheStoredDelivery() {
        Payload payload =
                Payloads.payloadFor(StoredDelivery.ACCESSION, StoredDelivery.files(), sidecarDocument(), chain());

        Map<String, byte[]> stored = StoredDelivery.entries();
        assertThat(payload.entries()).extracting(PayloadEntry::name).containsExactlyElementsOf(stored.keySet());
        for (PayloadEntry entry : payload.entries()) {
            assertThat(entry.bytes()).as(entry.name()).isEqualTo(stored.get(entry.name()));
        }
        assertThat(payload.digest()).isEqualTo(StoredDelivery.SET_DIGEST);
        assertThat(Payloads.objectionsToPayload(payload)).isEmpty();
    }
}
