import { Rect } from '../geom/rect.js';
import { assertPositiveInteger } from '../util/assert.js';
import { allChannelStats } from '../raster/stats.js';
import { SurfaceView } from '../raster/view.js';
import { labelComponents, fillRatio } from './components.js';

/** @typedef {import('../raster/surface.js').Surface} Surface */
/** @typedef {import('./mask.js').Mask} Mask */
/** @typedef {{rect: Rect, mean: number[], stdDev: number[]}} Patch */

/**
 * The colour target that sits on the bed next to every capture.
 *
 * The lab uses a step wedge with a row of grey patches running from paper
 * white to near black. The quality checks read tone off it, so it has to be
 * found before any of them can run, and it has to be found in a way that does
 * not confuse it with the sheet, a stray envelope, or the scale bar.
 *
 * The rule that works is the strip test: a target is a rectangle whose long
 * axis divides into patches that step monotonically. Nothing else on the bed
 * does that.
 */

/**
 * Divide a rect along its long axis and measure each cell. A margin is taken
 * off every cell so a patch boundary that is a pixel out does not pull the
 * reading.
 *
 * @param {Surface} surface
 * @param {Rect} rect
 * @param {number} count
 * @param {object} [options]
 * @param {number} [options.margin] share of each cell ignored at its edges
 * @returns {Patch[]}
 */
export function readPatches(surface, rect, count, { margin = 0.2 } = {}) {
  assertPositiveInteger(count, 'count');
  const horizontal = rect.width >= rect.height;
  const span = horizontal ? rect.width : rect.height;
  const step = span / count;

  /** @type {Patch[]} */
  const out = [];
  for (let i = 0; i < count; i += 1) {
    const inset = step * margin;
    const from = Math.round((horizontal ? rect.left : rect.top) + i * step + inset);
    const to = Math.round((horizontal ? rect.left : rect.top) + (i + 1) * step - inset);
    const thin = Math.max(1, Math.round((horizontal ? rect.height : rect.width) * margin));

    const cell = horizontal
      ? Rect.fromEdges(from, rect.top + thin, Math.max(from + 1, to), rect.bottom - thin)
      : Rect.fromEdges(rect.left + thin, from, rect.right - thin, Math.max(from + 1, to));

    const clipped = cell.intersect(surface.bounds);
    if (clipped.isEmpty) {
      out.push({ rect: cell, mean: [], stdDev: [] });
      continue;
    }
    const stats = allChannelStats(new SurfaceView(surface, clipped));
    out.push({
      rect: clipped,
      mean: stats.map((s) => s.mean),
      stdDev: stats.map((s) => s.stdDev),
    });
  }
  return out;
}

/**
 * @param {readonly Patch[]} patches
 * @param {object} [options]
 * @param {number} [options.channel]
 * @param {number} [options.slack] how many steps may go the wrong way
 * @returns {boolean}
 */
export function stepsMonotonically(patches, { channel = 0, slack = 1 } = {}) {
  const values = patches.map((patch) => patch.mean[channel]);
  if (values.some((value) => !Number.isFinite(value))) return false;

  let up = 0;
  let down = 0;
  for (let i = 1; i < values.length; i += 1) {
    if (values[i] > values[i - 1]) up += 1;
    else if (values[i] < values[i - 1]) down += 1;
  }
  return Math.min(up, down) <= slack;
}

/**
 * @param {readonly Patch[]} patches
 * @param {object} [options]
 * @param {number} [options.channel]
 * @returns {number} the difference between the lightest and darkest patch
 */
export function patchRange(patches, { channel = 0 } = {}) {
  const values = patches.map((patch) => patch.mean[channel]).filter(Number.isFinite);
  if (values.length === 0) return 0;
  return Math.max(...values) - Math.min(...values);
}

/**
 * @typedef {object} TargetFinding
 * @property {Rect} rect
 * @property {Patch[]} patches
 * @property {number} range
 * @property {boolean} monotone
 */

/**
 * Look through the components of a mask for the one that behaves like a step
 * wedge, ignoring anything the caller has already claimed as the sheet.
 *
 * @param {Surface} surface
 * @param {Mask} mask everything that is not bed
 * @param {object} [options]
 * @param {number} [options.patches] how many steps the wedge has
 * @param {number} [options.minimumArea] in pixels
 * @param {number} [options.minimumRange] in sample values
 * @param {number} [options.minimumFill] how solidly a component fills its box
 * @param {readonly number[]} [options.ignore] component ids to skip
 * @returns {TargetFinding|null}
 */
export function locateTarget(surface, mask, options = {}) {
  const {
    patches: patchCount = 8,
    minimumArea = 200,
    minimumRange = 60,
    minimumFill = 0.6,
    ignore = [],
  } = options;
  const skip = new Set(ignore);
  const { components } = labelComponents(mask);

  /** @type {TargetFinding|null} */
  let best = null;
  for (const component of components) {
    if (skip.has(component.id)) continue;
    if (component.area < minimumArea) continue;
    if (fillRatio(component) < minimumFill) continue;

    const patches = readPatches(surface, component.bounds, patchCount);
    const range = patchRange(patches);
    if (range < minimumRange) continue;
    if (!stepsMonotonically(patches)) continue;
    if (best === null || range > best.range) {
      best = { rect: component.bounds, patches, range, monotone: true };
    }
  }
  return best;
}

/**
 * The two patches a gray balance check works from: the lightest and the
 * darkest that are not clipped.
 *
 * @param {readonly Patch[]} patches
 * @param {number} fullScale
 * @returns {{light: Patch|null, dark: Patch|null}}
 */
export function endPatches(patches, fullScale) {
  const usable = patches.filter(
    (patch) =>
      patch.mean.length > 0 && patch.mean[0] > fullScale * 0.02 && patch.mean[0] < fullScale * 0.98,
  );
  if (usable.length === 0) return { light: null, dark: null };
  const sorted = [...usable].sort((a, b) => a.mean[0] - b.mean[0]);
  return { dark: sorted[0], light: sorted[sorted.length - 1] };
}
