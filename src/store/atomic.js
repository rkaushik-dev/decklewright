import { existsSync } from 'node:fs';
import { mkdir, readFile, readdir, rename, rm, stat, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';

import { StoreError } from '../errors.js';
import { isTemporary, temporaryFor } from './paths.js';

/**
 * Writing without leaving half a file behind.
 *
 * A capture run takes minutes and produces files that are hundreds of
 * megabytes. Somebody will close the laptop, the machine will lose power, and
 * the share will go away in the middle of a write, and all three have to leave
 * the folder in a state a later run can understand.
 *
 * The rule is that a file appears at its final name only when it is complete.
 * Everything is written to a name ending in .part and moved into place, and a
 * move within a filesystem is the one operation this whole tree can rely on to
 * either happen or not.
 */

/**
 * @param {string} path
 * @returns {Promise<void>}
 */
export async function ensureDirectory(path) {
  try {
    await mkdir(path, { recursive: true });
  } catch (cause) {
    throw new StoreError('that directory could not be made', { path, cause });
  }
}

/**
 * Write bytes so that the file is either the old one or the new one.
 *
 * @param {string} path
 * @param {Uint8Array|string} content
 * @param {object} [options]
 * @param {boolean} [options.overwrite] false refuses rather than replacing
 * @returns {Promise<{path: string, bytes: number}>}
 */
export async function writeAtomic(path, content, { overwrite = true } = {}) {
  if (!overwrite && existsSync(path)) {
    throw new StoreError('there is already a file there', { path });
  }

  await ensureDirectory(dirname(path));
  const temporary = temporaryFor(path);

  try {
    await writeFile(temporary, content);
    await rename(temporary, path);
  } catch (cause) {
    await rm(temporary, { force: true }).catch(() => {});
    throw new StoreError('that file could not be written', { path, cause });
  }

  const written = await stat(path);
  return { path, bytes: written.size };
}

/**
 * Write a whole set of files, and leave nothing behind if any of them fails.
 *
 * Every file is written to its temporary name first and they are moved into
 * place only once all of them are on disk. A folder with eight of nine
 * derivatives in it looks finished to everything downstream, which is the
 * whole reason this is not a loop over writeAtomic.
 *
 * @param {string} directory
 * @param {readonly {name: string, bytes: Uint8Array}[]} entries
 * @param {object} [options]
 * @param {boolean} [options.overwrite]
 * @returns {Promise<{directory: string, written: string[], bytes: number}>}
 */
export async function writeSetAtomic(directory, entries, { overwrite = true } = {}) {
  if (entries.length === 0) {
    throw new StoreError('there is nothing to write', { directory });
  }
  await ensureDirectory(directory);

  /** @type {{temporary: string, target: string}[]} */
  const staged = [];
  try {
    for (const entry of entries) {
      const target = join(directory, entry.name);
      if (!overwrite && existsSync(target)) {
        throw new StoreError('there is already a file there', { path: target });
      }
      await ensureDirectory(dirname(target));
      const temporary = temporaryFor(target);
      await writeFile(temporary, entry.bytes);
      staged.push({ temporary, target });
    }
  } catch (cause) {
    await Promise.all(staged.map((one) => rm(one.temporary, { force: true }).catch(() => {})));
    if (cause instanceof StoreError) throw cause;
    throw new StoreError('that set could not be staged', { directory, cause });
  }

  /** @type {string[]} */
  const written = [];
  try {
    for (const one of staged) {
      await rename(one.temporary, one.target);
      written.push(one.target);
    }
  } catch (cause) {
    throw new StoreError('that set was half moved into place', {
      directory,
      moved: written.length,
      of: staged.length,
      cause,
    });
  }

  return {
    directory,
    written,
    bytes: entries.reduce((sum, entry) => sum + entry.bytes.length, 0),
  };
}

/**
 * Append a line to a file, making it if it is not there.
 *
 * This is the one write that is not atomic and cannot be. A ledger is appended
 * to by every run and rewriting it whole would lose the entries another run
 * added in between. A short append is atomic enough on every filesystem this
 * runs on, and a torn line at the end of a ledger is recoverable by reading
 * the lines that parse.
 *
 * @param {string} path
 * @param {string} line
 * @returns {Promise<void>}
 */
export async function appendLine(path, line) {
  await ensureDirectory(dirname(path));
  const { appendFile } = await import('node:fs/promises');
  try {
    await appendFile(path, `${line.replace(/\n/g, ' ')}\n`, 'utf8');
  } catch (cause) {
    throw new StoreError('that line could not be appended', { path, cause });
  }
}

/**
 * @param {string} path
 * @returns {Promise<Uint8Array>}
 */
export async function readBytes(path) {
  try {
    return new Uint8Array(await readFile(path));
  } catch (cause) {
    throw new StoreError('that file could not be read', { path, cause });
  }
}

/**
 * @param {string} path
 * @returns {Promise<string>}
 */
export async function readText(path) {
  try {
    return await readFile(path, 'utf8');
  } catch (cause) {
    throw new StoreError('that file could not be read', { path, cause });
  }
}

/**
 * Everything in a directory, without the leftovers of a crashed run.
 *
 * @param {string} directory
 * @param {object} [options]
 * @param {boolean} [options.includeTemporary]
 * @returns {Promise<string[]>} names, sorted
 */
export async function listNames(directory, { includeTemporary = false } = {}) {
  /** @type {string[]} */
  let names;
  try {
    names = await readdir(directory);
  } catch (cause) {
    throw new StoreError('that directory could not be listed', { directory, cause });
  }
  const kept = includeTemporary ? names : names.filter((name) => !isTemporary(name));
  return kept.sort();
}

/**
 * Sweep up what a crashed run left. It only removes names this module would
 * have made, so a directory somebody else keeps .part files in is safe.
 *
 * @param {string} directory
 * @returns {Promise<string[]>} what was removed
 */
export async function sweepTemporary(directory) {
  const names = await listNames(directory, { includeTemporary: true });
  const leftovers = names.filter(isTemporary);
  await Promise.all(leftovers.map((name) => rm(join(directory, name), { force: true })));
  return leftovers;
}
