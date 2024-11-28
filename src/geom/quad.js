import { GeometryError } from '../errors.js';
import { assertArrayOf } from '../util/assert.js';
import { approxEqual } from '../util/num.js';
import { Angle, meanLineAngle } from './angle.js';
import { Point, centroid } from './point.js';
import { Rect } from './rect.js';

/**
 * Four corners in order, top left then clockwise.
 *
 * A sheet on the copy stand is never an axis aligned rectangle. It is skewed,
 * and if the stand is out of level it is very slightly keystoned as well. The
 * detector reports a quad, the deskew turns the quad into an angle, and only
 * after that does anything in the tree get to talk about rectangles again.
 */
export class Quad {
  /**
   * @param {readonly Point[]} corners exactly four, top left then clockwise
   */
  constructor(corners) {
    const list = assertArrayOf(corners, (p) => Point.from(p), 'corners');
    if (list.length !== 4) {
      throw new GeometryError('a quad needs exactly four corners', { count: list.length });
    }
    this.corners = Object.freeze(list);
    Object.freeze(this);
  }

  /**
   * @param {Rect} rect
   * @returns {Quad}
   */
  static fromRect(rect) {
    return new Quad(rect.corners());
  }

  /**
   * Put four corners into the order the rest of the tree expects. The two with
   * the smaller sum of coordinates are the top pair, and within each pair the
   * smaller x is the left one. That holds for any sheet skewed by less than a
   * quarter turn, which is every sheet an operator will ever place.
   *
   * @param {readonly Point[]} corners
   * @returns {Quad}
   */
  static ordered(corners) {
    const list = assertArrayOf(corners, (p) => Point.from(p), 'corners');
    if (list.length !== 4) {
      throw new GeometryError('a quad needs exactly four corners', { count: list.length });
    }
    const middle = centroid(list);
    const sorted = [...list].sort(
      (a, b) =>
        Math.atan2(a.y - middle.y, a.x - middle.x) - Math.atan2(b.y - middle.y, b.x - middle.x),
    );
    let start = 0;
    let best = Number.POSITIVE_INFINITY;
    for (let i = 0; i < 4; i += 1) {
      const score = sorted[i].x + sorted[i].y;
      if (score < best) {
        best = score;
        start = i;
      }
    }
    return new Quad([
      sorted[start],
      sorted[(start + 1) % 4],
      sorted[(start + 2) % 4],
      sorted[(start + 3) % 4],
    ]);
  }

  /** @returns {Point} */
  get topLeft() {
    return this.corners[0];
  }

  /** @returns {Point} */
  get topRight() {
    return this.corners[1];
  }

  /** @returns {Point} */
  get bottomRight() {
    return this.corners[2];
  }

  /** @returns {Point} */
  get bottomLeft() {
    return this.corners[3];
  }

  /** @returns {Point} */
  get centre() {
    return centroid(this.corners);
  }

  /**
   * Shoelace area. Always positive, because the corner order is fixed and a
   * negative reading would only mean the caller ignored that.
   *
   * @returns {number}
   */
  get area() {
    let acc = 0;
    for (let i = 0; i < 4; i += 1) {
      const a = this.corners[i];
      const b = this.corners[(i + 1) % 4];
      acc += a.x * b.y - b.x * a.y;
    }
    return Math.abs(acc) / 2;
  }

  /** @returns {Rect} */
  get boundingRect() {
    return Rect.bounding(this.corners);
  }

  /**
   * Top, right, bottom then left, matching the corner order.
   *
   * @returns {[number, number, number, number]}
   */
  edgeLengths() {
    return [
      this.topLeft.distanceTo(this.topRight),
      this.topRight.distanceTo(this.bottomRight),
      this.bottomRight.distanceTo(this.bottomLeft),
      this.bottomLeft.distanceTo(this.topLeft),
    ];
  }

  /**
   * The direction of each edge, in the same order as edgeLengths.
   *
   * @returns {[Angle, Angle, Angle, Angle]}
   */
  edgeAngles() {
    return [
      Angle.between(this.topLeft, this.topRight),
      Angle.between(this.topRight, this.bottomRight),
      Angle.between(this.bottomRight, this.bottomLeft),
      Angle.between(this.bottomLeft, this.topLeft),
    ];
  }

  /**
   * How far the sheet is turned, taken from the two long horizontal edges
   * treated as undirected lines so the bottom edge running right to left does
   * not cancel the top one.
   *
   * @returns {Angle}
   */
  skew() {
    const [top, , bottom] = this.edgeAngles();
    return meanLineAngle([top, bottom]);
  }

  /**
   * A quad is convex when every turn goes the same way round.
   *
   * @returns {boolean}
   */
  get isConvex() {
    let sign = 0;
    for (let i = 0; i < 4; i += 1) {
      const a = this.corners[i];
      const b = this.corners[(i + 1) % 4];
      const c = this.corners[(i + 2) % 4];
      const cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x);
      if (Math.abs(cross) < 1e-12) continue;
      const current = cross > 0 ? 1 : -1;
      if (sign === 0) sign = current;
      else if (sign !== current) return false;
    }
    return true;
  }

  /**
   * True when opposite edges match in length and the corners are square, both
   * within the tolerances given. A keystoned capture fails this and the
   * operator gets told to level the stand.
   *
   * @param {object} [tolerance]
   * @param {number} [tolerance.lengthPixels]
   * @param {number} [tolerance.cornerDegrees]
   * @returns {boolean}
   */
  isRectangular({ lengthPixels = 1, cornerDegrees = 0.5 } = {}) {
    const [top, right, bottom, left] = this.edgeLengths();
    if (Math.abs(top - bottom) > lengthPixels) return false;
    if (Math.abs(right - left) > lengthPixels) return false;
    const angles = this.edgeAngles();
    for (let i = 0; i < 4; i += 1) {
      const turn = angles[i].shortestTo(angles[(i + 1) % 4]).abs().degrees;
      if (Math.abs(turn - 90) > cornerDegrees) return false;
    }
    return true;
  }

  /**
   * @param {import('./matrix.js').Matrix} matrix
   * @returns {Quad}
   */
  transformedBy(matrix) {
    return new Quad(this.corners.map((corner) => matrix.apply(corner)));
  }

  /**
   * @param {Quad} other
   * @param {number} [tolerance]
   * @returns {boolean}
   */
  equals(other, tolerance = 1e-9) {
    if (!(other instanceof Quad)) return false;
    return this.corners.every((corner, i) => corner.equals(other.corners[i], tolerance));
  }

  /**
   * @param {number} [tolerance]
   * @returns {boolean}
   */
  isDegenerate(tolerance = 1e-9) {
    return approxEqual(this.area, 0, tolerance);
  }

  /** @returns {Array<{x: number, y: number}>} */
  toJSON() {
    return this.corners.map((corner) => corner.toJSON());
  }

  /** @returns {string} */
  toString() {
    return this.corners.map(String).join(' ');
  }
}
