import { BadArgumentError } from '../errors.js';

/** @typedef {import('./surface.js').Surface} Surface */
/** @typedef {import('./view.js').SurfaceView} SurfaceView */
/** @typedef {import('../geom/rect.js').Rect} Rect */

/**
 * Summed area tables.
 *
 * The bed background estimate walks a window over the whole capture, and the
 * illumination check walks a grid of patches over it again. Without one of
 * these each pass is quadratic in the window size, and on a three hundred
 * megapixel master that is the difference between a second and a lunch break.
 *
 * The tables are one row and one column larger than the source so the corner
 * lookups never need a bounds test.
 */
export class IntegralImage {
  /**
   * @param {Float64Array} sums
   * @param {Float64Array} squares
   * @param {number} width
   * @param {number} height
   */
  constructor(sums, squares, width, height) {
    this.sums = sums;
    this.squares = squares;
    this.width = width;
    this.height = height;
    Object.freeze(this);
  }

  /**
   * @param {Surface|SurfaceView} source
   * @param {number} [channel]
   * @returns {IntegralImage}
   */
  static from(source, channel = 0) {
    if (channel < 0 || channel >= source.channels) {
      throw new BadArgumentError('that channel is outside the layout', {
        channel,
        channels: source.channels,
      });
    }
    const stride = source.width + 1;
    const sums = new Float64Array(stride * (source.height + 1));
    const squares = new Float64Array(stride * (source.height + 1));

    for (let y = 0; y < source.height; y += 1) {
      const row = source.row(y);
      let runningSum = 0;
      let runningSquare = 0;
      const here = (y + 1) * stride;
      const above = y * stride;
      for (let x = 0; x < source.width; x += 1) {
        const value = row[x * source.channels + channel];
        runningSum += value;
        runningSquare += value * value;
        sums[here + x + 1] = sums[above + x + 1] + runningSum;
        squares[here + x + 1] = squares[above + x + 1] + runningSquare;
      }
    }
    return new IntegralImage(sums, squares, source.width, source.height);
  }

  /**
   * @param {Rect} rect
   * @returns {void}
   */
  assertInside(rect) {
    if (
      rect.left < 0 ||
      rect.top < 0 ||
      rect.right > this.width ||
      rect.bottom > this.height ||
      !Number.isInteger(rect.left) ||
      !Number.isInteger(rect.top) ||
      !Number.isInteger(rect.right) ||
      !Number.isInteger(rect.bottom)
    ) {
      throw new BadArgumentError('that window is not a whole rect inside the table', {
        rect: rect.toJSON(),
        width: this.width,
        height: this.height,
      });
    }
  }

  /**
   * @param {Float64Array} table
   * @param {Rect} rect
   * @returns {number}
   */
  corners(table, rect) {
    const stride = this.width + 1;
    const top = rect.top * stride;
    const bottom = rect.bottom * stride;
    return (
      table[bottom + rect.right] -
      table[bottom + rect.left] -
      table[top + rect.right] +
      table[top + rect.left]
    );
  }

  /**
   * @param {Rect} rect
   * @returns {number}
   */
  sum(rect) {
    this.assertInside(rect);
    return this.corners(this.sums, rect);
  }

  /**
   * @param {Rect} rect
   * @returns {number}
   */
  sumOfSquares(rect) {
    this.assertInside(rect);
    return this.corners(this.squares, rect);
  }

  /**
   * @param {Rect} rect
   * @returns {number}
   */
  mean(rect) {
    const area = rect.area;
    if (area === 0) return Number.NaN;
    return this.sum(rect) / area;
  }

  /**
   * Mean and deviation of a window in constant time. The deviation comes out
   * of the two tables rather than a second pass, and a tiny negative variance
   * from cancellation is clamped rather than turned into a not a number.
   *
   * @param {Rect} rect
   * @returns {{mean: number, variance: number, stdDev: number, count: number}}
   */
  window(rect) {
    const count = rect.area;
    if (count === 0) {
      return { mean: Number.NaN, variance: Number.NaN, stdDev: Number.NaN, count: 0 };
    }
    const total = this.sum(rect);
    const squares = this.sumOfSquares(rect);
    const mean = total / count;
    const variance = Math.max(0, squares / count - mean * mean);
    return { mean, variance, stdDev: Math.sqrt(variance), count };
  }
}

/**
 * @param {Surface|SurfaceView} source
 * @param {number} [channel]
 * @returns {IntegralImage}
 */
export function integralOf(source, channel = 0) {
  return IntegralImage.from(source, channel);
}
