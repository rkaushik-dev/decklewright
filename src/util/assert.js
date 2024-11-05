import { BadArgumentError } from '../errors.js';

/**
 * Argument guards. Every public entry point in the tree runs its inputs through
 * these so a bad batch fails at the call that made the mistake rather than four
 * stages later in a resampler.
 */

/**
 * @param {unknown} condition
 * @param {string} message
 * @param {Record<string, unknown>} [details]
 * @returns {asserts condition}
 */
export function assert(condition, message, details) {
  if (!condition) throw new BadArgumentError(message, details);
}

/**
 * @param {unknown} value
 * @param {string} name
 * @returns {number}
 */
export function assertFinite(value, name) {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    throw new BadArgumentError(`${name} must be a finite number`, { name, value });
  }
  return value;
}

/**
 * @param {unknown} value
 * @param {string} name
 * @returns {number}
 */
export function assertInteger(value, name) {
  assertFinite(value, name);
  if (!Number.isInteger(value)) {
    throw new BadArgumentError(`${name} must be a whole number`, { name, value });
  }
  return /** @type {number} */ (value);
}

/**
 * @param {unknown} value
 * @param {string} name
 * @returns {number}
 */
export function assertPositive(value, name) {
  assertFinite(value, name);
  if (!(/** @type {number} */ (value) > 0)) {
    throw new BadArgumentError(`${name} must be greater than zero`, { name, value });
  }
  return /** @type {number} */ (value);
}

/**
 * @param {unknown} value
 * @param {string} name
 * @returns {number}
 */
export function assertNonNegative(value, name) {
  assertFinite(value, name);
  if (/** @type {number} */ (value) < 0) {
    throw new BadArgumentError(`${name} must not be negative`, { name, value });
  }
  return /** @type {number} */ (value);
}

/**
 * @param {unknown} value
 * @param {string} name
 * @returns {number}
 */
export function assertPositiveInteger(value, name) {
  assertInteger(value, name);
  if (/** @type {number} */ (value) <= 0) {
    throw new BadArgumentError(`${name} must be a whole number above zero`, { name, value });
  }
  return /** @type {number} */ (value);
}

/**
 * Inclusive on both ends, because every range in the lab is quoted that way.
 *
 * @param {unknown} value
 * @param {number} low
 * @param {number} high
 * @param {string} name
 * @returns {number}
 */
export function assertInRange(value, low, high, name) {
  assertFinite(value, name);
  const n = /** @type {number} */ (value);
  if (n < low || n > high) {
    throw new BadArgumentError(`${name} must be between ${low} and ${high}`, {
      name,
      value,
      low,
      high,
    });
  }
  return n;
}

/**
 * @template T
 * @param {unknown} value
 * @param {readonly T[]} allowed
 * @param {string} name
 * @returns {T}
 */
export function assertOneOf(value, allowed, name) {
  if (!allowed.includes(/** @type {T} */ (value))) {
    throw new BadArgumentError(`${name} must be one of ${allowed.join(', ')}`, {
      name,
      value,
      allowed: [...allowed],
    });
  }
  return /** @type {T} */ (value);
}

/**
 * @param {unknown} value
 * @param {string} name
 * @returns {string}
 */
export function assertNonEmptyString(value, name) {
  if (typeof value !== 'string' || value.length === 0) {
    throw new BadArgumentError(`${name} must be a non empty string`, { name, value });
  }
  return value;
}

/**
 * Rejects arrays, class instances and null. Profiles and recipes are plain data
 * and anything else at those seams is a mistake worth catching early.
 *
 * @param {unknown} value
 * @param {string} name
 * @returns {Record<string, unknown>}
 */
export function assertPlainObject(value, name) {
  const ok =
    typeof value === 'object' &&
    value !== null &&
    !Array.isArray(value) &&
    (Object.getPrototypeOf(value) === Object.prototype || Object.getPrototypeOf(value) === null);
  if (!ok) throw new BadArgumentError(`${name} must be a plain object`, { name });
  return /** @type {Record<string, unknown>} */ (value);
}

/**
 * @template T
 * @param {unknown} value
 * @param {(item: unknown, index: number) => T} check
 * @param {string} name
 * @returns {T[]}
 */
export function assertArrayOf(value, check, name) {
  if (!Array.isArray(value)) throw new BadArgumentError(`${name} must be an array`, { name });
  return value.map((item, index) => check(item, index));
}
