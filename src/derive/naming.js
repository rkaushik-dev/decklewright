import { BadArgumentError } from '../errors.js';
import { fillName } from '../profile/settings.js';
import { assertNonEmptyString } from '../util/assert.js';

/**
 * What the files are called.
 *
 * A name is the only part of a delivery that a person types, reads out over a
 * phone and pastes into a spreadsheet, so it is worth more care than its size
 * suggests. The rules here are the ones a state archive already lives with:
 * nothing that a case insensitive filesystem will collide, nothing that needs
 * quoting in a shell, and nothing that a thirty year old catalogue import will
 * choke on.
 *
 * The pattern itself comes off the profile, because every collection was named
 * by a different person in a different decade and none of them will be renamed
 * now.
 */

/** Characters a name may keep. Everything else becomes a separator. */
const KEEP = /[^a-zA-Z0-9._-]+/g;

/** Names Windows will not let a file have, whatever the extension. */
export const RESERVED = Object.freeze([
  'con',
  'prn',
  'aux',
  'nul',
  'com1',
  'com2',
  'com3',
  'com4',
  'lpt1',
  'lpt2',
  'lpt3',
  'lpt4',
]);

/** How long a single name may be, before the extension. */
export const MAX_STEM = 96;

/**
 * Fold anything into something that will survive being a file name.
 *
 * The separator run is collapsed rather than kept, because an accession typed
 * with two spaces and one typed with one are the same accession and two files
 * whose names differ only in that are a problem nobody will diagnose.
 *
 * @param {string} value
 * @param {object} [options]
 * @param {string} [options.separator]
 * @returns {string}
 */
export function slug(value, { separator = '-' } = {}) {
  const folded = String(value)
    .normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(KEEP, separator);
  const trimmed = folded.replace(new RegExp(`^\\${separator}+|\\${separator}+$`, 'g'), '');
  const collapsed = trimmed.replace(new RegExp(`\\${separator}{2,}`, 'g'), separator);
  return collapsed;
}

/**
 * A stem that a filesystem will accept. Reserved names get a trailing
 * underscore rather than being rejected, because an accession really can be
 * called `aux` and refusing it helps nobody.
 *
 * @param {string} value
 * @param {object} [options]
 * @param {number} [options.max]
 * @returns {string}
 */
export function safeStem(value, { max = MAX_STEM } = {}) {
  const base = slug(value);
  if (base === '') throw new BadArgumentError('there is nothing left of that name', { value });
  const cut = base.length > max ? base.slice(0, max).replace(/[-._]+$/, '') : base;
  return RESERVED.includes(cut.toLowerCase()) ? `${cut}_` : cut;
}

/**
 * The name one derivative gets.
 *
 * @param {object} args
 * @param {Record<string, any>} args.profile the resolved profile
 * @param {string} args.accession
 * @param {string} args.derivative
 * @param {string} args.extension including the leading dot
 * @param {Record<string, string|number>} [args.extra] more placeholders
 * @returns {string}
 */
export function nameFor({ profile, accession, derivative, extension, extra = {} }) {
  assertNonEmptyString(accession, 'accession');
  assertNonEmptyString(derivative, 'derivative');

  const filled = fillName(profile, {
    accession: safeStem(accession),
    derivative: safeStem(derivative),
    ...extra,
  });
  return `${safeStem(filled)}${extension}`;
}

/**
 * Every name a set of encoded derivatives will be written under.
 *
 * @param {object} args
 * @param {Record<string, any>} args.profile
 * @param {string} args.accession
 * @param {readonly {name: string, extension: string}[]} args.encoded
 * @param {Record<string, string|number>} [args.extra]
 * @returns {Record<string, string>} derivative name to file name
 */
export function namesFor({ profile, accession, encoded, extra }) {
  /** @type {Record<string, string>} */
  const out = {};
  for (const item of encoded) {
    out[item.name] = nameFor({
      profile,
      accession,
      derivative: item.name,
      extension: item.extension,
      extra,
    });
  }
  return out;
}

/**
 * Two names that differ only in case, which is one file on the filesystems
 * half of this material will end up on.
 *
 * @param {readonly string[]} names
 * @returns {string[][]} each group that would collide, sorted
 */
export function collisions(names) {
  /** @type {Map<string, string[]>} */
  const byFold = new Map();
  for (const name of names) {
    const key = name.toLowerCase();
    const group = byFold.get(key);
    if (group) group.push(name);
    else byFold.set(key, [name]);
  }
  return [...byFold.values()].filter((group) => group.length > 1).map((group) => [...group].sort());
}

/**
 * Everything wrong with a set of names, before anything is written.
 *
 * @param {Record<string, string>} names
 * @returns {string[]}
 */
export function objectionsToNames(names) {
  /** @type {string[]} */
  const out = [];
  const values = Object.values(names);
  if (values.length === 0) return ['nothing was named'];

  for (const group of collisions(values)) {
    out.push(`${group.join(' and ')} differ only in case`);
  }
  for (const [derivative, name] of Object.entries(names)) {
    if (name.length > MAX_STEM + 8) out.push(`the name for ${derivative} is too long`);
    if (!/\.[a-z0-9]+$/i.test(name)) out.push(`the name for ${derivative} has no extension`);
  }
  return out;
}

/**
 * Pull an accession back out of a name written under a pattern. This is what
 * an intake run uses when a folder arrives already named and nobody kept the
 * spreadsheet.
 *
 * @param {string} name
 * @param {Record<string, any>} profile
 * @returns {Record<string, string>|null} the placeholder values, or null
 */
export function parseName(name, profile) {
  const stem = name.replace(/\.[a-z0-9]+$/i, '');
  /** @type {string[]} */
  const keys = [];
  const pattern = profile.naming.pattern.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const expression = pattern.replace(/\\?\{(\w+)\\?\}/g, (whole, key) => {
    keys.push(key);
    return '([a-zA-Z0-9._-]+?)';
  });

  const match = new RegExp(`^${expression}$`).exec(stem);
  if (!match) return null;

  /** @type {Record<string, string>} */
  const out = {};
  keys.forEach((key, i) => {
    out[key] = match[i + 1];
  });
  return out;
}
