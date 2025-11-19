import { existsSync } from 'node:fs';
import { rm, stat } from 'node:fs/promises';
import { basename, extname, join } from 'node:path';

import { codecForPath } from '../codec/registry.js';
import { StoreError } from '../errors.js';
import { SIDECAR_NAME, entryNamed, objectionsToPayload } from '../manifest/payload.js';
import { digests, sameDigest } from '../manifest/checksum.js';
import { parseSidecar } from '../manifest/sidecar.js';
import { ensureDirectory, listNames, readBytes, sweepTemporary, writeSetAtomic } from './atomic.js';
import { assertInside, deliveryPath, workspacePaths } from './paths.js';

/**
 * The four folders a shift moves material through.
 *
 * Captures land in incoming. A run picks one up, works in working, and the
 * finished delivery goes to delivered or, if it did not grade well enough, to
 * rejected. Nothing is ever deleted by this class: a rejected accession is
 * moved, not removed, because the reason it was rejected is usually something
 * about the capture that somebody has to see.
 *
 * The class holds paths and does io. It holds no state about a run, which is
 * what lets two runs share one workspace without arranging anything.
 */

export class Workspace {
  /**
   * @param {string} root
   */
  constructor(root) {
    this.paths = workspacePaths(root);
    Object.freeze(this);
  }

  /** @returns {string} */
  get root() {
    return this.paths.root;
  }

  /**
   * Make the folders if they are not there. Safe to call from two runs at
   * once, which is why it is mkdir rather than a check and a mkdir.
   *
   * @param {string} root
   * @returns {Promise<Workspace>}
   */
  static async open(root) {
    const workspace = new Workspace(root);
    for (const folder of ['incoming', 'working', 'delivered', 'rejected']) {
      await ensureDirectory(workspace.paths[folder]);
    }
    return workspace;
  }

  /**
   * Open one that is expected to exist already, so a typed path that is not a
   * workspace fails here rather than by quietly making one.
   *
   * @param {string} root
   * @returns {Workspace}
   */
  static at(root) {
    const workspace = new Workspace(root);
    if (!existsSync(workspace.paths.incoming)) {
      throw new StoreError('that is not a workspace', { root: workspace.root });
    }
    return workspace;
  }

  /**
   * The captures waiting to be worked, oldest first.
   *
   * Anything the codecs do not recognise by extension is left out rather than
   * reported. An incoming folder always has a spreadsheet and somebody's notes
   * in it, and treating those as failed captures makes every run look bad.
   *
   * @returns {Promise<{name: string, path: string, accession: string, bytes: number}[]>}
   */
  async incoming() {
    const names = await listNames(this.paths.incoming);
    /** @type {{name: string, path: string, accession: string, bytes: number}[]} */
    const out = [];

    for (const name of names) {
      if (!codecForPath(name)) continue;
      const path = join(this.paths.incoming, name);
      const info = await stat(path);
      if (!info.isFile()) continue;
      out.push({
        name,
        path,
        accession: basename(name, extname(name)),
        bytes: info.size,
      });
    }
    return out;
  }

  /**
   * @param {string} accession
   * @param {object} [options]
   * @param {string} [options.folder]
   * @returns {string}
   */
  pathFor(accession, options) {
    return assertInside(this.root, deliveryPath(this.paths, accession, options));
  }

  /**
   * Whether an accession has already been delivered. A run asks this before it
   * does the work, because redoing a delivery costs minutes and overwriting
   * one costs a curator their afternoon.
   *
   * @param {string} accession
   * @param {object} [options]
   * @param {string} [options.folder]
   * @returns {boolean}
   */
  has(accession, options) {
    return existsSync(this.pathFor(accession, options));
  }

  /**
   * Write a delivery.
   *
   * The payload is checked against itself again here, even though whatever
   * built it checked it too. It costs a hash of a few megabytes and it is the
   * last moment at which a wrong delivery is still only in memory.
   *
   * @param {import('../manifest/payload.js').Payload} payload
   * @param {object} [options]
   * @param {string} [options.folder]
   * @param {boolean} [options.overwrite]
   * @returns {Promise<{directory: string, written: string[], bytes: number}>}
   */
  async put(payload, { folder = 'delivered', overwrite = false } = {}) {
    const objections = objectionsToPayload(payload);
    if (objections.length > 0) {
      throw new StoreError('that delivery does not agree with itself', {
        accession: payload.accession,
        first: objections[0],
        objections: objections.length,
      });
    }

    const directory = this.pathFor(payload.accession, { folder });
    if (!overwrite && existsSync(directory)) {
      throw new StoreError('that accession has already been delivered', {
        accession: payload.accession,
        directory,
      });
    }
    return writeSetAtomic(directory, payload.entries, { overwrite: true });
  }

