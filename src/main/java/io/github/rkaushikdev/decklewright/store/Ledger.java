package io.github.rkaushikdev.decklewright.store;

import io.github.rkaushikdev.decklewright.StoreError;
import io.github.rkaushikdev.decklewright.qc.Check;
import io.github.rkaushikdev.decklewright.util.IsoTime;
import io.github.rkaushikdev.decklewright.util.Json;
import io.github.rkaushikdev.decklewright.util.JsonSyntaxException;
import io.github.rkaushikdev.decklewright.util.NumberText;
import io.github.rkaushikdev.decklewright.util.Whitespace;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * What happened, one line per accession.
 *
 * <p>A lab supervisor's first question in the morning is what came off the stand yesterday and what
 * did not, and answering it should not mean walking four hundred folders. The ledger answers it in
 * one file, and it is json lines rather than json so that two runs appending at once cannot corrupt
 * each other and so that a run that dies mid write costs one line rather than the whole record.
 * Each line is one entry written as compact json, its keys in a fixed order.
 *
 * <p>A line nobody can parse is counted and skipped. That is the only sensible behaviour for a log
 * that is written by a process that can be killed, and pretending otherwise means a torn byte at
 * the end of a file hides a year of work behind it.
 */
public final class Ledger {

    /** What a run can end as. */
    public static final List<String> OUTCOMES = List.of("delivered", "rejected", "failed", "skipped");

    private Ledger() {}

    /**
     * An entry with nothing but its time, accession and outcome, stamped now.
     *
     * @see #entryFor(String, String, EntryOptions)
     */
    public static LedgerEntry entryFor(String accession, String outcome) {
        return entryFor(accession, outcome, EntryOptions.defaults());
    }

    /**
     * The entry for one run. It leaves out what it was not given; a zero star grading is kept,
     * because it is not the same as none, and the warnings are kept only when there are some.
     *
     * @param outcome one of {@link #OUTCOMES}
     * @throws StoreError when a run does not end that way; the details name the outcome and list
     *     the ones there are
     */
    public static LedgerEntry entryFor(String accession, String outcome, EntryOptions options) {
        if (!OUTCOMES.contains(outcome)) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("outcome", outcome);
            details.put("known", String.join(", ", OUTCOMES));
            throw new StoreError("a run does not end that way", details);
        }
        return new LedgerEntry(
                IsoTime.format(options.at().orElseGet(Instant::now)),
                accession,
                outcome,
                given(options.profile()),
                options.stars(),
                options.bytes(),
                given(options.digest()),
                given(options.reason()),
                options.warnings());
    }

    /**
     * Append an entry to a ledger, making the file if it is not there.
     *
     * @return the entry
     * @throws StoreError when the line could not be appended
     */
    public static LedgerEntry appendEntry(Path path, LedgerEntry entry) {
        AtomicFiles.appendLine(path, Json.stringify(entry));
        return entry;
    }

    /**
     * Read the ledger, counting what could not be read rather than hiding it. A ledger that is not
     * there yet reads as empty, and blank lines are ignored.
     *
     * @throws StoreError when the file is there and could not be read
     */
    public static LedgerRead readLedger(Path path) {
        if (!Files.exists(path)) {
            return new LedgerRead(List.of(), 0);
        }
        List<LedgerEntry> entries = new ArrayList<>();
        int unreadable = 0;
        for (String line : AtomicFiles.readText(path).split("\n", -1)) {
            if (Whitespace.trim(line).isEmpty()) {
                continue;
            }
            Optional<LedgerEntry> entry = parsed(line).flatMap(LedgerEntry::fromJson);
            if (entry.isPresent()) {
                entries.add(entry.get());
            } else {
                unreadable += 1;
            }
        }
        return new LedgerRead(entries, unreadable);
    }

    /**
     * The most recent entry for each accession, which is what anybody means when they ask what
     * happened to a sheet. Earlier entries are kept in the file because a sheet that failed twice
     * before it went out is worth knowing about. Of two entries at the same time, the later read
     * wins.
     *
     * @return the entries by accession, in the order each accession was first seen
     */
    public static Map<String, LedgerEntry> latestByAccession(List<LedgerEntry> entries) {
        Map<String, LedgerEntry> out = new LinkedHashMap<>();
        for (LedgerEntry entry : entries) {
            LedgerEntry held = out.get(entry.accession());
            if (held == null || entry.at().compareTo(held.at()) >= 0) {
                out.put(entry.accession(), entry);
            }
        }
        return Collections.unmodifiableMap(out);
    }

    /** Every entry, in order. */
    public static List<LedgerEntry> select(List<LedgerEntry> entries) {
        return select(entries, LedgerFilter.everything());
    }

    /** The entries the filter picks, in order. */
    public static List<LedgerEntry> select(List<LedgerEntry> entries, LedgerFilter filter) {
        return entries.stream().filter(filter::picks).toList();
    }

    /** What a set of entries produced, counted. */
    public static Summary summarise(List<LedgerEntry> entries) {
        Map<String, Integer> byOutcome = new LinkedHashMap<>();
        Map<String, Integer> byProfile = new LinkedHashMap<>();
        Map<Integer, Integer> byStars = new LinkedHashMap<>();
        long bytes = 0;
        long stars = 0;
        for (LedgerEntry entry : entries) {
            byOutcome.merge(entry.outcome(), 1, Integer::sum);
            entry.profile().ifPresent(name -> byProfile.merge(name, 1, Integer::sum));
            bytes += entry.bytes().orElse(0);
            if (entry.stars().isPresent()) {
                stars += entry.stars().getAsInt();
                byStars.merge(entry.stars().getAsInt(), 1, Integer::sum);
            }
        }
        int graded = byStars.values().stream().mapToInt(Integer::intValue).sum();
        OptionalDouble average = graded == 0 ? OptionalDouble.empty() : OptionalDouble.of((double) stars / graded);
        return new Summary(entries.size(), byOutcome, bytes, graded, average, byStars, byProfile);
    }

    /**
     * A summary for a person: the totals, then each outcome there was any of in the fixed order of
     * {@link #OUTCOMES}, then the average grading to two places when anything was graded, how many
     * sit at each level from the top down, and how many ran under each profile when more than one
     * did. The levels are the table a funder's progress report asks for.
     */
    public static List<String> describeSummary(Summary summary) {
        List<String> lines = new ArrayList<>();
        lines.add(summary.total() + " accessions, " + summary.bytes() + " bytes");
        for (String outcome : OUTCOMES) {
            int count = summary.byOutcome().getOrDefault(outcome, 0);
            if (count != 0) {
                lines.add("  " + count + " " + outcome);
            }
        }
        summary.averageStars()
                .ifPresent(average ->
                        lines.add("  " + NumberText.fixed(average, 2) + " stars over " + summary.graded() + " graded"));
        for (int stars = Check.MAX_STARS; stars >= 0; stars -= 1) {
            int count = summary.byStars().getOrDefault(stars, 0);
            if (count != 0) {
                lines.add("    " + count + " at " + stars + (stars == 1 ? " star" : " stars"));
            }
        }
        if (summary.byProfile().size() > 1) {
            summary.byProfile().forEach((name, count) -> lines.add("  " + count + " under " + name));
        }
        return lines;
    }

    private static Optional<Object> parsed(String line) {
        try {
            return Optional.ofNullable(Json.parse(line));
        } catch (JsonSyntaxException torn) {
            return Optional.empty();
        }
    }

    private static Optional<String> given(Optional<String> value) {
        return value.filter(text -> !text.isEmpty());
    }
}
