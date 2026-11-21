package io.github.rkaushikdev.decklewright;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.decklewright.cli.Main;
import io.github.rkaushikdev.decklewright.cli.commands.Io;
import io.github.rkaushikdev.decklewright.codec.Codecs;
import io.github.rkaushikdev.decklewright.derive.Recipes;
import io.github.rkaushikdev.decklewright.manifest.Payload;
import io.github.rkaushikdev.decklewright.manifest.PayloadEntry;
import io.github.rkaushikdev.decklewright.manifest.Sidecars;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.pipeline.Policies;
import io.github.rkaushikdev.decklewright.pipeline.ProcessedIncoming;
import io.github.rkaushikdev.decklewright.pipeline.RunOptions;
import io.github.rkaushikdev.decklewright.pipeline.WorkspaceRunner;
import io.github.rkaushikdev.decklewright.profile.Profile;
import io.github.rkaushikdev.decklewright.profile.ProfileRegistry;
import io.github.rkaushikdev.decklewright.raster.Surface;
import io.github.rkaushikdev.decklewright.store.DeliveryCheck;
import io.github.rkaushikdev.decklewright.store.Dispatch;
import io.github.rkaushikdev.decklewright.store.ShiftReport;
import io.github.rkaushikdev.decklewright.store.Workspace;
import io.github.rkaushikdev.decklewright.testing.Console;
import io.github.rkaushikdev.decklewright.util.IsoTime;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * One sheet, all the way through, using nothing but the promised surface.
 *
 * <p>Every other test in the tree reaches into a layer. This one is the only test that is a
 * customer: it opens a workspace, drops a capture in it, runs the shift, and then checks the folder
 * the way somebody would in five years with none of this code to hand. If the layers are all right
 * and this is wrong, the thing that is wrong is the shape of the library.
 */
class AccessionTest {

    private static final Instant AT = Instant.parse("2025-12-19T09:00:00Z");

    private static final String ROOT = "io.github.rkaushikdev.decklewright.";

    private static Profile house() {
        return new ProfileRegistry().get("house");
    }

    private static ProcessedIncoming shift(Workspace workspace, RunOptions options) {
        return WorkspaceRunner.processIncoming(workspace, house(), Optional.of(Sampling.of(400)), options);
    }

    private static byte[] entryNamed(Payload payload, Predicate<String> name) {
        return payload.entries().stream()
                .filter(one -> name.test(one.name()))
                .map(PayloadEntry::bytes)
                .findFirst()
                .orElseThrow();
    }

    /**
     * The type or the public static member a promised name stands for, or nothing when there is no
     * such public thing.
     */
    private static Optional<Object> resolve(String name) {
        Optional<Class<?>> type = publicType(name);
        if (type.isPresent()) {
            return Optional.of(type.get());
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return Optional.empty();
        }
        String member = name.substring(dot + 1);
        return publicType(name.substring(0, dot)).flatMap(owner -> Stream.<Member>concat(
                        Arrays.stream(owner.getMethods()), Arrays.stream(owner.getFields()))
                .filter(found -> found.getName().equals(member))
                .filter(found -> Modifier.isStatic(found.getModifiers()))
                .filter(found -> Modifier.isPublic(found.getModifiers()))
                .findFirst()
                .map(Object.class::cast));
    }

    private static Optional<Class<?>> publicType(String name) {
        try {
            Class<?> type = Class.forName(ROOT + name);
            return Modifier.isPublic(type.getModifiers()) ? Optional.of(type) : Optional.empty();
        } catch (ClassNotFoundException absent) {
            return Optional.empty();
        }
    }

    @Nested
    class TheWholeJob {
        @Test
        void takesACaptureOffABedAndLeavesADeliveryOnADisk(@TempDir Path root) throws IOException {
            Workspace workspace = Workspace.open(root);
            Files.write(workspace.paths().incoming().resolve("g4181-1907.png"), Console.capturePng());

            ProcessedIncoming out = shift(
                    workspace,
                    RunOptions.defaults()
                            .withVersion("1.4.0")
                            .withHost("stand-2")
                            .withAt(AT));

            assertThat(out.counts()).isEqualTo(Map.of("delivered", 1));

            assertThat(Console.namesIn(root.resolve("delivered").resolve("g4181-1907")))
                    .containsExactly(
                            "g4181-1907_master.png",
                            "g4181-1907_service.png",
                            "g4181-1907_thumb.png",
                            "provenance.json",
                            "sha256sums.txt",
                            "sidecar.json");
        }

