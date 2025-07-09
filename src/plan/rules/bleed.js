import { Insets } from '../../geom/insets.js';
import { rule } from './rule.js';

/**
 * Bleed: paper kept beyond whatever the crop is anchored to.
 *
 * It runs in the anchor phase because it defines where the crop starts, not
 * what shape it is. A profile with no bleed leaves the anchor exactly where
 * the detector put it, which is the common case and costs nothing.
 *
 * Bleed is idempotent by construction: it is applied to the anchor rect held
 * in the context, never to the crop as it stands, so running it twice gives
 * the same answer as running it once.
 */

/**
 * @param {Insets} bleed in pixels
 * @returns {import('./rule.js').Rule}
 */
export function bleedRule(bleed) {
  return rule({
    name: 'bleed',
    phase: 'anchor',
    apply(plan, context) {
      if (bleed.isZero) return plan;
      const from = context.anchor ?? plan.rect;
      return plan.moved(bleed.expandFrom(from), {
        rule: 'bleed',
        note: `kept ${bleed} beyond the ${context.anchorName ?? 'anchor'}`,
      });
    },
  });
}

/**
 * A bleed that is trimmed so it cannot reach off the capture. Used by the
 * collections that would rather lose a millimetre of paper than have a black
 * strip down one edge of a master.
 *
 * @param {Insets} bleed
 * @returns {import('./rule.js').Rule}
 */
export function trimmedBleedRule(bleed) {
  return rule({
    name: 'bleed',
    phase: 'anchor',
    apply(plan, context) {
      if (bleed.isZero) return plan;
      const from = context.anchor ?? plan.rect;
      const wanted = bleed.expandFrom(from);
      const trimmed = wanted.intersect(plan.capture);
      const lost = wanted.area - trimmed.area;
      const moved = plan.moved(trimmed, {
        rule: 'bleed',
        note:
          lost > 0
            ? `kept ${bleed} beyond the anchor, trimmed to the capture`
            : `kept ${bleed} beyond the anchor`,
      });
      return lost > 0 ? moved.warned('the bleed was trimmed at the edge of the capture') : moved;
    },
  });
}

/**
 * The bleed a profile asked for, less whatever the anchor already includes.
 * A neatline anchor sits inside the paper, so a profile that wants three
 * millimetres of paper beyond the neatline is asking for something the sheet
 * may not have.
 *
 * @param {Insets} bleed
 * @param {import('../../geom/rect.js').Rect} sheet the paper edge
 * @returns {import('./rule.js').Rule}
 */
export function bleedWithinSheetRule(bleed, sheet) {
  return rule({
    name: 'bleed',
    phase: 'anchor',
    apply(plan, context) {
      if (bleed.isZero) return plan;
      const from = context.anchor ?? plan.rect;
      const wanted = bleed.expandFrom(from);
      const held = wanted.intersect(sheet);
      const short = wanted.area - held.area;
      const moved = plan.moved(held, {
        rule: 'bleed',
        note: `kept ${bleed} beyond the anchor, held inside the sheet`,
      });
      return short > 0
        ? moved.warned('the sheet does not have enough paper for the bleed asked for')
        : moved;
    },
  });
}

/**
 * @param {import('../../geom/insets.js').Insets} bleed
 * @param {object} [options]
 * @param {'plain'|'trimmed'|'withinSheet'} [options.mode]
 * @param {import('../../geom/rect.js').Rect} [options.sheet]
 * @returns {import('./rule.js').Rule}
 */
export function bleedFor(bleed, { mode = 'plain', sheet } = {}) {
  if (mode === 'trimmed') return trimmedBleedRule(bleed);
  if (mode === 'withinSheet' && sheet) return bleedWithinSheetRule(bleed, sheet);
  return bleedRule(bleed);
}

/** @returns {import('./rule.js').Rule} */
export function noBleedRule() {
  return bleedRule(Insets.zero());
}
