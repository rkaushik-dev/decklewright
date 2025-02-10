import { BadArgumentError } from '../errors.js';
import { Rect } from '../geom/rect.js';
import { Size } from '../geom/size.js';
import { assertDepth, assertLayout, channelCount, maxValue as maxValueFor } from './layout.js';

/**
 * What every readable pixel region has in common.
 *
 * The surface and the view had grown two copies of the same dozen accessors,
 * and they had already drifted once, so a measurement over a view answered a
 * different maximum value from the same measurement over its parent. The
 * shared parts live here now and a subclass only has to supply the extent and
 * the three sample accessors.
 */
export class Raster {
  /**
   * @param {import('./layout.js').LayoutName} layout
   * @param {import('./layout.js').Depth} depth
   */
  constructor(layout, depth) {
    this.layout = assertLayout(layout);
    this.depth = assertDepth(depth);
    this.channels = channelCount(this.layout);
    this.maxValue = maxValueFor(this.depth);
  }

  /** @returns {Size} */
  get size() {
    return new Size(this.width, this.height);
  }

  /** @returns {Rect} local bounds, always at the origin */
  get bounds() {
    return Rect.of(0, 0, this.width, this.height);
  }

  /** @returns {number} how many samples make up one row */
  get rowStride() {
    return this.width * this.channels;
  }

  /**
   * @param {number} x
   * @param {number} y
   * @returns {boolean}
   */
  contains(x, y) {
    return x >= 0 && y >= 0 && x < this.width && y < this.height;
  }

  /**
   * @param {number} x
   * @param {number} y
   * @returns {void}
   */
  assertInside(x, y) {
    if (!this.contains(x, y)) {
      throw new BadArgumentError(`that pixel is outside the ${this.regionName}`, {
        x,
        y,
        width: this.width,
        height: this.height,
      });
    }
  }

  /**
   * @param {number} channel
   * @returns {number}
   */
  assertChannel(channel) {
    if (channel < 0 || channel >= this.channels) {
      throw new BadArgumentError('that channel is outside the layout', {
        channel,
        channels: this.channels,
      });
    }
    return channel;
  }

  /** @returns {string} what the out of range messages call this thing */
  get regionName() {
    return 'raster';
  }

  /**
   * @param {number} x
   * @param {number} y
   * @returns {number[]}
   */
  getPixel(x, y) {
    const out = new Array(this.channels);
    for (let c = 0; c < this.channels; c += 1) out[c] = this.get(x, y, c);
    return out;
  }

  /**
   * @param {number} x
   * @param {number} y
   * @param {readonly number[]} values
   * @returns {void}
   */
  setPixel(x, y, values) {
    if (values.length !== this.channels) {
      throw new BadArgumentError('that pixel has the wrong number of channels', {
        given: values.length,
        expected: this.channels,
      });
    }
    for (let c = 0; c < this.channels; c += 1) this.set(x, y, c, values[c]);
  }

  /**
   * Walk the rows, which is how every measurement in the tree is written.
   *
   * @returns {Generator<Uint8Array|Uint16Array, void, void>}
   */
  *rows() {
    for (let y = 0; y < this.height; y += 1) yield this.row(y);
  }

  /** @returns {{width: number, height: number, layout: string, depth: number}} */
  toJSON() {
    return { width: this.width, height: this.height, layout: this.layout, depth: this.depth };
  }

  /** @returns {string} */
  toString() {
    return `${this.width}x${this.height} ${this.layout}${this.depth}`;
  }
}
