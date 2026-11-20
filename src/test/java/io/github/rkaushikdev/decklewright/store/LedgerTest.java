package io.github.rkaushikdev.decklewright.store;

import static io.github.rkaushikdev.decklewright.store.Ledger.OUTCOMES;
import static io.github.rkaushikdev.decklewright.store.Ledger.appendEntry;
import static io.github.rkaushikdev.decklewright.store.Ledger.describeSummary;
import static io.github.rkaushikdev.decklewright.store.Ledger.entryFor;
import static io.github.rkaushikdev.decklewright.store.Ledger.latestByAccession;
import static io.github.rkaushikdev.decklewright.store.Ledger.readLedger;
import static io.github.rkaushikdev.decklewright.store.Ledger.select;
import static io.github.rkaushikdev.decklewright.store.Ledger.summarise;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.decklewright.StoreError;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The ledger: one line per run, appended, read back counting what will not read, and summed. */
class LedgerTest {

    private static final List<LedgerEntry> ENTRIES = List.of(
            entryFor(
                    "a",
                    "delivered",
                    EntryOptions.defaults()
                            .withProfile("house")
                            .withStars(4)
                            .withBytes(100)
                            .withAt(Instant.parse("2025-11-20T09:00:00.000Z"))),
            entryFor(
                    "b",
                    "rejected",
                    EntryOptions.defaults()
                            .withProfile("house")
                            .withStars(1)
                            .withBytes(80)
                            .withAt(Instant.parse("2025-11-20T09:05:00.000Z"))),
            entryFor(
                    "c",
                    "failed",
                    EntryOptions.defaults()
                            .withProfile("quadrangles")
                            .withReason("no neatline")
                            .withAt(Instant.parse("2025-11-21T09:00:00.000Z"))));

    private static LedgerEntry at(String accession, String outcome, String time) {
        return entryFor(accession, outcome, EntryOptions.defaults().withAt(Instant.parse(time)));
    }

