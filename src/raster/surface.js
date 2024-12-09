import { BadArgumentError } from '../errors.js';
import { Rect } from '../geom/rect.js';
import { Size } from '../geom/size.js';
import { assertInteger, assertPositiveInteger } from '../util/assert.js';
import {
  arrayTypeFor,
  assertDepth,
  assertLayout,
  channelCount,
  maxValue as maxForDepth,
} from './layout.js';

/**
 * A pixel buffer.
 *
 * Samples are interleaved, rows run top to bottom and there is no padding
 * between them. Every capture the lab produces is small enough to hold whole,
 * because a twenty four by thirty six inch sheet at six hundred ppi is only
 * about three hundred megapixels and the machines have the memory.
 */
export class Surface {
  /**
   * @param {object} args
   * @param {number} args.width
   * @param {number} args.height
   * @param {import('./layout.js').LayoutName} args.layout
   * @param {import('./layout.js').Depth} args.depth
   * @param {Uint8Array|Uint16Array} args.data
   */
  constructor({ width, height, layout, depth, data }) {
    this.width = assertPositiveInteger(width, 'width');
    this.height = assertPositiveInteger(height, 'height');
    this.layout = assertLayout(layout);
    this.depth = assertDepth(depth);
    this.channels = channelCount(this.layout);

    const expected = this.width * this.height * this.channels;
    if (data.length !== expected) {
      throw new BadArgumentError('the buffer does not match the stated geometry', {
        given: data.length,
        expected,
      });
    }
    const Expected = arrayTypeFor(this.depth);
    if (!(data instanceof Expected)) {
      throw new BadArgumentError(`a ${this.depth} bit surface needs a ${Expected.name}`, {
        depth: this.depth,
        got: data.constructor.name,
      });
    }
    this.data = data;
    Object.freeze(this);
  }

  /**
   * @param {object} args
   * @param {number} args.width
   * @param {number} args.height
   * @param {import('./layout.js').LayoutName} [args.layout]
   * @param {import('./layout.js').Depth} [args.depth]
   * @param {number} [args.fill]
   * @returns {Surface}
   */
  static create({ width, height, layout = 'rgb', depth = 8, fill = 0 }) {
    const channels = channelCount(assertLayout(layout));
    const Array_ = arrayTypeFor(assertDepth(depth));
    const data = new Array_(assertPositiveInteger(width, 'width') * height * channels);
    if (fill !== 0) data.fill(fill);
    return new Surface({ width, height, layout, depth, data });
  }

  /**
   * @param {Uint8Array|Uint16Array} data
   * @param {object} args
   * @param {number} args.width
   * @param {number} args.height
   * @param {import('./layout.js').LayoutName} [args.layout]
   * @param {import('./layout.js').Depth} [args.depth]
   * @returns {Surface}
   */
  static wrap(data, { width, height, layout = 'rgb', depth = 8 }) {
    return new Surface({ width, height, layout, depth, data });
  }

  /** @returns {Size} */
  get size() {
    return new Size(this.width, this.height);
  }

  /** @returns {Rect} */
  get bounds() {
    return Rect.of(0, 0, this.width, this.height);
  }

  /** @returns {number} */
  get maxValue() {
    return maxForDepth(this.depth);
  }

  /** @returns {number} */
  get sampleCount() {
    return this.data.length;
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
   * @param {number} [channel]
   * @returns {number}
   */
  indexOf(x, y, channel = 0) {
    if (!this.contains(x, y)) {
      throw new BadArgumentError('that pixel is outside the surface', {
        x,
        y,
        width: this.width,
        height: this.height,
      });
    }
    if (channel < 0 || channel >= this.channels) {
      throw new BadArgumentError('that channel is outside the layout', {
        channel,
        channels: this.channels,
      });
    }
    return (y * this.width + x) * this.channels + channel;
  }

  /**
   * @param {number} x
   * @param {number} y
   * @param {number} [channel]
   * @returns {number}
   */
  get(x, y, channel = 0) {
    return this.data[this.indexOf(x, y, channel)];
  }

  /**
   * @param {number} x
   * @param {number} y
   * @param {number} channel
   * @param {number} value
   * @returns {void}
   */
  set(x, y, channel, value) {
    this.data[this.indexOf(x, y, channel)] = value;
  }

  /**
   * @param {number} x
   * @param {number} y
   * @returns {number[]}
   */
  getPixel(x, y) {
    const base = this.indexOf(x, y, 0);
    const out = new Array(this.channels);
    for (let c = 0; c < this.channels; c += 1) out[c] = this.data[base + c];
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
    const base = this.indexOf(x, y, 0);
    for (let c = 0; c < this.channels; c += 1) this.data[base + c] = values[c];
  }

  /**
   * A live view of one row, so a codec can write straight into it.
   *
   * @param {number} y
   * @returns {Uint8Array|Uint16Array}
   */
  row(y) {
    assertInteger(y, 'y');
    if (y < 0 || y >= this.height) {
      throw new BadArgumentError('that row is outside the surface', { y, height: this.height });
    }
    const start = y * this.rowStride;
    return this.data.subarray(start, start + this.rowStride);
  }

  /**
   * @param {number} value
   * @param {number} [channel] every channel when left out
   * @returns {void}
   */
  fill(value, channel) {
    if (channel === undefined) {
      this.data.fill(value);
      return;
    }
    if (channel < 0 || channel >= this.channels) {
      throw new BadArgumentError('that channel is outside the layout', {
        channel,
        channels: this.channels,
      });
    }
    for (let i = channel; i < this.data.length; i += this.channels) this.data[i] = value;
  }

  /** @returns {Surface} */
  clone() {
    return new Surface({
      width: this.width,
      height: this.height,
      layout: this.layout,
      depth: this.depth,
      data: this.data.slice(),
    });
  }

  /**
   * A surface of the same shape with a fresh buffer, which is what every
   * operation that cannot work in place starts from.
   *
   * @param {object} [overrides]
   * @param {number} [overrides.width]
   * @param {number} [overrides.height]
   * @param {import('./layout.js').LayoutName} [overrides.layout]
   * @param {import('./layout.js').Depth} [overrides.depth]
   * @param {number} [overrides.fill]
   * @returns {Surface}
   */
  like(overrides = {}) {
    return Surface.create({
      width: overrides.width ?? this.width,
      height: overrides.height ?? this.height,
      layout: overrides.layout ?? this.layout,
      depth: overrides.depth ?? this.depth,
      fill: overrides.fill ?? 0,
    });
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
