import { CodecError, UnsupportedError } from '../errors.js';
import { assertNonEmptyString } from '../util/assert.js';
import { looksLikePnm, probePnm, readPnm, writePnm } from './pnm.js';
import { looksLikePng } from './png/chunks.js';
import { decodePng, probePng } from './png/decode.js';
import { encodePng } from './png/encode.js';

/** @typedef {import('../raster/surface.js').Surface} Surface */
/** @typedef {{width: number, height: number, layout: string, depth: number}} Shape */

/**
 * Which codec handles which bytes.
 *
 * Nothing anywhere else in the tree names a format. The intake stage reads
 * whatever landed in the folder, the derivative stage writes whatever the
 * recipe asked for, and both of them come through here. Extensions are used
 * only when there are no bytes to look at yet.
 */

/**
 * @typedef {object} Codec
 * @property {string} name
 * @property {string[]} extensions
 * @property {(bytes: Uint8Array) => boolean} sniff
 * @property {(bytes: Uint8Array) => Surface} read
 * @property {(bytes: Uint8Array) => Shape} probe
 * @property {((surface: Surface, options?: object) => Uint8Array)|null} write
 */

/** @type {Codec[]} */
const CODECS = [
  {
    name: 'png',
    extensions: ['.png'],
    sniff: looksLikePng,
    read: (bytes) => decodePng(bytes).surface,
    probe: probePng,
    write: (surface, options) => encodePng(surface, options),
  },
  {
    name: 'pnm',
    extensions: ['.pnm', '.pgm', '.ppm'],
    sniff: looksLikePnm,
    read: readPnm,
    probe: probePnm,
    write: (surface, options) => writePnm(surface, options),
  },
];

export const CODEC_NAMES = Object.freeze(CODECS.map((codec) => codec.name));

/**
 * @param {string} name
 * @returns {Codec}
 */
export function codecNamed(name) {
  assertNonEmptyString(name, 'name');
  const found = CODECS.find((codec) => codec.name === name.toLowerCase());
  if (!found) {
    throw new UnsupportedError(`no codec called ${name}`, { name, known: [...CODEC_NAMES] });
  }
  return found;
}

/**
 * @param {string} path
 * @returns {Codec|null}
 */
export function codecForPath(path) {
  const lower = path.toLowerCase();
  const dot = lower.lastIndexOf('.');
  if (dot < 0) return null;
  const extension = lower.slice(dot);
  return CODECS.find((codec) => codec.extensions.includes(extension)) ?? null;
}

/**
 * @param {Uint8Array} bytes
 * @returns {Codec|null}
 */
export function codecForBytes(bytes) {
  return CODECS.find((codec) => codec.sniff(bytes)) ?? null;
}

/**
 * The bytes decide. A file named .png that is really a netpbm is read as a
 * netpbm, because the watch folder receives whatever the stand software felt
 * like naming it and arguing with the name helps nobody.
 *
 * @param {Uint8Array} bytes
 * @param {object} [options]
 * @param {string} [options.path] used only in the error message
 * @returns {Surface}
 */
export function readImage(bytes, { path } = {}) {
  const codec = codecForBytes(bytes);
  if (!codec) {
    throw new CodecError('nothing here recognises those bytes', {
      path: path ?? null,
      head: [...bytes.subarray(0, 8)],
    });
  }
  return codec.read(bytes);
}

/**
 * @param {Uint8Array} bytes
 * @param {object} [options]
 * @param {string} [options.path]
 * @returns {Shape}
 */
export function probeImage(bytes, { path } = {}) {
  const codec = codecForBytes(bytes);
  if (!codec) {
    throw new CodecError('nothing here recognises those bytes', {
      path: path ?? null,
      head: [...bytes.subarray(0, 8)],
    });
  }
  return codec.probe(bytes);
}

/**
 * @param {Surface} surface
 * @param {string} format
 * @param {object} [options]
 * @returns {Uint8Array}
 */
export function writeImage(surface, format, options) {
  const codec = codecNamed(format);
  if (!codec.write) {
    throw new UnsupportedError(`${codec.name} is read only here`, { format });
  }
  return codec.write(surface, options);
}

/**
 * @param {string} format
 * @returns {string} including the leading dot
 */
export function extensionFor(format) {
  return codecNamed(format).extensions[0];
}

/**
 * @param {Uint8Array} bytes
 * @returns {string|null}
 */
export function formatOf(bytes) {
  return codecForBytes(bytes)?.name ?? null;
}
