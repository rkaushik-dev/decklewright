import { BadArgumentError } from '../errors.js';
import { Rect } from '../geom/rect.js';
import { Size } from '../geom/size.js';
import { assertPositiveInteger } from '../util/assert.js';

/** @typedef {import('../raster/surface.js').Surface} Surface */
/** @typedef {import('../raster/view.js').SurfaceView} SurfaceView */

/**
 * A binary mask over a capture.
 *
 * There used to be a second way in here, a threshold picked by otsu off the
 * capture's own histogram. It was the first pass at finding a sheet and it
 * only worked when the bed and the paper were the two things in the frame.
 * The moment the colour target went on the bed it started splitting the target
 * off instead. The background estimate replaced it and this is now the plain
 * threshold and nothing else.
 *
 * One byte per pixel rather than one bit. The memory is not the constraint on
 * this hardware and the bit twiddling version was measurably slower on the
 * morphology passes, which are what actually cost time.
 *
 * Set means object. The bed is zero.
 */
export class Mask {
  /**
   * @param {number} width
   * @param {number} height
   * @param {Uint8Array} [data]
   */
  constructor(width, height, data) {
    this.width = assertPositiveInteger(width, 'width');
    this.height = assertPositiveInteger(height, 'height');
    const expected = this.width * this.height;
    if (data && data.length !== expected) {
      throw new BadArgumentError('the mask buffer does not match the stated geometry', {
        given: data.length,
        expected,
      });
    }
    this.data = data ?? new Uint8Array(expected);
    Object.freeze(this);
  }

  /**
   * @param {Surface|SurfaceView} source
   * @param {number} threshold
   * @param {object} [options]
   * @param {number} [options.channel]
   * @param {boolean} [options.above] set where the sample is above the threshold
   * @returns {Mask}
   */
  static threshold(source, threshold, { channel = 0, above = true } = {}) {
    const mask = new Mask(source.width, source.height);
    let at = 0;
    for (let y = 0; y < source.height; y += 1) {
      const row = source.row(y);
      for (let x = 0; x < source.width; x += 1) {
        const value = row[x * source.channels + channel];
        mask.data[at] = (above ? value > threshold : value < threshold) ? 1 : 0;
        at += 1;
      }
    }
    return mask;
  }

  /** @returns {Size} */
  get size() {
    return new Size(this.width, this.height);
  }

  /** @returns {Rect} */
  get bounds() {
    return Rect.of(0, 0, this.width, this.height);
  }

  /**
   * @param {number} x
   * @param {number} y
   * @returns {number} one or zero, and zero outside the mask
   */
  get(x, y) {
    if (x < 0 || y < 0 || x >= this.width || y >= this.height) return 0;
    return this.data[y * this.width + x];
  }

  /**
   * @param {number} x
   * @param {number} y
   * @param {number} value
   * @returns {void}
   */
  set(x, y, value) {
    if (x < 0 || y < 0 || x >= this.width || y >= this.height) return;
    this.data[y * this.width + x] = value ? 1 : 0;
  }

  /**
   * @param {number} y
   * @returns {Uint8Array}
   */
  row(y) {
    const start = y * this.width;
    return this.data.subarray(start, start + this.width);
  }

  /** @returns {number} */
  get count() {
    let total = 0;
    for (let i = 0; i < this.data.length; i += 1) total += this.data[i];
    return total;
  }

  /** @returns {number} the share of the capture that is set */
  get coverage() {
    return this.count / this.data.length;
  }

  /** @returns {Mask} */
  clone() {
    return new Mask(this.width, this.height, this.data.slice());
  }

  /** @returns {Mask} */
  inverted() {
    const out = new Mask(this.width, this.height);
    for (let i = 0; i < this.data.length; i += 1) out.data[i] = this.data[i] ? 0 : 1;
    return out;
  }

  /**
   * @param {Mask} other
   * @returns {Mask}
   */
  and(other) {
    this.assertSameShape(other);
    const out = new Mask(this.width, this.height);
    for (let i = 0; i < this.data.length; i += 1) out.data[i] = this.data[i] & other.data[i];
    return out;
  }

  /**
   * @param {Mask} other
   * @returns {Mask}
   */
  or(other) {
    this.assertSameShape(other);
    const out = new Mask(this.width, this.height);
    for (let i = 0; i < this.data.length; i += 1) out.data[i] = this.data[i] | other.data[i];
    return out;
  }

  /**
   * @param {Mask} other
   * @returns {Mask}
   */
  without(other) {
    this.assertSameShape(other);
    const out = new Mask(this.width, this.height);
    for (let i = 0; i < this.data.length; i += 1) {
      out.data[i] = this.data[i] && !other.data[i] ? 1 : 0;
    }
    return out;
  }

  /**
   * The smallest rect holding every set pixel, or an empty rect when nothing
   * is set. This is the first cut at where the sheet is.
   *
   * @returns {Rect}
   */
  extent() {
    let left = this.width;
    let top = this.height;
    let right = -1;
    let bottom = -1;
    for (let y = 0; y < this.height; y += 1) {
      const row = this.row(y);
      for (let x = 0; x < this.width; x += 1) {
        if (!row[x]) continue;
        if (x < left) left = x;
        if (x > right) right = x;
        if (y < top) top = y;
        if (y > bottom) bottom = y;
      }
    }
    if (right < 0) return Rect.empty();
    return Rect.fromEdges(left, top, right + 1, bottom + 1);
  }

  /**
   * @param {Mask} other
   * @returns {void}
   */
  assertSameShape(other) {
    if (other.width !== this.width || other.height !== this.height) {
      throw new BadArgumentError('those two masks are not the same shape', {
        left: { width: this.width, height: this.height },
        right: { width: other.width, height: other.height },
      });
    }
  }

  /** @returns {string} */
  toString() {
    return `mask ${this.width}x${this.height}, ${this.count} set`;
  }
}
