import { BadArgumentError, GeometryError } from '../errors.js';
import { assertNonNegative, assertPositive } from '../util/assert.js';
import { approxEqual, gcd, roundTo } from '../util/num.js';

/**
 * An extent in pixels. Zero is allowed because an empty crop is a real answer
 * a rule can produce, but negative is not, and the difference matters when a
 * planner subtracts one inset too many.
 */
export class Size {
  /**
   * @param {number} width
   * @param {number} height
   */
  constructor(width, height) {
    this.width = assertNonNegative(width, 'width');
    this.height = assertNonNegative(height, 'height');
    Object.freeze(this);
  }

  /**
   * @param {number} width
   * @param {number} height
   * @returns {Size}
   */
  static of(width, height) {
    return new Size(width, height);
  }

  /**
   * @param {number} side
   * @returns {Size}
   */
  static square(side) {
    return new Size(side, side);
  }

  /** @returns {Size} */
  static empty() {
    return new Size(0, 0);
  }

  /**
   * @param {unknown} value
   * @returns {Size}
   */
  static from(value) {
    if (value instanceof Size) return value;
    if (Array.isArray(value) && value.length === 2) return new Size(value[0], value[1]);
    if (value && typeof value === 'object' && 'width' in value && 'height' in value) {
      const bag = /** @type {{width: number, height: number}} */ (value);
      return new Size(bag.width, bag.height);
    }
    throw new BadArgumentError('cannot read a size from that', { value });
  }

  /** @returns {number} */
  get area() {
    return this.width * this.height;
  }

  /** @returns {boolean} */
  get isEmpty() {
    return this.width === 0 || this.height === 0;
  }

  /** @returns {boolean} */
  get isLandscape() {
    return this.width > this.height;
  }

  /** @returns {boolean} */
  get isPortrait() {
    return this.height > this.width;
  }

  /**
   * Width over height. An empty size has no aspect and asking for one is a
   * mistake worth surfacing rather than a quiet infinity.
   *
   * @returns {number}
   */
  get aspect() {
    if (this.height === 0) {
      throw new GeometryError('a size with no height has no aspect', { size: this.toJSON() });
    }
    return this.width / this.height;
  }

  /** @returns {Size} */
  get swapped() {
    return new Size(this.height, this.width);
  }

  /**
   * @param {number} factor
   * @param {number} [factorY]
   * @returns {Size}
   */
  scaled(factor, factorY = factor) {
    assertNonNegative(factor, 'factor');
    assertNonNegative(factorY, 'factorY');
    return new Size(this.width * factor, this.height * factorY);
  }

  /**
   * @param {number} dw
   * @param {number} dh
   * @returns {Size}
   */
  grown(dw, dh = dw) {
    return new Size(Math.max(0, this.width + dw), Math.max(0, this.height + dh));
  }

  /**
   * @param {(value: number) => number} [rounder]
   * @returns {Size}
   */
  rounded(rounder = Math.round) {
    return new Size(Math.max(0, rounder(this.width)), Math.max(0, rounder(this.height)));
  }

  /**
   * The largest size with this aspect that fits inside the bound.
   *
   * @param {Size} bound
   * @returns {Size}
   */
  containedIn(bound) {
    if (this.isEmpty || bound.isEmpty) return Size.empty();
    const factor = Math.min(bound.width / this.width, bound.height / this.height);
    return this.scaled(factor);
  }

  /**
   * The smallest size with this aspect that covers the bound.
   *
   * @param {Size} bound
   * @returns {Size}
   */
  covering(bound) {
    if (this.isEmpty || bound.isEmpty) return Size.empty();
    const factor = Math.max(bound.width / this.width, bound.height / this.height);
    return this.scaled(factor);
  }

  /**
   * @param {Size} other
   * @returns {boolean}
   */
  fitsInside(other) {
    return this.width <= other.width && this.height <= other.height;
  }

  /**
   * The aspect written the way a work order writes it, reduced to whole
   * numbers when the size is whole. Falls back to the ratio when it is not.
   *
   * @returns {string}
   */
  ratioLabel() {
    if (!Number.isInteger(this.width) || !Number.isInteger(this.height) || this.isEmpty) {
      return `${roundTo(this.width, 2)}:${roundTo(this.height, 2)}`;
    }
    const divisor = gcd(this.width, this.height) || 1;
    return `${this.width / divisor}:${this.height / divisor}`;
  }

  /**
   * @param {Size} other
   * @param {number} [tolerance]
   * @returns {boolean}
   */
  equals(other, tolerance = 1e-9) {
    if (!(other instanceof Size)) return false;
    return (
      approxEqual(this.width, other.width, tolerance) &&
      approxEqual(this.height, other.height, tolerance)
    );
  }

  /** @returns {{width: number, height: number}} */
  toJSON() {
    return { width: this.width, height: this.height };
  }

  /** @returns {string} */
  toString() {
    return `${roundTo(this.width, 3)}x${roundTo(this.height, 3)}`;
  }
}

/**
 * @param {Size} size
 * @param {string} name
 * @returns {Size}
 */
export function assertNotEmpty(size, name) {
  if (size.isEmpty) {
    throw new GeometryError(`${name} must have both extents above zero`, { size: size.toJSON() });
  }
  return size;
}

/**
 * @param {number} aspect
 * @param {number} width
 * @returns {Size}
 */
export function sizeFromAspect(aspect, width) {
  assertPositive(aspect, 'aspect');
  assertPositive(width, 'width');
  return new Size(width, width / aspect);
}
