import { BadArgumentError } from '../errors.js';
import { assertFinite, assertOneOf } from '../util/assert.js';
import { approxEqual } from '../util/num.js';
import { UNITS, assertUnit, convert, formatLength, parseLength, toInches } from './units.js';

/**
 * A length that remembers what unit it was written in.
 *
 * Profiles are written by curators in whatever unit the collection has always
 * used, so a bleed of 3 mm and a safe margin of 0.25 in end up in the same rule
 * set. Carrying the unit means the report can quote the figure back the way it
 * was entered instead of turning everything into inches and confusing people.
 */
export class Length {
  /**
   * @param {number} value
   * @param {import('./units.js').Unit} unit
   */
  constructor(value, unit) {
    this.value = assertFinite(value, 'value');
    this.unit = assertUnit(unit);
    Object.freeze(this);
  }

  /**
   * @param {number} value
   * @param {import('./units.js').Unit} unit
   * @returns {Length}
   */
  static of(value, unit) {
    return new Length(value, unit);
  }

  /**
   * @param {string} text
   * @returns {Length}
   */
  static parse(text) {
    const { value, unit } = parseLength(text);
    return new Length(value, unit);
  }

  /**
   * Zero is the one value that does not need a unit to be meaningful, but it
   * still gets one so the arithmetic below never has a special case.
   *
   * @param {import('./units.js').Unit} [unit]
   * @returns {Length}
   */
  static zero(unit = 'mm') {
    return new Length(0, unit);
  }

  /** @returns {number} */
  get inches() {
    return toInches(this.value, this.unit);
  }

  /** @returns {boolean} */
  get isZero() {
    return this.value === 0;
  }

  /** @returns {boolean} */
  get isNegative() {
    return this.value < 0;
  }

  /**
   * @param {import('./units.js').Unit} unit
   * @returns {Length}
   */
  to(unit) {
    if (unit === this.unit) return this;
    return new Length(convert(this.value, this.unit, unit), unit);
  }

  /**
   * The left operand keeps its unit, because in a rule the left operand is the
   * thing the curator wrote and the right operand is usually a correction.
   *
   * @param {Length} other
   * @returns {Length}
   */
  plus(other) {
    assertLength(other, 'other');
    return new Length(this.value + other.to(this.unit).value, this.unit);
  }

  /**
   * @param {Length} other
   * @returns {Length}
   */
  minus(other) {
    assertLength(other, 'other');
    return new Length(this.value - other.to(this.unit).value, this.unit);
  }

  /**
   * @param {number} factor
   * @returns {Length}
   */
  times(factor) {
    assertFinite(factor, 'factor');
    return new Length(this.value * factor, this.unit);
  }

  /**
   * @param {Length} other
   * @returns {number} how many of other fit in this
   */
  over(other) {
    assertLength(other, 'other');
    if (other.inches === 0) {
      throw new BadArgumentError('cannot divide a length by zero', { other: other.toString() });
    }
    return this.inches / other.inches;
  }

  /** @returns {Length} */
  abs() {
    return this.value < 0 ? new Length(-this.value, this.unit) : this;
  }

  /** @returns {Length} */
  negated() {
    return new Length(-this.value, this.unit);
  }

  /**
   * @param {Length} other
   * @returns {number} negative, zero or positive
   */
  compare(other) {
    assertLength(other, 'other');
    const a = this.inches;
    const b = other.inches;
    if (approxEqual(a, b, 1e-12)) return 0;
    return a < b ? -1 : 1;
  }

  /**
   * @param {Length} other
   * @param {Length} [tolerance]
   * @returns {boolean}
   */
  equals(other, tolerance) {
    if (!(other instanceof Length)) return false;
    if (!tolerance) return this.compare(other) === 0;
    return Math.abs(this.inches - other.inches) <= Math.abs(tolerance.inches);
  }

  /** @returns {{value: number, unit: string}} */
  toJSON() {
    return { value: this.value, unit: this.unit };
  }

  /** @returns {string} */
  toString() {
    return formatLength(this.value, this.unit);
  }
}

/**
 * @param {unknown} value
 * @param {string} name
 * @returns {Length}
 */
export function assertLength(value, name) {
  if (!(value instanceof Length)) {
    throw new BadArgumentError(`${name} must be a Length`, { name });
  }
  return value;
}

/**
 * Reads the three shapes a profile is allowed to write a length in, which are
 * a Length, the string an operator typed, and the pair a json file holds.
 *
 * @param {unknown} value
 * @returns {Length}
 */
export function toLength(value) {
  if (value instanceof Length) return value;
  if (typeof value === 'string') return Length.parse(value);
  if (value && typeof value === 'object' && 'value' in value && 'unit' in value) {
    const bag = /** @type {{value: number, unit: string}} */ (value);
    return new Length(bag.value, assertOneOf(bag.unit, UNITS, 'unit'));
  }
  throw new BadArgumentError('cannot read a length from that', { value });
}

/**
 * @param {readonly Length[]} lengths
 * @returns {Length}
 */
export function maxLength(lengths) {
  if (lengths.length === 0) throw new BadArgumentError('no lengths to compare', {});
  return lengths.reduce((best, item) => (item.compare(best) > 0 ? item : best));
}

/**
 * @param {readonly Length[]} lengths
 * @returns {Length}
 */
export function minLength(lengths) {
  if (lengths.length === 0) throw new BadArgumentError('no lengths to compare', {});
  return lengths.reduce((best, item) => (item.compare(best) < 0 ? item : best));
}
