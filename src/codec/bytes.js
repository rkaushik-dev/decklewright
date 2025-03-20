import { crc32 as zlibCrc32 } from 'node:zlib';

import { CodecError } from '../errors.js';

/**
 * Byte level helpers the codecs share.
 *
 * All four of them had grown their own copy of read a big endian word, turn
 * four bytes into a type name, and glue a list of buffers together. They were
 * the same code four times and one of them had a different name for the same
 * thing, so a reader had to check which file they were in before trusting it.
 *
 * The check value is node's now. It is the same polynomial and the same
 * answer, it runs in native code, and it is one less table for anyone to
 * transcribe wrongly.
 */

/**
 * @param {Uint8Array} bytes
 * @param {number} [seed]
 * @returns {number}
 */
export function crc32(bytes, seed = 0) {
  return zlibCrc32(Buffer.from(bytes.buffer, bytes.byteOffset, bytes.byteLength), seed) >>> 0;
}

/**
 * @param {Uint8Array} bytes
 * @param {number} at
 * @returns {number}
 */
export function readUint16BE(bytes, at) {
  return ((bytes[at] << 8) | bytes[at + 1]) >>> 0;
}

/**
 * @param {Uint8Array} bytes
 * @param {number} at
 * @returns {number}
 */
export function readUint32BE(bytes, at) {
  return ((bytes[at] << 24) | (bytes[at + 1] << 16) | (bytes[at + 2] << 8) | bytes[at + 3]) >>> 0;
}

/**
 * @param {Uint8Array} bytes
 * @param {number} at
 * @param {number} value
 * @returns {void}
 */
export function writeUint32BE(bytes, at, value) {
  bytes[at] = (value >>> 24) & 0xff;
  bytes[at + 1] = (value >>> 16) & 0xff;
  bytes[at + 2] = (value >>> 8) & 0xff;
  bytes[at + 3] = value & 0xff;
}

/**
 * @param {number} value
 * @returns {Uint8Array}
 */
export function uint32BE(value) {
  const out = new Uint8Array(4);
  writeUint32BE(out, 0, value);
  return out;
}

/**
 * @param {Uint8Array} bytes
 * @param {number} at
 * @param {number} length
 * @returns {string}
 */
export function readAscii(bytes, at, length) {
  let out = '';
  for (let i = 0; i < length; i += 1) out += String.fromCharCode(bytes[at + i]);
  return out;
}

/**
 * Stops at the first zero, which is how every string in these formats ends.
 *
 * @param {Uint8Array} bytes
 * @param {number} at
 * @param {number} length
 * @returns {string}
 */
export function readAsciiZ(bytes, at, length) {
  let out = '';
  for (let i = 0; i < length; i += 1) {
    const code = bytes[at + i];
    if (code === 0) break;
    out += String.fromCharCode(code);
  }
  return out;
}

/**
 * @param {string} text
 * @returns {Uint8Array}
 */
export function asciiBytes(text) {
  const out = new Uint8Array(text.length);
  for (let i = 0; i < text.length; i += 1) out[i] = text.charCodeAt(i) & 0xff;
  return out;
}

/**
 * Refuses anything wider than a byte rather than truncating it, because a
 * truncated identifier in a metadata chunk is worse than a failed write.
 *
 * @param {string} text
 * @param {string} [what] named in the error
 * @returns {Uint8Array}
 */
export function latin1Bytes(text, what = 'that text') {
  const out = new Uint8Array(text.length);
  for (let i = 0; i < text.length; i += 1) {
    const code = text.charCodeAt(i);
    if (code > 255) {
      throw new CodecError(`${what} holds latin one and nothing wider`, { text });
    }
    out[i] = code;
  }
  return out;
}

/**
 * @param {readonly Uint8Array[]} parts
 * @returns {Uint8Array}
 */
export function concatBytes(parts) {
  let total = 0;
  for (const part of parts) total += part.length;
  const out = new Uint8Array(total);
  let at = 0;
  for (const part of parts) {
    out.set(part, at);
    at += part.length;
  }
  return out;
}
