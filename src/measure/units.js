import { BadArgumentError } from '../errors.js';
import { assertFinite, assertNonEmptyString } from '../util/assert.js';
import { roundTo } from '../util/num.js';

/**
 * Physical units of length. Pixels are deliberately not in here because a pixel
 * has no length until you say at what sampling frequency it was taken, and
 * mixing the two is how a service copy ends up at the wrong size.
 */

/** @typedef {'mm'|'cm'|'m'|'in'|'pt'|'pc'} Unit */

/** Every unit this tree understands, in the order a report lists them. */
export const UNITS = Object.freeze(['mm', 'cm', 'm', 'in', 'pt', 'pc']);

/** Inches per one of each unit. The inch is the base because ppi is. */
const INCHES_PER = Object.freeze({
  mm: 1 / 25.4,
  cm: 1 / 2.54,
  m: 1 / 0.0254,
  in: 1,
  pt: 1 / 72,
  pc: 1 / 6,
});

/** How many decimal places a report keeps for each unit. */
const REPORT_PLACES = Object.freeze({ mm: 2, cm: 3, m: 5, in: 4, pt: 2, pc: 3 });

/**
 * @param {unknown} unit
 * @returns {Unit}
 */
export function assertUnit(unit) {
  if (typeof unit !== 'string' || !(unit in INCHES_PER)) {
    throw new BadArgumentError(`unknown unit ${String(unit)}`, { unit, known: [...UNITS] });
  }
  return /** @type {Unit} */ (unit);
}

/**
 * @param {unknown} unit
 * @returns {boolean}
 */
export function isUnit(unit) {
  return typeof unit === 'string' && unit in INCHES_PER;
}

/**
 * @param {number} value
 * @param {Unit} unit
 * @returns {number}
 */
export function toInches(value, unit) {
  assertFinite(value, 'value');
  return value * INCHES_PER[assertUnit(unit)];
}

/**
 * @param {number} inches
 * @param {Unit} unit
 * @returns {number}
 */
export function fromInches(inches, unit) {
  assertFinite(inches, 'inches');
  return inches / INCHES_PER[assertUnit(unit)];
}

/**
 * @param {number} value
 * @param {Unit} from
 * @param {Unit} to
 * @returns {number}
 */
export function convert(value, from, to) {
  if (from === to) return assertFinite(value, 'value');
  return fromInches(toInches(value, from), to);
}

/**
 * Accepts what an operator types into a profile, which is a number then a unit
 * with optional space. A bare number is an error, not an assumed millimetre.
 *
 * @param {string} text
 * @returns {{value: number, unit: Unit}}
 */
export function parseLength(text) {
  assertNonEmptyString(text, 'text');
  const match = /^\s*([+-]?(?:\d+\.?\d*|\.\d+))\s*([a-z]+)\s*$/i.exec(text);
  if (!match) {
    throw new BadArgumentError(`cannot read ${text} as a length`, { text });
  }
  const value = Number.parseFloat(match[1]);
  const unit = assertUnit(match[2].toLowerCase());
  return { value, unit };
}

/**
 * @param {number} value
 * @param {Unit} unit
 * @param {number} [places]
 * @returns {string}
 */
export function formatLength(value, unit, places) {
  assertUnit(unit);
  const digits = places ?? REPORT_PLACES[unit];
  return `${roundTo(value, digits).toFixed(Math.max(0, digits))} ${unit}`;
}

/**
 * The nominal sizes the lab quotes on a work order. Anything outside this table
 * is entered as an explicit measurement instead.
 */
export const SHEET_SIZES = Object.freeze({
  quad75: { width: 22, height: 27, unit: 'in' },
  quad15: { width: 21, height: 27, unit: 'in' },
  atlasPlate: { width: 17, height: 14, unit: 'in' },
  platBook: { width: 14, height: 17, unit: 'in' },
  archD: { width: 24, height: 36, unit: 'in' },
  archE: { width: 36, height: 48, unit: 'in' },
});

/**
 * @param {string} name
 * @param {Unit} [unit]
 * @returns {{width: number, height: number, unit: Unit}}
 */
export function sheetSize(name, unit = 'in') {
  assertNonEmptyString(name, 'name');
  const entry = SHEET_SIZES[/** @type {keyof typeof SHEET_SIZES} */ (name)];
  if (!entry) {
    throw new BadArgumentError(`no nominal size called ${name}`, {
      name,
      known: Object.keys(SHEET_SIZES),
    });
  }
  const from = /** @type {Unit} */ (entry.unit);
  return {
    width: convert(entry.width, from, unit),
    height: convert(entry.height, from, unit),
    unit: assertUnit(unit),
  };
}
