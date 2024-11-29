import { GeometryError } from '../errors.js';
import { assertArrayOf, assertNonNegative } from '../util/assert.js';
import { Point } from './point.js';
import { Rect } from './rect.js';

/**
 * A closed polygon with at least three vertices, used for the object outline
 * the mask produces before it is reduced to a quad. Nothing here assumes
 * convexity, because a rolled drawing that has not fully relaxed produces an
 * outline with a genuine dent in one edge.
 */
export class Polygon {
  /**
   * @param {readonly Point[]} vertices
   */
  constructor(vertices) {
    const list = assertArrayOf(vertices, (p) => Point.from(p), 'vertices');
    if (list.length < 3) {
      throw new GeometryError('a polygon needs at least three vertices', { count: list.length });
    }
    this.vertices = Object.freeze(list);
    Object.freeze(this);
  }

  /**
   * @param {Rect} rect
   * @returns {Polygon}
   */
  static fromRect(rect) {
    return new Polygon(rect.corners());
  }

  /**
   * Andrew's monotone chain. Returns the hull in the same clockwise on screen
   * order the rest of the tree uses.
   *
   * @param {readonly Point[]} points
   * @returns {Polygon}
   */
  static convexHull(points) {
    const list = assertArrayOf(points, (p) => Point.from(p), 'points');
    if (list.length < 3) {
      throw new GeometryError('a hull needs at least three points', { count: list.length });
    }
    const sorted = [...list].sort((a, b) => a.x - b.x || a.y - b.y);
    /** @param {Point} o @param {Point} a @param {Point} b @returns {number} */
    const cross = (o, a, b) => (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x);

    /** @type {Point[]} */
    const lower = [];
    for (const p of sorted) {
      while (lower.length >= 2 && cross(lower[lower.length - 2], lower[lower.length - 1], p) <= 0) {
        lower.pop();
      }
      lower.push(p);
    }
    /** @type {Point[]} */
    const upper = [];
    for (let i = sorted.length - 1; i >= 0; i -= 1) {
      const p = sorted[i];
      while (upper.length >= 2 && cross(upper[upper.length - 2], upper[upper.length - 1], p) <= 0) {
        upper.pop();
      }
      upper.push(p);
    }
    lower.pop();
    upper.pop();
    const hull = lower.concat(upper);
    if (hull.length < 3) {
      throw new GeometryError('those points are collinear and have no hull', {
        count: list.length,
      });
    }
    return new Polygon(hull);
  }

  /** @returns {number} */
  get count() {
    return this.vertices.length;
  }

  /**
   * Signed twice area. Positive means the vertices run clockwise on screen,
   * where y grows downward.
   *
   * @returns {number}
   */
  get signedArea() {
    let acc = 0;
    for (let i = 0; i < this.vertices.length; i += 1) {
      const a = this.vertices[i];
      const b = this.vertices[(i + 1) % this.vertices.length];
      acc += a.x * b.y - b.x * a.y;
    }
    return acc / 2;
  }

  /** @returns {number} */
  get area() {
    return Math.abs(this.signedArea);
  }

  /** @returns {boolean} */
  get isClockwise() {
    return this.signedArea > 0;
  }

  /** @returns {number} */
  get perimeter() {
    let acc = 0;
    for (let i = 0; i < this.vertices.length; i += 1) {
      acc += this.vertices[i].distanceTo(this.vertices[(i + 1) % this.vertices.length]);
    }
    return acc;
  }

  /** @returns {Rect} */
  get boundingRect() {
    return Rect.bounding(this.vertices);
  }

  /**
   * Area weighted centroid, which is not the same as the mean of the vertices
   * once the outline has more detail on one side than the other.
   *
   * @returns {Point}
   */
  get centroid() {
    const twiceArea = this.signedArea * 2;
    if (Math.abs(twiceArea) < 1e-12) {
      throw new GeometryError('a polygon with no area has no centroid', { count: this.count });
    }
    let cx = 0;
    let cy = 0;
    for (let i = 0; i < this.vertices.length; i += 1) {
      const a = this.vertices[i];
      const b = this.vertices[(i + 1) % this.vertices.length];
      const factor = a.x * b.y - b.x * a.y;
      cx += (a.x + b.x) * factor;
      cy += (a.y + b.y) * factor;
    }
    return new Point(cx / (3 * twiceArea), cy / (3 * twiceArea));
  }

