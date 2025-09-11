import { histogramOf } from '../../raster/histogram.js';
import { channelStats, signalToNoise } from '../../raster/stats.js';
import { SurfaceView } from '../../raster/view.js';
import { check, measurement, starsFor } from '../check.js';

/**
 * Noise and clipping.
 *
 * Noise is measured on a patch that should be flat, because a measure of
 * variation over something that genuinely varies says nothing. The wedge is
 * the only flat thing in the frame that is not the bed, and the bed is out
 * because the board has texture.
 *
 * Clipping is measured on the whole crop, because a sheet with a burnt out
 * highlight has lost that part of the record and it does not matter where.
 */

/** Signal to noise in decibels, per star. Higher is better here. */
export const NOISE_LADDER = Object.freeze([
  { stars: 4, limit: 40 },
  { stars: 3, limit: 30 },
  { stars: 2, limit: 24 },
  { stars: 1, limit: 18 },
]);

/** Share of samples at either end of the range, per star. */
export const CLIPPING_LADDER = Object.freeze([
  { stars: 4, limit: 0.001 },
  { stars: 3, limit: 0.005 },
  { stars: 2, limit: 0.02 },
  { stars: 1, limit: 0.05 },
]);

/**
 * The inner part of a patch, which is the only part worth measuring.
 *
 * @param {import('../../raster/surface.js').Surface} surface
 * @param {import('../../geom/rect.js').Rect} rect
 * @param {object} [options]
 * @param {number} [options.margin]
 * @returns {SurfaceView|null}
 */
export function patchView(surface, rect, { margin = 0.2 } = {}) {
  const insetX = Math.floor(rect.width * margin);
  const insetY = Math.floor(rect.height * margin);
  const inner = rect.inset(insetX, insetY).intersect(surface.bounds);
  if (inner.isEmpty) return null;
  return new SurfaceView(surface, inner);
}

/**
 * The patch a noise measurement should use: light enough that the shot noise
 * is representative and not so light that it is clipped. The guidelines say a
 * mid to light neutral and this picks the lightest one under the clipping
 * point.
 *
 * @param {import('../../raster/surface.js').Surface} surface
 * @param {readonly {rect: import('../../geom/rect.js').Rect}[]} patches
 * @returns {SurfaceView|null}
 */
export function flattestPatch(surface, patches) {
  const top = surface.maxValue;
  /** @type {{view: SurfaceView, mean: number}|null} */
  let best = null;
  for (const patch of patches) {
    const view = patchView(surface, patch.rect);
    if (!view) continue;
    const stats = channelStats(view, 0);
    if (stats.mean > top * 0.9 || stats.mean < top * 0.25) continue;
    if (best === null || stats.mean > best.mean) best = { view, mean: stats.mean };
  }
  return best ? best.view : null;
}

/**
 * @returns {import('../check.js').Check}
 */
export function noiseCheck() {
  return check({
    name: 'noise',
    needs: ['surface', 'target', 'limits'],
    run(context) {
      const view = flattestPatch(context.surface, context.target.patches);
      if (!view) {
        return measurement(Number.NaN, 0, 'no patch of the target is usable for noise');
      }

      const snr = signalToNoise(view, 0);
      const allowed = context.limits.minimumSnr;
      const stars = snr >= allowed ? starsFor(snr, NOISE_LADDER, { higherIsBetter: true }) : 0;
      return measurement(
        Number.isFinite(snr) ? snr : 99,
        stars,
        `${Number.isFinite(snr) ? snr.toFixed(1) : 'no measurable'} dB against a floor of ${allowed}`,
      );
    },
  });
}

/**
 * @returns {import('../check.js').Check}
 */
export function clippingCheck() {
  return check({
    name: 'clipping',
    needs: ['surface', 'limits'],
    run(context) {
      const region = context.crop
        ? new SurfaceView(context.surface, context.crop.intersect(context.surface.bounds))
        : context.surface;

      let worst = 0;
      let where = 'nothing';
      for (let channel = 0; channel < region.channels; channel += 1) {
        const { low, high } = histogramOf(region, { channel }).clipping();
        if (low > worst) {
          worst = low;
          where = 'the shadows';
        }
        if (high > worst) {
          worst = high;
          where = 'the highlights';
        }
      }

      const allowed = context.limits.maximumClipping;
      const stars = worst <= allowed ? starsFor(worst, CLIPPING_LADDER) : 0;
      return measurement(
        worst,
        stars,
        worst === 0
          ? 'nothing is clipped'
          : `${(worst * 100).toFixed(3)} percent clipped in ${where}, against ${(
              allowed * 100
            ).toFixed(3)}`,
      );
    },
  });
}
