import { DetectionError } from '../errors.js';
import { Angle } from '../geom/angle.js';
import { Point } from '../geom/point.js';
import { Polygon } from '../geom/polygon.js';
import { Quad } from '../geom/quad.js';
import { boundary } from './morphology.js';

/** @typedef {import('./mask.js').Mask} Mask */

/**
 * The rectangle that actually holds the sheet.
 *
 * The projection profiles give an axis aligned box, which is bigger than the
 * sheet whenever the sheet is turned. The minimum area rectangle over the
 * convex hull gives the real one, and its orientation is the skew, measured
 * from the whole outline rather than from four separately fitted sides.
 *
 * Rotating calipers: the minimum area rectangle always has a side flush with
 * a hull edge, so trying every hull edge is enough and there are rarely more
 * than a couple of dozen.
 */

/**
 * @param {Mask} mask
 * @param {object} [options]
 * @param {number} [options.step] take every nth boundary pixel
 * @returns {Polygon}
 */
export function outlineHull(mask, { step = 1 } = {}) {
  const edge = boundary(mask);
  /** @type {Point[]} */
  const points = [];
  let seen = 0;
  for (let y = 0; y < edge.height; y += 1) {
    const row = edge.row(y);
    for (let x = 0; x < edge.width; x += 1) {
      if (!row[x]) continue;
      if (seen % step === 0) points.push(new Point(x, y));
      seen += 1;
    }
  }
  if (points.length < 3) {
    throw new DetectionError('there is no outline to fit a quad to', { points: points.length });
  }
  return Polygon.convexHull(points);
}

/**
 * @param {Polygon} hull
 * @returns {{quad: Quad, angle: Angle, area: number}}
 */
export function minimumAreaRect(hull) {
  const vertices = hull.vertices;
  let best = null;

  for (let i = 0; i < vertices.length; i += 1) {
    const a = vertices[i];
    const b = vertices[(i + 1) % vertices.length];
    const dx = b.x - a.x;
    const dy = b.y - a.y;
    const length = Math.hypot(dx, dy);
    if (length < 1e-9) continue;
    const ux = dx / length;
    const uy = dy / length;

    let minAlong = Number.POSITIVE_INFINITY;
    let maxAlong = Number.NEGATIVE_INFINITY;
    let minAcross = Number.POSITIVE_INFINITY;
    let maxAcross = Number.NEGATIVE_INFINITY;
    for (const p of vertices) {
      const along = p.x * ux + p.y * uy;
      const across = -p.x * uy + p.y * ux;
      if (along < minAlong) minAlong = along;
      if (along > maxAlong) maxAlong = along;
      if (across < minAcross) minAcross = across;
      if (across > maxAcross) maxAcross = across;
    }

    const area = (maxAlong - minAlong) * (maxAcross - minAcross);
    if (best === null || area < best.area) {
      best = { area, ux, uy, minAlong, maxAlong, minAcross, maxAcross };
    }
  }

  if (best === null) {
    throw new DetectionError('the hull has no usable edge', { vertices: vertices.length });
  }

  /**
   * @param {number} along
   * @param {number} across
   * @returns {Point}
   */
  const toImage = (along, across) =>
    new Point(along * best.ux - across * best.uy, along * best.uy + across * best.ux);

  const corners = [
    toImage(best.minAlong, best.minAcross),
    toImage(best.maxAlong, best.minAcross),
    toImage(best.maxAlong, best.maxAcross),
    toImage(best.minAlong, best.maxAcross),
  ];

  return {
    quad: Quad.ordered(corners),
    angle: foldToBand(Angle.radians(Math.atan2(best.uy, best.ux))),
    area: best.area,
  };
}

/**
 * A rectangle has four equally good orientations, so the reported skew is
 * folded into the band from minus forty five up to forty five. Anything wider
 * than that is a quarter turn, which is a separate decision the work order
 * makes rather than something the detector guesses.
 *
 * @param {Angle} angle
 * @returns {Angle}
 */
export function foldToBand(angle) {
  const wrapped = angle.wrapped().degrees;
  const folded = ((((wrapped + 45) % 90) + 90) % 90) - 45;
  return Angle.degrees(folded === -45 ? 45 : folded);
}

/**
 * @param {Mask} mask
 * @param {object} [options]
 * @param {number} [options.step]
 * @returns {{quad: Quad, angle: Angle, hull: Polygon, fill: number}}
 */
export function fitQuad(mask, options = {}) {
  const hull = outlineHull(mask, options);
  const { quad, area } = minimumAreaRect(hull);
  const covered = mask.count;
  return {
    quad,
    angle: quadSkew(quad),
    hull,
    fill: area === 0 ? 0 : covered / area,
  };
}

/**
 * The orientation of a quad, taken from its longer pair of sides, folded into
 * the same band as everything else here.
 *
 * @param {Quad} quad
 * @returns {Angle}
 */
export function quadSkew(quad) {
  const [top, right, bottom, left] = quad.edgeLengths();
  const horizontal = (top + bottom) / 2;
  const vertical = (right + left) / 2;
  const angles = quad.edgeAngles();
  const chosen = horizontal >= vertical ? angles[0] : angles[1];
  return foldToBand(chosen);
}
