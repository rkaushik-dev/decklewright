/**
 * Crop planning as the rest of the tree sees it: from a read capture and a profile to a crop, and
 * the record of why the crop is where it is.
 *
 * <p>The pipeline calls {@link io.github.rkaushikdev.decklewright.plan.CropPlanner#planCrop} and gets
 * back a {@link io.github.rkaushikdev.decklewright.plan.PlannedCrop} holding the {@link
 * io.github.rkaushikdev.decklewright.plan.CropPlan}: the crop, the capture and the turn to take out
 * first, all in the straightened frame the render cuts in, with every rule that moved the crop and
 * every warning.
 *
 * <p>Everything else is public because a rule set is worth building by hand when an operator is
 * overriding one rule for one item, and because the explain command needs the pieces: {@link
 * io.github.rkaushikdev.decklewright.plan.RuleSets} builds the set a profile asks for out of the
 * rules in {@link io.github.rkaushikdev.decklewright.plan.rules}, {@link
 * io.github.rkaushikdev.decklewright.plan.Engine} runs a set until it settles and edits one by name,
 * and {@link io.github.rkaushikdev.decklewright.plan.Explanations} says in words what happened.
 *
 * <p>A crop that no rule set can give, such as one with no area, an anchor the capture does not
 * have, or one a strict collection refuses, is a {@link io.github.rkaushikdev.decklewright.PlanError}.
 */
package io.github.rkaushikdev.decklewright.plan;
