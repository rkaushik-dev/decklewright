import { check, measurement, starsFor } from '../check.js';
import { channelMeans } from '../sample.js';

/**
 * Gray balance, read off the step wedge.
 *
 * A neutral patch should come back with its three channels equal. When they
 * are not the capture has a cast, and a cast on a map sheet is the difference
 * between a contour line being brown and being red.
 *
 * It is the worst patch that counts, not the average. A capture that is
 * neutral in the midtones and blue in the shadows has a cast, and averaging
 * hides exactly the patch somebody would complain about.
 */

/** The largest channel difference on any patch, in sample values, per star. */
export const GRAY_BALANCE_LADDER = Object.freeze([
  { stars: 4, limit: 3 },
  { stars: 3, limit: 6 },
  { stars: 2, limit: 12 },
  { stars: 1, limit: 24 },
]);

/**
 * @param {readonly number[]} channels
 * @returns {number} how far apart the channels are
 */
export function neutralError(channels) {
  if (channels.length < 3) return 0;
  const rgb = channels.slice(0, 3);
  return Math.max(...rgb) - Math.min(...rgb);
}

/**
 * @returns {import('../check.js').Check}
 */
export function grayBalanceCheck() {
  return check({
    name: 'grayBalance',
    needs: ['surface', 'target', 'limits'],
    run(context) {
      if (context.surface.channels < 3) {
        return measurement(0, 4, 'a grayscale capture cannot have a cast');
      }

      const errors = context.target.patches
        .map((patch) => channelMeans(context.surface, patch.rect))
        .filter((channels) => channels.length >= 3)
        .map(neutralError);

      if (errors.length === 0) {
        return measurement(Number.NaN, 0, 'no patch of the target could be read');
      }

      const worst = Math.max(...errors);
      const allowed = context.limits.grayBalanceTolerance;
      const stars = worst <= allowed ? starsFor(worst, GRAY_BALANCE_LADDER) : 0;
      return measurement(
        worst,
        stars,
        `worst patch is ${worst.toFixed(1)} apart across ${errors.length} patches, against ${allowed}`,
      );
    },
  });
}

/**
 * Which way the cast goes, so an operator can fix it rather than just knowing
 * there is one.
 *
 * @param {import('../../raster/surface.js').Surface} surface
 * @param {readonly {rect: import('../../geom/rect.js').Rect}[]} patches
 * @returns {{channel: string, amount: number}|null}
 */
export function castDirection(surface, patches) {
  if (surface.channels < 3) return null;
  const names = ['red', 'green', 'blue'];
  const totals = [0, 0, 0];
  let read = 0;

  for (const patch of patches) {
    const channels = channelMeans(surface, patch.rect);
    if (channels.length < 3) continue;
    const middle = (channels[0] + channels[1] + channels[2]) / 3;
    for (let c = 0; c < 3; c += 1) totals[c] += channels[c] - middle;
    read += 1;
  }
  if (read === 0) return null;

  let at = 0;
  for (let c = 1; c < 3; c += 1) {
    if (Math.abs(totals[c]) > Math.abs(totals[at])) at = c;
  }
  return { channel: names[at], amount: totals[at] / read };
}