        @Test
        void leavesAMasterThatReadsBackAsTheSheetCutAndStraightened(@TempDir Path root) throws IOException {
            Workspace workspace = Workspace.open(root);
            Files.write(workspace.paths().incoming().resolve("g4181.png"), Console.capturePng(1.5));

            shift(
                    workspace,
                    RunOptions.defaults()
                            .withAt(AT)
                            .withPolicy(Policies.policy().withMinimumStars(0).withHoldOn(List.of())));

            Payload delivered = workspace.get("g4181");
            byte[] master = entryNamed(delivered, name -> name.endsWith("_master.png"));
            Surface surface = Codecs.readImage(master);

            assertThat(surface.depth()).isEqualTo(16);
            assertThat(surface.width()).isGreaterThan(140);
            assertThat(surface.width()).isLessThan(200);
            assertThat(Recipes.frequencyOf(master).orElseThrow().x()).isCloseTo(400, byLessThan(0.05));
        }

        @Test
        void leavesASidecarThatSaysWhatWasDoneInNumbers(@TempDir Path root) throws IOException {
            Workspace workspace = Workspace.open(root);
            Files.write(workspace.paths().incoming().resolve("g4181.png"), Console.capturePng());

            shift(workspace, RunOptions.defaults().withVersion("1.4.0").withAt(AT));

            Payload delivered = workspace.get("g4181");
            byte[] bytes = entryNamed(delivered, name -> name.equals("sidecar.json"));
            Map<String, Object> sidecar = Sidecars.parseSidecar(new String(bytes, StandardCharsets.UTF_8));

            assertThat(sidecar.get("accession")).isEqualTo("g4181");
            assertThat(((Map<?, ?>) sidecar.get("profile")).get("name")).isEqualTo("house");
            assertThat(sidecar.get("created")).isEqualTo(IsoTime.format(AT));
            assertThat(((Map<?, ?>) sidecar.get("capture")).get("pixels"))
                    .isEqualTo(Map.of("width", 300.0, "height", 240.0));
            assertThat((List<?>) sidecar.get("derivatives")).hasSize(3);
            assertThat((Double) ((Map<?, ?>) sidecar.get("quality")).get("stars"))
                    .isGreaterThanOrEqualTo(3);
        }

        @Test
        void leavesADeliveryThatVerifiesYearsLaterWithNothingElseToHand(@TempDir Path root) throws IOException {
            Workspace workspace = Workspace.open(root);
            Files.write(workspace.paths().incoming().resolve("g4181.png"), Console.capturePng());
            shift(workspace, RunOptions.defaults().withAt(AT));

            Workspace later = Workspace.at(root);
            assertThat(later.verify("g4181")).isEqualTo(new DeliveryCheck("g4181", true, 3, List.of()));
        }

        @Test
        void catchesAMasterSomebodyEditedOnTheDisk(@TempDir Path root) throws IOException {
            Workspace workspace = Workspace.open(root);
            Files.write(workspace.paths().incoming().resolve("g4181.png"), Console.capturePng());
            shift(workspace, RunOptions.defaults().withAt(AT));

            Files.writeString(root.resolve("delivered/g4181/g4181_master.png"), "improved");
            DeliveryCheck checked = workspace.verify("g4181");
            assertThat(checked.ok()).isFalse();
            assertThat(String.join(" ", checked.problems())).containsPattern("g4181_master\\.png");
        }

        @Test
        void recordsTheShiftInTheLedgerDeliveredAndNot(@TempDir Path root) throws IOException {
            Workspace workspace = Workspace.open(root);
            Files.write(workspace.paths().incoming().resolve("g4181.png"), Console.capturePng());
            Files.write(workspace.paths().incoming().resolve("lenscap.png"), new byte[] {1, 2, 3});

            shift(workspace, RunOptions.defaults().withAt(AT));

            ShiftReport report = Dispatch.shiftReport(workspace);
            assertThat(report.summary().total()).isEqualTo(2);
            assertThat(report.summary().byOutcome()).isEqualTo(Map.of("delivered", 1, "failed", 1));
            assertThat(report.unreadable()).isZero();
        }

