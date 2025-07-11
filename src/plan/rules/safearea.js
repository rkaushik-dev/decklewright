import { Rect } from '../../geom/rect.js';
import { rule } from './rule.js';

/**
 * The safe area: paper the crop must not include.
 *
 * A collection with a bound edge does not want the gutter in the plate, and a
 * collection whose sheets were stamped in one corner does not want the stamp.
 * The safe area is written per edge for exactly that reason.
 *
 * It runs in the shape phase, after the anchor and the bleed have settled
 * where the crop starts, and it is idempotent because it works from the anchor
 * held in the context rather than from wherever the crop has got to.
 */

/**
 * @param {import('../../geom/insets.js').Insets} safeArea in pixels
 * @returns {import('./rule.js').Rule}
 */
export function safeAreaRule(safeArea) {
  return rule({
    name: 'safeArea',
    phase: 'shape',
    apply(plan, context) {
      if (safeArea.isZero) return plan;
      const from = context.anchor ?? plan.rect;
      const wanted = safeArea.applyTo(from);
      if (wanted.isEmpty) {
        return plan.warned('the safe area leaves no crop at all').moved(wanted, {
          rule: 'safeArea',
          note: `pulled in by ${safeArea}, which consumed the anchor`,
        });
      }
      const held = plan.rect.intersect(wanted);
      return plan.moved(held.isEmpty ? wanted : held, {
        rule: 'safeArea',
        note: `pulled in by ${safeArea}`,
      });
    },
  });
}

/**
 * A safe area that only pulls in on the edges it was given, leaving the rest
 * of the crop wherever the bleed put it. This is the one the bound volumes
 * use: a gutter on the left and nothing anywhere else.
 *
 * @param {import('../../geom/insets.js').Insets} safeArea
 * @returns {import('./rule.js').Rule}
 */
export function edgeSafeAreaRule(safeArea) {
  return rule({
    name: 'safeArea',
    phase: 'shape',
    apply(plan, context) {
      if (safeArea.isZero) return plan;
      const from = context.anchor ?? plan.rect;
      const limit = safeArea.applyTo(from);
      const held = Object.freeze({
        left: safeArea.left > 0 ? Math.max(plan.rect.left, limit.left) : plan.rect.left,
        top: safeArea.top > 0 ? Math.max(plan.rect.top, limit.top) : plan.rect.top,
        right: safeArea.right > 0 ? Math.min(plan.rect.right, limit.right) : plan.rect.right,
        bottom: safeArea.bottom > 0 ? Math.min(plan.rect.bottom, limit.bottom) : plan.rect.bottom,
      });
      if (held.right <= held.left || held.bottom <= held.top) {
        return plan.warned('the safe area leaves no crop at all');
      }
      return plan.moved(Rect.fromEdges(held.left, held.top, held.right, held.bottom), {
        rule: 'safeArea',
        note: `held off the edges by ${safeArea}`,
      });
    },
  });
}

/**
 * @param {import('../../geom/insets.js').Insets} safeArea
 * @param {object} [options]
 * @param {'whole'|'edges'} [options.mode]
 * @returns {import('./rule.js').Rule}
 */
export function safeAreaFor(safeArea, { mode = 'whole' } = {}) {
  return mode === 'edges' ? edgeSafeAreaRule(safeArea) : safeAreaRule(safeArea);
}
