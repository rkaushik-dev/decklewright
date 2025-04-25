import { DetectionError } from '../errors.js';
import { Rect } from '../geom/rect.js';
import { assertOneOf, assertPositiveInteger } from '../util/assert.js';

/** @typedef {import('./mask.js').Mask} Mask */

/**
 * Projection profiles.
 *
 * Sum the mask down each column and across each row and you get two curves
 * that say where the object starts and stops on each axis. For a sheet that is
 * square to the bed this is the whole detection. For a skewed one it is still
 * the fastest way to a first guess that the quad fitter can refine.
 */

/** @typedef {'x'|'y'} Axis */

/**
 * @param {Mask} mask
 * @param {Axis} axis x sums down columns, y sums across rows
 * @returns {Float64Array}
 */
export function project(mask, axis) {
  assertOneOf(axis, ['x', 'y'], 'axis');
  if (axis === 'y') {
    const out = new Float64Array(mask.height);
    for (let y = 0; y < mask.height; y += 1) {
      const row = mask.row(y);
      let total = 0;
      for (let x = 0; x < mask.width; x += 1) total += row[x];
      out[y] = total;
    }
    return out;
  }
  const out = new Float64Array(mask.width);
  for (let y = 0; y < mask.height; y += 1) {
    const row = mask.row(y);
    for (let x = 0; x < mask.width; x += 1) out[x] += row[x];
  }
  return out;
}

/**
 * A moving average. The profile of a real sheet is ragged at the ends because
 * the corner is rounded and the last row of paper only covers part of the
 * pixel, and the raggedness moves the edge by a pixel or two if it is not
 * taken out first.
 *
 * @param {Float64Array} profile
 * @param {number} window odd, and at least one
 * @returns {Float64Array}
 */
export function smooth(profile, window) {
  assertPositiveInteger(window, 'window');
  if (window === 1) return Float64Array.from(profile);
  const half = Math.floor(window / 2);
  const out = new Float64Array(profile.length);
  for (let i = 0; i < profile.length; i += 1) {
    let total = 0;
    let count = 0;
    for (let k = -half; k <= half; k += 1) {
      const at = i + k;
      if (at < 0 || at >= profile.length) continue;
      total += profile[at];
      count += 1;
    }
    out[i] = total / count;
  }
  return out;
}

/**
 * @param {Float64Array} profile
 * @returns {Float64Array} the difference between neighbours, same length
 */
export function gradient(profile) {
  const out = new Float64Array(profile.length);
  for (let i = 1; i < profile.length; i += 1) out[i] = profile[i] - profile[i - 1];
  return out;
}

/**
 * Where the profile first and last rises above a share of its own peak.
 *
 * @param {Float64Array} profile
 * @param {object} [options]
 * @param {number} [options.fraction] of the peak
 * @returns {{start: number, end: number, peak: number, threshold: number}}
 */
export function span(profile, { fraction = 0.5 } = {}) {
  let peak = 0;
  for (const value of profile) if (value > peak) peak = value;
  if (peak === 0) {
    throw new DetectionError('the profile is flat at zero and has no span', {
      length: profile.length,
    });
  }
  const threshold = peak * fraction;
  let start = -1;
  let end = -1;
  for (let i = 0; i < profile.length; i += 1) {
    if (profile[i] < threshold) continue;
    if (start < 0) start = i;
    end = i;
  }
  return { start, end: end + 1, peak, threshold };
}

/**
 * The first cut at where the object is, taken from both profiles. It is an
 * axis aligned box, so it is only the truth when the sheet is square to the
 * bed, but it bounds the search for everything that follows.
 *
 * @param {Mask} mask
 * @param {object} [options]
 * @param {number} [options.fraction]
 * @param {number} [options.smoothing]
 * @returns {Rect}
 */
export function projectedExtent(mask, { fraction = 0.5, smoothing = 1 } = {}) {
  const horizontal = span(smooth(project(mask, 'x'), smoothing), { fraction });
  const vertical = span(smooth(project(mask, 'y'), smoothing), { fraction });
  return Rect.fromEdges(horizontal.start, vertical.start, horizontal.end, vertical.end);
}

/**
 * How square to the bed the object is, judged by how sharply the profile turns
 * on. A sheet at zero degrees turns on in one column. A sheet at three degrees
 * takes as many columns as the sheet is tall times the tangent, so the width
 * of the ramp is a direct read on the skew.
 *
 * @param {Float64Array} profile
 * @returns {{leading: number, trailing: number}} ramp widths in samples
 */
export function rampWidths(profile) {
  const { peak } = span(profile, { fraction: 0.5 });
  const low = peak * 0.1;
  const high = peak * 0.9;

  let leadingStart = -1;
  let leadingEnd = -1;
  for (let i = 0; i < profile.length; i += 1) {
    if (leadingStart < 0 && profile[i] >= low) leadingStart = i;
    if (profile[i] >= high) {
      leadingEnd = i;
      break;
    }
  }

  let trailingStart = -1;
  let trailingEnd = -1;
  for (let i = profile.length - 1; i >= 0; i -= 1) {
    if (trailingStart < 0 && profile[i] >= low) trailingStart = i;
    if (profile[i] >= high) {
      trailingEnd = i;
      break;
    }
  }

  return {
    leading: leadingEnd < 0 || leadingStart < 0 ? 0 : leadingEnd - leadingStart,
    trailing: trailingEnd < 0 || trailingStart < 0 ? 0 : trailingStart - trailingEnd,
  };
}
