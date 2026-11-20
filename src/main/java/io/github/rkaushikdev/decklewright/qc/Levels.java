package io.github.rkaushikdev.decklewright.qc;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Turning a set of findings into one number.
 *
 * <p>The funder grades a delivery at a star level and the level is the worst check, not the
 * average. That is not a choice this code gets to make, and it is worth stating plainly because
 * averaging looks so much friendlier: a capture with six checks at four stars and one at one star
 * is a one star capture, and the report has to say so.
 *
 * <p>A check that could not run is not a zero. It is an absence, and a delivery with an absence
 * has no level at all until somebody decides whether that check was required.
 */
public final class Levels {
    /** What each level is called on a report, zero to four, read only. */
    public static final Map<Integer, String> LEVEL_NAMES = ordered(
            Map.entry(0, "below the lowest level"),
            Map.entry(1, "one star"),
            Map.entry(2, "two stars"),
            Map.entry(3, "three stars"),
            Map.entry(4, "four stars"));

    /**
     * The order the original's {@code localeCompare} put check names in, for breaking a tie.
     *
     * <p>Check names are camelCase identifiers of ASCII letters and digits: {@code sampling}, {@code
     * skewAgreement}, {@code grayBalance}, or a site's own such as {@code always4}. {@code
     * localeCompare} collates those case-insensitively first, so {@code noise} sorts before {@code
     * Sharpness} where {@link String#compareTo(String)} would put every capital before every lower
     * case letter, and only then puts a lower case letter before its capital. For letters and digits
     * that is exactly comparing the lower cased names and, when they are equal, putting the name
     * whose first differing character is lower case first, which reverse code point order does
     * because the two names then differ only in case. For the standard checks' names both orders
     * agree with {@code compareTo}; this keeps a site's own names in the original order too.
     */
    private static final Comparator<String> NAME_ORDER = Comparator.comparing(
                    (String name) -> name.toLowerCase(Locale.ROOT))
            .thenComparing(Comparator.reverseOrder());

    private Levels() {}

    /** The worst check that ran, how many ran, and how many could not. */
    public static Level levelOf(List<Finding> findings) {
        List<Finding> ran = findings.stream().filter(Finding::ran).toList();
        int skipped = findings.size() - ran.size();
        if (ran.isEmpty()) {
            return new Level(0, Optional.empty(), 0, skipped);
        }

        int stars = Check.MAX_STARS;
        String worst = ran.get(0).name();
        for (Finding finding : ran) {
            if (finding.stars() < stars) {
                stars = finding.stars();
                worst = finding.name();
            }
        }
        return new Level(stars, Optional.of(worst), ran.size(), skipped);
    }

    /**
     * The level a delivery reaches once the checks that had to run are taken into account. A
     * required check that could not run drops the level to nothing, because an unmeasured capture is
     * not a passing one. A required check with no finding at all counts as one that could not run;
     * when two findings share a name, the later one is the one read.
     *
     * @param required the names of the checks that had to run
     */
    public static RequiredLevel levelWithRequired(List<Finding> findings, List<String> required) {
        Map<String, Finding> byName = new LinkedHashMap<>();
        findings.forEach(finding -> byName.put(finding.name(), finding));
        List<String> missing =
                required.stream().filter(name -> !ranIn(byName, name)).toList();
        if (!missing.isEmpty()) {
            return new RequiredLevel(0, Optional.of(missing.get(0)), missing);
        }
        Level base = levelOf(findings);
        return new RequiredLevel(base.stars(), base.worst(), List.of());
    }

    /** What the level is called on a report, or {@code unknown} for a number that is not a level. */
    public static String levelName(int stars) {
        return LEVEL_NAMES.getOrDefault(stars, "unknown");
    }

    /** True when the worst check that ran reaches the floor. */
    public static boolean meetsLevel(List<Finding> findings, int minimumStars) {
        return levelOf(findings).stars() >= minimumStars;
    }

    /**
     * How far a delivery is from the level it wants, named check by check. This is what an operator
     * reads when a batch comes back short: not that it failed but which two checks are holding it
     * down.
     *
     * @return the checks that ran and fell short, furthest short first, ties in name order so a
     *     report is stable
     */
    public static List<Shortfall> shortfall(List<Finding> findings, int wanted) {
        return findings.stream()
                .filter(finding -> finding.ran() && finding.stars() < wanted)
                .map(finding -> new Shortfall(finding.name(), finding.stars(), wanted - finding.stars()))
                .sorted(Comparator.comparingInt(Shortfall::shortBy)
                        .reversed()
                        .thenComparing(Shortfall::name, NAME_ORDER))
                .toList();
    }

    /**
     * A tally across a whole batch, which is the number a funder asks for.
     *
     * @return how many items sit at each level, keyed zero to four in that order, a level outside the
     *     range counted at the nearest end; read only
     */
    public static Map<Integer, Integer> tallyLevels(List<? extends Graded> levels) {
        Map<Integer, Integer> out = new LinkedHashMap<>();
        for (int stars = 0; stars <= Check.MAX_STARS; stars += 1) {
            out.put(stars, 0);
        }
        for (Graded level : levels) {
            int key = Math.max(0, Math.min(Check.MAX_STARS, level.stars()));
            out.merge(key, 1, Integer::sum);
        }
        return Collections.unmodifiableMap(out);
    }

    /** The level the whole batch delivers at: its worst item, or nothing for an empty batch. */
    public static int batchLevel(List<? extends Graded> levels) {
        if (levels.isEmpty()) {
            return 0;
        }
        int worst = Check.MAX_STARS;
        for (Graded level : levels) {
            worst = Math.min(worst, level.stars());
        }
        return worst;
    }

    private static boolean ranIn(Map<String, Finding> byName, String name) {
        Finding finding = byName.get(name);
        return finding != null && finding.ran();
    }

    @SafeVarargs
    private static Map<Integer, String> ordered(Map.Entry<Integer, String>... entries) {
        Map<Integer, String> out = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> entry : entries) {
            out.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(out);
    }
}
