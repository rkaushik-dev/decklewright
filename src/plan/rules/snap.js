import { Rect } from '../../geom/rect.js';
import { isWholePixel, roundRect, snapRect } from '../../geom/round.js';
import { assertOneOf, assertPositiveInteger } from '../../util/assert.js';
import { rule } from './rule.js';

/**
 * Whole pixels, and multiples of them.
 *
 * These are the last rules to run because everything before them works in real
 * numbers. Bleed of three millimetres at four hundred and eighteen ppi is not
 * a whole number of pixels and pretending otherwise earlier just moves the
 * rounding somewhere less visible.
 *
 * Snapping and rounding are separate rules on purpose. A collection that
 * delivers into a tiled viewer needs multiples of the tile and does not care
 * where the crop starts. A collection that only needs whole pixels should not
 * pay for a snap it did not ask for.
 */

/**
 * @param {number} multiple
 * @param {object} [options]
 * @param {'nearest'|'up'|'down'} [options.direction]
 * @returns {import('./rule.js').Rule}
 */
export function snapRule(multiple, { direction = 'up' } = {}) {
  assertPositiveInteger(multiple, 'multiple');
  assertOneOf(direction, ['nearest', 'up', 'down'], 'direction');
  return rule({
    name: 'snap',
    phase: 'grid',
    apply(plan) {
      if (plan.rect.isEmpty) return plan;
      if (multiple === 1) return plan;
      return plan.moved(snapRect(plan.rect, multiple, { direction }), {
        rule: 'snap',
        note: `snapped to ${multiple} px ${direction}`,
      });
    },
  });
}

/**
 * @param {import('../../geom/round.js').RoundPolicy} policy
 * @returns {import('./rule.js').Rule}
 */
export function roundRule(policy) {
  assertOneOf(policy, ['nearest', 'outward', 'inward', 'floor'], 'policy');
  return rule({
    name: 'round',
    phase: 'grid',
    apply(plan) {
      if (isWholePixel(plan.rect)) return plan;
      return plan.moved(roundRect(plan.rect, { policy }), {
        rule: 'round',
        note: `rounded ${policy}`,
      });
    },
  });
}

/**
 * Snap the origin as well as the extents, which the tiled viewer needs because
 * it addresses tiles by absolute position rather than by offset within a
 * plate.
 *
 * @param {number} multiple
 * @returns {import('./rule.js').Rule}
 */
export function gridAlignRule(multiple) {
  assertPositiveInteger(multiple, 'multiple');
  return rule({
    name: 'gridAlign',
    phase: 'grid',
    apply(plan) {
      if (plan.rect.isEmpty || multiple === 1) return plan;
      const left = Math.floor(plan.rect.left / multiple) * multiple;
      const top = Math.floor(plan.rect.top / multiple) * multiple;
      const right = Math.ceil(plan.rect.right / multiple) * multiple;
      const bottom = Math.ceil(plan.rect.bottom / multiple) * multiple;
      return plan.moved(Rect.fromEdges(left, top, right, bottom), {
        rule: 'gridAlign',
        note: `aligned to a ${multiple} px grid`,
      });
    },
  });
}

/**
 * Even extents, which the jpeg service copies need so the chroma subsampling
 * does not have to pad the last column. It is a snap of two, named for what it
 * is for.
 *
 * @returns {import('./rule.js').Rule}
 */
export function evenExtentsRule() {
  return rule({
    name: 'snap',
    phase: 'grid',
    apply(plan) {
      if (plan.rect.isEmpty) return plan;
      if (plan.rect.width % 2 === 0 && plan.rect.height % 2 === 0) return plan;
      return plan.moved(snapRect(plan.rect, 2, { direction: 'up' }), {
        rule: 'snap',
        note: 'grown to even extents',
      });
    },
  });
}

/**
 * @param {number} multiple
 * @param {object} [options]
 * @param {'nearest'|'up'|'down'} [options.direction]
 * @param {boolean} [options.alignOrigin]
 * @returns {import('./rule.js').Rule}
 */
export function snapFor(multiple, { direction = 'up', alignOrigin = false } = {}) {
  return alignOrigin ? gridAlignRule(multiple) : snapRule(multiple, { direction });
}
