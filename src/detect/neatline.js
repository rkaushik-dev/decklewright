import { Rect } from '../geom/rect.js';
import { assertInRange } from '../util/assert.js';
import { meanProfile } from '../raster/stats.js';
import { SurfaceView } from '../raster/view.js';

/** @typedef {import('../raster/surface.js').Surface} Surface */
/** @typedef {'top'|'right'|'bottom'|'left'} Side */

/**
 * The neatline: the ruled border a map sheet is drawn inside.
 *
 * On a quadrangle the neatline is the actual map. Everything outside it is
 * collar, which carries the legend, the scale bar, the credits and the grid
 * ticks. Two of the collections crop to the neatline and two crop to the paper
 * edge, and the difference is about an inch and a half of content, so nobody
 * gets to guess.
 *
 * It is found as the strongest dark ruled line within a band inside each paper
 * edge. A ruled line is dark across its whole length, so it shows up in the
 * mean profile as a narrow trough far below the paper around it.
 */

/**
 * @param {Surface} surface
 * @param {Rect} within
 * @param {'row'|'column'} along
 * @returns {Float64Array}
 */
export function darkness(surface, within, along) {
  const view = new SurfaceView(surface, within);
  const profile = meanProfile(view, { along });
  const out = new Float64Array(profile.length);
  const top = surface.maxValue;
  for (let i = 0; i < profile.length; i += 1) out[i] = top - profile[i];
  return out;
}

/**
 * The strongest trough in a run, taken as the largest rise above the run's own
 * median. Returning the contrast alongside the index lets the caller decide
 * whether it found a ruled line or just paper grain.
 *
 * @param {Float64Array} profile
 * @param {number} from index to start at
 * @param {number} to index to stop before
 * @returns {{at: number, contrast: number}}
 */
export function strongestLine(profile, from, to) {
  const start = Math.max(0, Math.min(from, to));
  const end = Math.min(profile.length, Math.max(from, to));
  if (end - start < 2) return { at: -1, contrast: 0 };

  const window = Array.from(profile.slice(start, end));
  const sorted = [...window].sort((a, b) => a - b);
  const middle = sorted[Math.floor(sorted.length / 2)];

  let at = -1;
  let best = 0;
  for (let i = start; i < end; i += 1) {
    const contrast = profile[i] - middle;
    if (contrast > best) {
      best = contrast;
      at = i;
    }
  }
  return { at, contrast: best };
}

/**
 * @typedef {object} Neatline
 * @property {Rect} rect
 * @property {Record<Side, number>} contrast how strong each ruled line was
 * @property {Side[]} missing sides where nothing convincing was found
 * @property {boolean} complete
 */

/**
 * @param {Surface} surface
 * @param {Rect} sheet the paper edge, already square to the axes
 * @param {object} [options]
 * @param {number} [options.band] share of the sheet searched in from each edge, and it
 *   has to reach past the legend collar at the bottom of a quadrangle
 * @param {number} [options.minimumContrast] in sample values
 * @returns {Neatline}
 */
export function findNeatline(surface, sheet, { band = 0.35, minimumContrast = 12 } = {}) {
  assertInRange(band, 0.01, 0.49, 'band');

  const rows = darkness(surface, sheet, 'row');
  const columns = darkness(surface, sheet, 'column');
  const bandY = Math.max(2, Math.round(sheet.height * band));
  const bandX = Math.max(2, Math.round(sheet.width * band));

  const top = strongestLine(rows, 0, bandY);
  const bottom = strongestLine(rows, rows.length - bandY, rows.length);
  const left = strongestLine(columns, 0, bandX);
  const right = strongestLine(columns, columns.length - bandX, columns.length);

  /** @type {Side[]} */
  const missing = [];
  const edge = {
    top: top.contrast >= minimumContrast ? sheet.top + top.at : sheet.top,
    bottom: bottom.contrast >= minimumContrast ? sheet.top + bottom.at + 1 : sheet.bottom,
    left: left.contrast >= minimumContrast ? sheet.left + left.at : sheet.left,
    right: right.contrast >= minimumContrast ? sheet.left + right.at + 1 : sheet.right,
  };
  if (top.contrast < minimumContrast) missing.push('top');
  if (right.contrast < minimumContrast) missing.push('right');
  if (bottom.contrast < minimumContrast) missing.push('bottom');
  if (left.contrast < minimumContrast) missing.push('left');

  return {
    rect: Rect.fromEdges(
      edge.left,
      edge.top,
      Math.max(edge.left, edge.right),
      Math.max(edge.top, edge.bottom),
    ),
    contrast: {
      top: top.contrast,
      right: right.contrast,
      bottom: bottom.contrast,
      left: left.contrast,
    },
    missing,
    complete: missing.length === 0,
  };
}

/**
 * How much paper sits between the neatline and the sheet edge, per side. This
 * is the collar, and the profiles quote it in millimetres.
 *
 * @param {Rect} sheet
 * @param {Rect} neatline
 * @returns {Record<Side, number>}
 */
export function collarWidths(sheet, neatline) {
  return {
    top: neatline.top - sheet.top,
    right: sheet.right - neatline.right,
    bottom: sheet.bottom - neatline.bottom,
    left: neatline.left - sheet.left,
  };
}

/**
 * A quadrangle has a wide collar at the bottom for the legend and narrower
 * ones elsewhere. A neatline that came back with the widest collar somewhere
 * else has probably locked onto a grid line rather than the border.
 *
 * @param {Record<Side, number>} widths
 * @returns {boolean}
 */
export function looksLikeQuadrangle(widths) {
  const sides = /** @type {Side[]} */ (['top', 'right', 'bottom', 'left']);
  const widest = sides.reduce((best, side) => (widths[side] > widths[best] ? side : best), 'top');
  return widest === 'bottom' && widths.bottom > 0;
}
