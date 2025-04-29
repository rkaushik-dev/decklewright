import { DetectionError } from '../errors.js';
import { Angle } from '../geom/angle.js';
import { Point, centroid } from '../geom/point.js';
import { assertOneOf } from '../util/assert.js';
import { median } from '../util/num.js';

/** @typedef {import('./mask.js').Mask} Mask */
/** @typedef {'top'|'right'|'bottom'|'left'} Side */
/** @typedef {{through: Point, angle: Angle, residual: number, points: Point[]}} Line */

export const SIDES = Object.freeze(['top', 'right', 'bottom', 'left']);

/**
 * Finding the four sides of a sheet.
 *
 * Scan inward from each edge of the capture and record where the mask first
 * turns on. That gives one point per scan line, and the four sets of points
 * are the four sides. A tear or a dog eared corner puts a few points well off
 * the line, so the fit throws out the worst tenth before it commits.
 */

/**
 * Scanning inward crosses a corner at each end of the run, so the first and
 * last few points belong to the neighbouring side rather than this one. The
 * margin drops a share from each end before anything is fitted, which is far
 * cheaper than trying to fit through them.
 *
 * @param {Point[]} points
 * @param {number} margin
 * @returns {Point[]}
 */
function trimEnds(points, margin) {
  if (margin <= 0 || points.length < 8) return points;
  const cut = Math.floor(points.length * margin);
  if (cut === 0) return points;
  return points.slice(cut, points.length - cut);
}

/**
 * @param {Mask} mask
 * @param {Side} side
 * @param {object} [options]
 * @param {number} [options.step] scan every nth line
 * @param {number} [options.margin] share dropped from each end of the run
 * @returns {Point[]}
 */
export function edgePoints(mask, side, { step = 1, margin = 0 } = {}) {
  assertOneOf(side, SIDES, 'side');
  /** @type {Point[]} */
  const points = [];

  if (side === 'top' || side === 'bottom') {
    const downward = side === 'top';
    for (let x = 0; x < mask.width; x += step) {
      for (let k = 0; k < mask.height; k += 1) {
        const y = downward ? k : mask.height - 1 - k;
        if (mask.get(x, y)) {
          points.push(new Point(x, y));
          break;
        }
      }
    }
    return trimEnds(points, margin);
  }

  const rightward = side === 'left';
  for (let y = 0; y < mask.height; y += step) {
    for (let k = 0; k < mask.width; k += 1) {
      const x = rightward ? k : mask.width - 1 - k;
      if (mask.get(x, y)) {
        points.push(new Point(x, y));
        break;
      }
    }
  }
  return trimEnds(points, margin);
}

/**
 * Total least squares. An ordinary regression of y on x cannot describe a
 * vertical side, and two of the four sides are vertical.
 *
 * @param {readonly Point[]} points
 * @returns {Line}
 */
export function fitLine(points) {
  if (points.length < 2) {
    throw new DetectionError('a line needs at least two points', { count: points.length });
  }
  const middle = centroid(points);
  let sxx = 0;
  let syy = 0;
  let sxy = 0;
  for (const p of points) {
    const dx = p.x - middle.x;
    const dy = p.y - middle.y;
    sxx += dx * dx;
    syy += dy * dy;
    sxy += dx * dy;
  }
  const angle = Angle.radians(0.5 * Math.atan2(2 * sxy, sxx - syy));

  const cos = Math.cos(angle.radians);
  const sin = Math.sin(angle.radians);
  let acc = 0;
  for (const p of points) {
    const offset = -(p.x - middle.x) * sin + (p.y - middle.y) * cos;
    acc += offset * offset;
  }
  return {
    through: middle,
    angle,
    residual: Math.sqrt(acc / points.length),
    points: [...points],
  };
}

/**
 * @param {Line} line
 * @param {Point} point
 * @returns {number} signed distance, positive below the line as drawn
 */
export function distanceToLine(line, point) {
  const cos = Math.cos(line.angle.radians);
  const sin = Math.sin(line.angle.radians);
  return -(point.x - line.through.x) * sin + (point.y - line.through.y) * cos;
}

/**
 * Fit, drop the worst offenders, fit again, and repeat a few times.
 *
 * One pass is not enough and that took a while to accept. The points scanned
 * inward from one side of a turned sheet belong to two edges, not one, because
 * the scan crosses the corner. The wrong edge can be a sixth of the run, and a
 * single trim of a tenth leaves enough of it to pull the angle the wrong way.
 * Three trims converge on the long edge and the residual says so.
 *
 * @param {readonly Point[]} points
 * @param {object} [options]
 * @param {number} [options.drop] share of points to discard each pass
 * @param {number} [options.passes]
 * @returns {Line}
 */
export function fitLineRobust(points, { drop = 0.15, passes = 3 } = {}) {
  let best = fitLine(points);
  if (points.length < 8 || drop <= 0) return best;

  const floor = Math.max(2, Math.ceil(points.length * 0.4));
  let current = [...points];
  for (let pass = 0; pass < passes; pass += 1) {
    const line = fitLine(current);
    const distances = current.map((p) => Math.abs(distanceToLine(line, p)));
    const cutoff = Math.max(1e-9, quantile(distances, 1 - drop));
    const kept = current.filter((p, i) => distances[i] <= cutoff);
    if (kept.length < floor || kept.length === current.length) {
      best = line;
      break;
    }
    current = kept;
    best = fitLine(current);
  }
  return best;
}

/**
 * @param {readonly number[]} values
 * @param {number} fraction
 * @returns {number}
 */
function quantile(values, fraction) {
  const sorted = [...values].sort((a, b) => a - b);
  if (sorted.length === 0) return 0;
  const at = Math.min(sorted.length - 1, Math.max(0, Math.round(fraction * (sorted.length - 1))));
  return sorted[at];
}

/**
 * @param {Mask} mask
 * @param {object} [options]
 * @param {number} [options.step]
 * @param {number} [options.drop]
 * @param {number} [options.margin]
 * @returns {Record<Side, Line>}
 */
export function edgeLines(mask, options = {}) {
  /** @type {Partial<Record<Side, Line>>} */
  const out = {};
  const scan = { margin: 0.2, ...options };
  for (const side of SIDES) {
    const points = edgePoints(mask, side, scan);
    if (points.length < 2) {
      throw new DetectionError(`the ${side} side has no edge to fit`, { side });
    }
    out[side] = fitLineRobust(points, scan);
  }
  return /** @type {Record<Side, Line>} */ (out);
}

/**
 * The skew the four sides agree on. Horizontal sides report their own angle
 * and vertical sides report theirs less a quarter turn, and the median of the
 * four is used rather than the mean so one bad side cannot drag it.
 *
 * @param {Record<Side, Line>} lines
 * @returns {Angle}
 */
export function skewFromLines(lines) {
  const readings = [
    lines.top.angle.wrapped().degrees,
    lines.bottom.angle.wrapped().degrees,
    normaliseVertical(lines.left.angle.wrapped().degrees),
    normaliseVertical(lines.right.angle.wrapped().degrees),
  ].map((value) => ((value + 45) % 90) - 45);
  return Angle.degrees(median(readings));
}

/**
 * @param {number} degrees
 * @returns {number}
 */
function normaliseVertical(degrees) {
  return degrees > 0 ? degrees - 90 : degrees + 90;
}
