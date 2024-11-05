import { assertFinite, assertPositive } from './assert.js';

/**
 * Small numeric helpers. Rounding shows up everywhere in this tree and the
 * lab cares which way it goes, so nothing here uses Math.round by accident.
 */

/** Distance below which two measurements are treated as the same. */
export const DEFAULT_EPSILON = 1e-9;

/**
 * @param {number} value
 * @param {number} low
 * @param {number} high
 * @returns {number}
 */
export function clamp(value, low, high) {
  if (value < low) return low;
  if (value > high) return high;
  return value;
}

/**
 * @param {number} a
 * @param {number} b
 * @param {number} t
 * @returns {number}
 */
export function lerp(a, b, t) {
  return a + (b - a) * t;
}

/**
 * @param {number} a
 * @param {number} b
 * @param {number} [epsilon]
 * @returns {boolean}
 */
export function approxEqual(a, b, epsilon = DEFAULT_EPSILON) {
  return Math.abs(a - b) <= epsilon;
}

/**
 * Banker's rounding. Half up biases every derivative a fraction of a pixel
 * larger, which shows up as a systematic drift once you stack four operations.
 *
 * @param {number} value
 * @returns {number}
 */
export function roundHalfEven(value) {
  const floor = Math.floor(value);
  const diff = value - floor;
  if (diff > 0.5) return floor + 1;
  if (diff < 0.5) return floor;
  return floor % 2 === 0 ? floor : floor + 1;
}

/**
 * @param {number} value
 * @param {number} step
 * @returns {number}
 */
export function roundToMultiple(value, step) {
  assertPositive(step, 'step');
  return roundHalfEven(value / step) * step;
}

/**
 * @param {number} value
 * @param {number} step
 * @returns {number}
 */
export function floorToMultiple(value, step) {
  assertPositive(step, 'step');
  return Math.floor(value / step) * step;
}

/**
 * @param {number} value
 * @param {number} step
 * @returns {number}
 */
export function ceilToMultiple(value, step) {
  assertPositive(step, 'step');
  return Math.ceil(value / step) * step;
}

/**
 * @param {number} value
 * @param {number} places
 * @returns {number}
 */
export function roundTo(value, places) {
  const scale = 10 ** places;
  return roundHalfEven(value * scale) / scale;
}

/**
 * @param {readonly number[]} values
 * @returns {number}
 */
export function sum(values) {
  let total = 0;
  for (const value of values) total += value;
  return total;
}

/**
 * @param {readonly number[]} values
 * @returns {number}
 */
export function mean(values) {
  if (values.length === 0) return Number.NaN;
  return sum(values) / values.length;
}

/**
 * Linear interpolation between the bracketing samples, which is what the
 * imaging guidelines assume when they quote a percentile.
 *
 * @param {readonly number[]} values
 * @param {number} fraction between 0 and 1
 * @returns {number}
 */
export function percentile(values, fraction) {
  if (values.length === 0) return Number.NaN;
  assertFinite(fraction, 'fraction');
  const sorted = [...values].sort((a, b) => a - b);
  const position = clamp(fraction, 0, 1) * (sorted.length - 1);
  const low = Math.floor(position);
  const high = Math.ceil(position);
  if (low === high) return sorted[low];
  return lerp(sorted[low], sorted[high], position - low);
}

/**
 * @param {readonly number[]} values
 * @returns {number}
 */
export function median(values) {
  return percentile(values, 0.5);
}

/**
 * Population standard deviation. Sample deviation is never what we want here
 * because the pixels are the whole population of the patch.
 *
 * @param {readonly number[]} values
 * @returns {number}
 */
export function stdDev(values) {
  if (values.length === 0) return Number.NaN;
  const m = mean(values);
  let acc = 0;
  for (const value of values) acc += (value - m) ** 2;
  return Math.sqrt(acc / values.length);
}

/**
 * @param {number} a
 * @param {number} b
 * @returns {number}
 */
export function gcd(a, b) {
  let x = Math.abs(Math.trunc(a));
  let y = Math.abs(Math.trunc(b));
  while (y !== 0) {
    const t = y;
    y = x % y;
    x = t;
  }
  return x;
}

/**
 * @param {number} value
 * @returns {boolean}
 */
export function isEven(value) {
  return Math.abs(value % 2) < DEFAULT_EPSILON;
}
