import { PlanError } from '../errors.js';
import { assertPositiveInteger } from '../util/assert.js';
import { CropPlan } from './plan.js';
import { assertNoDuplicates, inPhase, ordered } from './rules/rule.js';

/**
 * Running a rule set.
 *
 * The linear planner ran each step once in a fixed order, and that is what it
 * could not fix: snapping after an aspect breaks the aspect, and forcing the
 * aspect after snapping breaks the snap. Both collections exist and both were
 * right.
 *
 * The engine runs the moving rules again until the crop stops changing. A set
 * that settles gives an answer both rules agree with. A set that does not
 * settle is a contradiction, and the plan says so rather than handing back
 * whichever rule happened to run last.
 */

/** @typedef {import('./rules/rule.js').Rule} Rule */

/**
 * @param {readonly Rule[]} rules
 * @param {CropPlan} plan
 * @param {Record<string, any>} context
 * @param {object} [options]
 * @param {number} [options.maxPasses]
 * @returns {{plan: CropPlan, passes: number, settled: boolean}}
 */
export function runRules(rules, plan, context, { maxPasses = 6 } = {}) {
  assertPositiveInteger(maxPasses, 'maxPasses');
  assertNoDuplicates(rules);
  const sorted = ordered(rules);
  const moving = sorted.filter((item) => item.phase !== 'check');
  const checks = inPhase(sorted, 'check');

  let current = plan;
  let passes = 0;
  let settled = false;

  for (let pass = 0; pass < maxPasses; pass += 1) {
    const before = current.rect;
    for (const item of moving) current = item.apply(current, context);
    passes += 1;
    if (current.rect.equals(before)) {
      settled = true;
      break;
    }
  }

  if (!settled) {
    current = current.warned(
      `the rules did not settle in ${passes} passes, so the crop is the last one they produced`,
    );
  }

  for (const item of checks) current = item.apply(current, context);
  return { plan: current, passes, settled };
}

/**
 * @param {readonly Rule[]} rules
 * @param {object} args
 * @param {import('../geom/rect.js').Rect} args.capture
 * @param {import('../geom/rect.js').Rect} args.anchor
 * @param {import('../measure/resolution.js').Sampling} args.sampling
 * @param {import('../geom/angle.js').Angle} [args.deskew]
 * @param {object} [options]
 * @param {number} [options.maxPasses]
 * @returns {CropPlan}
 */
export function planWith(rules, { capture, anchor, sampling, deskew }, options) {
  if (anchor.isEmpty) {
    throw new PlanError('there is nothing to crop, the anchor has no area', {
      anchor: anchor.toJSON(),
    });
  }
  const start = CropPlan.start({ capture, rect: anchor, sampling, deskew });
  const { plan } = runRules(rules, start, { anchor, anchorName: 'anchor' }, options);
  return plan;
}

/**
 * The same run, with the bookkeeping the explain command wants.
 *
 * @param {readonly Rule[]} rules
 * @param {object} args
 * @param {import('../geom/rect.js').Rect} args.capture
 * @param {import('../geom/rect.js').Rect} args.anchor
 * @param {import('../measure/resolution.js').Sampling} args.sampling
 * @param {import('../geom/angle.js').Angle} [args.deskew]
 * @param {object} [options]
 * @param {number} [options.maxPasses]
 * @returns {{plan: CropPlan, passes: number, settled: boolean, rules: string[]}}
 */
export function planInDetail(rules, args, options) {
  if (args.anchor.isEmpty) {
    throw new PlanError('there is nothing to crop, the anchor has no area', {
      anchor: args.anchor.toJSON(),
    });
  }
  const start = CropPlan.start({
    capture: args.capture,
    rect: args.anchor,
    sampling: args.sampling,
    deskew: args.deskew,
  });
  const out = runRules(rules, start, { anchor: args.anchor, anchorName: 'anchor' }, options);
  return { ...out, rules: ordered(rules).map((item) => item.name) };
}

/**
 * Drop the rules a caller did not want, by name. An operator overriding one
 * rule for one item should not have to rebuild the set.
 *
 * @param {readonly Rule[]} rules
 * @param {readonly string[]} names
 * @returns {Rule[]}
 */
export function without(rules, names) {
  const drop = new Set(names);
  return rules.filter((item) => !drop.has(item.name));
}

/**
 * Replace a rule of the same name, or add it when there is none.
 *
 * @param {readonly Rule[]} rules
 * @param {Rule} replacement
 * @returns {Rule[]}
 */
export function withRule(rules, replacement) {
  const at = rules.findIndex((item) => item.name === replacement.name);
  if (at < 0) return [...rules, replacement];
  const out = [...rules];
  out[at] = replacement;
  return out;
}
