import { BadArgumentError } from '../errors.js';
import { assertFinite } from '../util/assert.js';
import { approxEqual, roundTo } from '../util/num.js';
import { Rect } from './rect.js';

/**
 * Four independent edge offsets in pixels.
 *
 * Bleed, safe margin and collar retention are all quoted per edge, because a
 * quadrangle sheet has a wide legend collar at the bottom and a narrow one at
 * the top, and treating them the same throws away the legend.
 */
export class Insets {
  /**
   * @param {number} top
   * @param {number} right
   * @param {number} bottom
   * @param {number} left
   */
  constructor(top, right, bottom, left) {
    this.top = assertFinite(top, 'top');
    this.right = assertFinite(right, 'right');
    this.bottom = assertFinite(bottom, 'bottom');
    this.left = assertFinite(left, 'left');
    Object.freeze(this);
  }

  /**
   * @param {number} amount
   * @returns {Insets}
   */
  static uniform(amount) {
    return new Insets(amount, amount, amount, amount);
  }

  /**
   * @param {number} vertical
   * @param {number} horizontal
   * @returns {Insets}
   */
  static symmetric(vertical, horizontal) {
    return new Insets(vertical, horizontal, vertical, horizontal);
  }

  /** @returns {Insets} */
  static zero() {
    return new Insets(0, 0, 0, 0);
  }

  /**
   * Reads the shapes a profile writes, which are a single number, a css style
   * list of one to four numbers, and a named bag.
   *
   * @param {unknown} value
   * @returns {Insets}
   */
  static from(value) {
    if (value instanceof Insets) return value;
    if (typeof value === 'number') return Insets.uniform(value);
    if (Array.isArray(value)) {
      if (value.length === 1) return Insets.uniform(value[0]);
      if (value.length === 2) return Insets.symmetric(value[0], value[1]);
      if (value.length === 4) return new Insets(value[0], value[1], value[2], value[3]);
      throw new BadArgumentError('an inset list must hold one, two or four numbers', {
        length: value.length,
      });
    }
    if (value && typeof value === 'object') {
      const bag = /** @type {Record<string, number>} */ (value);
      return new Insets(bag.top ?? 0, bag.right ?? 0, bag.bottom ?? 0, bag.left ?? 0);
    }
    throw new BadArgumentError('cannot read insets from that', { value });
  }

  /** @returns {number} */
  get horizontal() {
    return this.left + this.right;
  }

  /** @returns {number} */
  get vertical() {
    return this.top + this.bottom;
  }

  /** @returns {boolean} */
  get isZero() {
    return this.top === 0 && this.right === 0 && this.bottom === 0 && this.left === 0;
  }

  /** @returns {boolean} */
  get isUniform() {
    return this.top === this.right && this.right === this.bottom && this.bottom === this.left;
  }

  /** @returns {number} */
  get largest() {
    return Math.max(this.top, this.right, this.bottom, this.left);
  }

  /**
   * Pull the edges of a rect in by these amounts. A rect that would invert
   * collapses to zero on that axis and stays where its centre was.
   *
   * @param {Rect} rect
   * @returns {Rect}
   */
  applyTo(rect) {
    const width = rect.width - this.horizontal;
    const height = rect.height - this.vertical;
    if (width <= 0 || height <= 0) {
      const centre = rect.centre;
      return new Rect(centre.x, centre.y, 0, 0);
    }
    return new Rect(rect.x + this.left, rect.y + this.top, width, height);
  }

  /**
   * @param {Rect} rect
   * @returns {Rect}
   */
  expandFrom(rect) {
    return this.negated().applyTo(rect);
  }

  /** @returns {Insets} */
  negated() {
    return new Insets(-this.top, -this.right, -this.bottom, -this.left);
  }

  /**
   * @param {Insets} other
   * @returns {Insets}
   */
  plus(other) {
    return new Insets(
      this.top + other.top,
      this.right + other.right,
      this.bottom + other.bottom,
      this.left + other.left,
    );
  }

  /**
   * @param {number} factor
   * @returns {Insets}
   */
  times(factor) {
    assertFinite(factor, 'factor');
    return new Insets(
      this.top * factor,
      this.right * factor,
      this.bottom * factor,
      this.left * factor,
    );
  }

  /**
   * Edge by edge maximum, which is how two rules that both want a margin are
   * combined. The wider demand wins on each edge independently.
   *
   * @param {Insets} other
   * @returns {Insets}
   */
  maxWith(other) {
    return new Insets(
      Math.max(this.top, other.top),
      Math.max(this.right, other.right),
      Math.max(this.bottom, other.bottom),
      Math.max(this.left, other.left),
    );
  }

  /**
   * @param {(value: number) => number} [rounder]
   * @returns {Insets}
   */
  rounded(rounder = Math.round) {
    return new Insets(
      rounder(this.top),
      rounder(this.right),
      rounder(this.bottom),
      rounder(this.left),
    );
  }

  /**
   * @param {Insets} other
   * @param {number} [tolerance]
   * @returns {boolean}
   */
  equals(other, tolerance = 1e-9) {
    if (!(other instanceof Insets)) return false;
    return (
      approxEqual(this.top, other.top, tolerance) &&
      approxEqual(this.right, other.right, tolerance) &&
      approxEqual(this.bottom, other.bottom, tolerance) &&
      approxEqual(this.left, other.left, tolerance)
    );
  }

  /** @returns {{top: number, right: number, bottom: number, left: number}} */
  toJSON() {
    return { top: this.top, right: this.right, bottom: this.bottom, left: this.left };
  }

  /** @returns {string} */
  toString() {
    if (this.isUniform) return `${roundTo(this.top, 3)} all round`;
    const parts = [this.top, this.right, this.bottom, this.left].map((v) => roundTo(v, 3));
    return parts.join(' ');
  }
}
