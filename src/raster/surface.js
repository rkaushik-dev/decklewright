import { BadArgumentError } from '../errors.js';
import { assertInteger, assertPositiveInteger } from '../util/assert.js';
import { arrayTypeFor, assertDepth, assertLayout, channelCount } from './layout.js';
import { Raster } from './raster.js';

/**
 * A pixel buffer.
 *
 * Samples are interleaved, rows run top to bottom and there is no padding
 * between them. Every capture the lab produces is small enough to hold whole,
 * because a twenty four by thirty six inch sheet at six hundred ppi is only
 * about three hundred megapixels and the machines have the memory.
 */
export class Surface extends Raster {
  /**
   * @param {object} args
   * @param {number} args.width
   * @param {number} args.height
   * @param {import('./layout.js').LayoutName} args.layout
   * @param {import('./layout.js').Depth} args.depth
   * @param {Uint8Array|Uint16Array} args.data
   */
  constructor({ width, height, layout, depth, data }) {
    super(layout, depth);
    this.width = assertPositiveInteger(width, 'width');
    this.height = assertPositiveInteger(height, 'height');

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

  /** @returns {string} */
  get regionName() {
    return 'surface';
  }

  /** @returns {number} */
  get sampleCount() {
    return this.data.length;
  }

  /**
   * @param {number} x
   * @param {number} y
   * @param {number} [channel]
   * @returns {number}
   */
  indexOf(x, y, channel = 0) {
    this.assertInside(x, y);
    this.assertChannel(channel);
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
   * A live view of one row, so a codec can write straight into it.
   *
   * @param {number} y
   * @returns {Uint8Array|Uint16Array}
   */
  row(y) {
    assertInteger(y, 'y');
    this.assertInside(0, y);
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
    this.assertChannel(channel);
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
}
