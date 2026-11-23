package io.github.rkaushikdev.decklewright.store;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.decklewright.manifest.Payload;
import io.github.rkaushikdev.decklewright.manifest.PayloadEntry;
import io.github.rkaushikdev.decklewright.testing.StoredDelivery;
import io.github.rkaushikdev.decklewright.util.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A ledger and a delivered folder written by earlier releases still read, and the store still
 * writes them the same way, so a workspace a lab has been filling for years keeps working.
 */
class StoredLedgerTest {

    /** A ledger as earlier releases appended it: one compact json object per line, keys in order. */
    private static final String LEDGER =
            """
            {"at":"2025-12-18T12:00:00.000Z","accession":"g4181-1907","outcome":"delivered","profile":"house",\
            "stars":3,"bytes":4715,"digest":"7bfecb8eb3ff0d7eaa703ce7e404dce76e9963e77e0fffc604155ce44970c8e5",\
            "warnings":["no neatline was asked for"]}
            {"at":"2025-12-18T12:04:00.000Z","accession":"g4182","outcome":"rejected","profile":"house","stars":1,\
            "bytes":90112,"digest":"d505e913b78d18644e131e7abd5f436eccff7c5fafd10a16f575e54d29153119",\
            "warnings":["the label reads Café du Nord"]}
            {"at":"2025-12-18T12:09:30.500Z","accession":"g4183","outcome":"failed","profile":"house",\
            "reason":"no sheet on the bed, the \\"sheet\\" was a shadow"}
            {"at":"2025-12-18T12:10:00.000Z","accession":"g4184","outcome":"skipped","reason":"already delivered last week"}
            """;

    /** What each line of the ledger records. */
    private static List<LedgerEntry> expected() {
        return List.of(
                Ledger.entryFor(
                        "g4181-1907",
                        "delivered",
                        EntryOptions.defaults()
                                .withAt(Instant.parse("2025-12-18T12:00:00Z"))
                                .withProfile("house")
                                .withStars(3)
                                .withBytes(4715)
                                .withDigest(StoredDelivery.SET_DIGEST)
                                .withWarnings(List.of("no neatline was asked for"))),
                Ledger.entryFor(
                        "g4182",
                        "rejected",
                        EntryOptions.defaults()
                                .withAt(Instant.parse("2025-12-18T12:04:00Z"))
                                .withProfile("house")
                                .withStars(1)
                                .withBytes(90112)
                                .withDigest("d505e913b78d18644e131e7abd5f436eccff7c5fafd10a16f575e54d29153119")
                                .withWarnings(List.of("the label reads Café du Nord"))),
                Ledger.entryFor(
                        "g4183",
                        "failed",
                        EntryOptions.defaults()
                                .withAt(Instant.parse("2025-12-18T12:09:30.500Z"))
                                .withProfile("house")
                                .withReason("no sheet on the bed, the \"sheet\" was a shadow")),
                Ledger.entryFor(
                        "g4184",
                        "skipped",
                        EntryOptions.defaults()
                                .withAt(Instant.parse("2025-12-18T12:10:00Z"))
                                .withReason("already delivered last week")));
    }

    /** A workspace laid out on disk as earlier releases laid one out, with the stored delivery in it. */
    private static Path storedWorkspace(Path root) throws IOException {
        for (String folder : List.of("incoming", "working", "delivered", "rejected")) {
            Files.createDirectories(root.resolve(folder));
        }
        Path delivered = Files.createDirectories(root.resolve("delivered").resolve(StoredDelivery.ACCESSION));
        for (Map.Entry<String, byte[]> entry : StoredDelivery.entries().entrySet()) {
            Files.write(delivered.resolve(entry.getKey()), entry.getValue());
        }
        Files.writeString(root.resolve("ledger.jsonl"), LEDGER, StandardCharsets.UTF_8);
        return root;
    }

