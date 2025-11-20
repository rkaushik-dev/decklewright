import { existsSync } from 'node:fs';

import { StoreError } from '../errors.js';
import { appendLine, readText } from './atomic.js';

/**
 * What happened, one line per accession.
 *
 * A lab supervisor's first question in the morning is what came off the stand
 * yesterday and what did not, and answering it should not mean walking four
 * hundred folders. The ledger answers it in one file, and it is json lines
 * rather than json so that two runs appending at once cannot corrupt each
 * other and so that a run that dies mid write costs one line rather than the
 * whole record.
 *
 * A line nobody can parse is counted and skipped. That is the only sensible
 * behaviour for a log that is written by a process that can be killed, and
 * pretending otherwise means a torn byte at the end of a file hides a year of
 * work behind it.
 */

/** What a run can end as. */
export const OUTCOMES = Object.freeze(['delivered', 'rejected', 'failed', 'skipped']);

/**
 * @typedef {object} Entry
 * @property {string} at iso 8601
 * @property {string} accession
 * @property {string} outcome
 * @property {string} [profile]
 * @property {number} [stars]
 * @property {number} [bytes]
 * @property {string} [digest] the delivery digest
 * @property {string} [reason] why, when it did not go out
 * @property {string[]} [warnings]
 */

/**
 * @param {object} args
 * @param {string} args.accession
 * @param {string} args.outcome
 * @param {Date|string} [args.at]
 * @param {string} [args.profile]
 * @param {number} [args.stars]
 * @param {number} [args.bytes]
 * @param {string} [args.digest]
 * @param {string} [args.reason]
 * @param {readonly string[]} [args.warnings]
 * @returns {Entry}
 */
export function entryFor({
  accession,
  outcome,
  at,
  profile,
  stars,
  bytes,
  digest,
  reason,
  warnings,
}) {
  if (!OUTCOMES.includes(outcome)) {
    throw new StoreError('a run does not end that way', {
      outcome,
      known: OUTCOMES.join(', '),
    });
  }

  /** @type {Entry} */
  const entry = {
    at: at instanceof Date ? at.toISOString() : (at ?? new Date().toISOString()),
    accession,
    outcome,
  };
  if (profile) entry.profile = profile;
  if (stars !== undefined) entry.stars = stars;
  if (bytes !== undefined) entry.bytes = bytes;
  if (digest) entry.digest = digest;
  if (reason) entry.reason = reason;
  if (warnings && warnings.length > 0) entry.warnings = [...warnings];
  return entry;
}

/**
 * @param {string} path
 * @param {Entry} entry
 * @returns {Promise<Entry>}
 */
export async function appendEntry(path, entry) {
  await appendLine(path, JSON.stringify(entry));
  return entry;
}

/**
 * Read the ledger, counting what could not be read rather than hiding it.
 *
 * @param {string} path
 * @returns {Promise<{entries: Entry[], unreadable: number}>}
 */
export async function readLedger(path) {
  if (!existsSync(path)) return { entries: [], unreadable: 0 };

  const text = await readText(path);
  /** @type {Entry[]} */
  const entries = [];
  let unreadable = 0;

  for (const line of text.split('\n')) {
    if (line.trim() === '') continue;
    try {
      const parsed = JSON.parse(line);
      if (parsed && typeof parsed.accession === 'string' && typeof parsed.outcome === 'string') {
        entries.push(parsed);
      } else {
        unreadable += 1;
      }
    } catch {
      unreadable += 1;
    }
  }
  return { entries, unreadable };
}

/**
 * The most recent entry for each accession, which is what anybody means when
 * they ask what happened to a sheet. Earlier entries are kept in the file
 * because a sheet that failed twice before it went out is worth knowing about.
 *
 * @param {readonly Entry[]} entries
 * @returns {Map<string, Entry>}
 */
export function latestByAccession(entries) {
  /** @type {Map<string, Entry>} */
  const out = new Map();
  for (const entry of entries) {
    const held = out.get(entry.accession);
    if (!held || entry.at >= held.at) out.set(entry.accession, entry);
  }
  return out;
}

/**
 * @param {readonly Entry[]} entries
 * @param {object} [filter]
 * @param {string} [filter.outcome]
 * @param {string} [filter.profile]
 * @param {string} [filter.since] iso 8601, inclusive
 * @param {string} [filter.until] iso 8601, exclusive
 * @returns {Entry[]}
 */
export function select(entries, { outcome, profile, since, until } = {}) {
  return entries.filter((entry) => {
    if (outcome && entry.outcome !== outcome) return false;
    if (profile && entry.profile !== profile) return false;
    if (since && entry.at < since) return false;
    if (until && entry.at >= until) return false;
    return true;
  });
}

/**
 * What a shift produced, counted.
 *
 * The star average is over the entries that carry one, and the count of those
 * is reported beside it. An average over a set whose size is not stated is the
 * number that gets quoted in a funding report and then cannot be reproduced.
 *
 * @param {readonly Entry[]} entries
 * @returns {{total: number, byOutcome: Record<string, number>, bytes: number, graded: number, averageStars: number|null}}
 */
export function summarise(entries) {
  /** @type {Record<string, number>} */
  const byOutcome = {};
  let bytes = 0;
  let graded = 0;
  let stars = 0;

  for (const entry of entries) {
    byOutcome[entry.outcome] = (byOutcome[entry.outcome] ?? 0) + 1;
    bytes += entry.bytes ?? 0;
    if (typeof entry.stars === 'number') {
      graded += 1;
      stars += entry.stars;
    }
  }

  return {
    total: entries.length,
    byOutcome,
    bytes,
    graded,
    averageStars: graded === 0 ? null : stars / graded,
  };
}

/**
 * @param {ReturnType<typeof summarise>} summary
 * @returns {string[]}
 */
export function describeSummary(summary) {
  const lines = [`${summary.total} accessions, ${summary.bytes} bytes`];
  for (const outcome of OUTCOMES) {
    if (summary.byOutcome[outcome]) lines.push(`  ${summary.byOutcome[outcome]} ${outcome}`);
  }
  if (summary.averageStars !== null) {
    lines.push(`  ${summary.averageStars.toFixed(2)} stars over ${summary.graded} graded`);
  }
  return lines;
}