    private static void appendText(Path path, String text) throws IOException {
        Files.writeString(path, text, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    @Nested
    class EntryFor {
        @Test
        void alwaysCarriesATimeAnAccessionAndAnOutcome() {
            LedgerEntry entry = entryFor("a", "delivered");
            assertThat(entry.accession()).isEqualTo("a");
            assertThat(entry.outcome()).isEqualTo("delivered");
            assertThat(entry.at()).containsPattern("^\\d{4}-\\d{2}-\\d{2}T");
        }

        @Test
        void leavesOutWhatItWasNotGiven() {
            assertThat(entryFor("a", "skipped").toJson().keySet()).containsExactly("at", "accession", "outcome");
        }

        @Test
        void keepsAZeroStarGradingWhichIsNotTheSameAsNone() {
            assertThat(entryFor("a", "rejected", EntryOptions.defaults().withStars(0))
                            .stars())
                    .hasValue(0);
        }

        @Test
        void leavesAnEmptyWarningListOut() {
            LedgerEntry entry =
                    entryFor("a", "delivered", EntryOptions.defaults().withWarnings(List.of()));
            assertThat(entry.warnings()).isEmpty();
            assertThat(entry.toJson()).doesNotContainKey("warnings");
        }

        @Test
        void copiesTheWarningsRatherThanSharingThem() {
            List<String> warnings = new ArrayList<>(List.of("one"));
            LedgerEntry entry =
                    entryFor("a", "delivered", EntryOptions.defaults().withWarnings(warnings));
            warnings.add("two");
            assertThat(entry.warnings()).isEqualTo(List.of("one"));
        }

        @Test
        void refusesAnOutcomeARunCannotHave() {
            assertThatThrownBy(() -> entryFor("a", "mislaid"))
                    .isInstanceOf(StoreError.class)
                    .hasMessageFindingMatch("does not end that way");
        }

        @Test
        void namesEveryOutcomeTheReaderKnows() {
            for (String outcome : OUTCOMES) {
                assertThat(entryFor("a", outcome).outcome()).isEqualTo(outcome);
            }
        }
    }

    @Nested
    class AppendEntryAndReadLedger {
        @Test
        void readsBackNothingFromALedgerThatIsNotThereYet(@TempDir Path root) {
            assertThat(readLedger(root.resolve("ledger.jsonl"))).isEqualTo(new LedgerRead(List.of(), 0));
        }

        @Test
        void readsBackWhatItAppendedInOrder(@TempDir Path root) {
            Path path = root.resolve("ledger.jsonl");
            for (LedgerEntry entry : ENTRIES) {
                appendEntry(path, entry);
            }
            assertThat(readLedger(path).entries())
                    .extracting(LedgerEntry::accession)
                    .containsExactly("a", "b", "c");
        }

        @Test
        void keepsEveryFieldThroughTheRoundTrip(@TempDir Path root) {
            Path path = root.resolve("ledger.jsonl");
            appendEntry(path, ENTRIES.get(2));
            assertThat(readLedger(path).entries().get(0)).isEqualTo(ENTRIES.get(2));
        }

        @Test
        void countsATornLineRatherThanLosingTheFileBehindIt(@TempDir Path root) throws IOException {
            Path path = root.resolve("ledger.jsonl");
            appendEntry(path, ENTRIES.get(0));
            appendText(path, "{\"accession\": \"b\", \"outc\n");
            appendEntry(path, ENTRIES.get(1));

            LedgerRead out = readLedger(path);
            assertThat(out.entries()).extracting(LedgerEntry::accession).containsExactly("a", "b");
            assertThat(out.unreadable()).isEqualTo(1);
        }

        @Test
        void countsALineThatParsesButIsNotAnEntry(@TempDir Path root) throws IOException {
            Path path = root.resolve("ledger.jsonl");
            appendText(path, "{\"note\": \"somebody was here\"}\n");
            assertThat(readLedger(path).unreadable()).isEqualTo(1);
        }

        @Test
        void ignoresBlankLines(@TempDir Path root) throws IOException {
            Path path = root.resolve("ledger.jsonl");
            appendText(path, "\n\n");
            assertThat(readLedger(path)).isEqualTo(new LedgerRead(List.of(), 0));
        }
    }

    @Nested
    class LatestByAccession {
        @Test
        void keepsTheMostRecentForEach() {
            List<LedgerEntry> twice = List.of(
                    at("a", "failed", "2025-11-20T09:00:00.000Z"), at("a", "delivered", "2025-11-21T09:00:00.000Z"));
            assertThat(latestByAccession(twice).get("a").outcome()).isEqualTo("delivered");
        }

        @Test
        void doesNotDependOnTheOrderTheyWereReadIn() {
            List<LedgerEntry> twice = List.of(
                    at("a", "delivered", "2025-11-21T09:00:00.000Z"), at("a", "failed", "2025-11-20T09:00:00.000Z"));
            assertThat(latestByAccession(twice).get("a").outcome()).isEqualTo("delivered");
        }

        @Test
        void hasOneEntryPerAccession() {
            assertThat(latestByAccession(ENTRIES)).hasSize(3);
        }
    }

    @Nested
    class Select {
        @Test
        void picksByOutcome() {
            assertThat(select(ENTRIES, LedgerFilter.everything().withOutcome("failed")))
                    .extracting(LedgerEntry::accession)
                    .containsExactly("c");
        }

        @Test
        void picksByProfile() {
            assertThat(select(ENTRIES, LedgerFilter.everything().withProfile("house")))
                    .hasSize(2);
        }

        @Test
        void picksByDayTakingSinceAsInclusive() {
            assertThat(select(ENTRIES, LedgerFilter.everything().withSince("2025-11-21T00:00:00.000Z")))
                    .hasSize(1);
            assertThat(select(ENTRIES, LedgerFilter.everything().withSince("2025-11-20T09:00:00.000Z")))
                    .hasSize(3);
        }

        @Test
        void takesUntilAsExclusive() {
            assertThat(select(ENTRIES, LedgerFilter.everything().withUntil("2025-11-20T09:05:00.000Z")))
                    .hasSize(1);
        }

        @Test
        void takesSeveralAtOnce() {
            assertThat(select(
                            ENTRIES,
                            LedgerFilter.everything().withProfile("house").withOutcome("delivered")))
                    .hasSize(1);
        }

        @Test
        void givesEverythingBackForNoFilter() {
            assertThat(select(ENTRIES)).hasSize(3);
        }
    }

    @Nested
    class Summarise {
        @Test
        void countsEachOutcome() {
            assertThat(summarise(ENTRIES).byOutcome()).isEqualTo(Map.of("delivered", 1, "rejected", 1, "failed", 1));
        }

        @Test
        void addsTheBytesUp() {
            assertThat(summarise(ENTRIES).bytes()).isEqualTo(180);
        }

        @Test
        void averagesOverTheGradedOnesAndSaysHowManyThoseWere() {
            Summary out = summarise(ENTRIES);
            assertThat(out.graded()).isEqualTo(2);
            assertThat(out.averageStars()).hasValue(2.5);
        }

        @Test
        void hasNoAverageWhenNothingWasGraded() {
            assertThat(summarise(List.of(ENTRIES.get(2))).averageStars()).isEmpty();
        }

        @Test
        void countsAnEmptyLedgerAsEmpty() {
            assertThat(summarise(List.of())).isEqualTo(new Summary(0, Map.of(), 0, 0, OptionalDouble.empty()));
        }
    }

    @Nested
    class DescribeSummary {
        @Test
        void opensWithTheTotals() {
            assertThat(describeSummary(summarise(ENTRIES)).get(0)).isEqualTo("3 accessions, 180 bytes");
        }

        @Test
        void listsTheOutcomesInAFixedOrder() {
            List<String> lines = describeSummary(summarise(ENTRIES));
            assertThat(lines.get(1)).isEqualTo("  1 delivered");
            assertThat(lines.get(2)).isEqualTo("  1 rejected");
        }

        @Test
        void saysNothingAboutStarsWhenNothingWasGraded() {
            assertThat(String.join(" ", describeSummary(summarise(List.of(ENTRIES.get(2))))))
                    .doesNotContainPattern("stars");
        }
    }
}
