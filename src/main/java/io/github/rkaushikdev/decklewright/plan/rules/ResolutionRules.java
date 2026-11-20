package io.github.rkaushikdev.decklewright.plan.rules;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.PlanError;
import io.github.rkaushikdev.decklewright.measure.Unit;
import io.github.rkaushikdev.decklewright.measure.Units;
import io.github.rkaushikdev.decklewright.plan.CropPlan;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.Num;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The sampling floor.
 *
 * <p>This is a check and it cannot be anything else. Cropping does not change how finely the paper
 * was sampled: a crop half the size of another crop off the same capture is the same ppi. If the
 * capture was taken too low the answer is to capture it again, and the only useful thing a planner
 * can do is say so before three derivatives get written from it.
 *
 * <p>There are two shapes because there are two kinds of collection. Most warn and carry on, so a
 * batch finishes and somebody reads the log. The funded ones refuse, because a file under the floor
 * cannot be delivered at all and writing it wastes the disk.
 */
public final class ResolutionRules {
    /** What both sampling floors are called in the trail. */
    private static final String NAME = "resolution";

    private ResolutionRules() {}

    /**
     * Warns when the worse axis of the capture was sampled under the floor.
     *
     * @throws BadArgumentError when the floor is not a finite number above zero
     */
    public static Rule resolutionRule(double minimumPpi) {
        Guards.requirePositive(minimumPpi, "minimumPpi");
        return Rules.rule(NAME, Phase.CHECK, (plan, context) -> {
            double achieved = worseAxis(plan);
            if (achieved >= minimumPpi) {
                return plan;
            }
            return plan.warned("the capture is "
                    + NumberText.of(tenths(achieved))
                    + " ppi against a floor of "
                    + NumberText.of(minimumPpi));
        });
    }

    /**
     * Refuses a capture whose worse axis was sampled under the floor.
     *
     * @throws BadArgumentError when the floor is not a finite number above zero
     * @throws PlanError from the rule when the capture is under the floor, with the {@code achieved}
     *     ppi and the {@code minimumPpi} in the details
     */
    public static Rule strictResolutionRule(double minimumPpi) {
        Guards.requirePositive(minimumPpi, "minimumPpi");
        return Rules.rule(NAME, Phase.CHECK, (plan, context) -> {
            double achieved = worseAxis(plan);
            if (achieved >= minimumPpi) {
                return plan;
            }
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("achieved", achieved);
            details.put("minimumPpi", minimumPpi);
            throw new PlanError(
                    "the capture is "
                            + NumberText.of(tenths(achieved))
                            + " ppi and this collection will not take under "
                            + NumberText.of(minimumPpi),
                    details);
        });
    }

    /**
     * A floor on the finished pixel count rather than on the sampling. A viewer that wants at least
     * so many pixels across does not care how they were got.
     *
     * @param minimumPixels on the long side
     * @throws BadArgumentError when the floor is not a finite number above zero
     */
    public static Rule minimumSizeRule(double minimumPixels) {
        Guards.requirePositive(minimumPixels, "minimumPixels");
        return Rules.rule("minimumSize", Phase.CHECK, (plan, context) -> {
            double longest = Math.max(plan.rect().width(), plan.rect().height());
            if (longest >= minimumPixels) {
                return plan;
            }
            return plan.warned("the crop is "
                    + NumberText.of(tenths(longest))
                    + " px on its long side against a floor of "
                    + NumberText.of(minimumPixels));
        });
    }

    /**
     * The floor that warns.
     *
     * @see #resolutionFor(double, boolean)
     */
    public static Rule resolutionFor(double minimumPpi) {
        return resolutionFor(minimumPpi, false);
    }

    /**
     * The floor a collection asks for: one that refuses when strict, one that warns otherwise.
     *
     * @throws BadArgumentError when the floor is not a finite number above zero
     */
    public static Rule resolutionFor(double minimumPpi, boolean strict) {
        return strict ? strictResolutionRule(minimumPpi) : resolutionRule(minimumPpi);
    }

    /**
     * What a crop of a known physical width comes out at. The quality checks read the same number
     * later and the two have to agree, so they both come through here.
     *
     * @return pixels per inch
     * @throws BadArgumentError when the width is not a finite number above zero
     */
    public static double achievedPpi(CropPlan plan, double physicalWidth, Unit unit) {
        Guards.requirePositive(physicalWidth, "physicalWidth");
        return plan.rect().width() / Units.toInches(physicalWidth, unit);
    }

    private static double worseAxis(CropPlan plan) {
        return Math.min(plan.sampling().x(), plan.sampling().y());
    }

    /** To one place, halves up, the way the messages have always quoted a figure. */
    private static double tenths(double value) {
        return Num.roundHalfUp(value * 10) / 10;
    }
}
