package io.github.rkaushikdev.decklewright.store;

import static io.github.rkaushikdev.decklewright.manifest.Payloads.SIDECAR_NAME;
import static io.github.rkaushikdev.decklewright.store.AtomicFiles.writeAtomic;
import static io.github.rkaushikdev.decklewright.store.AtomicTest.namesIn;
import static io.github.rkaushikdev.decklewright.testing.Accession.delivery;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.StoreError;
import io.github.rkaushikdev.decklewright.manifest.Payload;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The four folders a shift moves material through, and the io on them. */
class WorkspaceTest {

    private static Workspace workspace(Path scratch) {
        return Workspace.open(scratch.resolve("ws"));
    }

    @Nested
    class Open {
        @Test
        void makesTheFourFolders(@TempDir Path scratch) throws IOException {
            Workspace store = workspace(scratch);
            assertThat(namesIn(store.root())).isEqualTo(List.of("delivered", "incoming", "rejected", "working"));
        }

        @Test
        void isHappyWithAWorkspaceThatIsAlreadyThere(@TempDir Path scratch) {
            Workspace store = workspace(scratch);
            assertThat(Workspace.open(store.root())).isInstanceOf(Workspace.class);
        }
    }

    @Nested
    class At {
        @Test
        void opensOneThatExists(@TempDir Path scratch) {
            Workspace store = workspace(scratch);
            assertThat(Workspace.at(store.root()).root()).isEqualTo(store.root());
        }

        @Test
        void refusesADirectoryThatIsNotAWorkspace(@TempDir Path scratch) throws IOException {
            Path plain = Files.createDirectory(scratch.resolve("plain"));
            assertThatThrownBy(() -> Workspace.at(plain))
                    .isInstanceOf(StoreError.class)
                    .hasMessageFindingMatch("not a workspace");
        }
    }

    @Nested
    class Incoming {
        @Test
        void listsTheCapturesWaiting(@TempDir Path scratch) {
            Workspace store = workspace(scratch);
            writeAtomic(store.paths().incoming().resolve("g4181.png"), new byte[] {1, 2});
            List<IncomingCapture> waiting = store.incoming();
            assertThat(waiting).hasSize(1);
            assertThat(waiting.get(0).accession()).isEqualTo("g4181");
            assertThat(waiting.get(0).bytes()).isEqualTo(2);
        }

        @Test
        void leavesTheSpreadsheetAndTheNotesOut(@TempDir Path scratch) {
            Workspace store = workspace(scratch);
            writeAtomic(store.paths().incoming().resolve("g4181.png"), new byte[] {1});
            writeAtomic(store.paths().incoming().resolve("shift notes.txt"), "nothing to report");
            assertThat(store.incoming()).extracting(IncomingCapture::name).containsExactly("g4181.png");
        }

        @Test
        void hasNothingToSayAboutAnEmptyFolder(@TempDir Path scratch) {
            assertThat(workspace(scratch).incoming()).isEqualTo(List.of());
        }
    }

    @Nested
    class Put {
        @Test
        void writesEveryFileOfTheDelivery(@TempDir Path scratch) throws IOException {
            Workspace store = workspace(scratch);
            WrittenSet out = store.put(delivery());
            assertThat(out.written().size()).isGreaterThan(4);
            assertThat(namesIn(out.directory())).contains(SIDECAR_NAME);
        }

        @Test
        void putsItUnderTheAccession(@TempDir Path scratch) {
            Workspace store = workspace(scratch);
            store.put(delivery());
            assertThat(store.list()).isEqualTo(List.of("g4181-1907"));
        }

        @Test
        void refusesToDeliverTheSameAccessionTwice(@TempDir Path scratch) {
            Workspace store = workspace(scratch);
            store.put(delivery());
            assertThatThrownBy(() -> store.put(delivery()))
                    .isInstanceOf(StoreError.class)
                    .hasMessageFindingMatch("already been delivered");
        }

        @Test
        void replacesOneWhenItIsToldTo(@TempDir Path scratch) {
            Workspace store = workspace(scratch);
            store.put(delivery());
            assertThat(store.put(delivery(), Folder.DELIVERED, true)).isNotNull();
        }

        @Test
        void takesAnotherFolder(@TempDir Path scratch) {
            Workspace store = workspace(scratch);
            store.put(delivery(), Folder.REJECTED);
            assertThat(store.list(Folder.REJECTED)).isEqualTo(List.of("g4181-1907"));
            assertThat(store.list()).isEqualTo(List.of());
        }

        @Test
        void refusesADeliveryThatDoesNotAgreeWithItself(@TempDir Path scratch) {
            Workspace store = workspace(scratch);
            Payload good = delivery();
            Payload bad = new Payload(
                    good.accession(),
                    good.entries().stream()
                            .filter(entry -> !entry.name().equals(SIDECAR_NAME))
                            .toList(),
                    good.digest());
            assertThatThrownBy(() -> store.put(bad))
                    .isInstanceOf(StoreError.class)
                    .hasMessageFindingMatch("does not agree with itself");
        }
    }

