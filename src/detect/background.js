import { DetectionError } from '../errors.js';
import { Rect } from '../geom/rect.js';
import { assertInRange } from '../util/assert.js';
import { allChannelStats } from '../raster/stats.js';
import { SurfaceView } from '../raster/view.js';
import { Mask } from './mask.js';

/** @typedef {import('../raster/surface.js').Surface} Surface */
/** @typedef {{mean: number[], stdDev: number[], samples: number}} Background */

/**
 * What the bed looks like.
 *
 * Every stand in the lab has a matte board behind the sheet and every board is
 * a different grey. Asking the profile to name the colour was tried and it
 * went stale within a month of the boards being replaced, so the estimate is
 * taken from the capture itself, off the four border strips where a correctly
 * placed sheet never reaches.
 */

/**
 * @param {Surface} surface
 * @param {number} fraction
 * @returns {Rect[]}
 */
export function borderStrips(surface, fraction) {
  assertInRange(fraction, 0.001, 0.45, 'fraction');
  const thickX = Math.max(1, Math.round(surface.width * fraction));
  const thickY = Math.max(1, Math.round(surface.height * fraction));
  return [
    Rect.of(0, 0, surface.width, thickY),
    Rect.of(0, surface.height - thickY, surface.width, thickY),
    Rect.of(0, thickY, thickX, Math.max(1, surface.height - 2 * thickY)),
    Rect.of(surface.width - thickX, thickY, thickX, Math.max(1, surface.height - 2 * thickY)),
  ];
}

/**
 * @param {Surface} surface
 * @param {object} [options]
 * @param {number} [options.fraction] how much of each edge counts as border
 * @returns {Background}
 */
export function estimateBackground(surface, { fraction = 0.04 } = {}) {
  const strips = borderStrips(surface, fraction).filter((rect) =>
    surface.bounds.containsRect(rect),
  );
  if (strips.length === 0) {
    throw new DetectionError('the capture is too small to have a border', {
      size: surface.size.toJSON(),
    });
  }

  const channels = surface.channels;
  const means = new Array(channels).fill(0);
  const variances = new Array(channels).fill(0);
  let samples = 0;

  for (const rect of strips) {
    const view = new SurfaceView(surface, rect);
    const stats = allChannelStats(view);
    const weight = stats[0].count;
    samples += weight;
    for (let c = 0; c < channels; c += 1) {
      means[c] += stats[c].mean * weight;
      variances[c] += (stats[c].stdDev ** 2 + stats[c].mean ** 2) * weight;
    }
  }

  const mean = means.map((total) => total / samples);
  const stdDev = variances.map((total, c) =>
    Math.sqrt(Math.max(0, total / samples - mean[c] ** 2)),
  );
  return { mean, stdDev, samples };
}

/**
 * How far a pixel sits from the background, in units of the background's own
 * spread. A flat bed gives a small spread and a textured board gives a large
 * one, so the same threshold works on both.
 *
 * @param {readonly number[]} pixel
 * @param {Background} background
 * @returns {number}
 */
export function distanceFrom(pixel, background) {
  let worst = 0;
  for (let c = 0; c < pixel.length; c += 1) {
    const spread = Math.max(1, background.stdDev[c]);
    const distance = Math.abs(pixel[c] - background.mean[c]) / spread;
    if (distance > worst) worst = distance;
  }
  return worst;
}

/**
 * @param {Surface} surface
 * @param {object} [options]
 * @param {Background} [options.background]
 * @param {number} [options.sigma] how many spreads away counts as object
 * @param {number} [options.fraction]
 * @returns {{mask: Mask, background: Background}}
 */
export function objectMask(surface, { background, sigma = 6, fraction = 0.04 } = {}) {
  const bed = background ?? estimateBackground(surface, { fraction });
  const mask = new Mask(surface.width, surface.height);
  const channels = surface.channels;
  const spread = bed.stdDev.map((value) => Math.max(1, value));

  let at = 0;
  for (let y = 0; y < surface.height; y += 1) {
    const row = surface.row(y);
    for (let x = 0; x < surface.width; x += 1) {
      const base = x * channels;
      let worst = 0;
      for (let c = 0; c < channels; c += 1) {
        const distance = Math.abs(row[base + c] - bed.mean[c]) / spread[c];
        if (distance > worst) worst = distance;
      }
      mask.data[at] = worst > sigma ? 1 : 0;
      at += 1;
    }
  }
  return { mask, background: bed };
}

/**
 * True when the bed is uniform enough for the estimate to mean anything. A
 * board with a shadow across it fails this and the operator gets told to
 * relight rather than getting a crop that is quietly wrong.
 *
 * @param {Background} background
 * @param {object} [options]
 * @param {number} [options.maxSpread] as a share of full scale
 * @param {number} [options.fullScale]
 * @returns {boolean}
 */
export function isUniform(background, { maxSpread = 0.06, fullScale = 255 } = {}) {
  return background.stdDev.every((value) => value / fullScale <= maxSpread);
}

/**
 * @param {Background} background
 * @returns {string}
 */
export function describeBackground(background) {
  const mean = background.mean.map((value) => Math.round(value)).join(', ');
  const spread = background.stdDev.map((value) => value.toFixed(1)).join(', ');
  return `bed at ${mean} give or take ${spread} over ${background.samples} samples`;
}
