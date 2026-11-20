package io.github.rkaushikdev.decklewright.store;

import static io.github.rkaushikdev.decklewright.store.StorePaths.LEDGER_NAME;
import static io.github.rkaushikdev.decklewright.store.StorePaths.deliveryPath;
import static io.github.rkaushikdev.decklewright.store.StorePaths.filePath;
import static io.github.rkaushikdev.decklewright.store.StorePaths.isInside;
import static io.github.rkaushikdev.decklewright.store.StorePaths.isTemporary;
import static io.github.rkaushikdev.decklewright.store.StorePaths.requireInside;
import static io.github.rkaushikdev.decklewright.store.StorePaths.shortPath;
import static io.github.rkaushikdev.decklewright.store.StorePaths.temporaryFor;
import static io.github.rkaushikdev.decklewright.store.StorePaths.workspacePaths;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.StoreError;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Where things go: the workspace's paths, a delivery's folder, and refusing a path that escapes. */
class PathsTest {

    private static final Path ROOT = resolved("/lab/oversize");

    private static Path resolved(String path) {
        return Path.of(path).toAbsolutePath().normalize();
    }

    @Nested
    class WorkspacePathsBehaviour {
        @Test
        void hasTheFourFoldersARunUses() {
            WorkspacePaths paths = workspacePaths(ROOT);
            assertThat(paths.incoming()).isEqualTo(ROOT.resolve("incoming"));
            assertThat(paths.working()).isEqualTo(ROOT.resolve("working"));
            assertThat(paths.delivered()).isEqualTo(ROOT.resolve("delivered"));
            assertThat(paths.rejected()).isEqualTo(ROOT.resolve("rejected"));
        }

        @Test
        void putsTheLedgerAtTheRoot() {
            assertThat(workspacePaths(ROOT).ledger()).isEqualTo(ROOT.resolve(LEDGER_NAME));
        }

        @Test
        void resolvesARelativeRoot() {
            assertThat(workspacePaths(Path.of(".")).root()).isEqualTo(resolved("."));
        }

        @Test
        void namesTheSameFoldersTheConstantDoes() {
            WorkspacePaths paths = workspacePaths(ROOT);
            for (Folder folder : Folder.values()) {
                assertThat(paths.folder(folder).endsWith(folder.label())).isTrue();
            }
        }
    }

    @Nested
    class DeliveryPath {
        private final WorkspacePaths workspace = workspacePaths(ROOT);

        @Test
        void putsADeliveryUnderDeliveredByDefault() {
            assertThat(deliveryPath(workspace, "g4181-1907"))
                    .isEqualTo(ROOT.resolve("delivered").resolve("g4181-1907"));
        }

        @Test
        void takesAnotherFolder() {
            assertThat(deliveryPath(workspace, "g4181", Folder.REJECTED))
                    .isEqualTo(ROOT.resolve("rejected").resolve("g4181"));
        }

        @Test
        void cleansTheAccessionTheSameWayTheFileNamesAreCleaned() {
            assertThat(deliveryPath(workspace, "sheet 17 / plate 4"))
                    .isEqualTo(ROOT.resolve("delivered").resolve("sheet-17-plate-4"));
        }

        @Test
        void refusesAFolderAWorkspaceDoesNotHave() {
            assertThatThrownBy(() -> deliveryPath(workspace, "a", Folder.parse("archive")))
                    .isInstanceOf(StoreError.class)
                    .hasMessageFindingMatch("no folder by that");
        }

        @Test
        void refusesToTreatTheRootAsAFolderForDeliveries() {
            assertThatThrownBy(() -> deliveryPath(workspace, "a", Folder.parse("root")))
                    .isInstanceOf(StoreError.class)
                    .hasMessageFindingMatch("no folder by that");
        }

        @Test
        void refusesAnAccessionThatCleansAwayToNothing() {
            assertThatThrownBy(() -> deliveryPath(workspace, "///"))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("nothing left");
        }
    }

    @Nested
    class FilePath {
        private final Path directory = Path.of("/lab/a");

        @Test
        void joinsANameOntoADirectory() {
            assertThat(filePath(directory, "master.png")).isEqualTo(directory.resolve("master.png"));
        }

        @Test
        void refusesANameThatClimbsOut() {
            assertThatThrownBy(() -> filePath(directory, "../b/master.png"))
                    .isInstanceOf(StoreError.class)
                    .hasMessageFindingMatch("outside the delivery");
        }

        @Test
        void refusesAnAbsoluteName() {
            assertThatThrownBy(() -> filePath(directory, resolved("/etc/passwd").toString()))
                    .isInstanceOf(StoreError.class)
                    .hasMessageFindingMatch("outside the delivery");
        }

        @Test
        void allowsANameInASubfolder() {
            assertThat(filePath(directory, Path.of("scans", "master.png").toString()))
                    .isEqualTo(directory.resolve("scans").resolve("master.png"));
        }
    }

    @Nested
    class IsInside {
        @Test
        void isTrueForAPathUnderTheDirectory() {
            assertThat(isInside(ROOT, ROOT.resolve("delivered").resolve("a"))).isTrue();
        }

        @Test
        void isFalseForASibling() {
            assertThat(isInside(ROOT.resolve("delivered"), ROOT.resolve("rejected")))
                    .isFalse();
        }

        @Test
        void isFalseForTheDirectoryItself() {
            assertThat(isInside(ROOT, ROOT)).isFalse();
        }

        @Test
        void isFalseForAPathThatClimbsOutAndBackInByName() {
            Path delivered = ROOT.resolve("delivered");
            assertThat(isInside(delivered, Path.of(delivered + "-old"))).isFalse();
        }
    }

    @Nested
    class RequireInside {
        @Test
        void givesTheResolvedPathBack() {
            assertThat(requireInside(ROOT, ROOT.resolve("a"))).isEqualTo(ROOT.resolve("a"));
        }

        @Test
        void refusesOneThatIsNotInside() {
            assertThatThrownBy(() -> requireInside(ROOT, resolved("/elsewhere")))
                    .isInstanceOf(StoreError.class)
                    .hasMessageFindingMatch("outside the workspace");
        }
    }

    @Nested
    class TemporaryFor {
        @Test
        void keepsTheTargetNameInIt() {
            assertThat(temporaryFor(Path.of("/lab/a/master.png"), "x")).isEqualTo(Path.of("/lab/a/master.png.x.part"));
        }

        @Test
        void endsInPartSoASweepCanFindIt() {
            assertThat(isTemporary(temporaryFor(Path.of("/lab/a/master.png")).toString()))
                    .isTrue();
        }

        @Test
        void givesTwoDifferentNamesWithoutAToken() {
            Path first = temporaryFor(Path.of("/lab/a/master.png"), "one");
            Path second = temporaryFor(Path.of("/lab/a/master.png"), "two");
            assertThat(first).isNotEqualTo(second);
        }
    }

    @Nested
    class IsTemporary {
        @Test
        void isFalseForAFinishedFile() {
            assertThat(isTemporary("master.png")).isFalse();
        }

        @Test
        void isFalseForANameThatOnlyContainsPart() {
            assertThat(isTemporary("partial.png")).isFalse();
        }
    }

    @Nested
    class ShortPath {
        @Test
        void keepsTheLastFewSegments() {
            assertThat(shortPath(Path.of("a", "b", "c", "d", "e"), 2))
                    .isEqualTo("..." + File.separator + Path.of("d", "e"));
        }

        @Test
        void leavesAShortPathAlone() {
            assertThat(shortPath(Path.of("a", "b"))).isEqualTo(Path.of("a", "b").toString());
        }
    }
}
