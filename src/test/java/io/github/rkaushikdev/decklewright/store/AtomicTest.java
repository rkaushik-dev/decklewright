package io.github.rkaushikdev.decklewright.store;

import static io.github.rkaushikdev.decklewright.store.AtomicFiles.appendLine;
import static io.github.rkaushikdev.decklewright.store.AtomicFiles.ensureDirectory;
import static io.github.rkaushikdev.decklewright.store.AtomicFiles.listNames;
import static io.github.rkaushikdev.decklewright.store.AtomicFiles.readBytes;
import static io.github.rkaushikdev.decklewright.store.AtomicFiles.readText;
import static io.github.rkaushikdev.decklewright.store.AtomicFiles.sweepTemporary;
import static io.github.rkaushikdev.decklewright.store.AtomicFiles.writeAtomic;
import static io.github.rkaushikdev.decklewright.store.AtomicFiles.writeSetAtomic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.StoreError;
import io.github.rkaushikdev.decklewright.manifest.PayloadEntry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Writing without leaving half a file behind, appending a line, and reading and listing back. */
class AtomicTest {

    private static final byte[] BYTES = {1, 2, 3, 4};

    /** The names in a directory, sorted. */
    static List<String> namesIn(Path directory) throws IOException {
        try (Stream<Path> listing = Files.list(directory)) {
            return listing.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    @Nested
    class EnsureDirectory {
        @Test
        void makesADirectoryAndItsParents(@TempDir Path root) throws IOException {
            ensureDirectory(root.resolve("a").resolve("b").resolve("c"));
            assertThat(namesIn(root.resolve("a").resolve("b"))).isEqualTo(List.of("c"));
        }

        @Test
        void isHappyWithOneThatIsAlreadyThere(@TempDir Path root) {
            ensureDirectory(root);
            assertThatCode(() -> ensureDirectory(root)).doesNotThrowAnyException();
        }
    }

    @Nested
    class WriteAtomic {
        @Test
        void writesTheBytes(@TempDir Path root) throws IOException {
            WrittenFile out = writeAtomic(root.resolve("master.png"), BYTES);
            assertThat(out.bytes()).isEqualTo(4);
            assertThat(Files.readAllBytes(root.resolve("master.png"))).isEqualTo(BYTES);
        }

        @Test
        void makesTheFolderOnTheWay(@TempDir Path root) throws IOException {
            writeAtomic(root.resolve("deep").resolve("down").resolve("master.png"), BYTES);
            assertThat(namesIn(root.resolve("deep").resolve("down"))).isEqualTo(List.of("master.png"));
        }

        @Test
        void leavesNothingBehind(@TempDir Path root) throws IOException {
            writeAtomic(root.resolve("master.png"), BYTES);
            assertThat(namesIn(root)).isEqualTo(List.of("master.png"));
        }

        @Test
        void replacesAnExistingFileByDefault(@TempDir Path root) throws IOException {
            writeAtomic(root.resolve("a.txt"), "first");
            writeAtomic(root.resolve("a.txt"), "second");
            assertThat(Files.readString(root.resolve("a.txt"))).isEqualTo("second");
        }

        @Test
        void refusesToReplaceOneWhenItWasToldNotTo(@TempDir Path root) throws IOException {
            writeAtomic(root.resolve("a.txt"), "first");
            assertThatThrownBy(() -> writeAtomic(root.resolve("a.txt"), "second", false))
                    .isInstanceOf(StoreError.class)
                    .hasMessageFindingMatch("already a file there");
            assertThat(Files.readString(root.resolve("a.txt"))).isEqualTo("first");
        }
    }

    @Nested
    class WriteSetAtomic {
        private final List<PayloadEntry> entries = List.of(
                new PayloadEntry("a_master.png", BYTES),
                new PayloadEntry("a_thumb.png", new byte[] {9}),
                new PayloadEntry("sidecar.json", "{}".getBytes(StandardCharsets.UTF_8)));

        @Test
        void writesEveryFile(@TempDir Path root) throws IOException {
            WrittenSet out = writeSetAtomic(root.resolve("a"), entries);
            assertThat(out.written()).hasSize(3);
            assertThat(namesIn(root.resolve("a"))).isEqualTo(List.of("a_master.png", "a_thumb.png", "sidecar.json"));
        }

        @Test
        void addsTheSetUp(@TempDir Path root) {
            assertThat(writeSetAtomic(root.resolve("a"), entries).bytes()).isEqualTo(7);
        }

        @Test
        void leavesNoPartFilesBehind(@TempDir Path root) throws IOException {
            writeSetAtomic(root.resolve("a"), entries);
            assertThat(namesIn(root.resolve("a")).stream().filter(name -> name.endsWith(".part")))
                    .isEmpty();
        }

        @Test
        void writesNothingAtAllWhenOneOfThemCannotBeWritten(@TempDir Path root) throws IOException {
            Path target = root.resolve("a");
            ensureDirectory(target);
            Files.writeString(target.resolve("a_thumb.png"), "in the way");

            assertThatThrownBy(() -> writeSetAtomic(target, entries, false))
                    .isInstanceOf(StoreError.class)
                    .hasMessageFindingMatch("already a file there");
            assertThat(namesIn(target)).isEqualTo(List.of("a_thumb.png"));
        }

        @Test
        void refusesAnEmptySetRatherThanMakingAnEmptyFolder(@TempDir Path root) {
            assertThatThrownBy(() -> writeSetAtomic(root.resolve("a"), List.of()))
                    .isInstanceOf(StoreError.class)
                    .hasMessageFindingMatch("nothing to write");
        }

        @Test
        void writesIntoASubfolderWhenANameHasOne(@TempDir Path root) throws IOException {
            writeSetAtomic(
                    root.resolve("a"),
                    List.of(new PayloadEntry(Path.of("scans", "x.png").toString(), BYTES)));
            assertThat(namesIn(root.resolve("a").resolve("scans"))).isEqualTo(List.of("x.png"));
        }
    }

    @Nested
    class AppendLine {
        @Test
        void makesTheFileIfItIsNotThere(@TempDir Path root) throws IOException {
            appendLine(root.resolve("ledger.jsonl"), "one");
            assertThat(Files.readString(root.resolve("ledger.jsonl"))).isEqualTo("one\n");
        }

        @Test
        void addsToWhatIsAlreadyThere(@TempDir Path root) throws IOException {
            appendLine(root.resolve("ledger.jsonl"), "one");
            appendLine(root.resolve("ledger.jsonl"), "two");
            assertThat(Files.readString(root.resolve("ledger.jsonl"))).isEqualTo("one\ntwo\n");
        }

        @Test
        void neverLetsALineBecomeTwo(@TempDir Path root) throws IOException {
            appendLine(root.resolve("ledger.jsonl"), "one\nsmuggled");
            assertThat(Files.readString(root.resolve("ledger.jsonl")).split("\n", -1))
                    .hasSize(2);
        }
    }

    @Nested
    class ReadBytesAndReadText {
        @Test
        void readsBackWhatWasWritten(@TempDir Path root) {
            writeAtomic(root.resolve("a.bin"), BYTES);
            assertThat(readBytes(root.resolve("a.bin"))).isEqualTo(BYTES);
        }

        @Test
        void readsTextBackAsText(@TempDir Path root) {
            writeAtomic(root.resolve("a.txt"), "plate 12");
            assertThat(readText(root.resolve("a.txt"))).isEqualTo("plate 12");
        }

        @Test
        void saysWhichFileItCouldNotRead(@TempDir Path root) {
            assertThatThrownBy(() -> readBytes(root.resolve("missing.png")))
                    .isInstanceOf(StoreError.class)
                    .hasMessageFindingMatch("could not be read");
        }
    }

    @Nested
    class ListNames {
        @Test
        void listsWhatIsThereSorted(@TempDir Path root) {
            writeAtomic(root.resolve("b.png"), BYTES);
            writeAtomic(root.resolve("a.png"), BYTES);
            assertThat(listNames(root)).isEqualTo(List.of("a.png", "b.png"));
        }

        @Test
        void leavesTheLeftoversOfACrashedRunOut(@TempDir Path root) throws IOException {
            writeAtomic(root.resolve("a.png"), BYTES);
            Files.writeString(root.resolve("a.png.1234.part"), "half");
            assertThat(listNames(root)).isEqualTo(List.of("a.png"));
        }

        @Test
        void showsThemWhenItIsAskedTo(@TempDir Path root) throws IOException {
            Files.writeString(root.resolve("a.png.1234.part"), "half");
            assertThat(listNames(root, true)).isEqualTo(List.of("a.png.1234.part"));
        }

        @Test
        void saysWhichDirectoryItCouldNotList(@TempDir Path root) {
            assertThatThrownBy(() -> listNames(root.resolve("nowhere")))
                    .isInstanceOf(StoreError.class)
                    .hasMessageFindingMatch("could not be listed");
        }
    }

    @Nested
    class SweepTemporary {
        @Test
        void removesWhatACrashedRunLeft(@TempDir Path root) throws IOException {
            writeAtomic(root.resolve("a.png"), BYTES);
            Files.writeString(root.resolve("a.png.1234.part"), "half");
            assertThat(sweepTemporary(root)).isEqualTo(List.of("a.png.1234.part"));
            assertThat(namesIn(root)).isEqualTo(List.of("a.png"));
        }

        @Test
        void leavesTheFinishedFilesAlone(@TempDir Path root) {
            writeAtomic(root.resolve("a.png"), BYTES);
            assertThat(sweepTemporary(root)).isEqualTo(List.of());
        }
    }
}
