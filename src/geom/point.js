import { BadArgumentError } from '../errors.js';
import { assertFinite } from '../util/assert.js';
import { approxEqual, roundTo } from '../util/num.js';

/**
 * A position in image space, x to the right and y down, measured in pixels of
 * the capture it came from. Nothing here is in physical units on purpose. A
 * point only becomes a real place once you know the sampling frequency, and
 * that conversion belongs to the caller who knows which capture this is.
 */
export class Point {
  /**
   * @param {number} x
   * @param {number} y
   */
  constructor(x, y) {
    this.x = assertFinite(x, 'x');
    this.y = assertFinite(y, 'y');
    Object.freeze(this);
  }

  /**
   * @param {number} x
   * @param {number} y
   * @returns {Point}
   */
  static of(x, y) {
    return new Point(x, y);
  }

  /** @returns {Point} */
  static origin() {
    return new Point(0, 0);
  }

  /**
   * @param {unknown} value
   * @returns {Point}
   */
  static from(value) {
    if (value instanceof Point) return value;
    if (Array.isArray(value) && value.length === 2) return new Point(value[0], value[1]);
    if (value && typeof value === 'object' && 'x' in value && 'y' in value) {
      const bag = /** @type {{x: number, y: number}} */ (value);
      return new Point(bag.x, bag.y);
    }
    throw new BadArgumentError('cannot read a point from that', { value });
  }

  /**
   * @param {Point} other
   * @returns {Point}
   */
  plus(other) {
    return new Point(this.x + other.x, this.y + other.y);
  }

  /**
   * @param {Point} other
   * @returns {Point}
   */
  minus(other) {
    return new Point(this.x - other.x, this.y - other.y);
  }

  /**
   * @param {number} factor
   * @param {number} [factorY] when the axes scale differently
   * @returns {Point}
   */
  times(factor, factorY = factor) {
    return new Point(this.x * factor, this.y * factorY);
  }

  /**
   * @param {number} dx
   * @param {number} dy
   * @returns {Point}
   */
  offset(dx, dy) {
    return new Point(this.x + dx, this.y + dy);
  }

  /**
   * @param {Point} other
   * @returns {number}
   */
  distanceTo(other) {
    return Math.hypot(this.x - other.x, this.y - other.y);
  }

  /**
   * Squared distance, for when a comparison is all that is wanted and the
   * square root would just be thrown away.
   *
   * @param {Point} other
   * @returns {number}
   */
  distanceSquaredTo(other) {
    const dx = this.x - other.x;
    const dy = this.y - other.y;
    return dx * dx + dy * dy;
  }

  /**
   * Positive angles turn clockwise on screen, which is what an operator sees
   * when they nudge a sheet on the copy stand.
   *
   * @param {Point} centre
   * @param {number} radians
   * @returns {Point}
   */
  rotatedAround(centre, radians) {
    const cos = Math.cos(radians);
    const sin = Math.sin(radians);
    const dx = this.x - centre.x;
    const dy = this.y - centre.y;
    return new Point(centre.x + dx * cos - dy * sin, centre.y + dx * sin + dy * cos);
  }

  /**
   * @param {Point} other
   * @param {number} t
   * @returns {Point}
   */
  lerpTo(other, t) {
    return new Point(this.x + (other.x - this.x) * t, this.y + (other.y - this.y) * t);
  }

  /**
   * @param {(value: number) => number} [rounder]
   * @returns {Point}
   */
  rounded(rounder = Math.round) {
    return new Point(rounder(this.x), rounder(this.y));
  }

  /**
   * @param {Point} other
   * @param {number} [tolerance] in pixels
   * @returns {boolean}
   */
  equals(other, tolerance = 1e-9) {
    if (!(other instanceof Point)) return false;
    return approxEqual(this.x, other.x, tolerance) && approxEqual(this.y, other.y, tolerance);
  }

  /** @returns {{x: number, y: number}} */
  toJSON() {
    return { x: this.x, y: this.y };
  }

  /** @returns {string} */
  toString() {
    return `(${roundTo(this.x, 3)}, ${roundTo(this.y, 3)})`;
  }
}

/**
 * @param {readonly Point[]} points
 * @returns {Point}
 */
export function centroid(points) {
  if (points.length === 0) throw new BadArgumentError('no points to average', {});
  let sx = 0;
  let sy = 0;
  for (const p of points) {
    sx += p.x;
    sy += p.y;
  }
  return new Point(sx / points.length, sy / points.length);
}
