import { Matrix } from '../../geom/matrix.js';
import { Point } from '../../geom/point.js';
import { Rect } from '../../geom/rect.js';
import { rotateQuarters } from './orient.js';
import { Sampler } from '../sampler.js';

/** @typedef {import('../surface.js').Surface} Surface */
/** @typedef {import('../../geom/angle.js').Angle} Angle */

/**
 * Rotation by an arbitrary angle.
 *
 * A deskew is nearly always under three degrees, and the quarter turns are
 * pulled out first so a sheet fed sideways does not pay for a resample it does
 * not need. What is left goes through the sampler once. Twice would be one
 * more generation of loss than the policy allows on a master.
 */

/**
 * @param {Surface} surface
 * @param {Angle} angle
 * @param {object} [options]
 * @param {boolean} [options.expand] grow the canvas so no corner is lost
 * @param {import('../sampler.js').SampleMode} [options.sample]
 * @param {import('../sampler.js').EdgeMode} [options.edge]
 * @param {number|readonly number[]} [options.background]
 * @returns {{surface: Surface, transform: Matrix, residual: Angle, quarterTurns: number}}
 */
export function rotate(surface, angle, options = {}) {
  const { expand = true, sample = 'bilinear', edge = 'constant', background = 0 } = options;

  const { quarterTurns, residual } = angle.split();
  const turned = quarterTurns === 0 ? surface : rotateQuarters(surface, quarterTurns);
  if (residual.isZero) {
    return {
      surface: turned,
      transform: Matrix.identity(),
      residual,
      quarterTurns,
    };
  }

  const centre = turned.bounds.centre;
  const spin = Matrix.rotation(residual, centre);
  const box = expand ? spin.applyToRect(turned.bounds).outward() : turned.bounds;
  const width = Math.max(1, Math.round(box.width));
  const height = Math.max(1, Math.round(box.height));

  const place = spin.then(Matrix.translation(-box.left, -box.top));
  const back = place.inverse();
  const sampler = new Sampler(turned, { edge, background });

  const out = turned.like({ width, height });
  const channels = turned.channels;
  for (let y = 0; y < height; y += 1) {
    const row = out.row(y);
    for (let x = 0; x < width; x += 1) {
      const source = back.apply(new Point(x, y));
      const base = x * channels;
      for (let c = 0; c < channels; c += 1) {
        row[base + c] = sampler.quantised(source.x, source.y, c, sample);
      }
    }
  }
  return { surface: out, transform: place, residual, quarterTurns };
}

/**
 * Take a measured skew back out. The angle handed in is the one the detector
 * reported, so the rotation applied is its negation and callers stop having to
 * remember which way round that goes.
 *
 * @param {Surface} surface
 * @param {Angle} skew
 * @param {object} [options]
 * @param {boolean} [options.expand]
 * @param {import('../sampler.js').SampleMode} [options.sample]
 * @param {import('../sampler.js').EdgeMode} [options.edge]
 * @param {number|readonly number[]} [options.background]
 * @returns {{surface: Surface, transform: Matrix, residual: Angle, quarterTurns: number}}
 */
export function deskew(surface, skew, options = {}) {
  return rotate(surface, skew.negated(), options);
}

/**
 * The canvas a rotation would need, worked out without touching a sample. The
 * planner asks this before it commits to a crop, because a deskew that grows
 * the canvas moves every coordinate the plan was written in.
 *
 * @param {Rect} bounds
 * @param {Angle} angle
 * @returns {Rect}
 */
export function rotatedBounds(bounds, angle) {
  const { residual, quarterTurns } = angle.split();
  const turned =
    quarterTurns % 2 === 0 ? bounds : Rect.of(bounds.x, bounds.y, bounds.height, bounds.width);
  if (residual.isZero) return turned;
  return Matrix.rotation(residual, turned.centre).applyToRect(turned).outward();
}
