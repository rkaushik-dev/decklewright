import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

import { afterEach } from 'vitest';

/**
 * A temporary directory that cleans itself up.
 *
 * Six test files had their own copy of this and every copy removed its
 * directories with a plain recursive rm. Under coverage, which runs everything
 * slower and more of it at once, that started failing on Windows with
 * ENOTEMPTY: the directory really was empty, and the handle on the file inside
 * it had not been released yet. It is a race in the filesystem rather than in
 * the test, so the fix is to retry rather than to serialise the tests.
 *
 * Importing this registers the cleanup for the file that imported it.
 */

/** @type {string[]} */
const made = [];

/**
 * @param {string} path
 * @returns {Promise<void>}
 */
export async function remove(path) {
  await rm(path, { recursive: true, force: true, maxRetries: 8, retryDelay: 60 });
}

afterEach(async () => {
  for (const path of made.splice(0)) await remove(path);
});

/**
 * @param {string} [what] a word that shows up in the directory name
 * @returns {Promise<string>}
 */
export async function scratch(what = 'run') {
  const path = await mkdtemp(join(tmpdir(), `decklewright-${what}-`));
  made.push(path);
  return path;
}

/**
 * Have a directory somebody else made cleaned up too.
 *
 * @param {string} path
 * @returns {string}
 */
export function cleanUp(path) {
  made.push(path);
  return path;
}
