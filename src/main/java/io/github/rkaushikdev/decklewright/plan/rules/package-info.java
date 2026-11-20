/**
 * The crop rules: each one proposes where the crop should be, or checks it, and none knows about
 * the others.
 *
 * <p>{@link io.github.rkaushikdev.decklewright.plan.rules.Rule} is the protocol and {@link
 * io.github.rkaushikdev.decklewright.plan.rules.Rules} the few things done to a set of them. Each of
 * the other classes builds one kind of rule, under the name the trail and the explanations call it
 * by: {@link io.github.rkaushikdev.decklewright.plan.rules.AnchorRules} ({@code anchor}), {@link
 * io.github.rkaushikdev.decklewright.plan.rules.BleedRules} ({@code bleed}), {@link
 * io.github.rkaushikdev.decklewright.plan.rules.SafeAreaRules} ({@code safeArea}), {@link
 * io.github.rkaushikdev.decklewright.plan.rules.AspectRules} ({@code aspect}), {@link
 * io.github.rkaushikdev.decklewright.plan.rules.SnapRules} ({@code snap}, {@code gridAlign} and
 * {@code round}), {@link io.github.rkaushikdev.decklewright.plan.rules.BoundsRules} ({@code bounds}
 * and {@code notEmpty}) and {@link io.github.rkaushikdev.decklewright.plan.rules.ResolutionRules}
 * ({@code resolution} and {@code minimumSize}).
 *
 * <p>A rule that cannot be built from what it was given throws a {@link
 * io.github.rkaushikdev.decklewright.BadArgumentError} or a {@link
 * io.github.rkaushikdev.decklewright.PlanError} when it is built. A check that refuses a plan throws
 * a {@link io.github.rkaushikdev.decklewright.PlanError} when it is applied.
 */
package io.github.rkaushikdev.decklewright.plan.rules;
