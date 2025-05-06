import { DetectionError } from '../errors.js';
import { Point } from '../geom/point.js';
import { Quad } from '../geom/quad.js';
import { distanceToLine } from './edges.js';

/** @typedef {import('./edges.js').Line} Line */
/** @typedef {import('./edges.js').Side} Side */

/**
 * Corners, taken as the crossing of two fitted sides.
 *
 * The minimum area rectangle is a good answer and it is not the best one. It
 * is forced to be a rectangle, so a sheet that is very slightly keystoned gets
 * a corner placed a pixel or two off the paper. Crossing the two lines that
 * were fitted to the sides puts each corner where the paper actually is, and
 * how far that moves it is a useful reading in its own right.
 */

/**
 * @param {Line} a
 * @param {Line} b
 * @returns {Point}
 */
export function intersect(a, b) {
  const ca = Math.cos(a.angle.radians);
  const sa = Math.sin(a.angle.radians);
  const cb = Math.cos(b.angle.radians);
  const sb = Math.sin(b.angle.radians);

  // each line is written as its normal dotted with a point equals an offset
  const na = [-sa, ca];
  const nb = [-sb, cb];
  const oa = na[0] * a.through.x + na[1] * a.through.y;
  const ob = nb[0] * b.through.x + nb[1] * b.through.y;

  const determinant = na[0] * nb[1] - na[1] * nb[0];
  if (Math.abs(determinant) < 1e-9) {
    throw new DetectionError('those two sides are parallel and never meet', {
      first: a.angle.degrees,
      second: b.angle.degrees,
    });
  }
  return new Point(
    (oa * nb[1] - na[1] * ob) / determinant,
    (na[0] * ob - oa * nb[0]) / determinant,
  );
}

/**
 * @param {Record<Side, Line>} lines
 * @returns {Quad}
 */
export function cornersFromLines(lines) {
  return Quad.ordered([
    intersect(lines.top, lines.left),
    intersect(lines.top, lines.right),
    intersect(lines.bottom, lines.right),
    intersect(lines.bottom, lines.left),
  ]);
}

/**
 * How far each corner moved when the lines were crossed. A sheet that is flat
 * and square moves by well under a pixel. Anything above a couple of pixels
 * means the stand is out of level and the operator needs to know.
 *
 * @param {Quad} before
 * @param {Quad} after
 * @returns {{moves: number[], worst: number, mean: number}}
 */
export function cornerShift(before, after) {
  const moves = before.corners.map((corner, i) => corner.distanceTo(after.corners[i]));
  const worst = moves.reduce((best, value) => Math.max(best, value), 0);
  const mean = moves.reduce((total, value) => total + value, 0) / moves.length;
  return { moves, worst, mean };
}

/**
 * How well a fitted side describes the points it was fitted to, turned into a
 * number between zero and one so a report can print it next to the others.
 *
 * @param {Line} line
 * @param {object} [options]
 * @param {number} [options.tolerance] residual at which confidence reaches a half
 * @returns {number}
 */
export function lineConfidence(line, { tolerance = 1 } = {}) {
  if (line.points.length < 2) return 0;
  return 1 / (1 + line.residual / tolerance);
}

/**
 * @param {Record<Side, Line>} lines
 * @param {object} [options]
 * @param {number} [options.tolerance]
 * @returns {number} the weakest of the four
 */
export function quadConfidence(lines, options) {
  return Math.min(
    lineConfidence(lines.top, options),
    lineConfidence(lines.right, options),
    lineConfidence(lines.bottom, options),
    lineConfidence(lines.left, options),
  );
}

/**
 * Take the rectangle the calipers produced and let each corner move to where
 * the two fitted sides cross, but only as far as the limit allows. A wild
 * crossing means one of the sides was fitted to something that was not a side,
 * and in that case the rectangle is the better answer.
 *
 * @param {Quad} quad
 * @param {Record<Side, Line>} lines
 * @param {object} [options]
 * @param {number} [options.limit] how far a corner may move, in pixels
 * @returns {{quad: Quad, shift: {moves: number[], worst: number, mean: number}, clamped: boolean}}
 */
export function refineCorners(quad, lines, { limit = 8 } = {}) {
  let crossed;
  try {
    crossed = cornersFromLines(lines);
  } catch {
    return { quad, shift: cornerShift(quad, quad), clamped: true };
  }

  const shift = cornerShift(quad, crossed);
  if (shift.worst > limit) return { quad, shift, clamped: true };
  return { quad: crossed, shift, clamped: false };
}

/**
 * The largest distance from any corner to the side it is supposed to sit on.
 * Zero for a rectangle, and a direct measure of keystone for anything else.
 *
 * @param {Quad} quad
 * @param {Record<Side, Line>} lines
 * @returns {number}
 */
export function cornerResidual(quad, lines) {
  const pairs = [
    [quad.topLeft, lines.top],
    [quad.topRight, lines.top],
    [quad.topRight, lines.right],
    [quad.bottomRight, lines.right],
    [quad.bottomRight, lines.bottom],
    [quad.bottomLeft, lines.bottom],
    [quad.bottomLeft, lines.left],
    [quad.topLeft, lines.left],
  ];
  let worst = 0;
  for (const [corner, line] of pairs) {
    const distance = Math.abs(
      distanceToLine(/** @type {Line} */ (line), /** @type {Point} */ (corner)),
    );
    if (distance > worst) worst = distance;
  }
  return worst;
}
