import { isAbsolute, join, normalize, relative, resolve, sep } from 'node:path';

import { StoreError } from '../errors.js';
import { safeStem } from '../derive/naming.js';

/**
 * Where things go.
 *
 * A workspace is four folders under one root, and the only reason it is worth
 * a module is that every path in it has to be built the same way twice: once
 * when the run writes a delivery and once when somebody comes back a year
 * later to look for it. A path built by string concatenation in two places is
 * a path that will differ in one of them.
 *
 * Nothing here touches the filesystem. These are names, and a name for a place
 * that does not exist is still a useful thing to hold.
 */

/** The folders a workspace has, in the order a run uses them. */
export const FOLDERS = Object.freeze({
  incoming: 'incoming',
  working: 'working',
  delivered: 'delivered',
  rejected: 'rejected',
});

/** The ledger lives at the root, because it is about the workspace itself. */
export const LEDGER_NAME = 'ledger.jsonl';

/**
 * @typedef {object} Workspace
 * @property {string} root
 * @property {string} incoming
 * @property {string} working
 * @property {string} delivered
 * @property {string} rejected
 * @property {string} ledger
 */

/**
 * @param {string} root
 * @returns {Workspace}
 */
export function workspacePaths(root) {
  const base = resolve(root);
  return {
    root: base,
    incoming: join(base, FOLDERS.incoming),
    working: join(base, FOLDERS.working),
    delivered: join(base, FOLDERS.delivered),
    rejected: join(base, FOLDERS.rejected),
    ledger: join(base, LEDGER_NAME),
  };
}

/**
 * Where one accession's delivery goes.
 *
 * The accession is put through the same cleaner the file names use, so a
 * folder and the files inside it cannot disagree about what the accession is
 * called.
 *
 * @param {Workspace} workspace
 * @param {string} accession
 * @param {object} [options]
 * @param {string} [options.folder] which of the four
 * @returns {string}
 */
export function deliveryPath(workspace, accession, { folder = 'delivered' } = {}) {
  const where = workspace[/** @type {keyof Workspace} */ (folder)];
  if (typeof where !== 'string' || folder === 'root' || folder === 'ledger') {
    throw new StoreError('a workspace has no folder by that name', {
      folder,
      known: Object.keys(FOLDERS).join(', '),
    });
  }
  return join(where, safeStem(accession));
}

/**
 * The path a named file inside a delivery gets.
 *
 * @param {string} directory
 * @param {string} name
 * @returns {string}
 */
export function filePath(directory, name) {
  const cleaned = normalize(name);
  if (isAbsolute(cleaned) || cleaned.split(/[\\/]/).includes('..')) {
    throw new StoreError('that file name would write outside the delivery', { name });
  }
  return join(directory, cleaned);
}

/**
 * Whether a path is inside a directory, which is the only question worth
 * asking before writing anything a name came from outside.
 *
 * A path equal to the directory is not inside it. That distinction matters:
 * the case it rules out is a delivery whose name resolved to the empty string
 * and would have been written over the folder holding every other delivery.
 *
 * @param {string} directory
 * @param {string} candidate
 * @returns {boolean}
 */
export function isInside(directory, candidate) {
  const from = resolve(directory);
  const to = resolve(candidate);
  if (from === to) return false;
  const between = relative(from, to);
  return between !== '' && !between.startsWith('..') && !isAbsolute(between);
}

/**
 * Refuse a path that is not where it says it is.
 *
 * @param {string} directory
 * @param {string} candidate
 * @returns {string} the candidate, resolved
 */
export function assertInside(directory, candidate) {
  if (!isInside(directory, candidate)) {
    throw new StoreError('that path is outside the workspace', {
      directory: resolve(directory),
      path: resolve(candidate),
    });
  }
  return resolve(candidate);
}

/**
 * The name a temporary file is written under before it is moved into place.
 * It carries the target name so that a crashed run leaves something a person
 * can identify rather than a folder full of hex.
 *
 * @param {string} target
 * @param {object} [options]
 * @param {string} [options.token] made unique by the caller
 * @returns {string}
 */
export function temporaryFor(target, { token } = {}) {
  const mark = token ?? `${process.pid}.${Date.now().toString(36)}`;
  return `${target}.${mark}.part`;
}

/**
 * Whether a name looks like one of ours left behind by a crash.
 *
 * @param {string} name
 * @returns {boolean}
 */
export function isTemporary(name) {
  return name.endsWith('.part');
}

/**
 * Split a path into the pieces a person reads out, for a log line that has to
 * fit on one line.
 *
 * @param {string} path
 * @param {number} [keep] how many trailing segments
 * @returns {string}
 */
export function shortPath(path, keep = 3) {
  const parts = normalize(path).split(sep).filter(Boolean);
  if (parts.length <= keep) return parts.join(sep);
  return `...${sep}${parts.slice(-keep).join(sep)}`;
}
