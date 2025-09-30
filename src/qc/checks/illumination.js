import { check, measurement, starsFor } from '../check.js';
import { gridOver, patchGrid, patchMeans } from '../sample.js';

/**
 * Illumination uniformity.
 *
 * Two lamps either side of a copy stand never light a thirty six inch sheet
 * evenly. The guidelines allow a spread and the collections that deliver into
 * a viewer care about it more than the ones that deliver plates, because a
 * viewer shows two sheets side by side and a difference in brightness between
 * them is the first thing anybody sees.
 *
 * It is measured on the bed rather than on the sheet: paper has its own tone
 * and a sheet that is genuinely darker at one end would fail a check that
 * cannot tell the two apart. Any patch that touches the sheet or the colour
 * target is dropped for the same reason, and dropping them is not optional:
 * a grid over the whole capture measures the difference between paper and
 * board, which is two hundred percent and means nothing. The caller says what
 * to keep off, because it is the caller that knows what else was on the bed.
 */

/** Spread as a share of the mean, per star. */
export const ILLUMINATION_LADDER = Object.freeze([
  { stars: 4, limit: 0.05 },
  { stars: 3, limit: 0.1 },
  { stars: 2, limit: 0.2 },
  { stars: 1, limit: 0.35 },
]);

/**
 * @param {object} [options]
 * @param {number} [options.across]
 * @param {number} [options.down]
 * @returns {import('../check.js').Check}
 */
export function illuminationCheck({ across = 5, down = 4 } = {}) {
  return check({
    name: 'illumination',
    needs: ['surface', 'limits'],
    run(context) {
      const region = context.bed ?? context.surface.bounds;
      const avoid = context.avoid ?? [context.sheet, context.target?.rect].filter(Boolean);
      const means = patchMeans(context.surface, gridOver(region, across, down, { avoid }));
      if (means.length < 2) {
        return measurement(Number.NaN, 0, 'not enough of the bed to measure');
      }

      const low = Math.min(...means);
      const high = Math.max(...means);
      const middle = means.reduce((a, b) => a + b, 0) / means.length;
      const spread = middle === 0 ? 0 : (high - low) / middle;

      const allowed = context.limits.illuminationTolerance;
      const stars = spread <= allowed ? starsFor(spread, ILLUMINATION_LADDER) : 0;
      return measurement(
        spread,
        stars,
        `${(spread * 100).toFixed(1)} percent across ${means.length} patches, against a limit of ${(
          allowed * 100
        ).toFixed(1)}`,
      );
    },
  });
}

/**
 * Which corner is darkest, which is what an operator needs in order to move a
 * lamp. It is not a check, it is the thing a failed check should have said.
 *
 * @param {import('../../raster/surface.js').Surface} surface
 * @param {import('../../geom/rect.js').Rect} [region]
 * @returns {{corner: string, mean: number}}
 */
export function darkestCorner(surface, region) {
  const within = region ?? surface.bounds;
  const patches = patchGrid(within, 2, 2);
  const means = patchMeans(surface, patches);
  const names = ['top left', 'top right', 'bottom left', 'bottom right'];
  let at = 0;
  for (let i = 1; i < means.length; i += 1) {
    if (means[i] < means[at]) at = i;
  }
  return { corner: names[at] ?? 'unknown', mean: means[at] ?? Number.NaN };
}
