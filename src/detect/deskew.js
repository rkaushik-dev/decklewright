import { DetectionError } from '../errors.js';
import { Angle } from '../geom/angle.js';
import { assertPositive } from '../util/assert.js';
import { boundary } from './morphology.js';

/** @typedef {import('./mask.js').Mask} Mask */

/**
 * Skew measured by projecting the outline at candidate angles.
 *
 * Project every set pixel onto the axis across a candidate angle and bin the
 * projections. When the angle is right the sheet's own edges line up and the
 * bins are sharply peaked. When it is wrong the projections smear. The sum of
 * the squared bin counts is the sharpest cheap measure of that, and it is what
 * document scanning has used for forty years.
 *
 * It runs on the outline rather than on the filled mask. A solid rectangle
 * projects to almost the same profile at every small angle, because only the
 * two rows at each end change, so the peak is far too shallow to find. The
 * outline is two thin lines that either line up or do not, and the peak is
 * unmistakable.
 *
 * This exists alongside the caliper fit on purpose. They fail differently: the
 * calipers are thrown by a torn corner, this is thrown by a sheet with a wide
 * pale border, and when the two agree the reading can be trusted.
 */

/**
 * @param {Mask} mask
 * @param {object} [options]
 * @param {number} [options.step] take every nth set pixel
 * @param {boolean} [options.outline] measure the boundary rather than the fill
 * @returns {{xs: Float64Array, ys: Float64Array}}
 */
export function sampleSetPixels(mask, { step = 1, outline = true } = {}) {
  const source = outline ? boundary(mask) : mask;
  /** @type {number[]} */
  const xs = [];
  /** @type {number[]} */
  const ys = [];
  let seen = 0;
  for (let y = 0; y < source.height; y += 1) {
    const row = source.row(y);
    for (let x = 0; x < source.width; x += 1) {
      if (!row[x]) continue;
      if (seen % step === 0) {
        xs.push(x);
        ys.push(y);
      }
      seen += 1;
    }
  }
  if (xs.length === 0) {
    throw new DetectionError('the mask holds nothing to measure skew from', {});
  }
  return { xs: Float64Array.from(xs), ys: Float64Array.from(ys) };
}

/**
 * @param {{xs: Float64Array, ys: Float64Array}} pixels
 * @param {Angle} angle
 * @returns {number} higher is sharper
 */
export function projectionScore(pixels, angle) {
  const cos = Math.cos(angle.radians);
  const sin = Math.sin(angle.radians);
  const { xs, ys } = pixels;

  let low = Number.POSITIVE_INFINITY;
  let high = Number.NEGATIVE_INFINITY;
  const projected = new Float64Array(xs.length);
  for (let i = 0; i < xs.length; i += 1) {
    const p = -xs[i] * sin + ys[i] * cos;
    projected[i] = p;
    if (p < low) low = p;
    if (p > high) high = p;
  }

  // Splat each projection across the two bins it falls between rather than
  // dropping it in one. Hard binning makes the score jump when a bin edge
  // happens to line up with a row, and the jump is large enough to beat the
  // real peak: a sheet at exactly zero degrees measured as half a degree off
  // for that reason alone.
  const bins = Math.max(2, Math.ceil(high - low) + 2);
  const counts = new Float64Array(bins);
  for (let i = 0; i < projected.length; i += 1) {
    const at = projected[i] - low;
    const bin = Math.min(bins - 2, Math.floor(at));
    const fraction = at - bin;
    counts[bin] += 1 - fraction;
    counts[bin + 1] += fraction;
  }

  let acc = 0;
  for (let i = 0; i < bins; i += 1) acc += counts[i] * counts[i];
  return acc / xs.length;
}

/**
 * A sweep across the band, coarse enough to be quick and fine enough that the
 * refinement afterwards has somewhere to start.
 *
 * @param {Mask} mask
 * @param {object} [options]
 * @param {number} [options.range] degrees either side of zero
 * @param {number} [options.step] degrees between candidates
 * @param {number} [options.sample] take every nth set pixel
 * @param {boolean} [options.outline]
 * @returns {{angle: Angle, score: number, tried: number}}
 */
export function coarseSkew(mask, { range = 12, step = 0.5, sample = 1, outline = true } = {}) {
  assertPositive(range, 'range');
  assertPositive(step, 'step');
  const pixels = sampleSetPixels(mask, { step: sample, outline });

  let bestAngle = 0;
  let bestScore = -1;
  let tried = 0;
  for (let degrees = -range; degrees <= range + 1e-9; degrees += step) {
    const score = projectionScore(pixels, Angle.degrees(degrees));
    tried += 1;
    if (score > bestScore) {
      bestScore = score;
      bestAngle = degrees;
    }
  }
  return { angle: Angle.degrees(bestAngle), score: bestScore, tried };
}

/**
 * Ternary search inside the window the sweep landed in. The score is not
 * perfectly unimodal at the pixel level, so the search is bounded by a
 * tolerance rather than run to convergence.
 *
 * @param {Mask} mask
 * @param {Angle} around
 * @param {object} [options]
 * @param {number} [options.window] degrees either side
 * @param {number} [options.tolerance] degrees
 * @param {number} [options.sample]
 * @param {boolean} [options.outline]
 * @returns {{angle: Angle, score: number, tried: number}}
 */
export function refineSkew(
  mask,
  around,
  { window = 0.5, tolerance = 0.01, sample = 1, outline = true } = {},
) {
  const pixels = sampleSetPixels(mask, { step: sample, outline });
  let low = around.degrees - window;
  let high = around.degrees + window;
  let tried = 0;

  while (high - low > tolerance) {
    const third = (high - low) / 3;
    const a = low + third;
    const b = high - third;
    const scoreA = projectionScore(pixels, Angle.degrees(a));
    const scoreB = projectionScore(pixels, Angle.degrees(b));
    tried += 2;
    if (scoreA < scoreB) low = a;
    else high = b;
  }

  const angle = Angle.degrees((low + high) / 2);
  return { angle, score: projectionScore(pixels, angle), tried };
}

/**
 * @param {Mask} mask
 * @param {object} [options]
 * @param {number} [options.range]
 * @param {number} [options.step]
 * @param {number} [options.tolerance]
 * @param {number} [options.sample]
 * @param {boolean} [options.outline]
 * @returns {{angle: Angle, coarse: Angle, score: number, tried: number}}
 */
export function measureSkew(mask, options = {}) {
  const coarse = coarseSkew(mask, options);
  const fine = refineSkew(mask, coarse.angle, {
    window: (options.step ?? 0.5) * 1.5,
    tolerance: options.tolerance,
    sample: options.sample,
    outline: options.outline,
  });
  return {
    angle: fine.angle,
    coarse: coarse.angle,
    score: fine.score,
    tried: coarse.tried + fine.tried,
  };
}