    @Nested
    class Has {
        @Test
        void isFalseBeforeAndTrueAfter(@TempDir Path scratch) {
            Workspace store = workspace(scratch);
            assertThat(store.has("g4181-1907")).isFalse();
            store.put(delivery());
            assertThat(store.has("g4181-1907")).isTrue();
        }
    }

    @Nested
    class Get {
        @Test
        void readsBackWhatPutWrote(@TempDir Path scratch) {
            Workspace store = workspace(scratch);
            Payload written = delivery();
            store.put(written);
            Payload read = store.get("g4181-1907");
            assertThat(read.digest()).isEqualTo(written.digest());
        }

        @Test
        void saysSoWhenThereIsNothingUnderThatAccession(@TempDir Path scratch) {
            Workspace store = workspace(scratch);
            assertThatThrownBy(() -> store.get("nobody"))
                    .isInstanceOf(StoreError.class)
                    .hasMessageFindingMatch("no delivery under that accession");
        }
    }

    @Nested
    class Verify {
        @Test
        void passesADeliveryThatHasNotBeenTouched(@TempDir Path scratch) {
            Workspace store = workspace(scratch);
            store.put(delivery());
            DeliveryCheck out = store.verify("g4181-1907");
            assertThat(out.ok()).isTrue();
            assertThat(out.checked()).isEqualTo(3);
        }

        @Test
        void catchesAFileEditedOnTheDisk(@TempDir Path scratch) throws IOException {
            Workspace store = workspace(scratch);
            WrittenSet out = store.put(delivery());
            Files.writeString(out.directory().resolve("g4181-1907_thumb.png"), "not a png any more");
            DeliveryCheck checked = store.verify("g4181-1907");
            assertThat(checked.ok()).isFalse();
            assertThat(String.join(" ", checked.problems())).containsPattern("g4181-1907_thumb\\.png");
        }

        @Test
        void catchesAFileRemovedFromTheFolder(@TempDir Path scratch) throws IOException {
            Workspace store = workspace(scratch);
            WrittenSet out = store.put(delivery());
            Files.delete(out.directory().resolve("g4181-1907_thumb.png"));
            assertThat(String.join(" ", store.verify("g4181-1907").problems())).containsPattern("is not here");
        }

        @Test
        void reportsRatherThanThrowingForAnAccessionThatIsNotThere(@TempDir Path scratch) {
            DeliveryCheck out = workspace(scratch).verify("nobody");
            assertThat(out.ok()).isFalse();
            assertThat(out.problems().get(0)).containsPattern("no delivery under that accession");
        }

        @Test
        void saysEachProblemOnce(@TempDir Path scratch) throws IOException {
            Workspace store = workspace(scratch);
            WrittenSet out = store.put(delivery());
            Files.writeString(out.directory().resolve("g4181-1907_thumb.png"), "x");
            DeliveryCheck checked = store.verify("g4181-1907");
            assertThat(new HashSet<>(checked.problems()))
                    .hasSize(checked.problems().size());
        }
    }

    @Nested
    class Move {
        @Test
        void movesADeliveryBetweenFolders(@TempDir Path scratch) {
            Workspace store = workspace(scratch);
            store.put(delivery());
            store.move("g4181-1907", Folder.DELIVERED, Folder.REJECTED);
            assertThat(store.list()).isEqualTo(List.of());
            assertThat(store.list(Folder.REJECTED)).isEqualTo(List.of("g4181-1907"));
        }

        @Test
        void refusesToMoveOneThatIsNotThere(@TempDir Path scratch) {
            Workspace store = workspace(scratch);
            assertThatThrownBy(() -> store.move("nobody", Folder.DELIVERED, Folder.REJECTED))
                    .isInstanceOf(StoreError.class)
                    .hasMessageFindingMatch("nothing to move");
        }

        @Test
        void refusesToMoveOneOnTopOfAnother(@TempDir Path scratch) {
            Workspace store = workspace(scratch);
            store.put(delivery());
            store.put(delivery(), Folder.REJECTED);
            assertThatThrownBy(() -> store.move("g4181-1907", Folder.DELIVERED, Folder.REJECTED))
                    .isInstanceOf(StoreError.class)
                    .hasMessageFindingMatch("already one there");
        }
    }

    @Nested
    class Tidy {
        @Test
        void emptiesTheWorkingFolder(@TempDir Path scratch) {
            Workspace store = workspace(scratch);
            writeAtomic(store.paths().working().resolve("half.png"), new byte[] {1});
            store.tidy();
            assertThat(store.list(Folder.WORKING)).isEqualTo(List.of());
        }

        @Test
        void sweepsUpWhatACrashedRunLeft(@TempDir Path scratch) throws IOException {
            Workspace store = workspace(scratch);
            Files.writeString(store.paths().working().resolve("a.png.999.part"), "half");
            assertThat(store.tidy()).isEqualTo(List.of("a.png.999.part"));
        }

        @Test
        void leavesTheDeliveredFolderAlone(@TempDir Path scratch) {
            Workspace store = workspace(scratch);
            store.put(delivery());
            store.tidy();
            assertThat(store.list()).isEqualTo(List.of("g4181-1907"));
        }
    }
}
