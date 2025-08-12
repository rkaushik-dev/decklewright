import { PlanError } from '../../errors.js';
import { assertOneOf } from '../../util/assert.js';
import { rule } from './rule.js';

/**
 * What to do when the crop reaches off the capture.
 *
 * It happens for two reasons and they want different answers. A sheet placed
 * too near the edge of the bed produces a crop that genuinely has no image in
 * one corner, and the master has to be padded so it comes out the size the
 * plan said. A bleed that is a hair too wide produces a crop that is one pixel
 * over, and trimming it is fine.
 *
 * The profile picks. Nothing here guesses, because the two look identical to
 * the code and completely different to a curator.
 */

export const BOUNDS_MODES = Object.freeze(['warn', 'clamp', 'nudge', 'refuse']);

/**
 * Say something and change nothing, which is what a collection that pads its
 * masters wants: the crop is the plan and the render fills the gap.
 *
 * @returns {import('./rule.js').Rule}
 */
export function warnOnOverhangRule() {
  return rule({
    name: 'bounds',
    phase: 'check',
    apply(plan) {
      if (!plan.overhangs) return plan;
      return plan.warned(`${Math.round(plan.overhangArea)} px of the crop falls off the capture`);
    },
  });
}

/**
 * Cut the crop back to what the capture holds. The output is smaller than the
 * plan asked for, which is why it says so.
 *
 * @returns {import('./rule.js').Rule}
 */
export function clampToCaptureRule() {
  return rule({
    name: 'bounds',
    phase: 'grid',
    apply(plan) {
      if (!plan.overhangs) return plan;
      const held = plan.rect.intersect(plan.capture);
      if (held.isEmpty) {
        throw new PlanError('the crop does not touch the capture at all', {
          rect: plan.rect.toJSON(),
          capture: plan.capture.toJSON(),
        });
      }
      return plan
        .moved(held, { rule: 'bounds', note: 'trimmed to the capture' })
        .warned('the crop was trimmed at the edge of the capture');
    },
  });
}

/**
 * Slide the crop back on, keeping its size. This is the one the tiled viewer
 * collections use, because a plate that is not the size the plan said breaks
 * the tiling and a plate that is a few pixels off centre does not.
 *
 * @returns {import('./rule.js').Rule}
 */
export function nudgeIntoCaptureRule() {
  return rule({
    name: 'bounds',
    phase: 'grid',
    apply(plan) {
      if (!plan.overhangs) return plan;
      const moved = plan.rect.nudgedInto(plan.capture);
      const stillOver = !plan.capture.containsRect(moved);
      const out = plan.moved(moved, { rule: 'bounds', note: 'slid back onto the capture' });
      return stillOver
        ? out.warned('the crop is larger than the capture and was centred on it')
        : out;
    },
  });
}

/**
 * Stop. Some collections would rather hold an item than deliver a padded
 * master, and for those a crop that runs off the bed means the capture has to
 * be taken again.
 *
 * @returns {import('./rule.js').Rule}
 */
export function refuseOverhangRule() {
  return rule({
    name: 'bounds',
    phase: 'check',
    apply(plan) {
      if (!plan.overhangs) return plan;
      throw new PlanError('the crop reaches off the capture and this collection will not pad', {
        overhang: Math.round(plan.overhangArea),
        rect: plan.rect.toJSON(),
      });
    },
  });
}

/**
 * @param {string} [mode]
 * @returns {import('./rule.js').Rule}
 */
export function boundsFor(mode = 'warn') {
  assertOneOf(mode, BOUNDS_MODES, 'mode');
  switch (mode) {
    case 'clamp':
      return clampToCaptureRule();
    case 'nudge':
      return nudgeIntoCaptureRule();
    case 'refuse':
      return refuseOverhangRule();
    default:
      return warnOnOverhangRule();
  }
}

/**
 * A crop with no area is a fault whatever the collection thinks, because there
 * is nothing to write. It is separate from the bounds rules so it runs even
 * when a profile turned those off.
 *
 * @returns {import('./rule.js').Rule}
 */
export function notEmptyRule() {
  return rule({
    name: 'notEmpty',
    phase: 'check',
    apply(plan) {
      if (!plan.rect.isEmpty) return plan;
      throw new PlanError('the rules between them left no crop at all', { trail: plan.trail });
    },
  });
}
