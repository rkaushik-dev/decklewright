package io.github.rkaushikdev.decklewright.qc;

import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The quality report.
 *
 * <p>Two readers and they want different things. An operator wants one line that says whether to
 * go and look at the stand. A funder wants every measurement with the limit beside it, because a
 * report that says four stars without saying what was measured is not evidence of anything.
 *
 * <p>Both come out of the same findings, so they cannot disagree.
 */
public final class Reports {
    /**
     * What to do about it. Every entry names a check and says what an operator would actually
     * change, which is the only part of a report anybody acts on.
     */
    private static final Map<String, String> REMEDIES = Map.of(
            "sampling", "set the scanner to the frequency the profile asks for",
            "samplingFloor", "capture again at a higher frequency",
            "skew", "square the sheet on the bed",
            "skewAgreement", "look at the capture, the detector was not sure what it found",
            "illumination", "move a lamp towards the darker side",
            "grayBalance", "white balance the stand against the target",
            "toneResponse", "set the exposure so the wedge reads its aims",
            "noise", "lower the gain or lengthen the exposure",
            "clipping", "lower the exposure and capture again",
            "sharpness", "refocus the stand");

    private static final String NO_REMEDY = "look at the capture";

    private Reports() {}

    /**
     * One finding as a report line: its stars padded to four places, such as {@code skew: ***. a bit
     * off}, or that it was not measured and why.
     */
    public static String describeFinding(Finding finding) {
        if (!finding.ran()) {
            return finding.name() + ": not measured, " + finding.note();
        }
        String stars = "*".repeat(finding.stars());
        if (stars.length() < Check.MAX_STARS) {
            stars += ".".repeat(Check.MAX_STARS - stars.length());
        }
        return finding.name() + ": " + stars + " " + finding.note();
    }

    /**
     * The report against three stars, with no item named.
     *
     * @see #report(List, ReportOptions)
     */
    public static List<String> report(List<Finding> findings) {
        return report(findings, ReportOptions.defaults());
    }

    /**
     * The report for a person: the level, what is holding it down when it falls short of the level
     * wanted, how many checks could not run, then every finding indented under it.
     */
    public static List<String> report(List<Finding> findings, ReportOptions options) {
        Level level = Levels.levelOf(findings);
        String name = Levels.levelName(level.stars());
        Optional<String> item = shown(options.item());
        List<String> lines = new ArrayList<>();
        lines.add(item.map(text -> text + ": " + name).orElse(name));

        if (level.ran() == 0) {
            lines.add("nothing could be measured");
        } else if (level.stars() < options.wanted()) {
            lines.add("asked for " + Levels.levelName(options.wanted()) + ", held down by "
                    + level.worst().orElseThrow());
        }
        if (level.skipped() > 0) {
            lines.add(NumberText.of(level.skipped()) + " checks could not run");
        }

        findings.forEach(finding -> lines.add("  " + describeFinding(finding)));
        return List.copyOf(lines);
    }

    /**
     * The one line against three stars, with no item named.
     *
     * @see #oneLine(List, ReportOptions)
     */
    public static String oneLine(List<Finding> findings) {
        return oneLine(findings, ReportOptions.defaults());
    }

    /**
     * One line for a batch log, such as {@code q18, two stars, held down by skew, 1 not measured}.
     */
    public static String oneLine(List<Finding> findings, ReportOptions options) {
        Level level = Levels.levelOf(findings);
        List<String> parts = new ArrayList<>();
        shown(options.item()).ifPresent(parts::add);
        parts.add(Levels.levelName(level.stars()));
        if (level.stars() < options.wanted()) {
            shown(level.worst()).ifPresent(worst -> parts.add("held down by " + worst));
        }
        if (level.skipped() > 0) {
            parts.add(NumberText.of(level.skipped()) + " not measured");
        }
        return String.join(", ", parts);
    }

    /**
     * The remedies against three stars.
     *
     * @see #remedies(List, int)
     */
    public static List<String> remedies(List<Finding> findings) {
        return remedies(findings, Check.DEFAULT_MINIMUM_STARS);
    }

    /**
     * What to change, one line per check that fell short of the level wanted, furthest short first,
     * such as {@code skew: square the sheet on the bed}. A check with no advice of its own is told to
     * look at the capture.
     */
    public static List<String> remedies(List<Finding> findings, int wanted) {
        return Levels.shortfall(findings, wanted).stream()
                .map(shortfall -> shortfall.name() + ": " + REMEDIES.getOrDefault(shortfall.name(), NO_REMEDY))
                .toList();
    }

    /**
     * The measurements on their own, for a machine rather than a person.
     *
     * @return {@code {measured, stars, ran}} by check name, in finding order, with a null
     *     measurement for one that is not finite and every number a {@link Double}; a new map the
     *     caller may keep
     */
    public static Map<String, Map<String, Object>> measurements(List<Finding> findings) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (Finding finding : findings) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("measured", Double.isFinite(finding.measured()) ? finding.measured() : null);
            entry.put("stars", (double) finding.stars());
            entry.put("ran", finding.ran());
            out.put(finding.name(), entry);
        }
        return out;
    }

    /**
     * The plain data against three stars, with no item named.
     *
     * @see #toJson(List, ReportOptions)
     */
    public static Map<String, Object> toJson(List<Finding> findings) {
        return toJson(findings, ReportOptions.defaults());
    }

    /**
     * The whole report as plain data: {@code {item, stars, level, wanted, passed, worst, ran,
     * skipped, measurements, remedies}}, in that order, with null for an item or a worst check that
     * is not there and every number a {@link Double}; a new map the caller may keep.
     */
    public static Map<String, Object> toJson(List<Finding> findings, ReportOptions options) {
        Level level = Levels.levelOf(findings);
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("item", options.item().orElse(null));
        json.put("stars", (double) level.stars());
        json.put("level", Levels.levelName(level.stars()));
        json.put("wanted", (double) options.wanted());
        json.put("passed", level.stars() >= options.wanted());
        json.put("worst", level.worst().orElse(null));
        json.put("ran", (double) level.ran());
        json.put("skipped", (double) level.skipped());
        json.put("measurements", measurements(findings));
        json.put("remedies", new ArrayList<Object>(remedies(findings, options.wanted())));
        return json;
    }

    /** A name worth printing on a line a person reads: present and not empty. */
    private static Optional<String> shown(Optional<String> name) {
        return name.filter(text -> !text.isEmpty());
    }
}
