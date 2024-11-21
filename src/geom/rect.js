import { BadArgumentError, GeometryError } from '../errors.js';
import { assertFinite, assertNonNegative } from '../util/assert.js';
import { approxEqual, roundTo } from '../util/num.js';
import { Point } from './point.js';
import { Size } from './size.js';

/**
 * An axis aligned rectangle in image space.
 *
 * The right and bottom edges are exclusive, so a rect at the origin with a
 * width of ten covers columns zero through nine. Every crop in the tree is one
 * of these, and half the bugs the lab has ever filed came from someone
 * disagreeing about that last column.
 */
export class Rect {
  /**
   * @param {number} x
   * @param {number} y
   * @param {number} width
   * @param {number} height
   */
  constructor(x, y, width, height) {
    this.x = assertFinite(x, 'x');
    this.y = assertFinite(y, 'y');
    this.width = assertNonNegative(width, 'width');
    this.height = assertNonNegative(height, 'height');
    Object.freeze(this);
  }

  /**
   * @param {number} x
   * @param {number} y
   * @param {number} width
   * @param {number} height
   * @returns {Rect}
   */
  static of(x, y, width, height) {
    return new Rect(x, y, width, height);
  }

  /** @returns {Rect} */
  static empty() {
    return new Rect(0, 0, 0, 0);
  }

  /**
   * @param {number} left
   * @param {number} top
   * @param {number} right
   * @param {number} bottom
   * @returns {Rect}
   */
  static fromEdges(left, top, right, bottom) {
    if (right < left || bottom < top) {
      throw new GeometryError('edges are inside out', { left, top, right, bottom });
    }
    return new Rect(left, top, right - left, bottom - top);
  }

  /**
   * Unlike fromEdges this accepts the corners in any order, because a drag on
   * screen can start at any corner.
   *
   * @param {Point} a
   * @param {Point} b
   * @returns {Rect}
   */
  static fromCorners(a, b) {
    const left = Math.min(a.x, b.x);
    const top = Math.min(a.y, b.y);
    return new Rect(left, top, Math.abs(a.x - b.x), Math.abs(a.y - b.y));
  }

  /**
   * @param {Point} centre
   * @param {Size} size
   * @returns {Rect}
   */
  static fromCentre(centre, size) {
    return new Rect(centre.x - size.width / 2, centre.y - size.height / 2, size.width, size.height);
  }

  /**
   * @param {Point} origin
   * @param {Size} size
   * @returns {Rect}
   */
  static fromOrigin(origin, size) {
    return new Rect(origin.x, origin.y, size.width, size.height);
  }

  /**
   * @param {readonly Point[]} points
   * @returns {Rect}
   */
  static bounding(points) {
    if (points.length === 0) throw new BadArgumentError('no points to bound', {});
    let left = Number.POSITIVE_INFINITY;
    let top = Number.POSITIVE_INFINITY;
    let right = Number.NEGATIVE_INFINITY;
    let bottom = Number.NEGATIVE_INFINITY;
    for (const p of points) {
      if (p.x < left) left = p.x;
      if (p.y < top) top = p.y;
      if (p.x > right) right = p.x;
      if (p.y > bottom) bottom = p.y;
    }
    return Rect.fromEdges(left, top, right, bottom);
  }

  /** @returns {number} */
  get left() {
    return this.x;
  }

  /** @returns {number} */
  get top() {
    return this.y;
  }

  /** @returns {number} */
  get right() {
    return this.x + this.width;
  }

  /** @returns {number} */
  get bottom() {
    return this.y + this.height;
  }

  /** @returns {Point} */
  get origin() {
    return new Point(this.x, this.y);
  }

  /** @returns {Size} */
  get size() {
    return new Size(this.width, this.height);
  }

  /** @returns {Point} */
  get centre() {
    return new Point(this.x + this.width / 2, this.y + this.height / 2);
  }

  /** @returns {number} */
  get area() {
    return this.width * this.height;
  }

  /** @returns {boolean} */
  get isEmpty() {
    return this.width === 0 || this.height === 0;
  }

  /**
   * Clockwise from the top left, which is the order the corner refinement and
   * the quad both expect.
   *
   * @returns {[Point, Point, Point, Point]}
   */
  corners() {
    return [
      new Point(this.left, this.top),
      new Point(this.right, this.top),
      new Point(this.right, this.bottom),
      new Point(this.left, this.bottom),
    ];
  }

  /**
   * @param {number} dx
   * @param {number} dy
   * @returns {Rect}
   */
  translated(dx, dy) {
    return new Rect(this.x + dx, this.y + dy, this.width, this.height);
  }

  /**
   * @param {number} factor
   * @param {number} [factorY]
   * @returns {Rect}
   */
  scaled(factor, factorY = factor) {
    return new Rect(this.x * factor, this.y * factorY, this.width * factor, this.height * factorY);
  }