        @Test
        void runsTheSameJobThroughTheCommandLineAndGetsTheSameFolder(@TempDir Path one, @TempDir Path two)
                throws IOException {
            for (Path root : List.of(one, two)) {
                Workspace workspace = Workspace.open(root);
                Files.write(workspace.paths().incoming().resolve("g4181.png"), Console.capturePng());
            }

            shift(Workspace.at(one), RunOptions.defaults().withVersion("1.4.0").withAt(AT));
            Io silent = Io.of(line -> {}, line -> {}).withCwd(two).withNow(() -> AT);
            Main.run(List.of("batch", two.toString(), "-r", "400"), silent, "1.4.0");

            assertThat(Console.namesIn(one.resolve("delivered").resolve("g4181")))
                    .isEqualTo(Console.namesIn(two.resolve("delivered").resolve("g4181")));
        }
    }

    @Nested
    class ThePublicSurface {
        @Test
        void exportsExactlyTheNamesItSaysItDoes() {
            List<String> unresolved = new ArrayList<>();
            for (String name : Decklewright.PUBLIC_NAMES) {
                if (resolve(name).isEmpty()) {
                    unresolved.add(name);
                }
            }
            assertThat(unresolved).isEmpty();
            assertThat(Decklewright.PUBLIC_NAMES).doesNotHaveDuplicates().isSorted();

            List<String> own = Arrays.stream(Decklewright.class.getDeclaredFields())
                    .filter(field -> Modifier.isPublic(field.getModifiers()))
                    .map(field -> "Decklewright." + field.getName())
                    .sorted()
                    .toList();
            assertThat(Decklewright.PUBLIC_NAMES.stream()
                            .filter(name -> name.startsWith("Decklewright."))
                            .toList())
                    .isEqualTo(own);

            assertThat(resolve("pipeline.WorkspaceRunner.processEverything")).isEmpty();
            assertThat(resolve("store.Workspac")).isEmpty();
            assertThat(resolve("geom.Rect")).contains(io.github.rkaushikdev.decklewright.geom.Rect.class);
        }

        @Test
        void saysWhichVersionOfTheSurfaceThisIs() {
            assertThat(Decklewright.API_VERSION).isEqualTo(2);
        }

        @Test
        void exportsTheOneCallThatDoesTheWholeJob() {
            assertThat(Decklewright.PUBLIC_NAMES)
                    .contains("pipeline.WorkspaceRunner.process", "pipeline.WorkspaceRunner.processIncoming");
            assertThat(resolve("pipeline.WorkspaceRunner.process")).isPresent();
            assertThat(resolve("pipeline.WorkspaceRunner.processIncoming")).isPresent();
        }

        @Test
        void exportsEveryErrorACallerHasToCatch() throws ReflectiveOperationException {
            for (String name : List.of(
                    "BadArgumentError",
                    "CodecError",
                    "DetectionError",
                    "GeometryError",
                    "ManifestError",
                    "PipelineError",
                    "PlanError",
                    "ProfileError",
                    "QcError",
                    "StoreError",
                    "UnsupportedError")) {
                assertThat(Decklewright.PUBLIC_NAMES).as(name).contains(name);
                Class<?> type = publicType(name).orElseThrow();
                Object made = type.getConstructor(String.class).newInstance("x");
                assertThat(made).as(name).isInstanceOf(DeckleError.class);
            }
        }

        @Test
        void keepsTheListFrozenSoItCannotBeWidenedAtRuntime() throws NoSuchFieldException {
            assertThatThrownBy(() -> Decklewright.PUBLIC_NAMES.add("widened"))
                    .isInstanceOf(UnsupportedOperationException.class);
            Field field = Decklewright.class.getField("PUBLIC_NAMES");
            assertThat(Modifier.isFinal(field.getModifiers())).isTrue();
        }
    }
}
