import { BadArgumentError } from '../errors.js';
import { assertFinite } from '../util/assert.js';
import { approxEqual, roundTo } from '../util/num.js';

/**
 * An angle stored in degrees, because every profile, work order and quality
 * report in the lab is written in degrees and converting at the edges kept
 * losing a sign somewhere in the middle.
 *
 * Positive turns clockwise on screen, matching Point.rotatedAround.
 */
export class Angle {
  /**
   * @param {number} degrees
   */
  constructor(degrees) {
    this.degrees = assertFinite(degrees, 'degrees');
    Object.freeze(this);
  }

  /**
   * @param {number} value
   * @returns {Angle}
   */
  static degrees(value) {
    return new Angle(value);
  }

  /**
   * @param {number} value
   * @returns {Angle}
   */
  static radians(value) {
    return new Angle((assertFinite(value, 'value') * 180) / Math.PI);
  }

  /** @returns {Angle} */
  static zero() {
    return new Angle(0);
  }

  /**
   * The angle of the line from a to b, which is how an edge candidate reports
   * its direction.
   *
   * @param {import('./point.js').Point} a
   * @param {import('./point.js').Point} b
   * @returns {Angle}
   */
  static between(a, b) {
    if (a.x === b.x && a.y === b.y) {
      throw new BadArgumentError('two identical points have no direction', { a: String(a) });
    }
    return Angle.radians(Math.atan2(b.y - a.y, b.x - a.x));
  }

  /** @returns {number} */
  get radians() {
    return (this.degrees * Math.PI) / 180;
  }

  /** @returns {boolean} */
  get isZero() {
    return this.degrees === 0;
  }

  /**
   * Fold into the half open turn that runs from just above minus one hundred
   * and eighty up to and including one hundred and eighty.
   *
   * @returns {Angle}
   */
  wrapped() {
    let d = this.degrees % 360;
    if (d <= -180) d += 360;
    if (d > 180) d -= 360;
    return new Angle(d === 0 ? 0 : d);
  }

  /**
   * Split into the quarter turns a lossless rotation can handle and the small
   * residual a resampler has to deal with. The residual lands in the half open
   * band from minus forty five up to and including forty five.
   *
   * @returns {{quarterTurns: number, residual: Angle}}
   */
  split() {
    const wrapped = this.wrapped().degrees;
    let quarterTurns = Math.round(wrapped / 90);
    let residual = wrapped - quarterTurns * 90;
    if (residual <= -45) {
      residual += 90;
      quarterTurns -= 1;
    }
    quarterTurns = ((quarterTurns % 4) + 4) % 4;
    return { quarterTurns, residual: new Angle(residual === 0 ? 0 : residual) };
  }

  /**
   * @param {Angle} other
   * @returns {Angle}
   */
  plus(other) {
    return new Angle(this.degrees + other.degrees);
  }

  /**
   * @param {Angle} other
   * @returns {Angle}
   */
  minus(other) {
    return new Angle(this.degrees - other.degrees);
  }

  /**
   * @param {number} factor
   * @returns {Angle}
   */
  times(factor) {
    assertFinite(factor, 'factor');
    return new Angle(this.degrees * factor);
  }

  /** @returns {Angle} */
  negated() {
    return new Angle(-this.degrees);
  }

  /** @returns {Angle} */
  abs() {
    return this.degrees < 0 ? new Angle(-this.degrees) : this;
  }

  /**
   * The shortest way round from this angle to the other, signed.
   *
   * @param {Angle} other
   * @returns {Angle}
   */
  shortestTo(other) {
    return other.minus(this).wrapped();
  }

  /**
   * @param {Angle} other
   * @param {number} [toleranceDegrees]
   * @returns {boolean}
   */
  equals(other, toleranceDegrees = 1e-9) {
    if (!(other instanceof Angle)) return false;
    return approxEqual(this.shortestTo(other).degrees, 0, toleranceDegrees);
  }

  /**
   * @param {number} toleranceDegrees
   * @returns {boolean}
   */
  isNearZero(toleranceDegrees) {
    return Math.abs(this.wrapped().degrees) <= toleranceDegrees;
  }

  /** @returns {number} */
  toJSON() {
    return this.degrees;
  }

  /** @returns {string} */
  toString() {
    return `${roundTo(this.degrees, 4)} deg`;
  }
}

/**
 * Circular mean of a set of angles. A plain average of minus one hundred and
 * seventy nine and one hundred and seventy nine gives zero, which is wrong by
 * half a turn, and edge candidates near the wrap really do land like that.
 *
 * @param {readonly Angle[]} angles
 * @returns {Angle}
 */
export function meanAngle(angles) {
  if (angles.length === 0) throw new BadArgumentError('no angles to average', {});
  let sx = 0;
  let sy = 0;
  for (const angle of angles) {
    sx += Math.cos(angle.radians);
    sy += Math.sin(angle.radians);
  }
  if (approxEqual(sx, 0, 1e-12) && approxEqual(sy, 0, 1e-12)) {
    throw new BadArgumentError('those angles cancel out and have no mean', {
      count: angles.length,
    });
  }
  return Angle.radians(Math.atan2(sy, sx)).wrapped();
}

/**
 * Angles that describe an undirected line are the same when they differ by a
 * half turn, so the mean has to be taken on the doubled angle.
 *
 * @param {readonly Angle[]} angles
 * @returns {Angle}
 */
export function meanLineAngle(angles) {
  const doubled = angles.map((a) => a.times(2));
  return meanAngle(doubled).times(0.5).wrapped();
}
