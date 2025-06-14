import { ProfileError } from '../errors.js';

/**
 * Bringing an old profile forward.
 *
 * Profiles are written by hand and kept in the collection's own folder, so
 * there are files on this site older than most of this code. Renaming a
 * setting therefore costs a migration, and the migration has to be written at
 * the same time as the rename or the file stops loading a year later with no
 * clue why.
 *
 * A document with no version is version one. That is not a guess: version one
 * predates the field.
 */

/** The version this build writes and understands. */
export const CURRENT_VERSION = 2;

/** @typedef {(document: Record<string, any>) => Record<string, any>} Migration */

/**
 * @param {Record<string, any>} document
 * @returns {Record<string, any>}
 */
function toVersionTwo(document) {
  const out = structuredClone(document);
  out.version = 2;

  if (out.crop && typeof out.crop === 'object') {
    if ('margin' in out.crop) {
      out.crop.bleed = out.crop.margin;
      delete out.crop.margin;
    }
    if ('roundTo' in out.crop) {
      out.crop.snapTo = out.crop.roundTo;
      delete out.crop.roundTo;
    }
    if ('straighten' in out.crop) {
      out.crop.deskew = out.crop.straighten;
      delete out.crop.straighten;
    }
  }

  if (out.qc && typeof out.qc === 'object') {
    if ('dpi' in out.qc) {
      out.qc.samplingPpi = out.qc.dpi;
      delete out.qc.dpi;
    }
    if ('dpiTolerance' in out.qc) {
      out.qc.samplingTolerance = out.qc.dpiTolerance;
      delete out.qc.dpiTolerance;
    }
  }

  if (Array.isArray(out.derivatives)) {
    out.derivatives = out.derivatives.map((derivative) => {
      const copy = { ...derivative };
      if ('maxSide' in copy) {
        copy.longestSide = copy.maxSide;
        delete copy.maxSide;
      }
      if (typeof copy.depth === 'number') copy.depth = String(copy.depth);
      return copy;
    });
  }

  return out;
}

/** Keyed by the version the migration moves a document off. */
export const MIGRATIONS = Object.freeze({ 1: toVersionTwo });

/**
 * @param {Record<string, any>} document
 * @returns {number}
 */
export function versionOf(document) {
  const version = document.version;
  if (version === undefined) return 1;
  if (typeof version !== 'number' || !Number.isInteger(version) || version < 1) {
    throw new ProfileError('the version has to be a whole number from one upward', { version });
  }
  return version;
}

/**
 * @param {Record<string, any>} document
 * @returns {boolean}
 */
export function needsMigration(document) {
  return versionOf(document) < CURRENT_VERSION;
}

/**
 * @param {Record<string, any>} document
 * @returns {{document: Record<string, any>, applied: number[]}}
 */
export function migrate(document) {
  let current = document;
  /** @type {number[]} */
  const applied = [];

  let version = versionOf(current);
  if (version > CURRENT_VERSION) {
    throw new ProfileError(
      `that profile was written for version ${version} and this build understands ${CURRENT_VERSION}`,
      { version, current: CURRENT_VERSION },
    );
  }

  while (version < CURRENT_VERSION) {
    const step = MIGRATIONS[/** @type {keyof typeof MIGRATIONS} */ (version)];
    if (!step) {
      throw new ProfileError(`there is no way to bring version ${version} forward`, { version });
    }
    current = step(current);
    applied.push(version);
    const next = versionOf(current);
    if (next <= version) {
      throw new ProfileError('a migration did not move the version forward', { version });
    }
    version = next;
  }

  return { document: current, applied };
}

/**
 * @param {readonly Record<string, any>[]} documents
 * @returns {Record<string, any>[]}
 */
export function migrateAll(documents) {
  return documents.map((document) => migrate(document).document);
}

/**
 * What a migration would change, without changing it. The verify command
 * prints this so a curator can update their own file rather than relying on
 * the migration for ever.
 *
 * @param {Record<string, any>} document
 * @returns {string[]}
 */
export function migrationNotes(document) {
  if (!needsMigration(document)) return [];
  const { document: after, applied } = migrate(document);
  const notes = [
    `brought forward from version ${versionOf(document)} through ${applied.join(', ')}`,
  ];

  const renames = [
    ['crop.margin', 'crop.bleed'],
    ['crop.roundTo', 'crop.snapTo'],
    ['crop.straighten', 'crop.deskew'],
    ['qc.dpi', 'qc.samplingPpi'],
    ['qc.dpiTolerance', 'qc.samplingTolerance'],
  ];
  for (const [from, to] of renames) {
    const [group, key] = from.split('.');
    if (document[group] && key in document[group]) notes.push(`${from} is now ${to}`);
  }
  if (Array.isArray(document.derivatives)) {
    if (document.derivatives.some((d) => 'maxSide' in d)) {
      notes.push('derivatives[].maxSide is now longestSide');
    }
    if (after.derivatives.some((d) => typeof d.depth === 'string')) {
      if (document.derivatives.some((d) => typeof d.depth === 'number')) {
        notes.push('derivatives[].depth is written as text now');
      }
    }
  }
  return notes;
}
