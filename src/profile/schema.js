import { ProfileError } from '../errors.js';
import { Insets } from '../geom/insets.js';
import { toLength } from '../measure/length.js';
import { toTolerance } from '../measure/tolerance.js';

/**
 * A very small schema language for collection profiles.
 *
 * A profile is written by a curator in a text file and it has to be read
 * strictly: a misspelled key that is silently ignored is how a collection ends
 * up cropped to the wrong edge for six months. Every field says what it is,
 * every unknown key is an error, and every message names the path it went
 * wrong at.
 *
 * This is not a general validator and does not want to be. It handles the
 * dozen shapes profiles actually use.
 */

/** @typedef {(value: unknown, path: string) => unknown} Reader */
/** @typedef {{read: Reader, required: boolean, fallback?: unknown, describe: string}} Field */

/**
 * @param {string} path
 * @param {string} message
 * @param {Record<string, unknown>} [details]
 * @returns {ProfileError}
 */
export function fieldError(path, message, details = {}) {
  return new ProfileError(`${path}: ${message}`, { path, ...details });
}

/**
 * @param {Reader} read
 * @param {string} describe
 * @param {object} [options]
 * @param {boolean} [options.required]
 * @param {unknown} [options.fallback]
 * @returns {Field}
 */
export function field(read, describe, { required = false, fallback } = {}) {
  return { read, describe, required, fallback };
}

/** @returns {Field} */
export function text(options) {
  return field(
    (value, path) => {
      if (typeof value !== 'string' || value.length === 0) {
        throw fieldError(path, 'expected a non empty string');
      }
      return value;
    },
    'text',
    options,
  );
}

/**
 * @param {object} [options]
 * @param {number} [options.min]
 * @param {number} [options.max]
 * @param {boolean} [options.integer]
 * @param {boolean} [options.required]
 * @param {unknown} [options.fallback]
 * @returns {Field}
 */
export function number(options = {}) {
  const { min, max, integer = false } = options;
  return field(
    (value, path) => {
      if (typeof value !== 'number' || !Number.isFinite(value)) {
        throw fieldError(path, 'expected a number');
      }
      if (integer && !Number.isInteger(value)) throw fieldError(path, 'expected a whole number');
      if (min !== undefined && value < min) throw fieldError(path, `must be at least ${min}`);
      if (max !== undefined && value > max) throw fieldError(path, `must be at most ${max}`);
      return value;
    },
    'number',
    options,
  );
}

/** @returns {Field} */
export function flag(options) {
  return field(
    (value, path) => {
      if (typeof value !== 'boolean') throw fieldError(path, 'expected true or false');
      return value;
    },
    'true or false',
    options,
  );
}

/**
 * @param {readonly string[]} allowed
 * @param {object} [options]
 * @returns {Field}
 */
export function choice(allowed, options) {
  return field(
    (value, path) => {
      if (typeof value !== 'string' || !allowed.includes(value)) {
        throw fieldError(path, `expected one of ${allowed.join(', ')}`);
      }
      return value;
    },
    `one of ${allowed.join(', ')}`,
    options,
  );
}

/** @returns {Field} */
export function length(options) {
  return field(
    (value, path) => {
      try {
        return toLength(value);
      } catch (cause) {
        throw fieldError(path, 'expected a length such as 3mm or 0.25in', { cause: String(cause) });
      }
    },
    'a length',
    options,
  );
}

/** @returns {Field} */
export function tolerance(options) {
  return field(
    (value, path) => {
      try {
        return toTolerance(value);
      } catch (cause) {
        throw fieldError(path, 'expected a tolerance such as 0.5 or +2/-1', {
          cause: String(cause),
        });
      }
    },
    'a tolerance',
    options,
  );
}

/**
 * Four lengths, one per edge, written the way css writes them or as a bag.
 *
 * @param {object} [options]
 * @returns {Field}
 */
export function edges(options) {
  return field(
    (value, path) => {
      const sides = ['top', 'right', 'bottom', 'left'];
      /** @type {Record<string, import('../measure/length.js').Length>} */
      const out = {};
      if (typeof value === 'string' || (value && typeof value === 'object' && 'value' in value)) {
        const one = toLength(value);
        for (const side of sides) out[side] = one;
        return out;
      }
      if (!value || typeof value !== 'object' || Array.isArray(value)) {
        throw fieldError(path, 'expected a length or a bag of four');
      }
      const bag = /** @type {Record<string, unknown>} */ (value);
      for (const key of Object.keys(bag)) {
        if (!sides.includes(key)) throw fieldError(`${path}.${key}`, 'is not an edge');
      }
      for (const side of sides) {
        out[side] = side in bag ? toLength(bag[side]) : toLength('0mm');
      }
      return out;
    },
    'a length or one per edge',
    options,
  );
}

/**
 * @param {Field} member
 * @param {object} [options]
 * @returns {Field}
 */
export function list(member, options) {
  return field(
    (value, path) => {
      if (!Array.isArray(value)) throw fieldError(path, 'expected a list');
      return value.map((item, i) => member.read(item, `${path}[${i}]`));
    },
    `a list of ${member.describe}`,
    options,
  );
}

/**
 * @param {Record<string, Field>} fields
 * @param {object} [options]
 * @returns {Field}
 */
export function group(fields, options) {
  return field((value, path) => readGroup(fields, value, path), 'a group', options);
}

/**
 * @param {Record<string, Field>} fields
 * @param {unknown} value
 * @param {string} path
 * @returns {Record<string, unknown>}
 */
export function readGroup(fields, value, path = '') {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw fieldError(path || '(root)', 'expected a group of settings');
  }
  const bag = /** @type {Record<string, unknown>} */ (value);

  for (const key of Object.keys(bag)) {
    if (!(key in fields)) {
      throw fieldError(join(path, key), 'is not a setting this profile understands', {
        known: Object.keys(fields),
      });
    }
  }

  /** @type {Record<string, unknown>} */
  const out = {};
  for (const [key, spec] of Object.entries(fields)) {
    const here = join(path, key);
    if (!(key in bag) || bag[key] === undefined) {
      if (spec.required) throw fieldError(here, 'is required');
      if (spec.fallback !== undefined) out[key] = spec.read(spec.fallback, here);
      continue;
    }
    out[key] = spec.read(bag[key], here);
  }
  return out;
}

/**
 * @param {string} path
 * @param {string} key
 * @returns {string}
 */
function join(path, key) {
  return path ? `${path}.${key}` : key;
}

/**
 * @param {Record<string, unknown>} bag
 * @returns {Insets}
 */
export function edgesToInsets(bag) {
  return new Insets(
    /** @type {any} */ (bag.top).value,
    /** @type {any} */ (bag.right).value,
    /** @type {any} */ (bag.bottom).value,
    /** @type {any} */ (bag.left).value,
  );
}
