import { ProfileError } from '../errors.js';

/**
 * Profile inheritance.
 *
 * Every collection here is a variation on one of two house styles, so a
 * profile says what it extends and then only what it changes. The merge is
 * deep for groups and flat for lists.
 *
 * Lists replace rather than merge on purpose. A collection that lists two
 * derivatives means two, not two plus whatever the parent had, and the
 * alternative was tried and produced a thumbnail nobody asked for in every
 * collection that inherited.
 */

/**
 * @param {unknown} value
 * @returns {boolean}
 */
function isGroup(value) {
  return (
    typeof value === 'object' &&
    value !== null &&
    !Array.isArray(value) &&
    Object.getPrototypeOf(value) === Object.prototype
  );
}

/**
 * @param {Record<string, unknown>} base
 * @param {Record<string, unknown>} patch
 * @returns {Record<string, unknown>}
 */
export function overlay(base, patch) {
  /** @type {Record<string, unknown>} */
  const out = { ...base };
  for (const [key, value] of Object.entries(patch)) {
    if (value === null) {
      delete out[key];
      continue;
    }
    if (isGroup(value) && isGroup(out[key])) {
      out[key] = overlay(/** @type {Record<string, unknown>} */ (out[key]), value);
      continue;
    }
    out[key] = value;
  }
  return out;
}

/**
 * @param {readonly Record<string, unknown>[]} documents oldest first
 * @returns {Record<string, unknown>}
 */
export function overlayAll(documents) {
  return documents.reduce((acc, item) => overlay(acc, item), {});
}

/**
 * Walk the extends chain and flatten it. The chain is followed by name and a
 * name that is not there is an error rather than a silent skip, because a
 * typo in extends would otherwise hand back the house style unchanged.
 *
 * @param {Record<string, unknown>} document
 * @param {(name: string) => Record<string, unknown>|undefined} lookup
 * @param {object} [options]
 * @param {number} [options.limit] how deep a chain may go
 * @returns {{document: Record<string, unknown>, chain: string[]}}
 */
export function resolveExtends(document, lookup, { limit = 8 } = {}) {
  /** @type {Record<string, unknown>[]} */
  const stack = [document];
  /** @type {string[]} */
  const chain = [];
  const seen = new Set();
  const own = typeof document.name === 'string' ? document.name : null;
  if (own) seen.add(own);

  let current = document;
  while (typeof current.extends === 'string') {
    const parentName = current.extends;
    if (seen.has(parentName)) {
      throw new ProfileError(`the extends chain loops back to ${parentName}`, {
        chain: [...chain, parentName],
      });
    }
    if (chain.length >= limit) {
      throw new ProfileError('the extends chain is too deep', { chain, limit });
    }
    const parent = lookup(parentName);
    if (!parent) {
      throw new ProfileError(`there is no profile called ${parentName} to extend`, {
        wanted: parentName,
        chain,
      });
    }
    seen.add(parentName);
    chain.push(parentName);
    stack.unshift(parent);
    current = parent;
  }

  const flattened = overlayAll(stack);
  delete flattened.extends;
  if (own) flattened.name = own;
  return { document: flattened, chain };
}

/**
 * Which settings a child actually changed, as dotted paths. The registry
 * prints this so a curator can see what their own file is doing rather than
 * what it inherited.
 *
 * @param {Record<string, unknown>} base
 * @param {Record<string, unknown>} child
 * @param {string} [prefix]
 * @returns {string[]}
 */
export function changedPaths(base, child, prefix = '') {
  /** @type {string[]} */
  const out = [];
  for (const [key, value] of Object.entries(child)) {
    const path = prefix ? `${prefix}.${key}` : key;
    const before = /** @type {Record<string, unknown>} */ (base)[key];
    if (isGroup(value) && isGroup(before)) {
      out.push(...changedPaths(before, value, path));
      continue;
    }
    if (JSON.stringify(before) !== JSON.stringify(value)) out.push(path);
  }
  return out;
}
