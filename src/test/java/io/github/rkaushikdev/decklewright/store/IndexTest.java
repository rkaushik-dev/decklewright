package io.github.rkaushikdev.decklewright.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.StoreError;
import io.github.rkaushikdev.decklewright.manifest.Payload;
import io.github.rkaushikdev.decklewright.testing.Accession;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The store as the rest of the tree sees it: the workspace, the calls a run makes on it, and the
 * atomic writes for a lab that keeps its own layout.
 */
class IndexTest {

    private static boolean exports(Class<?> type, String name) {
        return Modifier.isPublic(type.getModifiers())
                && Arrays.stream(type.getMethods())
                        .filter(method -> method.getName().equals(name))
                        .map(Method::getModifiers)
                        .anyMatch(modifiers -> Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers));
    }

    private static Payload deliveryOf(String accession) {
        return Accession.delivery(Accession.Options.defaults().withId(accession));
    }

    @Nested
    class TheLayer {
        @Test
        void exportsTheWorkspaceAndTheTwoCallsARunMakes() {
            assertThat(Modifier.isPublic(Workspace.class.getModifiers())).isTrue();
            assertThat(exports(Dispatch.class, "deliver")).isTrue();
            assertThat(exports(Dispatch.class, "recordFailure")).isTrue();
        }

        @Test
        void exportsTheAtomicWritesForALabThatKeepsItsOwnLayout() {
            assertThat(exports(AtomicFiles.class, "writeSetAtomic")).isTrue();
            assertThat(exports(AtomicFiles.class, "sweepTemporary")).isTrue();
        }
    }

    @Nested
    class Deliver {
        @Test
        void writesTheFilesAndRecordsTheLine(@TempDir Path root) {
            Workspace space = Workspace.open(root);
            Dispatched out = Dispatch.deliver(
                    space,
                    Accession.delivery(),
                    DeliverOptions.defaults().withProfile("house").withStars(4));
            assertThat(out.directory().endsWith("g4181-1907")).isTrue();
            assertThat(out.entry().outcome()).isEqualTo("delivered");
            assertThat(out.entry().stars()).hasValue(4);
        }

        @Test
        void recordsTheDeliveryDigestSoAFolderCanBeTracedToALine(@TempDir Path root) {
            Workspace space = Workspace.open(root);
            Payload payload = Accession.delivery();
            Dispatched out = Dispatch.deliver(space, payload);
            assertThat(out.entry().digest()).hasValue(payload.digest());
        }

        @Test
        void recordsTheSizeThatWasActuallyWritten(@TempDir Path root) {
            Dispatched out = Dispatch.deliver(Workspace.open(root), Accession.delivery());
            assertThat(out.entry().bytes().orElseThrow()).isGreaterThan(0);
        }

        @Test
        void recordsARejectionAsARejection(@TempDir Path root) {
            Workspace space = Workspace.open(root);
            Dispatched out = Dispatch.deliver(
                    space,
                    Accession.delivery(),
                    DeliverOptions.defaults().withFolder(Folder.REJECTED).withStars(1));
            assertThat(out.entry().outcome()).isEqualTo("rejected");
            assertThat(space.list(Folder.REJECTED)).isEqualTo(List.of("g4181-1907"));
        }

        @Test
        void writesNoLineWhenTheFilesCouldNotBeWritten(@TempDir Path root) {
            Workspace space = Workspace.open(root);
            Dispatch.deliver(space, Accession.delivery());
            assertThatThrownBy(() -> Dispatch.deliver(space, Accession.delivery()))
                    .isInstanceOf(StoreError.class)
                    .hasMessageFindingMatch("already been delivered");

            assertThat(Ledger.readLedger(space.paths().ledger()).entries()).hasSize(1);
        }
    }

    @Nested
    class RecordFailure {
        @Test
        void recordsARunThatProducedNothing(@TempDir Path root) {
            LedgerEntry entry = Dispatch.recordFailure(
                    Workspace.open(root),
                    "g4182",
                    "no sheet on the bed",
                    FailureOptions.defaults().withProfile("house"));
            assertThat(entry.outcome()).isEqualTo("failed");
            assertThat(entry.reason()).hasValue("no sheet on the bed");
        }

        @Test
        void takesAnotherOutcomeForACaptureThatWasDeliberatelyPassedOver(@TempDir Path root) {
            LedgerEntry entry = Dispatch.recordFailure(
                    Workspace.open(root),
                    "g4183",
                    "already delivered last week",
                    FailureOptions.defaults().withOutcome("skipped"));
            assertThat(entry.outcome()).isEqualTo("skipped");
        }
    }

    @Nested
    class ShiftReportBehaviour {
        @Test
        void countsWhatTheWorkspaceDid(@TempDir Path root) {
            Workspace space = Workspace.open(root);
            Dispatch.deliver(space, deliveryOf("a"), DeliverOptions.defaults().withStars(4));
            Dispatch.deliver(
                    space,
                    deliveryOf("b"),
                    DeliverOptions.defaults().withStars(2).withFolder(Folder.REJECTED));
            Dispatch.recordFailure(space, "c", "nothing on the bed");

            ShiftReport report = Dispatch.shiftReport(space);
            assertThat(report.summary().total()).isEqualTo(3);
            assertThat(report.summary().byOutcome()).isEqualTo(Map.of("delivered", 1, "rejected", 1, "failed", 1));
            assertThat(report.summary().averageStars()).hasValue(3);
        }

        @Test
        void reportsAnEmptyWorkspaceAsEmpty(@TempDir Path root) {
            ShiftReport report = Dispatch.shiftReport(Workspace.open(root));
            assertThat(report.summary().total()).isEqualTo(0);
            assertThat(report.unreadable()).isEqualTo(0);
        }

        @Test
        void saysHowManyLinesCouldNotBeRead(@TempDir Path root) {
            Workspace space = Workspace.open(root);
            AtomicFiles.appendLine(space.paths().ledger(), "this is not json");
            assertThat(Dispatch.shiftReport(space).unreadable()).isEqualTo(1);
        }
    }
}
