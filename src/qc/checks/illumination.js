import { Rect } from '../../geom/rect.js';
import { allChannelStats } from '../../raster/stats.js';
import { SurfaceView } from '../../raster/view.js';
import { check, measurement, starsFor } from '../check.js';

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
 * cannot tell the two apart.
 */

/** Spread as a share of the mean, per star. */
export const ILLUMINATION_LADDER = Object.freeze([
  { stars: 4, limit: 0.05 },
  { stars: 3, limit: 0.1 },
  { stars: 2, limit: 0.2 },
  { stars: 1, limit: 0.35 },
]);

/**
 * A grid of patches over a region, each one a fraction of the cell so a patch
 * that lands on an edge does not take the edge with it.
 *
 * @param {import('../../geom/rect.js').Rect} within
 * @param {number} across
 * @param {number} down
 * @param {object} [options]
 * @param {number} [options.margin]
 * @returns {Rect[]}
 */
export function patchGrid(within, across, down, { margin = 0.25 } = {}) {
  /** @type {Rect[]} */
  const out = [];
  const cellWidth = within.width / across;
  const cellHeight = within.height / down;
  const insetX = cellWidth * margin;
  const insetY = cellHeight * margin;

  for (let row = 0; row < down; row += 1) {
    for (let column = 0; column < across; column += 1) {
      const left = Math.round(within.left + column * cellWidth + insetX);
      const top = Math.round(within.top + row * cellHeight + insetY);
      const right = Math.round(within.left + (column + 1) * cellWidth - insetX);
      const bottom = Math.round(within.top + (row + 1) * cellHeight - insetY);
      out.push(Rect.fromEdges(left, top, Math.max(left + 1, right), Math.max(top + 1, bottom)));
    }
  }
  return out;
}

/**
 * @param {import('../../raster/surface.js').Surface} surface
 * @param {readonly Rect[]} patches
 * @returns {number[]} the mean of the first channel of each patch
 */
export function patchMeans(surface, patches) {
  return patches
    .map((rect) => rect.intersect(surface.bounds))
    .filter((rect) => !rect.isEmpty)
    .map((rect) => allChannelStats(new SurfaceView(surface, rect))[0].mean);
}

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
      const means = patchMeans(context.surface, patchGrid(region, across, down));
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
