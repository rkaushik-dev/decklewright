import { Rect } from '../geom/rect.js';
import { allChannelStats, channelStats } from '../raster/stats.js';
import { SurfaceView } from '../raster/view.js';

/**
 * Reading a small area of a capture, for the checks that need one.
 *
 * Every check that measures a patch was doing the same three things: take a
 * margin off so a boundary that is a pixel out does not pull the reading, clip
 * to the surface, and refuse rather than return a reading off an empty view.
 * Three copies of that is three chances for the margins to drift apart, and a
 * gray balance measured on a smaller inner area than the noise measurement is
 * a difference nobody would ever find.
 *
 * The margin is a share of the patch, not a pixel count, because the wedge
 * patches on a quadrangle sheet are four times the size of the ones on a
 * microfilm target and a fixed inset would eat the small ones.
 */

/** How much of a patch to leave off, as a share of each side. */
export const PATCH_MARGIN = 0.2;

/**
 * The inner part of a patch, which is the only part worth measuring.
 *
 * @param {import('../raster/surface.js').Surface} surface
 * @param {import('../geom/rect.js').Rect} rect
 * @param {object} [options]
 * @param {number} [options.margin]
 * @returns {SurfaceView|null} null when there is nothing left to read
 */
export function patchView(surface, rect, { margin = PATCH_MARGIN } = {}) {
  const insetX = Math.floor(rect.width * margin);
  const insetY = Math.floor(rect.height * margin);
  const inner = rect.inset(insetX, insetY).intersect(surface.bounds);
  if (inner.isEmpty) return null;
  return new SurfaceView(surface, inner);
}

/**
 * The mean of every channel over a patch.
 *
 * @param {import('../raster/surface.js').Surface} surface
 * @param {import('../geom/rect.js').Rect} rect
 * @param {object} [options]
 * @param {number} [options.margin]
 * @returns {number[]} empty when the patch could not be read
 */
export function channelMeans(surface, rect, options) {
  const view = patchView(surface, rect, options);
  if (!view) return [];
  return allChannelStats(view).map((stats) => stats.mean);
}

/**
 * The mean of the first channel of each of a list of patches, dropping the
 * ones that fell off the capture.
 *
 * @param {import('../raster/surface.js').Surface} surface
 * @param {readonly import('../geom/rect.js').Rect[]} rects
 * @param {object} [options]
 * @param {number} [options.margin]
 * @returns {number[]}
 */
export function patchMeans(surface, rects, { margin = 0 } = {}) {
  /** @type {number[]} */
  const out = [];
  for (const rect of rects) {
    const view = patchView(surface, rect, { margin });
    if (view) out.push(channelStats(view, 0).mean);
  }
  return out;
}

/**
 * A grid of patches over a region, each one a fraction of the cell so a patch
 * that lands on an edge does not take the edge with it.
 *
 * @param {import('../geom/rect.js').Rect} within
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
 * The same grid with everything the caller named kept off it. A patch that so
 * much as touches one of them is dropped whole rather than trimmed, because a
 * trimmed patch is a smaller sample of a different place and comparing it
 * against the others is what the caller was trying to avoid.
 *
 * @param {import('../geom/rect.js').Rect} within
 * @param {number} across
 * @param {number} down
 * @param {object} [options]
 * @param {number} [options.margin]
 * @param {readonly import('../geom/rect.js').Rect[]} [options.avoid]
 * @returns {Rect[]}
 */
export function gridOver(within, across, down, { margin, avoid = [] } = {}) {
  const grid = patchGrid(within, across, down, margin === undefined ? undefined : { margin });
  if (avoid.length === 0) return grid;
  return grid.filter((patch) => !avoid.some((other) => patch.intersects(other)));
}

/**
 * The patch a measurement of variation should use: light enough that the
 * reading is representative and not so light that it is clipped.
 *
 * @param {import('../raster/surface.js').Surface} surface
 * @param {readonly {rect: import('../geom/rect.js').Rect}[]} patches
 * @param {object} [options]
 * @param {number} [options.ceiling] share of the range above which it is clipped
 * @param {number} [options.floor] share of the range below which it is too dark
 * @returns {SurfaceView|null}
 */
export function lightestFlatPatch(surface, patches, { ceiling = 0.9, floor = 0.25 } = {}) {
  const top = surface.maxValue;
  /** @type {{view: SurfaceView, mean: number}|null} */
  let best = null;
  for (const patch of patches) {
    const view = patchView(surface, patch.rect);
    if (!view) continue;
    const mean = channelStats(view, 0).mean;
    if (mean > top * ceiling || mean < top * floor) continue;
    if (best === null || mean > best.mean) best = { view, mean };
  }
  return best ? best.view : null;
}
