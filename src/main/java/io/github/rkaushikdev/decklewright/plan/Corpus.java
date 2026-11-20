package io.github.rkaushikdev.decklewright.plan;

import io.github.rkaushikdev.decklewright.geom.Rect;
import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.plan.rules.Landmarks;
import io.github.rkaushikdev.decklewright.profile.CropOptions;
import io.github.rkaushikdev.decklewright.profile.Profile;
import io.github.rkaushikdev.decklewright.profile.ProfileRegistry;
import io.github.rkaushikdev.decklewright.profile.Settings;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * A corpus of planning cases with the answers written down.
 *
 * <p>A profile change is meant to change one collection. What it does in practice is change every
 * collection that inherits from the same house style, and the only way anybody notices is when a
 * plate comes out wrong three weeks later.
 *
 * <p>This is the answer to that: a set of cases with the crop each one produced on the day it was
 * recorded. Run it after a change and read the differences. A difference is not automatically wrong.
 * It has to be looked at, which is the whole point.
 */
final class Corpus {
    /** The cases as they stood when the rule engine replaced the linear planner. */
    static final List<Case> CASES = List.of(
            new Case(
                    "plate, square to the bed",
                    "house",
                    Rect.of(0, 0, 8000, 6400),
                    Rect.of(700, 600, 6800, 5600),
                    Optional.empty(),
                    400,
                    Rect.of(700, 600, 6800, 5600),
                    OptionalInt.of(0)),
            new Case(
                    "plate with the atlas bleed",
                    "atlas-plates",
                    Rect.of(0, 0, 12000, 9600),
                    Rect.of(1000, 900, 10200, 8400),
                    Optional.empty(),
                    600,
                    Rect.of(952, 852, 10296, 8496),
                    OptionalInt.of(0)),
            new Case(
                    "quadrangle cropped to the neatline",
                    "quadrangles",
                    Rect.of(0, 0, 14000, 17000),
                    Rect.of(800, 700, 12600, 15600),
                    Optional.of(Rect.of(1400, 1300, 11400, 13600)),
                    600,
                    Rect.of(1328, 1225, 11544, 14576),
                    OptionalInt.of(0)),
            new Case(
                    "rolled drawing with a wide bleed",
                    "rolled-drawings",
                    Rect.of(0, 0, 8000, 12000),
                    Rect.of(500, 600, 7000, 10800),
                    Optional.empty(),
                    300,
                    Rect.of(429, 529, 7142, 10942),
                    OptionalInt.of(0)),
            new Case(
                    "microfilm frame, no bleed at all",
                    "microfilm",
                    Rect.of(0, 0, 3000, 2400),
                    Rect.of(200, 150, 2600, 2100),
                    Optional.empty(),
                    300,
                    Rect.of(200, 150, 2600, 2100),
                    OptionalInt.of(0)),
            new Case(
                    "plate placed too near the edge of the bed",
                    "atlas-plates",
                    Rect.of(0, 0, 8000, 6400),
                    Rect.of(20, 20, 7900, 6300),
                    Optional.empty(),
                    600,
                    Rect.of(-28, -28, 7996, 6396),
                    OptionalInt.of(1)),
            new Case(
                    "plate captured well under the floor",
                    "quadrangles",
                    Rect.of(0, 0, 4000, 5000),
                    Rect.of(200, 200, 3600, 4600),
                    Optional.of(Rect.of(400, 400, 3200, 4000)),
                    200,
                    Rect.of(376, 374, 3248, 4328),
                    OptionalInt.of(1)));

    private Corpus() {}

    /** The plan the case's profile makes of it today, with the house rules. */
    static CropPlan runCase(Case item, ProfileRegistry registry) {
        Profile profile = registry.get(item.profile());
        Sampling sampling = Sampling.of(item.ppi());
        CropOptions crop = Settings.cropOptions(profile, sampling);
        Landmarks found = new Landmarks(item.sheet(), item.neatline());
        Rect anchor = CropPlanner.startingAnchor(crop, found);
        return Engine.planWith(RuleSets.rulesFor(crop, found), PlanInput.of(item.capture(), anchor, sampling));
    }

    /** Every recorded case run again. */
    static List<CaseResult> runCorpus(ProfileRegistry registry) {
        return runCorpus(registry, CASES);
    }

