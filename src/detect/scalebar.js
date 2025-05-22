import { DetectionError } from '../errors.js';
import { Angle } from '../geom/angle.js';
import { Sampling } from '../measure/resolution.js';
import { toInches, assertUnit } from '../measure/units.js';
import { assertPositive } from '../util/assert.js';
import { fillRatio, labelComponents } from './components.js';

/** @typedef {import('./mask.js').Mask} Mask */
/** @typedef {import('../geom/rect.js').Rect} Rect */

/**
 * The scale bar that lies on the bed beside every capture.
 *
 * It is a machined steel rule of a known length, and it is the only object in
 * the frame whose true size the lab is certain of. Finding it gives a second
 * opinion on sampling frequency that does not come from the scanner driver,
 * which is worth having because the driver has been wrong twice.
 *
 * It is picked out by shape: long, thin, solid, and much thinner than anything
 * else that is not bed.
 */

/**
 * @typedef {object} ScaleBarFinding
 * @property {Rect} rect
 * @property {number} lengthPixels along the bar
 * @property {number} aspect long side over short side
 * @property {Angle} angle
 */

/**
 * @param {Mask} mask
 * @param {object} [options]
 * @param {number} [options.minimumAspect] how much longer than wide
 * @param {number} [options.minimumLength] in pixels
 * @param {number} [options.minimumFill] how solidly it fills its own box
 * @param {readonly number[]} [options.ignore] component ids to skip
 * @returns {ScaleBarFinding|null}
 */
export function findScaleBar(mask, options = {}) {
  const { minimumAspect = 6, minimumLength = 40, minimumFill = 0.7, ignore = [] } = options;
  const skip = new Set(ignore);
  const { components } = labelComponents(mask);

  /** @type {ScaleBarFinding|null} */
  let best = null;
  for (const component of components) {
    if (skip.has(component.id)) continue;
    const long = Math.max(component.bounds.width, component.bounds.height);
    const short = Math.min(component.bounds.width, component.bounds.height);
    if (short === 0) continue;
    const aspect = long / short;
    if (aspect < minimumAspect) continue;
    if (long < minimumLength) continue;
    if (fillRatio(component) < minimumFill) continue;

    const horizontal = component.bounds.width >= component.bounds.height;
    const finding = {
      rect: component.bounds,
      lengthPixels: long,
      aspect,
      angle: Angle.degrees(horizontal ? 0 : 90),
    };
    if (best === null || finding.lengthPixels > best.lengthPixels) best = finding;
  }
  return best;
}

/**
 * @param {ScaleBarFinding} bar
 * @param {number} length the true length of the rule
 * @param {import('../measure/units.js').Unit} unit
 * @returns {Sampling}
 */
export function samplingFromBar(bar, length, unit) {
  assertPositive(length, 'length');
  assertUnit(unit);
  if (bar.lengthPixels <= 0) {
    throw new DetectionError('the scale bar has no length to measure', { bar: bar.rect.toJSON() });
  }
  return Sampling.of(bar.lengthPixels / toInches(length, unit));
}

/**
 * Compare a measured frequency against what the scanner claimed. The rule is
 * the more trustworthy of the two, so a disagreement is reported as a fault in
 * the claim rather than in the measurement.
 *
 * @param {Sampling} measured
 * @param {Sampling} claimed
 * @param {object} [options]
 * @param {number} [options.tolerance] as a share of the claim
 * @returns {{agrees: boolean, error: number, measured: number, claimed: number}}
 */
export function checkAgainstClaim(measured, claimed, { tolerance = 0.02 } = {}) {
  const error = (measured.x - claimed.x) / claimed.x;
  return {
    agrees: Math.abs(error) <= tolerance,
    error,
    measured: measured.x,
    claimed: claimed.x,
  };
}

/**
 * The bar is graduated, so its own ticks give a second reading. Counting the
 * runs of ink along it is a cheap check that what was found really is a rule
 * and not the spine of a folder.
 *
 * @param {Mask} mask
 * @param {ScaleBarFinding} bar
 * @param {Mask} inkMask pixels darker than the bar face
 * @returns {number}
 */
export function countTicks(mask, bar, inkMask) {
  const horizontal = bar.rect.width >= bar.rect.height;
  const along = horizontal ? bar.rect.width : bar.rect.height;
  const middle = horizontal
    ? Math.floor(bar.rect.top + bar.rect.height / 2)
    : Math.floor(bar.rect.left + bar.rect.width / 2);

  let ticks = 0;
  let inRun = false;
  for (let i = 0; i < along; i += 1) {
    const x = horizontal ? bar.rect.left + i : middle;
    const y = horizontal ? middle : bar.rect.top + i;
    const inked = inkMask.get(x, y) === 1 && mask.get(x, y) === 1;
    if (inked && !inRun) ticks += 1;
    inRun = inked;
  }
  return ticks;
}

/**
 * @param {ScaleBarFinding} bar
 * @param {number} length
 * @param {import('../measure/units.js').Unit} unit
 * @returns {string}
 */
export function describeBar(bar, length, unit) {
  const sampling = samplingFromBar(bar, length, unit);
  return `${Math.round(bar.lengthPixels)} px for ${length} ${unit}, so ${sampling}`;
}
