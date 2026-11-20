package io.github.rkaushikdev.decklewright.plan.rules;

import io.github.rkaushikdev.decklewright.BadArgumentError;
import io.github.rkaushikdev.decklewright.PlanError;
import io.github.rkaushikdev.decklewright.geom.AspectDirection;
import io.github.rkaushikdev.decklewright.geom.Fitting;
import io.github.rkaushikdev.decklewright.util.Guards;
import io.github.rkaushikdev.decklewright.util.NumberText;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Forcing a crop to a shape.
 *
 * <p>Two collections deliver into a viewer that wants every plate the same shape, and one delivers
 * into a print template. The rest do not care and set nothing.
 *
 * <p>Growing is the safe direction and it is the default: a crop that grows takes in more paper, and
 * a crop that shrinks throws image away. A profile that shrinks is saying it would rather lose an
 * edge than have a margin, which is a real preference and a rare one.
 */
public final class AspectRules {
    /** What every aspect rule is called in the trail. */
    private static final String NAME = "aspect";

    /** How far off the ratio a crop may be, as a share, for the nudge to apply. */
    private static final double NUDGE_WITHIN = 0.05;

    private AspectRules() {}

    /**
     * Grows the crop to the aspect about its centre.
     *
     * @see #aspectRule(double, AspectDirection)
     */
    public static Rule aspectRule(double aspect) {
        return aspectRule(aspect, AspectDirection.GROW);
    }

    /**
     * Forces the crop to the aspect about its centre, growing or shrinking it. An empty crop is left
     * alone.
     *
     * @param aspect width over height
     * @throws BadArgumentError when the aspect is not a finite number above zero, or the direction
     *     is missing
     */
    public static Rule aspectRule(double aspect, AspectDirection direction) {
        Guards.requirePositive(aspect, "aspect");
        requireDirection(direction);
        String note = "forced to " + NumberText.of(aspect) + " by " + direction.label() + "ing";
        return Rules.rule(NAME, Phase.SHAPE, (plan, context) -> {
            if (plan.rect().isEmpty()) {
                return plan;
            }
            return plan.moved(Fitting.forceAspect(plan.rect(), aspect, direction), NAME, note);
        });
    }

    /**
     * Grows the crop to the nearest allowed ratio.
     *
     * @see #nearestAspectRule(List, AspectDirection)
     */
    public static Rule nearestAspectRule(List<Double> allowed) {
        return nearestAspectRule(allowed, AspectDirection.GROW);
    }

    /**
     * The aspect of the crop itself, rounded to the nearest ratio a viewer would use. Some
     * collections want every plate the same shape without anybody having to work out what shape
     * that is. Nearness is measured on the logarithm of the ratio, so twice as wide and twice as
     * tall are equally far.
     *
     * @param allowed the ratios the collection accepts, width over height
     * @throws PlanError when there are no ratios to choose from
     * @throws BadArgumentError when a ratio is not a finite number above zero, or the direction is
     *     missing
     */
    public static Rule nearestAspectRule(List<Double> allowed, AspectDirection direction) {
        if (allowed.isEmpty()) {
            throw new PlanError("an aspect rule needs at least one ratio to choose from", Map.of());
        }
        for (double value : allowed) {
            Guards.requirePositive(value, "aspect");
        }
        requireDirection(direction);
        List<Double> ratios = List.copyOf(allowed);

        return Rules.rule(NAME, Phase.SHAPE, (plan, context) -> {
            if (plan.rect().isEmpty() || plan.rect().height() == 0) {
                return plan;
            }
            double current = plan.rect().width() / plan.rect().height();
            double chosen = ratios.get(0);
            for (double value : ratios.subList(1, ratios.size())) {
                if (Math.abs(StrictMath.log(value / current)) < Math.abs(StrictMath.log(chosen / current))) {
                    chosen = value;
                }
            }
            return plan.moved(
                    Fitting.forceAspect(plan.rect(), chosen, direction),
                    NAME,
                    "forced to the nearest allowed ratio, " + NumberText.of(chosen) + ", by " + direction.label()
                            + "ing");
        });
    }

    /**
     * Grows a crop to the aspect when it is already within five percent of it.
     *
     * @see #nudgeAspectRule(double, double, AspectDirection)
     */
    public static Rule nudgeAspectRule(double aspect) {
        return nudgeAspectRule(aspect, NUDGE_WITHIN, AspectDirection.GROW);
    }

    /**
     * Grows a crop to the aspect when it is already close to it.
     *
     * @see #nudgeAspectRule(double, double, AspectDirection)
     */
    public static Rule nudgeAspectRule(double aspect, double within) {
        return nudgeAspectRule(aspect, within, AspectDirection.GROW);
    }

    /**
     * An aspect that only applies when the crop is already close, so a plate that is genuinely a
     * different shape is left alone, with a warning, rather than being stretched into the
     * collection's usual one.
     *
     * @param within how far off the ratio the crop may be, as a share of it
     * @throws BadArgumentError when the aspect is not a finite number above zero, or the direction
     *     is missing
     */
    public static Rule nudgeAspectRule(double aspect, double within, AspectDirection direction) {
        Guards.requirePositive(aspect, "aspect");
        Rule inner = aspectRule(aspect, direction);
        return Rules.rule(NAME, Phase.SHAPE, (plan, context) -> {
            if (plan.rect().isEmpty() || plan.rect().height() == 0) {
                return plan;
            }
            double current = plan.rect().width() / plan.rect().height();
            double off = Math.abs(current - aspect) / aspect;
            if (off > within) {
                return plan.warned("the crop is "
                        + NumberText.fixed(off * 100, 1)
                        + " percent off the "
                        + NumberText.of(aspect)
                        + " ratio and was left alone");
            }
            return inner.apply(plan, context);
        });
    }

    /**
     * The aspect rule a profile asks for, growing the crop.
     *
     * @see #aspectFor(OptionalDouble, AspectDirection)
     */
    public static Optional<Rule> aspectFor(OptionalDouble aspect) {
        return aspectFor(aspect, AspectDirection.GROW);
    }

    /**
     * The aspect rule a profile asks for.
     *
     * @return the rule, or empty when the profile set no aspect
     * @throws BadArgumentError as {@link #aspectRule(double, AspectDirection)} does
     */
    public static Optional<Rule> aspectFor(OptionalDouble aspect, AspectDirection direction) {
        return aspect.isPresent() ? Optional.of(aspectRule(aspect.getAsDouble(), direction)) : Optional.empty();
    }

    private static void requireDirection(AspectDirection direction) {
        Guards.requireOneOf(direction, List.of(AspectDirection.values()), "direction");
    }
}
