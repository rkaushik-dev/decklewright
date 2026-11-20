package io.github.rkaushikdev.decklewright.plan;

import io.github.rkaushikdev.decklewright.plan.rules.BoundsMode;
import java.util.Objects;

/**
 * How a crop is planned beyond what the profile says: the choices an operator or a pipeline makes
 * per run rather than per collection.
 *
 * @param fallBackToSheet crop to the paper edge, with a warning, when the profile wants a neatline
 *     and none was found, rather than refusing
 * @param boundsMode what to do when the crop reaches off the capture
 * @param strictResolution refuse a capture under the sampling floor rather than warn
 * @param maxPasses how many times the shape and grid rules may run before the set is called
 *     unsettled; checked when the rules run
 * @see RuleSets#rulesFor(io.github.rkaushikdev.decklewright.profile.CropOptions,
 *     io.github.rkaushikdev.decklewright.plan.rules.Landmarks, PlanOptions)
 */
public record PlanOptions(boolean fallBackToSheet, BoundsMode boundsMode, boolean strictResolution, int maxPasses) {

    /** How many passes the engine allows when nobody says otherwise. */
    static final int DEFAULT_MAX_PASSES = 6;

    /** Options of the given parts. */
    public PlanOptions {
        Objects.requireNonNull(boundsMode, "boundsMode");
    }

    /** No fall back, a warning for an overhang or a capture under the floor, and six passes. */
    public static PlanOptions defaults() {
        return new PlanOptions(false, BoundsMode.WARN, false, DEFAULT_MAX_PASSES);
    }

    /** These options, falling back to the paper edge or not. */
    public PlanOptions withFallBackToSheet(boolean fallBackToSheet) {
        return new PlanOptions(fallBackToSheet, boundsMode, strictResolution, maxPasses);
    }

    /** These options with another answer to an overhang. */
    public PlanOptions withBoundsMode(BoundsMode boundsMode) {
        return new PlanOptions(fallBackToSheet, boundsMode, strictResolution, maxPasses);
    }

    /** These options, refusing a capture under the floor or not. */
    public PlanOptions withStrictResolution(boolean strictResolution) {
        return new PlanOptions(fallBackToSheet, boundsMode, strictResolution, maxPasses);
    }

    /** These options with another limit on the passes. */
    public PlanOptions withMaxPasses(int maxPasses) {
        return new PlanOptions(fallBackToSheet, boundsMode, strictResolution, maxPasses);
    }
}