    @Test
    void readsALedgerWrittenBeforeThisCodeExisted(@TempDir Path root) throws IOException {
        Path path = root.resolve("ledger.jsonl");
        Files.writeString(path, LEDGER, StandardCharsets.UTF_8);

        LedgerRead read = Ledger.readLedger(path);
        assertThat(read.entries()).isEqualTo(expected());
        assertThat(read.unreadable()).isEqualTo(0);
        assertThat(Ledger.describeSummary(Ledger.summarise(read.entries())))
                .containsExactly(
                        "4 accessions, 94827 bytes",
                        "  1 delivered",
                        "  1 rejected",
                        "  1 failed",
                        "  1 skipped",
                        "  2.00 stars over 2 graded",
                        "    1 at 3 stars",
                        "    1 at 1 star");
    }

    @Test
    void appendsTheSameLinesForTheSameEntries(@TempDir Path root) throws IOException {
        Path path = root.resolve("ledger.jsonl");
        for (LedgerEntry entry : expected()) {
            Ledger.appendEntry(path, entry);
        }
        assertThat(Files.readString(path, StandardCharsets.UTF_8)).isEqualTo(LEDGER);
    }

    @Test
    void readsADeliveredFolderLaidOutBeforeThisCodeExisted(@TempDir Path root) throws IOException {
        Workspace workspace = Workspace.at(storedWorkspace(root));

        assertThat(workspace.list()).containsExactly(StoredDelivery.ACCESSION);
        DeliveryCheck check = workspace.verify(StoredDelivery.ACCESSION);
        assertThat(check).isEqualTo(new DeliveryCheck(StoredDelivery.ACCESSION, true, 3, List.of()));
        assertThat(Json.stringify(check))
                .isEqualTo("{\"accession\":\"g4181-1907\",\"ok\":true,\"checked\":3,\"problems\":[]}");

        Payload read = workspace.get(StoredDelivery.ACCESSION);
        assertThat(read.entries())
                .extracting(PayloadEntry::name)
                .containsExactly(
                        "g4181-1907_master.png",
                        "g4181-1907_service.png",
                        "g4181-1907_thumb.png",
                        "provenance.json",
                        "sha256sums.txt",
                        "sidecar.json");
        assertThat(read.digest()).isEqualTo(StoredDelivery.SET_DIGEST);

        Map<String, LedgerEntry> latest = Ledger.latestByAccession(
                Ledger.readLedger(workspace.paths().ledger()).entries());
        assertThat(latest.get(StoredDelivery.ACCESSION).digest()).hasValue(read.digest());
        assertThat(Dispatch.shiftReport(workspace).summary().total()).isEqualTo(4);
    }

    @Test
    void writesADeliveryInTheLayoutItReads(@TempDir Path root) throws IOException {
        Workspace workspace = Workspace.open(root);
        List<PayloadEntry> entries = new ArrayList<>();
        StoredDelivery.entries().forEach((name, bytes) -> entries.add(new PayloadEntry(name, bytes)));

        Dispatched out = Dispatch.deliver(
                workspace,
                Payload.of(StoredDelivery.ACCESSION, entries),
                DeliverOptions.defaults()
                        .withProfile("house")
                        .withStars(3)
                        .withWarnings(List.of("no neatline was asked for"))
                        .withAt(Instant.parse("2025-12-18T12:00:00Z")));

        assertThat(out.directory()).isEqualTo(root.resolve("delivered").resolve(StoredDelivery.ACCESSION));
        for (Map.Entry<String, byte[]> entry : StoredDelivery.entries().entrySet()) {
            assertThat(Files.readAllBytes(out.directory().resolve(entry.getKey())))
                    .as(entry.getKey())
                    .isEqualTo(entry.getValue());
        }
        String firstLine = LEDGER.substring(0, LEDGER.indexOf('\n') + 1);
        assertThat(Files.readString(workspace.paths().ledger(), StandardCharsets.UTF_8))
                .isEqualTo(firstLine);
    }
}
