import { CodecError } from '../../errors.js';

/**
 * The five png row filters.
 *
 * They are cheap and they matter: a scan of a paper sheet is mostly a slow
 * gradient, and Paeth over that gives about a third smaller file than no
 * filter at all. Everything here works on one row at a time against the row
 * above, which is all the format allows.
 */

export const FILTER_NONE = 0;
export const FILTER_SUB = 1;
export const FILTER_UP = 2;
export const FILTER_AVERAGE = 3;
export const FILTER_PAETH = 4;

export const FILTER_NAMES = Object.freeze(['none', 'sub', 'up', 'average', 'paeth']);

/**
 * @param {number} a left
 * @param {number} b above
 * @param {number} c above left
 * @returns {number}
 */
export function paethPredictor(a, b, c) {
  const p = a + b - c;
  const pa = Math.abs(p - a);
  const pb = Math.abs(p - b);
  const pc = Math.abs(p - c);
  if (pa <= pb && pa <= pc) return a;
  if (pb <= pc) return b;
  return c;
}

/**
 * Undo one filtered row in place. The row handed in already has its filter
 * byte stripped off, and the previous row is the reconstructed one.
 *
 * @param {number} filter
 * @param {Uint8Array} row
 * @param {Uint8Array|null} previous
 * @param {number} step bytes per pixel, at least one
 * @returns {Uint8Array}
 */
export function undoFilter(filter, row, previous, step) {
  switch (filter) {
    case FILTER_NONE:
      return row;
    case FILTER_SUB:
      for (let i = step; i < row.length; i += 1) {
        row[i] = (row[i] + row[i - step]) & 0xff;
      }
      return row;
    case FILTER_UP:
      if (previous) {
        for (let i = 0; i < row.length; i += 1) row[i] = (row[i] + previous[i]) & 0xff;
      }
      return row;
    case FILTER_AVERAGE:
      for (let i = 0; i < row.length; i += 1) {
        const left = i >= step ? row[i - step] : 0;
        const above = previous ? previous[i] : 0;
        row[i] = (row[i] + ((left + above) >> 1)) & 0xff;
      }
      return row;
    case FILTER_PAETH:
      for (let i = 0; i < row.length; i += 1) {
        const left = i >= step ? row[i - step] : 0;
        const above = previous ? previous[i] : 0;
        const aboveLeft = previous && i >= step ? previous[i - step] : 0;
        row[i] = (row[i] + paethPredictor(left, above, aboveLeft)) & 0xff;
      }
      return row;
    default:
      throw new CodecError(`unknown row filter ${filter}`, { filter });
  }
}

/**
 * @param {number} filter
 * @param {Uint8Array} row raw samples
 * @param {Uint8Array|null} previous raw samples of the row above
 * @param {number} step
 * @param {Uint8Array} [into]
 * @returns {Uint8Array}
 */
export function applyFilter(filter, row, previous, step, into) {
  const out = into ?? new Uint8Array(row.length);
  switch (filter) {
    case FILTER_NONE:
      out.set(row);
      return out;
    case FILTER_SUB:
      for (let i = 0; i < row.length; i += 1) {
        out[i] = (row[i] - (i >= step ? row[i - step] : 0)) & 0xff;
      }
      return out;
    case FILTER_UP:
      for (let i = 0; i < row.length; i += 1) {
        out[i] = (row[i] - (previous ? previous[i] : 0)) & 0xff;
      }
      return out;
    case FILTER_AVERAGE:
      for (let i = 0; i < row.length; i += 1) {
        const left = i >= step ? row[i - step] : 0;
        const above = previous ? previous[i] : 0;
        out[i] = (row[i] - ((left + above) >> 1)) & 0xff;
      }
      return out;
    case FILTER_PAETH:
      for (let i = 0; i < row.length; i += 1) {
        const left = i >= step ? row[i - step] : 0;
        const above = previous ? previous[i] : 0;
        const aboveLeft = previous && i >= step ? previous[i - step] : 0;
        out[i] = (row[i] - paethPredictor(left, above, aboveLeft)) & 0xff;
      }
      return out;
    default:
      throw new CodecError(`unknown row filter ${filter}`, { filter });
  }
}

/**
 * The minimum sum of absolute differences heuristic from the specification.
 * It is not optimal and it is not close to slow, which is the right trade for
 * a master that is written once and read for the next hundred years.
 *
 * @param {Uint8Array} row
 * @param {Uint8Array|null} previous
 * @param {number} step
 * @returns {{filter: number, data: Uint8Array}}
 */
export function chooseFilter(row, previous, step) {
  let best = FILTER_NONE;
  let bestScore = Number.POSITIVE_INFINITY;
  let bestData = null;
  const scratch = new Uint8Array(row.length);

  for (const filter of [FILTER_NONE, FILTER_SUB, FILTER_UP, FILTER_AVERAGE, FILTER_PAETH]) {
    const candidate = applyFilter(filter, row, previous, step, scratch);
    let score = 0;
    for (let i = 0; i < candidate.length; i += 1) {
      score += candidate[i] < 128 ? candidate[i] : 256 - candidate[i];
    }
    if (score < bestScore) {
      bestScore = score;
      best = filter;
      bestData = candidate.slice();
    }
  }
  return { filter: best, data: /** @type {Uint8Array} */ (bestData) };
}

/**
 * @param {number} filter
 * @returns {string}
 */
export function filterName(filter) {
  const name = FILTER_NAMES[filter];
  if (!name) throw new CodecError(`unknown row filter ${filter}`, { filter });
  return name;
}
