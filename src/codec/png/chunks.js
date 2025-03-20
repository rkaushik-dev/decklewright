import { CodecError } from '../../errors.js';
import { concatBytes, crc32, readAscii, readUint32BE, writeUint32BE } from '../bytes.js';

/**
 * The png container: a signature and then a run of length, type, data, check.
 *
 * The lab cares about the container as much as the pixels, because the
 * archival profile says a master carries its capture date and its collection
 * identifier in text chunks, and a service copy carries neither. Splitting and
 * reassembling without decoding is how that gets enforced.
 */

export const PNG_SIGNATURE = Object.freeze([137, 80, 78, 71, 13, 10, 26, 10]);

/** @typedef {{type: string, data: Uint8Array, offset: number, crc: number}} Chunk */

/**
 * @param {Uint8Array} bytes
 * @returns {boolean}
 */
export function looksLikePng(bytes) {
  if (bytes.length < 8) return false;
  return PNG_SIGNATURE.every((value, i) => bytes[i] === value);
}

/**
 * @param {Uint8Array} bytes
 * @param {object} [options]
 * @param {boolean} [options.checkCrc]
 * @returns {Chunk[]}
 */
export function splitChunks(bytes, { checkCrc = true } = {}) {
  if (!looksLikePng(bytes)) throw new CodecError('that is not a png, the signature is wrong', {});
  /** @type {Chunk[]} */
  const chunks = [];
  let at = 8;
  while (at < bytes.length) {
    if (at + 8 > bytes.length) {
      throw new CodecError('the file stops inside a chunk header', { at });
    }
    const length = readUint32BE(bytes, at);
    const type = readAscii(bytes, at + 4, 4);
    const start = at + 8;
    if (start + length + 4 > bytes.length) {
      throw new CodecError('the file stops inside a chunk body', { type, at, length });
    }
    const data = bytes.subarray(start, start + length);
    const crc = readUint32BE(bytes, start + length);
    if (checkCrc) {
      const expected = crc32(bytes.subarray(at + 4, start + length));
      if (expected !== crc) {
        throw new CodecError(`the check value on the ${type} chunk does not match`, {
          type,
          expected,
          found: crc,
        });
      }
    }
    chunks.push({ type, data, offset: at, crc });
    at = start + length + 4;
    if (type === 'IEND') break;
  }
  if (chunks.length === 0 || chunks[0].type !== 'IHDR') {
    throw new CodecError('a png has to open with an IHDR chunk', {
      first: chunks[0]?.type ?? null,
    });
  }
  if (chunks[chunks.length - 1].type !== 'IEND') {
    throw new CodecError('a png has to close with an IEND chunk', {});
  }
  return chunks;
}

/**
 * @param {string} type
 * @param {Uint8Array} data
 * @returns {Uint8Array}
 */
export function buildChunk(type, data) {
  if (type.length !== 4) throw new CodecError('a chunk type is four characters', { type });
  const out = new Uint8Array(12 + data.length);
  writeUint32BE(out, 0, data.length);
  for (let i = 0; i < 4; i += 1) out[4 + i] = type.charCodeAt(i);
  out.set(data, 8);
  writeUint32BE(out, 8 + data.length, crc32(out.subarray(4, 8 + data.length)));
  return out;
}

/**
 * @param {readonly {type: string, data: Uint8Array}[]} chunks
 * @returns {Uint8Array}
 */
export function assemble(chunks) {
  const parts = chunks.map((chunk) => buildChunk(chunk.type, chunk.data));
  return concatBytes([Uint8Array.from(PNG_SIGNATURE), ...parts]);
}

/**
 * @param {readonly Chunk[]} chunks
 * @param {string} type
 * @returns {Chunk|undefined}
 */
export function findChunk(chunks, type) {
  return chunks.find((chunk) => chunk.type === type);
}

/**
 * @param {readonly Chunk[]} chunks
 * @param {string} type
 * @returns {Chunk[]}
 */
export function chunksOfType(chunks, type) {
  return chunks.filter((chunk) => chunk.type === type);
}

/**
 * The four case bits carry meaning. A lower case first letter means the chunk
 * is optional, and a lower case last letter means a rewriter that does not
 * understand it may keep it. That is exactly the rule the derivative stage
 * needs when it strips a master down to a service copy.
 *
 * @param {string} type
 * @returns {{critical: boolean, publicChunk: boolean, safeToCopy: boolean}}
 */
export function chunkFlags(type) {
  return {
    critical: (type.charCodeAt(0) & 0x20) === 0,
    publicChunk: (type.charCodeAt(1) & 0x20) === 0,
    safeToCopy: (type.charCodeAt(3) & 0x20) !== 0,
  };
}
