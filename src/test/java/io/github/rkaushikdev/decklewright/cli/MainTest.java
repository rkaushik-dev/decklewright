package io.github.rkaushikdev.decklewright.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.decklewright.cli.commands.Command;
import io.github.rkaushikdev.decklewright.cli.commands.Io;
import io.github.rkaushikdev.decklewright.store.AtomicFiles;
import io.github.rkaushikdev.decklewright.store.Workspace;
import io.github.rkaushikdev.decklewright.testing.Console;
import io.github.rkaushikdev.decklewright.testing.Console.Ran;
import io.github.rkaushikdev.decklewright.util.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTest {

    private static Ran invoke(List<String> argv) {
        return invoke(argv, "1.4.0");
    }

    private static Ran invoke(List<String> argv, String version) {
        Console console = new Console();
        int code = Main.run(argv, console.io(), version);
        return new Ran(code, console.out(), console.err());
    }

    private static Object firstAgent(Path root, String accession) {
        Object chain = Json.parse(AtomicFiles.readText(
                root.resolve("delivered").resolve(accession).resolve("provenance.json")));
        return ((Map<?, ?>) ((List<?>) chain).get(0)).get("agent");
    }

    @Nested
    class Commands {
        @Test
        void hasEveryCommandTheHelpLists() {
            assertThat(Main.COMMANDS.keySet()).containsExactly("plan", "crop", "qc", "batch", "profile", "verify");
        }

        @Test
        void givesEachOneAUsageADescriptionOptionsAndARun() {
            for (Map.Entry<String, Command> entry : Main.COMMANDS.entrySet()) {
                Command command = entry.getValue();
                assertThat(command.usage()).as(entry.getKey()).isNotBlank();
                assertThat(command.describe()).as(entry.getKey()).isNotBlank();
                assertThat(command.options()).as(entry.getKey()).isNotNull();
                assertThat(command).as(entry.getKey()).isInstanceOf(Command.class);
            }
        }

        @Test
        void namesItselfTheSameWayInEveryUsageLine() {
            for (Command command : Main.COMMANDS.values()) {
                assertThat(command.usage().startsWith(Main.PROGRAM + " ")).isTrue();
            }
        }
    }

    @Nested
    class Help {
        @Test
        void listsEveryCommandWithWhatItDoes() {
            String said = String.join("\n", Main.help("1.4.0"));
            for (Map.Entry<String, Command> entry : Main.COMMANDS.entrySet()) {
                assertThat(said).contains(entry.getKey());
                assertThat(said).contains(entry.getValue().describe());
            }
        }

        @Test
        void namesTheVersionWhenItHasOne() {
            assertThat(Main.help("1.4.0").get(0)).containsPattern("^deckle 1\\.4\\.0, ");
            assertThat(Main.help().get(0)).containsPattern("^deckle, ");
        }
    }

    @Nested
    class HelpFor {
        @Test
        void showsTheUsageAndTheOptionsOfOneCommand() {
            String said = String.join("\n", Main.helpFor("crop"));
            assertThat(said).containsPattern("deckle crop <capture>");
            assertThat(said).containsPattern("--out <string>");
        }
    }

    @Nested
    class NearestCommands {
        @Test
        void findsOneThatStartsTheSameWay() {
            assertThat(Main.nearestCommands("cro")).containsExactly("crop");
        }

        @Test
        void findsOneTheTypedNameStartsWith() {
            assertThat(Main.nearestCommands("batchall")).containsExactly("batch");
        }

        @Test
        void findsOneWithTheSameLettersInAnotherOrder() {
            assertThat(Main.nearestCommands("propfile")).contains("profile");
        }

        @Test
        void findsNothingForAWordWithNothingInCommon() {
            assertThat(Main.nearestCommands("zzzz")).isEmpty();
        }
    }

    @Nested
    class Run {
        @Test
        void showsTheHelpAndExitsTwoWhenItWasGivenNothing() {
            Ran ran = invoke(List.of());
            assertThat(ran.code()).isEqualTo(2);
            assertThat(ran.out().get(0)).containsPattern("^deckle 1\\.4\\.0");
        }

        @Test
        void showsTheHelpAndExitsZeroWhenItWasAskedForIt() {
            for (List<String> argv : List.of(List.of("--help"), List.of("-h"), List.of("help"))) {
                Ran ran = invoke(argv);
                assertThat(ran.code()).isZero();
                assertThat(ran.outText()).containsPattern("usage: deckle <command>");
            }
        }

        @Test
        void saysTheVersion() {
            assertThat(invoke(List.of("--version")).out()).containsExactly("1.4.0");
            assertThat(invoke(List.of("-V")).out()).containsExactly("1.4.0");
        }

        @Test
        void showsTheHelpForOneCommand() {
            Ran ran = invoke(List.of("crop", "--help"));
            assertThat(ran.code()).isZero();
            assertThat(ran.out().get(0)).containsPattern("^deckle crop");
        }

        @Test
        void runsACommand(@TempDir Path root) throws IOException {
            Files.write(root.resolve("g4181.png"), Console.capturePng());
            Ran ran = invoke(List.of("plan", root.resolve("g4181.png").toString()));
            assertThat(ran.code()).isZero();
            assertThat(ran.out().get(0)).isEqualTo("g4181, profile house");
        }

        @Test
        void givesBackTheCodeTheCommandGave(@TempDir Path root) throws IOException {
            Files.write(root.resolve("broken.png"), new byte[] {1});
            assertThat(invoke(List.of("plan", root.resolve("broken.png").toString()))
                            .code())
                    .isEqualTo(1);
        }

        @Test
        void refusesACommandNobodyHasAndSuggestsOne() {
            Ran ran = invoke(List.of("crup", "x.png"));
            assertThat(ran.code()).isEqualTo(2);
            assertThat(ran.err().get(0)).containsPattern("no command called crup");
            assertThat(ran.err().get(1)).containsPattern("did you mean crop");
        }

        @Test
        void listsTheCommandsWhenNothingIsClose() {
            Ran ran = invoke(List.of("zzzz"));
            assertThat(ran.err().get(1)).containsPattern("the commands are plan, crop, qc");
        }

        @Test
        void exitsTwoForAnOptionACommandDoesNotHave() {
            Ran ran = invoke(List.of("crop", "x.png", "--colour"));
            assertThat(ran.code()).isEqualTo(2);
            assertThat(ran.err().get(0)).containsPattern("no such option");
            assertThat(ran.err().get(ran.err().size() - 1)).containsPattern("try deckle crop --help");
        }

        @Test
        void givesTheVersionToACommandThatRecordsOne(@TempDir Path root) {
            Workspace space = Workspace.open(root);
            AtomicFiles.writeAtomic(space.paths().incoming().resolve("g4181.png"), Console.capturePng());

            invoke(List.of("batch", root.toString(), "-r", "400"), "3.2.1");
            assertThat(firstAgent(root, "g4181")).isEqualTo("decklewright 3.2.1");
        }

        @Test
        void letsACommandSayItsOwnVersionOverTheProgramOne(@TempDir Path root) {
            Workspace space = Workspace.open(root);
            AtomicFiles.writeAtomic(space.paths().incoming().resolve("g4181.png"), Console.capturePng());

            invoke(List.of("batch", root.toString(), "-r", "400", "--version", "9.9.9"), "3.2.1");
            assertThat(firstAgent(root, "g4181")).isEqualTo("decklewright 9.9.9");
        }
    }

    /** The process entry, which hands every word it is given to the run unchanged. */
    @Nested
    class TheProcessEntry {
        private Ran execute(String... args) {
            Console console = new Console();
            Io io = console.io();
            int code = Main.commandLine(io, "1.4.0").execute(args);
            return new Ran(code, console.out(), console.err());
        }

        @Test
        void runsTheToolAndReturnsItsCode() {
            Ran ran = execute("--version");
            assertThat(ran.code()).isZero();
            assertThat(ran.out()).containsExactly("1.4.0");
            assertThat(execute().code()).isEqualTo(2);
        }

        @Test
        void passesALoneDoubleDashThroughToTheCommand() {
            Ran ended = execute("crop", "--", "--colour");
            assertThat(ended.code()).isEqualTo(2);
            assertThat(ended.err()).containsExactly("which capture? try: deckle crop g4181.png --out delivered");

            Ran control = execute("crop", "--colour");
            assertThat(control.code()).isEqualTo(2);
            assertThat(control.err().get(0)).isEqualTo("there is no such option");
        }

        @Test
        void leavesAnAtWordAsItWasTyped(@TempDir Path root) throws IOException {
            Path listed = root.resolve("words.txt");
            Files.writeString(listed, "--version\n");
            Ran ran = execute("plan", "@" + listed);
            assertThat(ran.code()).isEqualTo(1);
            assertThat(ran.errText()).contains("@" + listed);
        }

        @Test
        void passesHelpWordsToTheCommandRatherThanAnsweringThem() {
            Ran ran = execute("crop", "-h");
            assertThat(ran.code()).isZero();
            assertThat(ran.out()).isEqualTo(Main.helpFor("crop"));
        }

        @Test
        void reportsTheUnpackagedVersionWhenItIsNotRunFromAJar() {
            assertThat(Main.version()).isEqualTo("0.0.0");
        }
    }
}
