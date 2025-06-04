import {
  choice,
  edges,
  flag,
  group,
  length,
  list,
  number,
  readGroup,
  text,
  tolerance,
} from './schema.js';

/**
 * What a collection profile actually says.
 *
 * Read this file to find out what a curator may write. Everything a profile
 * can control is here and nothing is controlled anywhere else, which is the
 * whole point: the code has no opinions about a collection, only about how to
 * carry out the ones written down.
 */

/** The formats a derivative may be written in. */
export const OUTPUT_FORMATS = Object.freeze(['png', 'pnm']);

/** What a crop is measured from. */
export const ANCHORS = Object.freeze(['sheet', 'neatline']);

const captureFields = {
  sigma: number({ min: 1, max: 40, fallback: 6 }),
  speckle: number({ min: 0, max: 20, integer: true, fallback: 2 }),
  gap: number({ min: 0, max: 20, integer: true, fallback: 3 }),
  expectNeatline: flag({ fallback: false }),
  barLength: length({ fallback: '6in' }),
  claimedPpi: number({ min: 1, fallback: 400 }),
};

const cropFields = {
  anchor: choice(ANCHORS, { fallback: 'sheet' }),
  bleed: edges({ fallback: '0mm' }),
  safeArea: edges({ fallback: '0mm' }),
  aspect: number({ min: 0.05, max: 20 }),
  aspectDirection: choice(['grow', 'shrink'], { fallback: 'grow' }),
  minimumPpi: number({ min: 1, fallback: 300 }),
  snapTo: number({ min: 1, integer: true, fallback: 2 }),
  snapDirection: choice(['nearest', 'up', 'down'], { fallback: 'up' }),
  rounding: choice(['nearest', 'outward', 'inward', 'floor'], { fallback: 'outward' }),
  background: number({ min: 0, max: 65535, integer: true, fallback: 0 }),
  deskew: flag({ fallback: true }),
};

const derivativeFields = {
  name: text({ required: true }),
  format: choice(OUTPUT_FORMATS, { fallback: 'png' }),
  longestSide: number({ min: 1, integer: true }),
  scale: number({ min: 0.001, max: 4 }),
  depth: choice(['8', '16'], { fallback: '8' }),
  layout: choice(['gray', 'rgb'], { fallback: 'rgb' }),
  kernel: choice(['box', 'triangle', 'catmullRom', 'mitchell', 'lanczos2', 'lanczos3'], {
    fallback: 'catmullRom',
  }),
  keepMetadata: flag({ fallback: false }),
};

const qcFields = {
  samplingPpi: number({ min: 1, fallback: 400 }),
  samplingTolerance: tolerance({ fallback: '1%' }),
  skewTolerance: number({ min: 0, max: 45, fallback: 0.3 }),
  illuminationTolerance: number({ min: 0, max: 1, fallback: 0.1 }),
  grayBalanceTolerance: number({ min: 0, max: 255, fallback: 6 }),
  minimumSnr: number({ min: 0, fallback: 30 }),
  maximumClipping: number({ min: 0, max: 1, fallback: 0.005 }),
  minimumStars: number({ min: 1, max: 4, integer: true, fallback: 3 }),
};

/** Every field of a profile, as one tree. */
export const PROFILE_FIELDS = Object.freeze({
  name: text({ required: true }),
  title: text({ fallback: 'untitled collection' }),
  version: number({ min: 1, integer: true, fallback: 1 }),
  extends: text(),
  capture: group(captureFields, { fallback: {} }),
  crop: group(cropFields, { fallback: {} }),
  derivatives: list(group(derivativeFields), { fallback: [] }),
  qc: group(qcFields, { fallback: {} }),
  naming: group(
    {
      pattern: text({ fallback: '{accession}_{derivative}' }),
      lowercase: flag({ fallback: true }),
    },
    { fallback: {} },
  ),
});

/**
 * Read a written profile into typed values. Nothing here looks at whether the
 * settings make sense together, which is a separate pass with its own errors.
 *
 * @param {unknown} document
 * @returns {Record<string, unknown>}
 */
export function parseProfile(document) {
  return readGroup(PROFILE_FIELDS, document, '');
}

/**
 * @returns {string[]} every path a profile may set, in reading order
 */
export function settingPaths() {
  /** @type {string[]} */
  const out = [];
  const walk = (fields, prefix) => {
    for (const [key, spec] of Object.entries(fields)) {
      const path = prefix ? `${prefix}.${key}` : key;
      out.push(path);
      if (spec.describe === 'a group' && spec.fields) walk(spec.fields, path);
    }
  };
  walk(PROFILE_FIELDS, '');
  return out;
}

/**
 * @param {Record<string, unknown>} profile
 * @param {string} path dotted
 * @returns {unknown}
 */
export function settingAt(profile, path) {
  let current = /** @type {any} */ (profile);
  for (const part of path.split('.')) {
    if (current === undefined || current === null) return undefined;
    current = current[part];
  }
  return current;
}