  /**
   * Read a delivery back off the disk as the same shape put took.
   *
   * @param {string} accession
   * @param {object} [options]
   * @param {string} [options.folder]
   * @returns {Promise<import('../manifest/payload.js').Payload>}
   */
  async get(accession, { folder = 'delivered' } = {}) {
    const directory = this.pathFor(accession, { folder });
    if (!existsSync(directory)) {
      throw new StoreError('there is no delivery under that accession', { accession, directory });
    }

    const names = await listNames(directory);
    /** @type {{name: string, bytes: Uint8Array}[]} */
    const entries = [];
    for (const name of names) {
      entries.push({ name, bytes: await readBytes(join(directory, name)) });
    }
    const { digestOfSet } = await import('../manifest/checksum.js');
    return { accession, entries, digest: digestOfSet(entries) };
  }

  /**
   * Check a delivery on disk against the sidecar sitting in it.
   *
   * This is the call a fixity sweep makes, years later, over everything. It
   * reports rather than throws, because a sweep that stops at the first bad
   * file tells a repository about one problem when it has forty.
   *
   * @param {string} accession
   * @param {object} [options]
   * @param {string} [options.folder]
   * @returns {Promise<{accession: string, ok: boolean, checked: number, problems: string[]}>}
   */
  async verify(accession, options) {
    /** @type {import('../manifest/payload.js').Payload} */
    let payload;
    try {
      payload = await this.get(accession, options);
    } catch (cause) {
      return {
        accession,
        ok: false,
        checked: 0,
        problems: [/** @type {Error} */ (cause).message],
      };
    }

    const problems = objectionsToPayload(payload);
    const sidecarBytes = entryNamed(payload, SIDECAR_NAME);
    let checked = 0;

    if (sidecarBytes) {
      try {
        const sidecar = parseSidecar(new TextDecoder().decode(sidecarBytes));
        for (const listed of sidecar.derivatives ?? []) {
          const bytes = entryNamed(payload, listed.file);
          if (!bytes) continue;
          checked += 1;
          if (!sameDigest(digests(bytes).sha256, listed.digests?.sha256)) {
            problems.push(`${listed.file} has changed since it was delivered`);
          }
        }
      } catch {
        // objectionsToPayload has already said the sidecar will not read back
      }
    }

    return { accession, ok: problems.length === 0, checked, problems: [...new Set(problems)] };
  }

  /**
   * Move a delivery from one folder to another, which is what happens when a
   * curator overrules a grading in either direction.
   *
   * @param {string} accession
   * @param {object} args
   * @param {string} args.from
   * @param {string} args.to
   * @returns {Promise<{from: string, to: string}>}
   */
  async move(accession, { from, to }) {
    const source = this.pathFor(accession, { folder: from });
    const target = this.pathFor(accession, { folder: to });

    if (!existsSync(source)) {
      throw new StoreError('there is nothing to move', { accession, from: source });
    }
    if (existsSync(target)) {
      throw new StoreError('there is already one there', { accession, to: target });
    }

    const { rename } = await import('node:fs/promises');
    await ensureDirectory(this.paths[to]);
    try {
      await rename(source, target);
    } catch (cause) {
      throw new StoreError('that delivery could not be moved', { accession, cause });
    }
    return { from: source, to: target };
  }

  /**
   * Everything delivered, for a listing.
   *
   * @param {object} [options]
   * @param {string} [options.folder]
   * @returns {Promise<string[]>}
   */
  async list({ folder = 'delivered' } = {}) {
    const where = this.paths[folder];
    if (!existsSync(where)) return [];
    return listNames(where);
  }

  /**
   * Clear the working folder and sweep up what a crashed run left in it.
   *
   * @returns {Promise<{swept: string[]}>}
   */
  async tidy() {
    const swept = await sweepTemporary(this.paths.working).catch(() => []);
    for (const name of await this.list({ folder: 'working' })) {
      await rm(join(this.paths.working, name), { recursive: true, force: true });
    }
    return { swept };
  }
}
