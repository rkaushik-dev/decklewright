package io.github.rkaushikdev.decklewright.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.profile.Profile;
import io.github.rkaushikdev.decklewright.testing.Console;
import io.github.rkaushikdev.decklewright.testing.Console.Ran;
import io.github.rkaushikdev.decklewright.util.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CropTest {

    private static final Command CROP = new CropCommand();
    private static final Command PLAN = new PlanCommand();

    private static Ran invoke(Command command, List<String> argv) {
        return Console.invoke(command, argv, io -> io);
    }

    private static Ran invoke(Command command, List<String> argv, Path cwd) {
        return Console.invoke(command, argv, io -> io.withCwd(cwd));
    }

    private static String captureIn(Path root, String name) throws IOException {
        Path path = root.resolve(name);
        Files.write(path, Console.capturePng());
        return path.toString();
    }

    @Nested
    class CommonBehaviour {
        @Test
        void findsABuiltInProfileByName() {
            assertThat(Common.profileNamed("quadrangles").name()).isEqualTo("quadrangles");
        }

        @Test
        void refusesANameNothingKnowsAndSaysWhatItDoesKnow() {
            assertThatThrownBy(() -> Common.profileNamed("nothing"))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("no profile by that name");
        }

        @Test
        void readsAProfileOffADiskWhenTheNameLooksLikeAPath(@TempDir Path root) throws IOException {
            Path path = root.resolve("local.json");
            Files.writeString(path, "{\"name\":\"local\",\"extends\":\"house\",\"version\":2}");
            assertThat(Common.profileNamed(path.toString()).name()).isEqualTo("local");
        }

        @Test
        void saysSoWhenAProfileFileWillNotRead(@TempDir Path root) throws IOException {
            Path path = root.resolve("broken.json");
            Files.writeString(path, "not json");
            assertThatThrownBy(() -> Common.profileNamed(path.toString()))
                    .isInstanceOf(BadArgumentError.class)
                    .hasMessageFindingMatch("will not read as json");
        }

        @Test
        void takesTheFrequencyTheCallerGaveOverTheOneTheProfileClaims() {
            Profile profile = Common.profileNamed("quadrangles");
            assertThat(Common.samplingFrom(OptionalDouble.of(400), profile)
                            .orElseThrow()
                            .x())
                    .isEqualTo(400.0);
            assertThat(Common.samplingFrom(OptionalDouble.empty(), profile)
                            .orElseThrow()
                            .x())
                    .isEqualTo(600.0);
        }

        @Test
        void writesASizeAPersonReads() {
            assertThat(Common.humanBytes(900)).isEqualTo("900 B");
            assertThat(Common.humanBytes(2048)).isEqualTo("2.0 kB");
            assertThat(Common.humanBytes(5 * 1024 * 1024)).isEqualTo("5.0 MB");
        }
    }

    @Nested
    class Plan {
        @Test
        void saysWhereTheCropWouldBe(@TempDir Path root) throws IOException {
            Ran ran = invoke(PLAN, List.of(captureIn(root, "g4181.png")));
            assertThat(ran.code()).isZero();
            assertThat(ran.out().get(0)).isEqualTo("g4181, profile house");
            assertThat(ran.outText()).containsPattern("crop ");
            assertThat(ran.out()).contains("  sheet measures 0.4 in by 0.3 in, no nominal size");
        }

        @Test
        void measuresTheSheetInTheUnitAsked(@TempDir Path root) throws IOException {
            Ran ran = invoke(PLAN, List.of(captureIn(root, "g4181.png"), "--unit", "mm"));
            assertThat(ran.out()).contains("  sheet measures 10.2 mm by 7.1 mm, no nominal size");
        }

        @Test
        void writesJsonWhenItIsAskedTo(@TempDir Path root) throws IOException {
            Ran ran = invoke(PLAN, List.of(captureIn(root, "g4181.png"), "--json"));
            Map<?, ?> document = (Map<?, ?>) Json.parse(ran.outText());
            assertThat(document.get("accession")).isEqualTo("g4181");
            assertThat((Double) ((Map<?, ?>) document.get("rect")).get("width")).isGreaterThan(100);
            assertThat(document.get("settled")).isEqualTo(true);
            Map<?, ?> sheet = (Map<?, ?>) document.get("sheet");
            assertThat((Double) sheet.get("width")).isCloseTo(0.4, byLessThan(0.01));
            assertThat(sheet.get("unit")).isEqualTo("in");
            assertThat(sheet.containsKey("nominal")).isTrue();
            assertThat(sheet.get("nominal")).isNull();
        }

        @Test
        void writesNothingToTheDisk(@TempDir Path root) throws IOException {
            invoke(PLAN, List.of(captureIn(root, "g4181.png")));
            assertThat(Console.namesIn(root)).containsExactly("g4181.png");
        }

        @Test
        void asksForACaptureWhenItWasGivenNone() {
            Ran ran = invoke(PLAN, List.of());
            assertThat(ran.code()).isEqualTo(2);
            assertThat(ran.err().get(0)).containsPattern("which capture");
        }

        @Test
        void reportsACaptureItCouldNotReadRatherThanThrowing(@TempDir Path root) throws IOException {
            Path path = root.resolve("broken.png");
            Files.write(path, new byte[] {1, 2, 3});
            Ran ran = invoke(PLAN, List.of(path.toString()));
            assertThat(ran.code()).isEqualTo(1);
            assertThat(ran.err()).isNotEmpty();
        }

        @Test
        void namesTheProfileItWasToldToUse(@TempDir Path root) throws IOException {
            Ran ran = invoke(PLAN, List.of(captureIn(root, "g4181.png"), "-p", "rolled-drawings"));
            assertThat(ran.out().get(0)).containsPattern("profile rolled-drawings");
        }
    }

    @Nested
    class Crop {
        @Test
        void writesTheDerivatives(@TempDir Path root) throws IOException {
            Ran ran = invoke(CROP, List.of(captureIn(root, "g4181.png"), "--out", "cut"), root);

            assertThat(ran.code()).isZero();
            assertThat(Console.namesIn(root.resolve("cut")))
                    .containsExactly("g4181_master.png", "g4181_service.png", "g4181_thumb.png");
            assertThat(ran.lastOut()).containsPattern("^wrote 3 files");
        }

        @Test
        void writesOnlyTheDerivativeItWasAskedFor(@TempDir Path root) throws IOException {
            invoke(CROP, List.of(captureIn(root, "g4181.png"), "--out", "cut", "--only", "thumb"), root);
            assertThat(Console.namesIn(root.resolve("cut"))).containsExactly("g4181_thumb.png");
        }

        @Test
        void saysSoWhenTheOnlyItWasGivenIsNotADerivative(@TempDir Path root) throws IOException {
            Ran ran = invoke(CROP, List.of(captureIn(root, "g4181.png"), "--only", "poster"), root);
            assertThat(ran.code()).isEqualTo(1);
            assertThat(ran.err().get(0)).containsPattern("this profile makes master, service, thumb");
        }

        @Test
        void takesAnAccessionThatIsNotTheFileName(@TempDir Path root) throws IOException {
            invoke(CROP, List.of(captureIn(root, "scan001.png"), "--out", "cut", "-n", "g4181-1907"), root);
            assertThat(Console.namesIn(root.resolve("cut"))).contains("g4181-1907_master.png");
        }

        @Test
        void writesNothingOnADryRunAndSaysWhatItWouldHaveWritten(@TempDir Path root) throws IOException {
            Ran ran = invoke(CROP, List.of(captureIn(root, "g4181.png"), "--dry-run"), root);
            assertThat(ran.lastOut()).containsPattern("^would write 3 files");
            assertThat(Console.namesIn(root)).containsExactly("g4181.png");
        }

        @Test
        void refusesToReplaceFilesThatAreThere(@TempDir Path root) throws IOException {
            String path = captureIn(root, "g4181.png");
            invoke(CROP, List.of(path, "--out", "cut"), root);
            Ran ran = invoke(CROP, List.of(path, "--out", "cut"), root);
            assertThat(ran.code()).isEqualTo(1);
            assertThat(String.join(" ", ran.err())).containsPattern("already a file there");
        }

        @Test
        void replacesThemWhenItIsToldTo(@TempDir Path root) throws IOException {
            String path = captureIn(root, "g4181.png");
            invoke(CROP, List.of(path, "--out", "cut"), root);
            Ran ran = invoke(CROP, List.of(path, "--out", "cut", "-f"), root);
            assertThat(ran.code()).isZero();
        }

        @Test
        void listsWhatItWroteWithTheSizeAndTheFrequency(@TempDir Path root) throws IOException {
            Ran ran = invoke(CROP, List.of(captureIn(root, "g4181.png"), "--out", "cut", "-r", "600"), root);
            assertThat(ran.out().get(1)).containsPattern("g4181_master\\.png, [\\d.]+ (B|kB|MB), 600");
        }

        @Test
        void asksForACaptureWhenItWasGivenNone() {
            Ran ran = invoke(CROP, List.of());
            assertThat(ran.code()).isEqualTo(2);
            assertThat(ran.err().get(0)).containsPattern("which capture");
        }
    }
}
