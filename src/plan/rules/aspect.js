import { PlanError } from '../../errors.js';
import { forceAspect } from '../../geom/fit.js';
import { assertOneOf, assertPositive } from '../../util/assert.js';
import { rule } from './rule.js';

/**
 * Forcing a crop to a shape.
 *
 * Two collections deliver into a viewer that wants every plate the same shape,
 * and one delivers into a print template. The rest do not care and set nothing.
 *
 * Growing is the safe direction and it is the default: a crop that grows takes
 * in more paper, and a crop that shrinks throws image away. A profile that
 * shrinks is saying it would rather lose an edge than have a margin, which is
 * a real preference and a rare one.
 */

/**
 * @param {number} aspect width over height
 * @param {object} [options]
 * @param {'grow'|'shrink'} [options.direction]
 * @returns {import('./rule.js').Rule}
 */
export function aspectRule(aspect, { direction = 'grow' } = {}) {
  assertPositive(aspect, 'aspect');
  assertOneOf(direction, ['grow', 'shrink'], 'direction');
  return rule({
    name: 'aspect',
    phase: 'shape',
    apply(plan) {
      if (plan.rect.isEmpty) return plan;
      return plan.moved(forceAspect(plan.rect, aspect, { direction }), {
        rule: 'aspect',
        note: `forced to ${aspect} by ${direction}ing`,
      });
    },
  });
}

/**
 * The aspect of the anchor itself, rounded to a ratio a viewer would use. Some
 * collections want every plate the same shape without anybody having to work
 * out what shape that is.
 *
 * @param {readonly number[]} allowed the ratios the collection accepts
 * @param {object} [options]
 * @param {'grow'|'shrink'} [options.direction]
 * @returns {import('./rule.js').Rule}
 */
export function nearestAspectRule(allowed, { direction = 'grow' } = {}) {
  if (allowed.length === 0) {
    throw new PlanError('an aspect rule needs at least one ratio to choose from', {});
  }
  for (const value of allowed) assertPositive(value, 'aspect');
  assertOneOf(direction, ['grow', 'shrink'], 'direction');

  return rule({
    name: 'aspect',
    phase: 'shape',
    apply(plan) {
      if (plan.rect.isEmpty || plan.rect.height === 0) return plan;
      const current = plan.rect.width / plan.rect.height;
      const chosen = allowed.reduce((best, value) =>
        Math.abs(Math.log(value / current)) < Math.abs(Math.log(best / current)) ? value : best,
      );
      return plan.moved(forceAspect(plan.rect, chosen, { direction }), {
        rule: 'aspect',
        note: `forced to the nearest allowed ratio, ${chosen}, by ${direction}ing`,
      });
    },
  });
}

/**
 * An aspect that only applies when the crop is already close, so a plate that
 * is genuinely a different shape is left alone rather than being stretched
 * into the collection's usual one.
 *
 * @param {number} aspect
 * @param {object} [options]
 * @param {number} [options.within] how far off the ratio may be, as a share
 * @param {'grow'|'shrink'} [options.direction]
 * @returns {import('./rule.js').Rule}
 */
export function nudgeAspectRule(aspect, { within = 0.05, direction = 'grow' } = {}) {
  assertPositive(aspect, 'aspect');
  const inner = aspectRule(aspect, { direction });
  return rule({
    name: 'aspect',
    phase: 'shape',
    apply(plan, context) {
      if (plan.rect.isEmpty || plan.rect.height === 0) return plan;
      const current = plan.rect.width / plan.rect.height;
      const off = Math.abs(current - aspect) / aspect;
      if (off > within) {
        return plan.warned(
          `the crop is ${(off * 100).toFixed(1)} percent off the ${aspect} ratio and was left alone`,
        );
      }
      return inner.apply(plan, context);
    },
  });
}

/**
 * @param {number|undefined} aspect
 * @param {object} [options]
 * @param {'grow'|'shrink'} [options.direction]
 * @returns {import('./rule.js').Rule|null}
 */
export function aspectFor(aspect, options) {
  return aspect === undefined ? null : aspectRule(aspect, options);
}
