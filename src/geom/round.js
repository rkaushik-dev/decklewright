import { assertOneOf, assertPositiveInteger } from '../util/assert.js';
import { ceilToMultiple, floorToMultiple, roundHalfEven, roundToMultiple } from '../util/num.js';
import { Rect } from './rect.js';
import { Size } from './size.js';

/**
 * Turning a planned crop into whole pixels.
 *
 * The rule engine works in real numbers because bleed of three millimetres at
 * four hundred and eighteen ppi is not a whole number of pixels. Exactly one
 * step turns that into a crop the raster layer can take, and which way it
 * rounds is a collection decision, not an implementation detail.
 */

/** @typedef {'nearest'|'outward'|'inward'|'floor'} RoundPolicy */

export const ROUND_POLICIES = Object.freeze(['nearest', 'outward', 'inward', 'floor']);

/**
 * @param {RoundPolicy} policy
 * @returns {{low: (n: number) => number, high: (n: number) => number}}
 */
function roundersFor(policy) {
  switch (policy) {
    case 'outward':
      return { low: Math.floor, high: Math.ceil };
    case 'inward':
      return { low: Math.ceil, high: Math.floor };
    case 'floor':
      return { low: Math.floor, high: Math.floor };
    default:
      return { low: roundHalfEven, high: roundHalfEven };
  }
}

/**
 * @param {Rect} rect
 * @param {object} [options]
 * @param {RoundPolicy} [options.policy]
 * @returns {Rect}
 */
export function roundRect(rect, { policy = 'nearest' } = {}) {
  assertOneOf(policy, ROUND_POLICIES, 'policy');
  const { low, high } = roundersFor(policy);
  const left = low(rect.left);
  const top = low(rect.top);
  const right = high(rect.right);
  const bottom = high(rect.bottom);
  return Rect.fromEdges(left, top, Math.max(left, right), Math.max(top, bottom));
}

/**
 * Force the extents onto a multiple, which the tiled deliverables need because
 * the viewer wants every level to divide cleanly.
 *
 * @param {Size} size
 * @param {number} multiple
 * @param {object} [options]
 * @param {'nearest'|'up'|'down'} [options.direction]
 * @returns {Size}
 */
export function snapSize(size, multiple, { direction = 'nearest' } = {}) {
  assertPositiveInteger(multiple, 'multiple');
  assertOneOf(direction, ['nearest', 'up', 'down'], 'direction');
  const snap =
    direction === 'up' ? ceilToMultiple : direction === 'down' ? floorToMultiple : roundToMultiple;
  return new Size(
    Math.max(0, snap(size.width, multiple)),
    Math.max(0, snap(size.height, multiple)),
  );
}

/**
 * Snap the extents of a crop while keeping its centre where the planner put
 * it. The origin is then rounded to whole pixels, because a half pixel origin
 * would need a resample the profile did not ask for.
 *
 * @param {Rect} rect
 * @param {number} multiple
 * @param {object} [options]
 * @param {'nearest'|'up'|'down'} [options.direction]
 * @returns {Rect}
 */
export function snapRect(rect, multiple, options = {}) {
  const size = snapSize(rect.size, multiple, options);
  const centred = Rect.fromCentre(rect.centre, size);
  return new Rect(roundHalfEven(centred.x), roundHalfEven(centred.y), size.width, size.height);
}

/**
 * Whole pixels with even extents, which is what the jpeg service copies need
 * so the chroma subsampling does not have to pad the last column.
 *
 * @param {Rect} rect
 * @returns {Rect}
 */
export function evenRect(rect) {
  return snapRect(rect, 2, { direction: 'up' });
}

/**
 * @param {Rect} rect
 * @returns {boolean}
 */
export function isWholePixel(rect) {
  return (
    Number.isInteger(rect.x) &&
    Number.isInteger(rect.y) &&
    Number.isInteger(rect.width) &&
    Number.isInteger(rect.height)
  );
}
