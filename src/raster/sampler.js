import { assertOneOf } from '../util/assert.js';
import { clamp } from '../util/num.js';

/** @typedef {import('./surface.js').Surface} Surface */
/** @typedef {'clamp'|'mirror'|'constant'} EdgeMode */
/** @typedef {'nearest'|'bilinear'} SampleMode */

/**
 * Reading a sample at a position that is not a pixel centre.
 *
 * The convention here is that pixel centres sit at whole coordinates, so the
 * sample at x equals two is exactly column two and no interpolation happens.
 * The other convention, with centres at half offsets, shifts a deskewed sheet
 * by half a pixel against its own quality report, which took a week to find.
 */

export const EDGE_MODES = Object.freeze(['clamp', 'mirror', 'constant']);
export const SAMPLE_MODES = Object.freeze(['nearest', 'bilinear']);

/**
 * @param {number} value
 * @param {number} extent
 * @param {EdgeMode} mode
 * @returns {number} the column to read, or minus one when there is none
 */
export function resolveIndex(value, extent, mode) {
  // adding zero folds a negative zero from Math.ceil back onto the real one
  if (value >= 0 && value < extent) return value + 0;
  switch (mode) {
    case 'mirror': {
      if (extent === 1) return 0;
      const period = 2 * extent - 2;
      let wrapped = ((value % period) + period) % period;
      if (wrapped >= extent) wrapped = period - wrapped;
      return wrapped;
    }
    case 'constant':
      return -1;
    default:
      return clamp(value, 0, extent - 1);
  }
}

/**
 * A reader bound to one surface, so the edge policy is decided once rather
 * than on every one of the hundred million samples a deskew touches.
 */
export class Sampler {
  /**
   * @param {Surface} surface
   * @param {object} [options]
   * @param {EdgeMode} [options.edge]
   * @param {number|readonly number[]} [options.background]
   */
  constructor(surface, { edge = 'clamp', background = 0 } = {}) {
    this.surface = surface;
    this.edge = /** @type {EdgeMode} */ (assertOneOf(edge, EDGE_MODES, 'edge'));
    this.background =
      typeof background === 'number'
        ? new Array(surface.channels).fill(background)
        : [...background];
    Object.freeze(this);
  }

  /**
   * @param {number} x
   * @param {number} y
   * @param {number} channel
   * @returns {number}
   */
  at(x, y, channel) {
    const sx = resolveIndex(x, this.surface.width, this.edge);
    const sy = resolveIndex(y, this.surface.height, this.edge);
    if (sx < 0 || sy < 0) return this.background[channel];
    return this.surface.get(sx, sy, channel);
  }

  /**
   * @param {number} x
   * @param {number} y
   * @param {number} channel
   * @returns {number}
   */
  nearest(x, y, channel) {
    return this.at(Math.round(x), Math.round(y), channel);
  }

  /**
   * @param {number} x
   * @param {number} y
   * @param {number} channel
   * @returns {number}
   */
  bilinear(x, y, channel) {
    const x0 = Math.floor(x);
    const y0 = Math.floor(y);
    const fx = x - x0;
    const fy = y - y0;
    if (fx === 0 && fy === 0) return this.at(x0, y0, channel);
    const a = this.at(x0, y0, channel);
    const b = this.at(x0 + 1, y0, channel);
    const c = this.at(x0, y0 + 1, channel);
    const d = this.at(x0 + 1, y0 + 1, channel);
    const top = a + (b - a) * fx;
    const bottom = c + (d - c) * fx;
    return top + (bottom - top) * fy;
  }

  /**
   * @param {number} x
   * @param {number} y
   * @param {number} channel
   * @param {SampleMode} [mode]
   * @returns {number}
   */
  sample(x, y, channel, mode = 'bilinear') {
    assertOneOf(mode, SAMPLE_MODES, 'mode');
    return mode === 'nearest' ? this.nearest(x, y, channel) : this.bilinear(x, y, channel);
  }

  /**
   * @param {number} x
   * @param {number} y
   * @param {SampleMode} [mode]
   * @returns {number[]}
   */
  pixel(x, y, mode = 'bilinear') {
    const out = new Array(this.surface.channels);
    for (let c = 0; c < out.length; c += 1) out[c] = this.sample(x, y, c, mode);
    return out;
  }

  /**
   * Rounded and clamped to the depth, which is what anything writing back into
   * a surface needs. Kept separate from sample so a measurement can keep the
   * real value.
   *
   * @param {number} x
   * @param {number} y
   * @param {number} channel
   * @param {SampleMode} [mode]
   * @returns {number}
   */
  quantised(x, y, channel, mode = 'bilinear') {
    const value = this.sample(x, y, channel, mode);
    return clamp(Math.round(value), 0, this.surface.maxValue);
  }
}

/**
 * @param {Surface} surface
 * @param {object} [options]
 * @param {EdgeMode} [options.edge]
 * @param {number|readonly number[]} [options.background]
 * @returns {Sampler}
 */
export function samplerFor(surface, options) {
  return new Sampler(surface, options);
}
