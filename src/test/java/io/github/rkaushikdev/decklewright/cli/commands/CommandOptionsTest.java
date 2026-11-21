package io.github.rkaushikdev.decklewright.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.testing.Console;
import io.github.rkaushikdev.decklewright.testing.Console.Ran;
import io.github.rkaushikdev.decklewright.util.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The options and the error report that the command tests above do not reach. */
class CommandOptionsTest {

    private static Ran invoke(Command command, String... argv) {
        return Console.invoke(command, List.of(argv), io -> io);
    }

    private static String captureIn(Path root) throws IOException {
        Path path = root.resolve("g4181.png");
        Files.write(path, Console.capturePng());
        return path.toString();
    }

    @Nested
    class PlanRules {
        @Test
        void namesTheRulesThatRanAndChangedNothing(@TempDir Path root) throws IOException {
            String path = captureIn(root);
            Map<?, ?> plan = (Map<?, ?>)
                    Json.parse(invoke(new PlanCommand(), path, "--json").outText());
            List<Object> tried = List.copyOf((List<?>) plan.get("rules"));

            Ran ran = invoke(new PlanCommand(), path, "--rules");
            assertThat(ran.code()).isZero();
            String quiet = ran.out().stream()
                    .filter(line -> line.startsWith("rules that changed nothing: "))
                    .findFirst()
                    .orElseThrow()
                    .substring("rules that changed nothing: ".length());
            List<String> named = Arrays.asList(quiet.split(", ", -1));
            assertThat(named).isNotEmpty().doesNotContain("").allSatisfy(name -> assertThat(tried)
                    .contains(name));
        }

        @Test
        void leavesTheQuietRulesOutWhenNotAsked(@TempDir Path root) throws IOException {
            Ran ran = invoke(new PlanCommand(), captureIn(root));
            assertThat(ran.outText()).doesNotContain("rules that changed nothing");
        }

        @Test
        void reportsLengthsInTheUnitItIsGiven(@TempDir Path root) throws IOException {
            String path = captureIn(root);
            assertThat(invoke(new PlanCommand(), path, "-u", "mm").outText()).containsPattern("\\bmm\\b");
            Ran refused = invoke(new PlanCommand(), path, "-u", "furlong");
            assertThat(refused.code()).isEqualTo(1);
            assertThat(refused.errText()).contains("furlong");
        }
    }

    @Nested
    class ProfileListing {
        @Test
        void listsTheNamesAsJsonWhenAskedTo() {
            Ran ran = invoke(new ProfileCommand(), "--json");
            assertThat(ran.code()).isZero();
            assertThat(Json.parse(ran.outText()))
                    .isEqualTo(List.of("house", "atlas-plates", "quadrangles", "rolled-drawings", "microfilm"));
        }

        @Test
        void showsAGroupSettingAsJson() {
            Ran ran = invoke(new ProfileCommand(), "quadrangles", "-s", "crop");
            assertThat(ran.code()).isZero();
            assertThat(((Map<?, ?>) Json.parse(ran.outText())).get("anchor")).isEqualTo("neatline");
        }
    }

    @Nested
    class QcLevel {
        @Test
        void refusesALevelThatIsNotAWholeNumberOfStars(@TempDir Path root) throws IOException {
            String path = captureIn(root);
            for (String stars : List.of("2.5", "5", "-1")) {
                Ran ran = invoke(new QcCommand(), path, "-r", "400", "-s", stars);
                assertThat(ran.code()).as(stars).isEqualTo(1);
                assertThat(ran.err().get(0)).as(stars).contains("stars");
            }
        }
    }

    @Nested
    class Report {
        @Test
        void printsTheSentenceThenEachDetailButTheCause() {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("known", List.of("house", "microfilm"));
            details.put("count", 2.0);
            details.put("where", Map.of("x", 1.0));
            details.put("cause", new IllegalStateException("hidden"));
            details.put("gap", Arrays.asList("a", null, "b"));
            details.put("missing", null);
            Console console = new Console();

            int code = Common.report(console.io(), new BadArgumentError("that will not do", details));

            assertThat(code).isEqualTo(1);
            assertThat(console.err())
                    .containsExactly(
                            "that will not do",
                            "  known: house,microfilm",
                            "  count: 2",
                            "  where: {\"x\":1}",
                            "  gap: a,,b",
                            "  missing: null");
        }

        @Test
        void namesACaptureByItsFileWithoutTheExtension(@TempDir Path root) throws IOException {
            List<String> accessions = new ArrayList<>();
            for (String name : List.of("g4181.png", "g4181.1907.tif", ".hidden", "plain")) {
                Path path = root.resolve(name);
                Files.write(path, new byte[] {1});
                accessions.add(Common.captureAt(path.toString()).accession());
            }
            assertThat(accessions).containsExactly("g4181", "g4181.1907", ".hidden", "plain");
        }
    }
}