  /**
   * @param {Size} size
   * @returns {Rect}
   */
  withSize(size) {
    return new Rect(this.x, this.y, size.width, size.height);
  }

  /**
   * @param {Point} centre
   * @returns {Rect}
   */
  withCentre(centre) {
    return Rect.fromCentre(centre, this.size);
  }

  /**
   * @param {Point} origin
   * @returns {Rect}
   */
  withOrigin(origin) {
    return new Rect(origin.x, origin.y, this.width, this.height);
  }

  /**
   * Positive amounts pull the edges in. A shrink that would invert the rect
   * collapses to zero on that axis rather than going negative.
   *
   * @param {number} amount
   * @param {number} [amountY]
   * @returns {Rect}
   */
  inset(amount, amountY = amount) {
    const width = Math.max(0, this.width - amount * 2);
    const height = Math.max(0, this.height - amountY * 2);
    const centre = this.centre;
    return new Rect(centre.x - width / 2, centre.y - height / 2, width, height);
  }

  /**
   * @param {number} amount
   * @param {number} [amountY]
   * @returns {Rect}
   */
  outset(amount, amountY = amount) {
    return this.inset(-amount, -amountY);
  }

  /**
   * @param {Point} point
   * @returns {boolean}
   */
  contains(point) {
    return (
      point.x >= this.left && point.x < this.right && point.y >= this.top && point.y < this.bottom
    );
  }

  /**
   * @param {Rect} other
   * @returns {boolean}
   */
  containsRect(other) {
    return (
      other.left >= this.left &&
      other.top >= this.top &&
      other.right <= this.right &&
      other.bottom <= this.bottom
    );
  }

  /**
   * @param {Rect} other
   * @returns {boolean}
   */
  intersects(other) {
    return !this.intersect(other).isEmpty;
  }

  /**
   * @param {Rect} other
   * @returns {Rect}
   */
  intersect(other) {
    const left = Math.max(this.left, other.left);
    const top = Math.max(this.top, other.top);
    const right = Math.min(this.right, other.right);
    const bottom = Math.min(this.bottom, other.bottom);
    if (right <= left || bottom <= top) return Rect.empty();
    return Rect.fromEdges(left, top, right, bottom);
  }

  /**
   * @param {Rect} other
   * @returns {Rect}
   */
  union(other) {
    if (this.isEmpty) return other;
    if (other.isEmpty) return this;
    return Rect.fromEdges(
      Math.min(this.left, other.left),
      Math.min(this.top, other.top),
      Math.max(this.right, other.right),
      Math.max(this.bottom, other.bottom),
    );
  }

  /**
   * Slide this rect until it sits inside the bound, keeping its size. If it is
   * larger than the bound on an axis it is centred on that axis instead, which
   * is what a planner wants when the crop is deliberately oversized.
   *
   * @param {Rect} bound
   * @returns {Rect}
   */
  nudgedInto(bound) {
    let x = this.x;
    let y = this.y;
    if (this.width >= bound.width) {
      x = bound.left + (bound.width - this.width) / 2;
    } else {
      x = Math.min(Math.max(x, bound.left), bound.right - this.width);
    }
    if (this.height >= bound.height) {
      y = bound.top + (bound.height - this.height) / 2;
    } else {
      y = Math.min(Math.max(y, bound.top), bound.bottom - this.height);
    }
    return new Rect(x, y, this.width, this.height);
  }

  /**
   * @param {(value: number) => number} [rounder]
   * @returns {Rect}
   */
  rounded(rounder = Math.round) {
    const left = rounder(this.left);
    const top = rounder(this.top);
    const right = rounder(this.right);
    const bottom = rounder(this.bottom);
    return Rect.fromEdges(left, top, Math.max(left, right), Math.max(top, bottom));
  }

  /**
   * Grow to whole pixels in every direction, which is what a crop has to do
   * before it can be handed to the raster layer.
   *
   * @returns {Rect}
   */
  outward() {
    return Rect.fromEdges(
      Math.floor(this.left),
      Math.floor(this.top),
      Math.ceil(this.right),
      Math.ceil(this.bottom),
    );
  }

  /**
   * @param {Rect} other
   * @param {number} [tolerance]
   * @returns {boolean}
   */
  equals(other, tolerance = 1e-9) {
    if (!(other instanceof Rect)) return false;
    return (
      approxEqual(this.x, other.x, tolerance) &&
      approxEqual(this.y, other.y, tolerance) &&
      approxEqual(this.width, other.width, tolerance) &&
      approxEqual(this.height, other.height, tolerance)
    );
  }

  /** @returns {{x: number, y: number, width: number, height: number}} */
  toJSON() {
    return { x: this.x, y: this.y, width: this.width, height: this.height };
  }

  /** @returns {string} */
  toString() {
    return `${roundTo(this.width, 3)}x${roundTo(this.height, 3)}+${roundTo(this.x, 3)}+${roundTo(
      this.y,
      3,
    )}`;
  }
}