  /** @returns {Polygon} */
  reversed() {
    return new Polygon([...this.vertices].reverse());
  }

  /** @returns {Polygon} */
  clockwise() {
    return this.isClockwise ? this : this.reversed();
  }

  /**
   * Crossing number test. A point exactly on an edge is reported as inside,
   * because a mask boundary pixel belongs to the object.
   *
   * @param {Point} point
   * @returns {boolean}
   */
  contains(point) {
    let inside = false;
    for (let i = 0, j = this.vertices.length - 1; i < this.vertices.length; j = i, i += 1) {
      const a = this.vertices[i];
      const b = this.vertices[j];
      const cross = (b.x - a.x) * (point.y - a.y) - (b.y - a.y) * (point.x - a.x);
      const within =
        Math.min(a.x, b.x) - 1e-9 <= point.x &&
        point.x <= Math.max(a.x, b.x) + 1e-9 &&
        Math.min(a.y, b.y) - 1e-9 <= point.y &&
        point.y <= Math.max(a.y, b.y) + 1e-9;
      if (Math.abs(cross) < 1e-9 && within) return true;
      if (a.y > point.y !== b.y > point.y) {
        const x = ((b.x - a.x) * (point.y - a.y)) / (b.y - a.y) + a.x;
        if (point.x < x) inside = !inside;
      }
    }
    return inside;
  }

  /**
   * Ramer Douglas Peucker. The outline the mask produces has a vertex per
   * boundary pixel and the quad fitter only wants the corners.
   *
   * @param {number} tolerance in pixels
   * @returns {Polygon}
   */
  simplified(tolerance) {
    assertNonNegative(tolerance, 'tolerance');
    if (this.vertices.length <= 3 || tolerance === 0) return this;
    const kept = simplifyRun([...this.vertices, this.vertices[0]], tolerance);
    kept.pop();
    if (kept.length < 3) return this;
    return new Polygon(kept);
  }

  /**
   * @param {import('./matrix.js').Matrix} matrix
   * @returns {Polygon}
   */
  transformedBy(matrix) {
    return new Polygon(this.vertices.map((vertex) => matrix.apply(vertex)));
  }

  /** @returns {Array<{x: number, y: number}>} */
  toJSON() {
    return this.vertices.map((vertex) => vertex.toJSON());
  }

  /** @returns {string} */
  toString() {
    return `polygon(${this.count} vertices, area ${Math.round(this.area)})`;
  }
}

/**
 * @param {readonly Point[]} run
 * @param {number} tolerance
 * @returns {Point[]}
 */
function simplifyRun(run, tolerance) {
  if (run.length < 3) return [...run];
  const first = run[0];
  const last = run[run.length - 1];
  let worst = 0;
  let index = 0;
  for (let i = 1; i < run.length - 1; i += 1) {
    const distance = perpendicularDistance(run[i], first, last);
    if (distance > worst) {
      worst = distance;
      index = i;
    }
  }
  if (worst <= tolerance) return [first, last];
  const left = simplifyRun(run.slice(0, index + 1), tolerance);
  const right = simplifyRun(run.slice(index), tolerance);
  left.pop();
  return left.concat(right);
}

/**
 * @param {Point} point
 * @param {Point} a
 * @param {Point} b
 * @returns {number}
 */
function perpendicularDistance(point, a, b) {
  const dx = b.x - a.x;
  const dy = b.y - a.y;
  const length = Math.hypot(dx, dy);
  if (length < 1e-12) return point.distanceTo(a);
  return Math.abs(dy * point.x - dx * point.y + b.x * a.y - b.y * a.x) / length;
}
