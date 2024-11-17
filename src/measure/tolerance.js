import { BadArgumentError } from '../errors.js';
import { assertNonNegative, assertNonEmptyString, assertOneOf } from '../util/assert.js';
import { roundTo } from '../util/num.js';

/**
 * Asymmetric tolerance bands.
 *
 * Nearly every figure the funder cares about is quoted as a target with a band
 * around it, and the band is often lopsided. Sampling frequency may go a little
 * high but never low. Skew may go either way by the same amount. Keeping the
 * two sides apart means a check can say which side it failed on, which is the
 * first thing an operator asks.
 */

/** @typedef {'absolute'|'relative'} ToleranceKind */

const KINDS = Object.freeze(['absolute', 'relative']);

export class Tolerance {
  /**
   * @param {number} minus how far below the target is still acceptable
   * @param {number} plus how far above the target is still acceptable
   * @param {ToleranceKind} [kind]
   */
  constructor(minus, plus, kind = 'absolute') {
    this.minus = assertNonNegative(minus, 'minus');
    this.plus = assertNonNegative(plus, 'plus');
    this.kind = /** @type {ToleranceKind} */ (assertOneOf(kind, KINDS, 'kind'));
    Object.freeze(this);
  }

  /**
   * @param {number} amount
   * @param {ToleranceKind} [kind]
   * @returns {Tolerance}
   */
  static symmetric(amount, kind = 'absolute') {
    return new Tolerance(amount, amount, kind);
  }

  /**
   * @param {number} percent
   * @returns {Tolerance}
   */
  static percent(percent) {
    return new Tolerance(percent / 100, percent / 100, 'relative');
  }

  /** @returns {Tolerance} */
  static exact() {
    return new Tolerance(0, 0, 'absolute');
  }

  /**
   * @param {number} amount how far above the target is allowed
   * @returns {Tolerance}
   */
  static upOnly(amount) {
    return new Tolerance(0, amount, 'absolute');
  }

  /**
   * @param {number} amount how far below the target is allowed
   * @returns {Tolerance}
   */
  static downOnly(amount) {
    return new Tolerance(amount, 0, 'absolute');
  }

  /**
   * Reads what a profile writes. Accepts a bare number as symmetric absolute,
   * a percentage, a plus or minus form, and the two sided form.
   *
   * @param {string} text
   * @returns {Tolerance}
   */
  static parse(text) {
    assertNonEmptyString(text, 'text');
    const trimmed = text.trim();

    const both = /^\+([0-9.]+)\s*\/\s*-([0-9.]+)(%?)$/.exec(trimmed);
    if (both) {
      const kind = both[3] === '%' ? 'relative' : 'absolute';
      const scale = kind === 'relative' ? 100 : 1;
      return new Tolerance(Number(both[2]) / scale, Number(both[1]) / scale, kind);
    }

    const single = /^([+-]?)([0-9.]+)(%?)$/.exec(trimmed);
    if (single) {
      const value = Number(single[2]);
      const kind = single[3] === '%' ? 'relative' : 'absolute';
      const amount = kind === 'relative' ? value / 100 : value;
      if (single[1] === '+') return new Tolerance(0, amount, kind);
      if (single[1] === '-') return new Tolerance(amount, 0, kind);
      return new Tolerance(amount, amount, kind);
    }

    throw new BadArgumentError(`cannot read ${text} as a tolerance`, { text });
  }

  /** @returns {boolean} */
  get isSymmetric() {
    return this.minus === this.plus;
  }

  /** @returns {boolean} */
  get isExact() {
    return this.minus === 0 && this.plus === 0;
  }

  /**
   * @param {number} target
   * @returns {{low: number, high: number}}
   */
  boundsFor(target) {
    if (this.kind === 'relative') {
      const scale = Math.abs(target);
      return { low: target - scale * this.minus, high: target + scale * this.plus };
    }
    return { low: target - this.minus, high: target + this.plus };
  }

  /**
   * @param {number} target
   * @param {number} value
   * @returns {boolean}
   */
  contains(target, value) {
    const { low, high } = this.boundsFor(target);
    return value >= low && value <= high;
  }

  /**
   * How far outside the band the value sits, signed the way it failed. Zero
   * when it is inside, so a report can sort by this and put the worst first.
   *
   * @param {number} target
   * @param {number} value
   * @returns {number}
   */
  excursion(target, value) {
    const { low, high } = this.boundsFor(target);
    if (value < low) return value - low;
    if (value > high) return value - high;
    return 0;
  }

  /**
   * @param {number} target
   * @param {number} value
   * @returns {number}
   */
  clampInto(target, value) {
    const { low, high } = this.boundsFor(target);
    if (value < low) return low;
    if (value > high) return high;
    return value;
  }

  /**
   * @param {number} factor
   * @returns {Tolerance}
   */
  widened(factor) {
    assertNonNegative(factor, 'factor');
    return new Tolerance(this.minus * factor, this.plus * factor, this.kind);
  }

  /**
   * @param {Tolerance} other
   * @returns {boolean}
   */
  equals(other) {
    if (!(other instanceof Tolerance)) return false;
    return this.minus === other.minus && this.plus === other.plus && this.kind === other.kind;
  }

  /** @returns {{minus: number, plus: number, kind: string}} */
  toJSON() {
    return { minus: this.minus, plus: this.plus, kind: this.kind };
  }

  /** @returns {string} */
  toString() {
    const suffix = this.kind === 'relative' ? '%' : '';
    const scale = this.kind === 'relative' ? 100 : 1;
    const minus = roundTo(this.minus * scale, 4);
    const plus = roundTo(this.plus * scale, 4);
    if (this.isSymmetric) return `+/-${minus}${suffix}`;
    return `+${plus}${suffix}/-${minus}${suffix}`;
  }
}

/**
 * @param {unknown} value
 * @returns {Tolerance}
 */
export function toTolerance(value) {
  if (value instanceof Tolerance) return value;
  if (typeof value === 'number') return Tolerance.symmetric(value);
  if (typeof value === 'string') return Tolerance.parse(value);
  if (value && typeof value === 'object' && 'minus' in value && 'plus' in value) {
    const bag = /** @type {{minus: number, plus: number, kind?: string}} */ (value);
    return new Tolerance(
      bag.minus,
      bag.plus,
      /** @type {ToleranceKind} */ (bag.kind ?? 'absolute'),
    );
  }
  throw new BadArgumentError('cannot read a tolerance from that', { value });
}
