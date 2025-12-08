import { basename, extname } from 'node:path';

import { BadArgumentError } from '../../errors.js';
import { Sampling } from '../../measure/resolution.js';
import { loadProfile } from '../../profile/index.js';
import { ProfileRegistry } from '../../profile/registry.js';
import { readBytes, readText } from '../../store/atomic.js';

/**
 * The parts every command needs.
 *
 * A command is a function of its arguments and an io object, and the io object
 * is passed in rather than reached for. That is the whole reason the commands
 * are testable: a test hands in a writer that collects lines, and nothing
 * anywhere prints to a console that a test cannot see.
 */

/**
 * @typedef {object} Io
 * @property {(line: string) => void} out
 * @property {(line: string) => void} err
 * @property {string} cwd
 * @property {() => Date} now
 */

/**
 * @param {Partial<Io>} [parts]
 * @returns {Io}
 */
export function io(parts = {}) {
  return {
    out: parts.out ?? ((line) => console.log(line)),
    err: parts.err ?? ((line) => console.error(line)),
    cwd: parts.cwd ?? process.cwd(),
    now: parts.now ?? (() => new Date()),
  };
}

/** The options every command that touches a capture understands. */
export const CAPTURE_OPTIONS = Object.freeze({
  profile: {
    short: 'p',
    type: 'string',
    fallback: 'house',
    describe: 'a built in profile name, or a path to one',
  },
  ppi: { short: 'r', type: 'number', describe: 'the sampling frequency of the capture' },
});

/**
 * A profile by name, or read off a disk if it looks like a path.
 *
 * The two are told apart by whether the name ends in .json, not by trying one
 * and falling back to the other. A fallback would turn a typed path into the
 * house profile and quietly crop four hundred sheets to the wrong rules.
 *
 * @param {string} name
 * @returns {Promise<Record<string, any>>}
 */
export async function profileNamed(name) {
  const registry = new ProfileRegistry();
  if (!name.endsWith('.json')) {
    if (!registry.names().includes(name)) {
      throw new BadArgumentError('there is no profile by that name', {
        profile: name,
        known: registry.names().join(', '),
      });
    }
    return registry.get(name);
  }

  /** @type {any} */
  let document;
  try {
    document = JSON.parse(await readText(name));
  } catch (cause) {
    throw new BadArgumentError('that profile will not read as json', { path: name, cause });
  }
  return loadProfile(document, { registry });
}

/**
 * @param {string} path
 * @returns {Promise<{bytes: Uint8Array, accession: string, path: string}>}
 */
export async function captureAt(path) {
  return {
    bytes: await readBytes(path),
    accession: basename(path, extname(path)),
    path,
  };
}

/**
 * @param {number|undefined} ppi
 * @param {Record<string, any>} profile
 * @returns {Sampling|undefined}
 */
export function samplingFrom(ppi, profile) {
  if (ppi !== undefined) return Sampling.of(ppi);
  const claimed = profile?.capture?.claimedPpi;
  return claimed ? Sampling.of(claimed) : undefined;
}

/**
 * Print an error the way a terminal wants one: the sentence, then the details
 * indented under it. A stack trace is for a bug report and not for an operator
 * who typed a path wrong.
 *
 * @param {Io} sink
 * @param {unknown} caught
 * @returns {number} the exit code
 */
export function report(sink, caught) {
  const error = /** @type {any} */ (caught);
  sink.err(error?.message ?? String(caught));
  for (const [key, value] of Object.entries(error?.details ?? {})) {
    if (key === 'cause') continue;
    sink.err(`  ${key}: ${value}`);
  }
  return 1;
}

/**
 * @param {number} value
 * @returns {string} a size a person reads without counting digits
 */
export function humanBytes(value) {
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} kB`;
  return `${(value / (1024 * 1024)).toFixed(1)} MB`;
}
