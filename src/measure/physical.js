import { BadArgumentError } from '../errors.js';
import { Size } from '../geom/size.js';
import { assertNonEmptyString } from '../util/assert.js';
import { Sampling, toSampling } from './resolution.js';
import { SHEET_SIZES, assertUnit, convert, formatLength, sheetSize } from './units.js';

/** @typedef {import('./units.js').Unit} Unit */
/** @typedef {{width: number, height: number, unit: Unit}} PhysicalSize */

/**
 * Turning pixels into inches and back.
 *
 * Detection works in pixels and profiles are written in millimetres, and this
 * is the only place the two meet. Keeping it in one file means there is one
 * place to look when a crop comes out an eighth of an inch wrong, which has
 * happened twice and was a unit both times.
 */

/**
 * @param {Size} pixels
 * @param {Sampling|number} sampling
 * @param {Unit} [unit]
 * @returns {PhysicalSize}
 */
export function physicalSize(pixels, sampling, unit = 'in') {
  const frequency = toSampling(sampling);
  assertUnit(unit);
  return {
    width: frequency.toLength(pixels.width, unit, 'x'),
    height: frequency.toLength(pixels.height, unit, 'y'),
    unit,
  };
}

/**
 * @param {PhysicalSize} size
 * @param {Sampling|number} sampling
 * @returns {Size}
 */
export function pixelSize(size, sampling) {
  const frequency = toSampling(sampling);
  return new Size(
    frequency.toPixels(size.width, size.unit, 'x'),
    frequency.toPixels(size.height, size.unit, 'y'),
  );
}

/**
 * @param {PhysicalSize} size
 * @param {Unit} unit
 * @returns {PhysicalSize}
 */
export function inUnit(size, unit) {
  assertUnit(unit);
  if (size.unit === unit) return size;
  return {
    width: convert(size.width, size.unit, unit),
    height: convert(size.height, size.unit, unit),
    unit,
  };
}

/**
 * How far a measured sheet is from a nominal one, taken as the worse of the
 * two sides and allowing for the sheet having been placed the other way round.
 *
 * @param {PhysicalSize} measured
 * @param {PhysicalSize} nominal
 * @returns {{error: number, rotated: boolean}}
 */
export function sizeError(measured, nominal) {
  const mine = inUnit(measured, 'in');
  const theirs = inUnit(nominal, 'in');
  const upright = Math.max(
    Math.abs(mine.width - theirs.width),
    Math.abs(mine.height - theirs.height),
  );
  const turned = Math.max(
    Math.abs(mine.width - theirs.height),
    Math.abs(mine.height - theirs.width),
  );
  return upright <= turned ? { error: upright, rotated: false } : { error: turned, rotated: true };
}

/**
 * Which nominal size a measured sheet is, or nothing when it is not one of
 * them. A sheet that matches nothing is not an error: the collections hold
 * plenty of odd sizes and the work order names them by hand.
 *
 * @param {PhysicalSize} measured
 * @param {object} [options]
 * @param {number} [options.tolerance] in inches
 * @returns {{name: string, error: number, rotated: boolean}|null}
 */
export function matchNominal(measured, { tolerance = 0.5 } = {}) {
  /** @type {{name: string, error: number, rotated: boolean}|null} */
  let best = null;
  for (const name of Object.keys(SHEET_SIZES)) {
    const nominal = sheetSize(name, 'in');
    const { error, rotated } = sizeError(measured, nominal);
    if (error > tolerance) continue;
    if (best === null || error < best.error) best = { name, error, rotated };
  }
  return best;
}

/**
 * @param {string} name
 * @param {Sampling|number} sampling
 * @returns {Size}
 */
export function nominalInPixels(name, sampling) {
  assertNonEmptyString(name, 'name');
  return pixelSize(sheetSize(name, 'in'), sampling);
}

/**
 * The frequency a capture of a known object was really taken at, measured on
 * whichever axis the caller trusts more.
 *
 * @param {Size} pixels
 * @param {PhysicalSize} physical
 * @returns {Sampling}
 */
export function achievedFrom(pixels, physical) {
  if (physical.width <= 0 || physical.height <= 0) {
    throw new BadArgumentError('a physical size needs both sides above zero', { physical });
  }
  return new Sampling(
    Sampling.ppiFrom({ pixels: pixels.width, length: physical.width, unit: physical.unit }),
    Sampling.ppiFrom({ pixels: pixels.height, length: physical.height, unit: physical.unit }),
  );
}

/**
 * @param {PhysicalSize} size
 * @param {number} [places]
 * @returns {string}
 */
export function describeSize(size, places) {
  return `${formatLength(size.width, size.unit, places)} by ${formatLength(
    size.height,
    size.unit,
    places,
  )}`;
}
