import { BadArgumentError } from '../errors.js';
import { assertPositive, assertOneOf } from '../util/assert.js';
import { approxEqual, roundTo } from '../util/num.js';
import { convert, fromInches, toInches, assertUnit } from './units.js';

/**
 * Sampling frequency, which the guidelines call ppi and everyone in the lab
 * calls dpi. It is the only bridge between a pixel count and a real size, so
 * every conversion in the tree goes through this object rather than a loose
 * number that might have come from anywhere.
 */

/** @typedef {'x'|'y'} Axis */

const AXES = Object.freeze(['x', 'y']);

export class Sampling {
  /**
   * @param {number} x pixels per inch across the bed
   * @param {number} [y] pixels per inch down the bed, defaults to x
   */
  constructor(x, y = x) {
    this.x = assertPositive(x, 'x');
    this.y = assertPositive(y, 'y');
    Object.freeze(this);
  }

  /**
   * @param {number} ppi
   * @returns {Sampling}
   */
  static of(ppi) {
    return new Sampling(ppi, ppi);
  }

  /**
   * Derive the frequency from a known object size and the pixels it occupied,
   * which is how the copy stand gets calibrated against the scale bar.
   *
   * @param {object} args
   * @param {number} args.pixels
   * @param {number} args.length
   * @param {import('./units.js').Unit} args.unit
   * @returns {number}
   */
  static ppiFrom({ pixels, length, unit }) {
    assertPositive(pixels, 'pixels');
    assertPositive(length, 'length');
    return pixels / toInches(length, assertUnit(unit));
  }

  /** @returns {boolean} */
  get isSquare() {
    return approxEqual(this.x, this.y, 1e-6);
  }

  /**
   * @param {Axis} axis
   * @returns {number}
   */
  along(axis) {
    return this[assertOneOf(axis, AXES, 'axis')];
  }

  /**
   * @param {number} value
   * @param {import('./units.js').Unit} unit
   * @param {Axis} [axis]
   * @returns {number}
   */
  toPixels(value, unit, axis = 'x') {
    return toInches(value, assertUnit(unit)) * this.along(axis);
  }

  /**
   * @param {number} pixels
   * @param {import('./units.js').Unit} unit
   * @param {Axis} [axis]
   * @returns {number}
   */
  toLength(pixels, unit, axis = 'x') {
    return fromInches(pixels / this.along(axis), assertUnit(unit));
  }

  /**
   * @param {import('./units.js').Unit} unit
   * @param {Axis} [axis]
   * @returns {number} samples per one of that unit
   */
  per(unit, axis = 'x') {
    return this.along(axis) * convert(1, assertUnit(unit), 'in');
  }

  /**
   * @param {number} factor
   * @returns {Sampling}
   */
  scaled(factor) {
    assertPositive(factor, 'factor');
    return new Sampling(this.x * factor, this.y * factor);
  }

  /**
   * @param {Sampling} other
   * @param {number} [tolerance] in ppi
   * @returns {boolean}
   */
  equals(other, tolerance = 1e-6) {
    if (!(other instanceof Sampling)) return false;
    return approxEqual(this.x, other.x, tolerance) && approxEqual(this.y, other.y, tolerance);
  }

  /** @returns {{x: number, y: number}} */
  toJSON() {
    return { x: this.x, y: this.y };
  }

  /** @returns {string} */
  toString() {
    if (this.isSquare) return `${roundTo(this.x, 2)} ppi`;
    return `${roundTo(this.x, 2)}x${roundTo(this.y, 2)} ppi`;
  }
}

/**
 * @param {unknown} value
 * @returns {Sampling}
 */
export function toSampling(value) {
  if (value instanceof Sampling) return value;
  if (typeof value === 'number') return Sampling.of(value);
  if (value && typeof value === 'object' && 'x' in value && 'y' in value) {
    const bag = /** @type {{x: number, y: number}} */ (value);
    return new Sampling(bag.x, bag.y);
  }
  throw new BadArgumentError('cannot read a sampling frequency from that', { value });
}

/**
 * How many pixels a length has to occupy to reach a stated frequency. Rounded
 * up, because falling one pixel short of the funder's floor fails the check.
 *
 * @param {number} length
 * @param {import('./units.js').Unit} unit
 * @param {number} ppi
 * @returns {number}
 */
export function requiredPixels(length, unit, ppi) {
  assertPositive(ppi, 'ppi');
  return Math.ceil(toInches(length, assertUnit(unit)) * ppi);
}

/**
 * The frequency an object of this size would be captured at if it filled the
 * given pixel extent. Used to say what a derivative actually achieved.
 *
 * @param {number} pixels
 * @param {number} length
 * @param {import('./units.js').Unit} unit
 * @returns {Sampling}
 */
export function achievedSampling(pixels, length, unit) {
  return Sampling.of(Sampling.ppiFrom({ pixels, length, unit }));
}
