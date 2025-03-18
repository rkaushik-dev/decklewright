import { ERROR_CODES, isDeckleError } from '../errors.js';
import { codecForBytes, codecForPath, formatOf } from './registry.js';

/**
 * Triage for the intake folder.
 *
 * Half of what lands there is not what the work order says it is: a capture
 * that was still being written when the watcher saw it, a file the operator
 * renamed by hand, a tiff from a scanner nobody told us about. Throwing on the
 * first one stops the batch. This reports on every file instead and lets the
 * pipeline decide what to hold back.
 */

/** @typedef {'unreadable'|'truncated'|'unsupported'|'wrongExtension'|'empty'} ProblemKind */
/** @typedef {{kind: ProblemKind, detail: string}} Problem */

/**
 * @typedef {object} Inspection
 * @property {boolean} ok
 * @property {string|null} format
 * @property {{width: number, height: number, layout: string, depth: number}|null} shape
 * @property {Problem[]} problems
 * @property {string|null} path
 */

const TRUNCATION_HINTS = [
  'stops early',
  'stops before',
  'stops inside',
  'shorter than',
  'past the end',
  'runs past',
  'do not cover',
];

/**
 * @param {unknown} error
 * @returns {Problem}
 */
export function classify(error) {
  const message = error instanceof Error ? error.message : String(error);
  if (isDeckleError(error) && error.code === ERROR_CODES.UNSUPPORTED) {
    return { kind: 'unsupported', detail: message };
  }
  if (TRUNCATION_HINTS.some((hint) => message.includes(hint))) {
    return { kind: 'truncated', detail: message };
  }
  return { kind: 'unreadable', detail: message };
}

/**
 * Look at a file without decoding the samples.
 *
 * @param {Uint8Array} bytes
 * @param {object} [options]
 * @param {string} [options.path]
 * @returns {Inspection}
 */
export function inspect(bytes, { path } = {}) {
  /** @type {Problem[]} */
  const problems = [];

  if (bytes.length === 0) {
    return {
      ok: false,
      format: null,
      shape: null,
      path: path ?? null,
      problems: [{ kind: 'empty', detail: 'the file holds no bytes' }],
    };
  }

  const codec = codecForBytes(bytes);
  if (!codec) {
    return {
      ok: false,
      format: null,
      shape: null,
      path: path ?? null,
      problems: [{ kind: 'unreadable', detail: 'nothing here recognises those bytes' }],
    };
  }

  if (path) {
    const byName = codecForPath(path);
    if (byName && byName.name !== codec.name) {
      problems.push({
        kind: 'wrongExtension',
        detail: `named ${byName.name} but the bytes say ${codec.name}`,
      });
    }
  }

  /** @type {Inspection['shape']} */
  let shape = null;
  try {
    shape = codec.probe(bytes);
  } catch (error) {
    problems.push(classify(error));
  }

  return {
    ok: problems.every((problem) => problem.kind === 'wrongExtension') && shape !== null,
    format: codec.name,
    shape,
    path: path ?? null,
    problems,
  };
}

/**
 * The same look, but reading the samples too, which is the only way to catch a
 * strip that decodes short.
 *
 * @param {Uint8Array} bytes
 * @param {object} [options]
 * @param {string} [options.path]
 * @returns {Inspection}
 */
export function inspectDeep(bytes, options = {}) {
  const shallow = inspect(bytes, options);
  if (!shallow.ok) return shallow;
  const codec = codecForBytes(bytes);
  try {
    codec?.read(bytes);
  } catch (error) {
    return { ...shallow, ok: false, problems: [...shallow.problems, classify(error)] };
  }
  return shallow;
}

/**
 * @param {Inspection} inspection
 * @returns {string}
 */
export function describe(inspection) {
  const name = inspection.path ?? '(no name)';
  if (inspection.ok && inspection.shape) {
    const { width, height, layout, depth } = inspection.shape;
    const note = inspection.problems.length > 0 ? ` (${inspection.problems[0].detail})` : '';
    return `${name}: ${inspection.format} ${width}x${height} ${layout}${depth}${note}`;
  }
  const first = inspection.problems[0];
  return `${name}: ${first ? `${first.kind}, ${first.detail}` : 'unreadable'}`;
}

/**
 * @param {readonly {bytes: Uint8Array, path?: string}[]} files
 * @returns {{good: Inspection[], bad: Inspection[]}}
 */
export function triage(files) {
  /** @type {Inspection[]} */
  const good = [];
  /** @type {Inspection[]} */
  const bad = [];
  for (const file of files) {
    const result = inspect(file.bytes, { path: file.path });
    (result.ok ? good : bad).push(result);
  }
  return { good, bad };
}

/**
 * @param {Uint8Array} bytes
 * @returns {boolean}
 */
export function isReadable(bytes) {
  return formatOf(bytes) !== null;
}
