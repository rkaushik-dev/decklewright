import { PlanError } from '../../errors.js';
import { toInches } from '../../measure/units.js';
import { assertPositive } from '../../util/assert.js';
import { rule } from './rule.js';

/**
 * The sampling floor.
 *
 * This is a check and it cannot be anything else. Cropping does not change how
 * finely the paper was sampled: a crop half the size of another crop off the
 * same capture is the same ppi. If the capture was taken too low the answer is
 * to capture it again, and the only useful thing a planner can do is say so
 * before three derivatives get written from it.
 *
 * There are two shapes because there are two kinds of collection. Most warn
 * and carry on, so a batch finishes and somebody reads the log. The funded
 * ones refuse, because a file under the floor cannot be delivered at all and
 * writing it wastes the disk.
 */

/**
 * @param {number} minimumPpi
 * @returns {import('./rule.js').Rule}
 */
export function resolutionRule(minimumPpi) {
  assertPositive(minimumPpi, 'minimumPpi');
  return rule({
    name: 'resolution',
    phase: 'check',
    apply(plan) {
      const achieved = Math.min(plan.sampling.x, plan.sampling.y);
      if (achieved >= minimumPpi) return plan;
      return plan.warned(`the capture is ${round(achieved)} ppi against a floor of ${minimumPpi}`);
    },
  });
}

/**
 * @param {number} minimumPpi
 * @returns {import('./rule.js').Rule}
 */
export function strictResolutionRule(minimumPpi) {
  assertPositive(minimumPpi, 'minimumPpi');
  return rule({
    name: 'resolution',
    phase: 'check',
    apply(plan) {
      const achieved = Math.min(plan.sampling.x, plan.sampling.y);
      if (achieved >= minimumPpi) return plan;
      throw new PlanError(
        `the capture is ${round(achieved)} ppi and this collection will not take under ${minimumPpi}`,
        { achieved, minimumPpi },
      );
    },
  });
}

/**
 * A floor on the finished pixel count rather than on the sampling. A viewer
 * that wants at least so many pixels across does not care how they were got.
 *
 * @param {number} minimumPixels on the long side
 * @returns {import('./rule.js').Rule}
 */
export function minimumSizeRule(minimumPixels) {
  assertPositive(minimumPixels, 'minimumPixels');
  return rule({
    name: 'minimumSize',
    phase: 'check',
    apply(plan) {
      const longest = Math.max(plan.rect.width, plan.rect.height);
      if (longest >= minimumPixels) return plan;
      return plan.warned(
        `the crop is ${round(longest)} px on its long side against a floor of ${minimumPixels}`,
      );
    },
  });
}

/**
 * @param {number} value
 * @returns {number}
 */
function round(value) {
  return Math.round(value * 10) / 10;
}

/**
 * @param {number} minimumPpi
 * @param {object} [options]
 * @param {boolean} [options.strict]
 * @returns {import('./rule.js').Rule}
 */
export function resolutionFor(minimumPpi, { strict = false } = {}) {
  return strict ? strictResolutionRule(minimumPpi) : resolutionRule(minimumPpi);
}

/**
 * What a crop of a known physical width comes out at. The quality checks read
 * the same number later and the two have to agree, so they both come through
 * here.
 *
 * @param {import('../plan.js').CropPlan} plan
 * @param {number} physicalWidth
 * @param {import('../../measure/units.js').Unit} unit
 * @returns {number} ppi
 */
export function achievedPpi(plan, physicalWidth, unit) {
  assertPositive(physicalWidth, 'physicalWidth');
  return plan.rect.width / toInches(physicalWidth, unit);
}
