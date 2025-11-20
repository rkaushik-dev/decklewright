import { appendEntry, entryFor, readLedger, summarise } from './ledger.js';

/** @typedef {import('./workspace.js').Workspace} Workspace */

/**
 * The store as the rest of the tree sees it.
 *
 * This is the only layer that knows a disk exists. Everything above it hands
 * down bytes and names, which is why the whole of the rest of the tree can be
 * tested without a temporary directory and why a lab can point this at a share
 * that behaves nothing like a local disk without changing anything else.
 */

export { Workspace } from './workspace.js';
export {
  FOLDERS,
  LEDGER_NAME,
  assertInside,
  deliveryPath,
  filePath,
  isInside,
  isTemporary,
  shortPath,
  temporaryFor,
  workspacePaths,
} from './paths.js';
export {
  appendLine,
  ensureDirectory,
  listNames,
  readBytes,
  readText,
  sweepTemporary,
  writeAtomic,
  writeSetAtomic,
} from './atomic.js';
export {
  OUTCOMES,
  appendEntry,
  describeSummary,
  entryFor,
  latestByAccession,
  readLedger,
  select,
  summarise,
} from './ledger.js';

/**
 * Write a delivery and record it, which is the pair that must not come apart.
 *
 * The ledger line goes down after the files, never before. A line saying an
 * accession was delivered when it was not sends somebody looking for a folder
 * that is not there. Files on the disk with no line beside them are harmless
 * by comparison: the folder is its own evidence.
 *
 * @param {Workspace} workspace
 * @param {import('../manifest/payload.js').Payload} payload
 * @param {object} [options]
 * @param {string} [options.folder]
 * @param {boolean} [options.overwrite]
 * @param {string} [options.profile]
 * @param {number} [options.stars]
 * @param {readonly string[]} [options.warnings]
 * @param {Date} [options.at]
 * @returns {Promise<{directory: string, entry: import('./ledger.js').Entry}>}
 */
export async function deliver(workspace, payload, options = {}) {
  const written = await workspace.put(payload, {
    folder: options.folder ?? 'delivered',
    overwrite: options.overwrite ?? false,
  });

  const entry = await appendEntry(
    workspace.paths.ledger,
    entryFor({
      accession: payload.accession,
      outcome: options.folder === 'rejected' ? 'rejected' : 'delivered',
      profile: options.profile,
      stars: options.stars,
      bytes: written.bytes,
      digest: payload.digest,
      warnings: options.warnings,
      at: options.at,
    }),
  );

  return { directory: written.directory, entry };
}

/**
 * Record a run that produced nothing, so a failure is as visible as a success.
 *
 * @param {Workspace} workspace
 * @param {object} args
 * @param {string} args.accession
 * @param {string} args.reason
 * @param {string} [args.outcome]
 * @param {string} [args.profile]
 * @param {Date} [args.at]
 * @returns {Promise<import('./ledger.js').Entry>}
 */
export async function recordFailure(workspace, { accession, reason, outcome, profile, at }) {
  return appendEntry(
    workspace.paths.ledger,
    entryFor({ accession, outcome: outcome ?? 'failed', reason, profile, at }),
  );
}

/**
 * What the workspace holds, read off the ledger rather than off the folders.
 *
 * @param {Workspace} workspace
 * @returns {Promise<ReturnType<typeof summarise> & {unreadable: number}>}
 */
export async function shiftReport(workspace) {
  const { entries, unreadable } = await readLedger(workspace.paths.ledger);
  return { ...summarise(entries), unreadable };
}
