package io.github.rkaushikdev.decklewright.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.decklewright.store.AtomicFiles;
import io.github.rkaushikdev.decklewright.store.Workspace;
import io.github.rkaushikdev.decklewright.testing.Console;
import io.github.rkaushikdev.decklewright.testing.Console.Ran;
import io.github.rkaushikdev.decklewright.util.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VerifyTest {

    private static final Instant AT = Instant.parse("2025-12-15T08:00:00Z");

    private static final Command BATCH = new BatchCommand();
    private static final Command PROFILE = new ProfileCommand();
    private static final Command VERIFY = new VerifyCommand();

    private static Ran invoke(Command command, String... argv) {
        return Console.invoke(command, List.of(argv), AT);
    }

    /** A workspace with the given number of captures worked into delivered, as {@code g4180} on. */
    private static Path delivered(Path root, int count) {
        Workspace space = Workspace.open(root);
        for (int i = 0; i < count; i += 1) {
            AtomicFiles.writeAtomic(space.paths().incoming().resolve("g418" + i + ".png"), Console.capturePng());
        }
        invoke(BATCH, root.toString(), "-r", "400");
        return root;
    }

    private static Path delivered(Path root) {
        return delivered(root, 1);
    }

    @Nested
    class ProfileBehaviour {
        @Test
        void listsWhatShips() {
            Ran ran = invoke(PROFILE);
            assertThat(ran.code()).isZero();
            assertThat(ran.out().stream().map(line -> line.split("  ", -1)[0]))
                    .containsExactly("house", "atlas-plates", "quadrangles", "rolled-drawings", "microfilm");
        }

        @Test
        void showsOneResolvedWithItsParentsFoldedIn() {
            Ran ran = invoke(PROFILE, "atlas-plates");
            assertThat(ran.out().get(0)).containsPattern("^atlas-plates: county atlas plates");
            assertThat(ran.outText()).containsPattern("crops to the");
        }

        @Test
        void writesItAsJsonWhenItIsAskedTo() {
            Ran ran = invoke(PROFILE, "quadrangles", "--json");
            Map<?, ?> document = (Map<?, ?>) Json.parse(ran.outText());
            assertThat(document.get("name")).isEqualTo("quadrangles");
            assertThat(((Map<?, ?>) document.get("crop")).get("anchor")).isEqualTo("neatline");
        }

        @Test
        void showsOneSettingOnItsOwn() {
            Ran ran = invoke(PROFILE, "quadrangles", "-s", "qc.minimumStars");
            assertThat(ran.code()).isZero();
            assertThat(ran.out()).containsExactly("4");
        }

        @Test
        void saysSoWhenASettingIsNotThere() {
            Ran ran = invoke(PROFILE, "house", "-s", "qc.colour");
            assertThat(ran.code()).isEqualTo(1);
            assertThat(ran.err().get(0)).containsPattern("no setting called qc.colour");
        }

        @Test
        void listsEverySettingAProfileCanCarry() {
            Ran ran = invoke(PROFILE, "--paths");
            assertThat(ran.out()).contains("qc.minimumStars");
            assertThat(ran.out().size()).isGreaterThan(10);
        }

        @Test
        void reportsAProfileThatContradictsItself(@TempDir Path root) throws IOException {
            Path path = root.resolve("wrong.json");
            Files.writeString(
                    path,
                    "{\"name\":\"wrong\",\"extends\":\"house\",\"version\":2,\"crop\":{\"anchor\":\"neatline\"}}");
            Ran ran = invoke(PROFILE, path.toString());
            assertThat(ran.code()).isEqualTo(1);
            assertThat(String.join(" ", ran.err())).containsPattern("never looks for one");
        }

        @Test
        void refusesANameNothingKnows() {
            Ran ran = invoke(PROFILE, "nothing");
            assertThat(ran.code()).isEqualTo(1);
            assertThat(ran.err().get(0)).containsPattern("no profile by that name");
        }
    }

    @Nested
    class Verify {
        @Test
        void passesAWorkspaceNobodyHasTouched(@TempDir Path dir) {
            Path root = delivered(dir, 2);
            Ran ran = invoke(VERIFY, root.toString());
            assertThat(ran.code()).isZero();
            assertThat(ran.lastOut()).containsPattern("2 accessions, \\d+ files, 0 with problems");
        }

        @Test
        void namesTheFileThatChanged(@TempDir Path dir) throws IOException {
            Path root = delivered(dir);
            Files.writeString(root.resolve("delivered/g4180/g4180_thumb.png"), "not a png");
            Ran ran = invoke(VERIFY, root.toString());

            assertThat(ran.code()).isEqualTo(1);
            assertThat(ran.errText()).containsPattern("g4180_thumb\\.png");
        }

        @Test
        void keepsGoingPastABadOne(@TempDir Path dir) throws IOException {
            Path root = delivered(dir, 2);
            Files.writeString(root.resolve("delivered/g4180/g4180_thumb.png"), "not a png");
            Ran ran = invoke(VERIFY, root.toString());
            assertThat(ran.lastOut()).containsPattern("2 accessions, \\d+ files, 1 with problems");
        }

        @Test
        void checksOnlyTheAccessionsItWasNamed(@TempDir Path dir) {
            Path root = delivered(dir, 2);
            Ran ran = invoke(VERIFY, root.toString(), "g4181");
            assertThat(ran.lastOut()).containsPattern("^1 accessions");
        }

        @Test
        void saysNothingAboutTheGoodOnesWhenItIsAskedToBeQuiet(@TempDir Path dir) {
            Path root = delivered(dir, 2);
            Ran ran = invoke(VERIFY, root.toString(), "-q");
            assertThat(ran.out()).hasSize(1);
        }

        @Test
        void writesJsonWhenItIsAskedTo(@TempDir Path dir) {
            Path root = delivered(dir);
            Ran ran = invoke(VERIFY, root.toString(), "--json");
            Map<?, ?> document = (Map<?, ?>) Json.parse(ran.outText());
            assertThat(document.get("ok")).isEqualTo(true);
            assertThat(document.get("accessions")).isEqualTo(1.0);
        }

        @Test
        void saysSoWhenThereIsNothingInTheFolder(@TempDir Path root) {
            Workspace.open(root);
            Ran ran = invoke(VERIFY, root.toString());
            assertThat(ran.code()).isZero();
            assertThat(ran.out()).containsExactly("nothing in delivered");
        }

        @Test
        void sweepsAnotherFolderWhenItIsToldTo(@TempDir Path dir) {
            Path root = delivered(dir);
            Ran ran = invoke(VERIFY, root.toString(), "-d", "rejected");
            assertThat(ran.out()).containsExactly("nothing in rejected");
        }

        @Test
        void reportsAnAccessionThatIsNotThereRatherThanThrowing(@TempDir Path dir) {
            Path root = delivered(dir);
            Ran ran = invoke(VERIFY, root.toString(), "nobody");
            assertThat(ran.code()).isEqualTo(1);
            assertThat(String.join(" ", ran.err())).containsPattern("no delivery under that accession");
        }

        @Test
        void refusesADirectoryThatIsNotAWorkspace(@TempDir Path root) {
            Ran ran = invoke(VERIFY, root.toString());
            assertThat(ran.code()).isEqualTo(1);
            assertThat(ran.err().get(0)).containsPattern("not a workspace");
        }

        @Test
        void asksForAWorkspaceWhenItWasGivenNone() {
            Ran ran = invoke(VERIFY);
            assertThat(ran.code()).isEqualTo(2);
            assertThat(ran.err().get(0)).containsPattern("which workspace");
        }
    }
}