    /**
     * The cases run again, each compared with its recorded crop and, where one was recorded, its
     * warning count. A case that cannot be planned at all is reported with its error rather than
     * thrown, so one broken case does not hide the rest.
     */
    static List<CaseResult> runCorpus(ProfileRegistry registry, List<Case> cases) {
        return cases.stream().map(item -> runOne(item, registry)).toList();
    }

    /** One line per result, saying whether it is as recorded, how it changed, or why it failed. */
    static List<String> describeResults(List<CaseResult> results) {
        return results.stream().map(Corpus::describe).toList();
    }

    /** How many cases there were, and how many matched, changed and failed. */
    static Tally tally(List<CaseResult> results) {
        int matched = 0;
        int changed = 0;
        int failed = 0;
        for (CaseResult result : results) {
            if (result.matched()) {
                matched += 1;
            }
            if (result.error().isPresent()) {
                failed += 1;
            } else if (!result.matched()) {
                changed += 1;
            }
        }
        return new Tally(results.size(), matched, changed, failed);
    }

    private static CaseResult runOne(Case item, ProfileRegistry registry) {
        try {
            CropPlan plan = runCase(item, registry);
            int warnings = plan.warnings().size();
            boolean matched = plan.rect().approxEquals(item.expected())
                    && item.warnings().stream().allMatch(expected -> expected == warnings);
            return new CaseResult(item.name(), matched, item.expected(), plan.rect(), warnings, Optional.empty());
        } catch (RuntimeException error) {
            // Whatever stopped this case is reported with it: the corpus is read for every
            // difference, and one case that cannot be planned must not hide the rest.
            return new CaseResult(
                    item.name(),
                    false,
                    item.expected(),
                    Rect.empty(),
                    0,
                    Optional.of(Objects.toString(error.getMessage(), "")));
        }
    }

    private static String describe(CaseResult result) {
        if (result.error().isPresent()) {
            return result.name() + ": failed, " + result.error().get();
        }
        if (result.matched()) {
            return result.name() + ": as recorded";
        }
        return result.name() + ": was " + geometry(result.expected()) + ", now " + geometry(result.actual());
    }

    /** The crop as {@code width x height + x + y}, every figure exactly as recorded. */
    private static String geometry(Rect rect) {
        return NumberText.of(rect.width())
                + "x"
                + NumberText.of(rect.height())
                + "+"
                + NumberText.of(rect.x())
                + "+"
                + NumberText.of(rect.y());
    }

    /**
     * One planning case and the answer it gave on the day it was recorded.
     *
     * @param name what the case is, which says what it guards against
     * @param profile the name of the profile it is planned with
     * @param capture the whole capture
     * @param sheet the paper edge the detector found
     * @param neatline the neatline it found, when the sheet has one
     * @param ppi the sampling frequency
     * @param expected the crop recorded
     * @param warnings how many warnings were recorded; empty when the count is not checked
     */
    record Case(
            String name,
            String profile,
            Rect capture,
            Rect sheet,
            Optional<Rect> neatline,
            double ppi,
            Rect expected,
            OptionalInt warnings) {

        Case {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(capture, "capture");
            Objects.requireNonNull(sheet, "sheet");
            Objects.requireNonNull(neatline, "neatline");
            Objects.requireNonNull(expected, "expected");
            Objects.requireNonNull(warnings, "warnings");
        }
    }

    /**
     * What running a case again gave.
     *
     * @param name the case
     * @param matched true when the crop and the warning count are as recorded
     * @param expected the crop recorded
     * @param actual the crop today; empty when the case failed
     * @param warnings how many warnings the plan carries today; zero when the case failed
     * @param error why the case could not be planned; empty when it could
     */
    record CaseResult(String name, boolean matched, Rect expected, Rect actual, int warnings, Optional<String> error) {

        CaseResult {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(expected, "expected");
            Objects.requireNonNull(actual, "actual");
            Objects.requireNonNull(error, "error");
        }
    }

    /**
     * The corpus in four numbers.
     *
     * @param total how many cases ran
     * @param matched how many were as recorded
     * @param changed how many planned but came out differently
     * @param failed how many could not be planned
     */
    record Tally(int total, int matched, int changed, int failed) {}
}
