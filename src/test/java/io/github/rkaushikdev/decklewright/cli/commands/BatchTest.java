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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BatchTest {

    private static final Instant AT = Instant.parse("2025-12-11T08:00:00Z");

    private static final Command QC = new QcCommand();
    private static final Command BATCH = new BatchCommand();

    private static Ran invoke(Command command, String... argv) {
        return Console.invoke(command, List.of(argv), AT);
    }

    private static Path workspaceWith(Path root, Map<String, byte[]> files) {
        Workspace space = Workspace.open(root);
        files.forEach((name, bytes) ->
                AtomicFiles.writeAtomic(space.paths().incoming().resolve(name), bytes));
        return root;
    }

    private static Map<String, byte[]> files(Object... pairs) {
        Map<String, byte[]> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            out.put((String) pairs[i], (byte[]) pairs[i + 1]);
        }
        return out;
    }

    @Nested
    class Qc {
        @Test
        void gradesACaptureAndExitsZeroWhenItPassed(@TempDir Path root) throws IOException {
            Files.write(root.resolve("g4181.png"), Console.capturePng());
            Ran ran = invoke(QC, root.resolve("g4181.png").toString(), "-r", "400");
            assertThat(ran.code()).isZero();
            assertThat(ran.out().get(0)).containsPattern("^g4181: ");
        }

        @Test
        void exitsOneWhenItDidNot(@TempDir Path root) throws IOException {
            Files.write(root.resolve("g4181.png"), Console.capturePng(3));
            Ran ran = invoke(QC, root.resolve("g4181.png").toString(), "-r", "400");
            assertThat(ran.code()).isEqualTo(1);
            assertThat(ran.outText()).containsPattern("square the sheet");
        }

        @Test
        void measuresAgainstAnotherLevelWhenItIsToldOne(@TempDir Path root) throws IOException {
            Files.write(root.resolve("g4181.png"), Console.capturePng());
            String path = root.resolve("g4181.png").toString();
            assertThat(invoke(QC, path, "-r", "400", "-s", "4").code()).isZero();
            assertThat(invoke(QC, path, "-r", "100", "-s", "4").code()).isEqualTo(1);
        }

        @Test
        void writesJsonWhenItIsAskedTo(@TempDir Path root) throws IOException {
            Files.write(root.resolve("g4181.png"), Console.capturePng());
            Ran ran = invoke(QC, root.resolve("g4181.png").toString(), "-r", "400", "--json");
            Map<?, ?> document = (Map<?, ?>) Json.parse(ran.outText());
            assertThat(document.get("item")).isEqualTo("g4181");
            assertThat((Map<?, ?>) document.get("measurements")).hasSize(8);
        }

        @Test
        void gradesTheCropWhenItIsToldTo(@TempDir Path root) throws IOException {
            Files.write(root.resolve("g4181.png"), Console.capturePng());
            assertThat(invoke(QC, root.resolve("g4181.png").toString(), "-r", "400", "--crop")
                            .code())
                    .isZero();
        }

        @Test
        void asksForACaptureWhenItWasGivenNone() {
            Ran ran = invoke(QC);
            assertThat(ran.code()).isEqualTo(2);
            assertThat(ran.err().get(0)).containsPattern("which capture");
        }
    }

    @Nested
    class Batch {
        @Test
        void worksEverythingWaitingAndSaysSo(@TempDir Path dir) {
            Path root = workspaceWith(dir, files("g4181.png", Console.capturePng(), "g4182.png", Console.capturePng()));
            Ran ran = invoke(BATCH, root.toString(), "-r", "400");

            assertThat(ran.code()).isZero();
            assertThat(ran.out().get(0)).isEqualTo("2 waiting, profile house");
            assertThat(Console.namesIn(root.resolve("delivered"))).containsExactly("g4181", "g4182");
        }

        @Test
        void writesASummaryAtTheEnd(@TempDir Path dir) {
            Path root = workspaceWith(dir, files("g4181.png", Console.capturePng()));
            Ran ran = invoke(BATCH, root.toString(), "-r", "400");
            assertThat(ran.outText()).containsPattern("1 accessions, \\d+ bytes");
            assertThat(ran.outText()).containsPattern("1 delivered");
        }

        @Test
        void saysSoWhenNothingIsWaiting(@TempDir Path dir) {
            Path root = workspaceWith(dir, files());
            Ran ran = invoke(BATCH, root.toString());
            assertThat(ran.code()).isZero();
            assertThat(ran.out()).containsExactly("nothing is waiting");
        }

        @Test
        void carriesOnPastACaptureItCouldNotReadAndExitsOne(@TempDir Path dir) {
            Path root =
                    workspaceWith(dir, files("broken.png", new byte[] {1, 2, 3}, "g4181.png", Console.capturePng()));
            Ran ran = invoke(BATCH, root.toString(), "-r", "400");

            assertThat(ran.code()).isEqualTo(1);
            assertThat(String.join(" ", ran.err())).containsPattern("1 accessions need somebody");
            assertThat(Console.namesIn(root.resolve("delivered"))).containsExactly("g4181");
        }

        @Test
        void stopsAtTheFirstFailureWhenItIsToldTo(@TempDir Path dir) {
            Path root =
                    workspaceWith(dir, files("broken.png", new byte[] {1, 2, 3}, "g4181.png", Console.capturePng()));
            invoke(BATCH, root.toString(), "-r", "400", "--stop");
            assertThat(Console.namesIn(root.resolve("delivered"))).isEmpty();
        }

        @Test
        void rejectsWorkUnderTheLevelItWasToldToDeliverAt(@TempDir Path dir) {
            Path root = workspaceWith(dir, files("g4181.png", Console.capturePng(3)));
            invoke(BATCH, root.toString(), "-r", "400");
            assertThat(Console.namesIn(root.resolve("rejected"))).containsExactly("g4181");
            assertThat(Console.namesIn(root.resolve("delivered"))).isEmpty();
        }

        @Test
        void writesOneLinePerAccessionWhenItIsAskedToBeQuiet(@TempDir Path dir) {
            Path root = workspaceWith(dir, files("g4181.png", Console.capturePng()));
            Ran ran = invoke(BATCH, root.toString(), "-r", "400", "-q");
            assertThat(ran.out().stream().filter(line -> line.startsWith("g4181")))
                    .hasSize(1);
        }

        @Test
        void recordsTheVersionItWasToldIntoTheProvenance(@TempDir Path dir) {
            Path root = workspaceWith(dir, files("g4181.png", Console.capturePng()));
            invoke(BATCH, root.toString(), "-r", "400", "--version", "2.1.0");

            Object chain = Json.parse(AtomicFiles.readText(
                    root.resolve("delivered").resolve("g4181").resolve("provenance.json")));
            assertThat(((Map<?, ?>) ((List<?>) chain).get(0)).get("agent")).isEqualTo("decklewright 2.1.0");
        }

        @Test
        void holdsASecondRunOverTheSameAccessionRatherThanReplacingIt(@TempDir Path dir) {
            Path root = workspaceWith(dir, files("g4181.png", Console.capturePng()));
            invoke(BATCH, root.toString(), "-r", "400");
            Ran ran = invoke(BATCH, root.toString(), "-r", "400");
            assertThat(ran.code()).isEqualTo(1);
            assertThat(String.join(" ", ran.err())).containsPattern("need somebody");
        }

        @Test
        void replacesOneWhenItIsToldTo(@TempDir Path dir) {
            Path root = workspaceWith(dir, files("g4181.png", Console.capturePng()));
            invoke(BATCH, root.toString(), "-r", "400");
            assertThat(invoke(BATCH, root.toString(), "-r", "400", "-f").code()).isZero();
        }

        @Test
        void refusesADirectoryThatIsNotAWorkspace(@TempDir Path root) {
            Ran ran = invoke(BATCH, root.toString());
            assertThat(ran.code()).isEqualTo(1);
            assertThat(ran.err().get(0)).containsPattern("not a workspace");
        }

        @Test
        void asksForAWorkspaceWhenItWasGivenNone() {
            Ran ran = invoke(BATCH);
            assertThat(ran.code()).isEqualTo(2);
            assertThat(ran.err().get(0)).containsPattern("which workspace");
        }
    }
}
