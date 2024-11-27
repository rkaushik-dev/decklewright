import { GeometryError } from '../errors.js';
import { assertFinite } from '../util/assert.js';
import { approxEqual, roundTo } from '../util/num.js';
import { Angle } from './angle.js';
import { Point } from './point.js';
import { Rect } from './rect.js';

/**
 * A two by three affine transform laid out the way every drawing library does
 * it, so a matrix can be handed straight to anything that speaks that form.
 *
 *     x' = a*x + c*y + e
 *     y' = b*x + d*y + f
 *
 * The lab only ever needs translate, scale, rotate and the odd flip, but it
 * needs them composed, because a deskew is a rotation about the sheet centre
 * followed by a crop translation followed by a scale to the service size.
 */
export class Matrix {
  /**
   * @param {number} a
   * @param {number} b
   * @param {number} c
   * @param {number} d
   * @param {number} e
   * @param {number} f
   */
  constructor(a, b, c, d, e, f) {
    this.a = assertFinite(a, 'a');
    this.b = assertFinite(b, 'b');
    this.c = assertFinite(c, 'c');
    this.d = assertFinite(d, 'd');
    this.e = assertFinite(e, 'e');
    this.f = assertFinite(f, 'f');
    Object.freeze(this);
  }

  /** @returns {Matrix} */
  static identity() {
    return new Matrix(1, 0, 0, 1, 0, 0);
  }

  /**
   * @param {number} dx
   * @param {number} dy
   * @returns {Matrix}
   */
  static translation(dx, dy) {
    return new Matrix(1, 0, 0, 1, dx, dy);
  }

  /**
   * @param {number} sx
   * @param {number} [sy]
   * @returns {Matrix}
   */
  static scaling(sx, sy = sx) {
    return new Matrix(sx, 0, 0, sy, 0, 0);
  }

  /**
   * @param {Angle} angle
   * @param {Point} [about]
   * @returns {Matrix}
   */
  static rotation(angle, about) {
    const cos = Math.cos(angle.radians);
    const sin = Math.sin(angle.radians);
    const spin = new Matrix(cos, sin, -sin, cos, 0, 0);
    if (!about) return spin;
    return Matrix.translation(-about.x, -about.y)
      .then(spin)
      .then(Matrix.translation(about.x, about.y));
  }

  /**
   * @param {boolean} horizontal
   * @param {boolean} vertical
   * @returns {Matrix}
   */
  static flip(horizontal, vertical) {
    return new Matrix(horizontal ? -1 : 1, 0, 0, vertical ? -1 : 1, 0, 0);
  }

  /**
   * Compose so that the argument is applied after this one. Reading a chain
   * left to right then matches the order the operations happen in.
   *
   * @param {Matrix} next
   * @returns {Matrix}
   */
  then(next) {
    return new Matrix(
      next.a * this.a + next.c * this.b,
      next.b * this.a + next.d * this.b,
      next.a * this.c + next.c * this.d,
      next.b * this.c + next.d * this.d,
      next.a * this.e + next.c * this.f + next.e,
      next.b * this.e + next.d * this.f + next.f,
    );
  }

  /**
   * @param {Point} point
   * @returns {Point}
   */
  apply(point) {
    return new Point(
      this.a * point.x + this.c * point.y + this.e,
      this.b * point.x + this.d * point.y + this.f,
    );
  }

  /**
   * The axis aligned box that holds the transformed corners. A rotation makes
   * this larger than the source, which is exactly the growth a deskew has to
   * budget for.
   *
   * @param {Rect} rect
   * @returns {Rect}
   */
  applyToRect(rect) {
    return Rect.bounding(rect.corners().map((corner) => this.apply(corner)));
  }

  /** @returns {number} */
  get determinant() {
    return this.a * this.d - this.b * this.c;
  }

  /** @returns {boolean} */
  get isIdentity() {
    return this.equals(Matrix.identity());
  }

  /** @returns {boolean} */
  get isInvertible() {
    return Math.abs(this.determinant) > 1e-12;
  }

  /** @returns {Matrix} */
  inverse() {
    const det = this.determinant;
    if (Math.abs(det) <= 1e-12) {
      throw new GeometryError('this transform collapses the plane and cannot be undone', {
        determinant: det,
      });
    }
    const a = this.d / det;
    const b = -this.b / det;
    const c = -this.c / det;
    const d = this.a / det;
    return new Matrix(a, b, c, d, -(a * this.e + c * this.f), -(b * this.e + d * this.f));
  }

  /**
   * Pull apart into the pieces an operator understands. Shear is reported but
   * nothing in the tree produces one, so a non zero shear means a matrix came
   * from somewhere unexpected.
   *
   * @returns {{translation: Point, rotation: Angle, scaleX: number, scaleY: number, shear: number}}
   */
  decompose() {
    const scaleX = Math.hypot(this.a, this.b);
    const rotation = Angle.radians(Math.atan2(this.b, this.a));
    const shear = (this.a * this.c + this.b * this.d) / (scaleX * scaleX || 1);
    const scaleY = Math.hypot(this.c - shear * this.a, this.d - shear * this.b);
    return {
      translation: new Point(this.e, this.f),
      rotation,
      scaleX,
      scaleY,
      shear,
    };
  }

  /**
   * @param {Matrix} other
   * @param {number} [tolerance]
   * @returns {boolean}
   */
  equals(other, tolerance = 1e-9) {
    if (!(other instanceof Matrix)) return false;
    return (
      approxEqual(this.a, other.a, tolerance) &&
      approxEqual(this.b, other.b, tolerance) &&
      approxEqual(this.c, other.c, tolerance) &&
      approxEqual(this.d, other.d, tolerance) &&
      approxEqual(this.e, other.e, tolerance) &&
      approxEqual(this.f, other.f, tolerance)
    );
  }

  /** @returns {number[]} */
  toArray() {
    return [this.a, this.b, this.c, this.d, this.e, this.f];
  }

  /** @returns {{a: number, b: number, c: number, d: number, e: number, f: number}} */
  toJSON() {
    return { a: this.a, b: this.b, c: this.c, d: this.d, e: this.e, f: this.f };
  }

  /** @returns {string} */
  toString() {
    return `matrix(${this.toArray()
      .map((v) => roundTo(v, 6))
      .join(', ')})`;
  }
}

/**
 * @param {readonly Matrix[]} matrices
 * @returns {Matrix}
 */
export function compose(matrices) {
  return matrices.reduce((acc, m) => acc.then(m), Matrix.identity());
}
