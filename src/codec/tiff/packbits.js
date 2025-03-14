import { CodecError } from '../../errors.js';

/**
 * PackBits, the run length scheme baseline tiff uses.
 *
 * One control byte then either a literal run or a repeat. It compresses a
 * scanned map sheet by about a third, because the paper between the linework
 * is a long flat run, and it costs nothing to decode. The flatbed writes it by
 * default and the vendor gives no way to turn it off.
 */

/**
 * @param {Uint8Array} bytes
 * @param {number} [expected] how many bytes the caller wants back
 * @returns {Uint8Array}
 */
export function unpackBits(bytes, expected) {
  /** @type {number[]} */
  const out = [];
  let at = 0;
  while (at < bytes.length) {
    const control = bytes[at];
    at += 1;
    if (control === 128) continue;
    if (control < 128) {
      const count = control + 1;
      if (at + count > bytes.length) {
        throw new CodecError('a literal run reaches past the end of the data', { at, count });
      }
      for (let i = 0; i < count; i += 1) out.push(bytes[at + i]);
      at += count;
    } else {
      const count = 257 - control;
      if (at >= bytes.length) {
        throw new CodecError('a repeat run has no byte to repeat', { at });
      }
      const value = bytes[at];
      at += 1;
      for (let i = 0; i < count; i += 1) out.push(value);
    }
    if (expected !== undefined && out.length > expected) {
      throw new CodecError('the unpacked data is longer than the strip claims', {
        expected,
        found: out.length,
      });
    }
  }
  if (expected !== undefined && out.length !== expected) {
    throw new CodecError('the unpacked data is shorter than the strip claims', {
      expected,
      found: out.length,
    });
  }
  return Uint8Array.from(out);
}

/**
 * Pack a run of bytes. Repeats of three or more become a repeat run and
 * everything else goes out literally, which is what the specification
 * recommends and what every reader is tested against.
 *
 * @param {Uint8Array} bytes
 * @returns {Uint8Array}
 */
export function packBits(bytes) {
  /** @type {number[]} */
  const out = [];
  let at = 0;
  while (at < bytes.length) {
    let runLength = 1;
    while (
      at + runLength < bytes.length &&
      runLength < 128 &&
      bytes[at + runLength] === bytes[at]
    ) {
      runLength += 1;
    }

    if (runLength >= 3) {
      out.push(257 - runLength, bytes[at]);
      at += runLength;
      continue;
    }

    let literal = 0;
    while (at + literal < bytes.length && literal < 128) {
      const remaining = bytes.length - (at + literal);
      if (
        remaining >= 3 &&
        bytes[at + literal] === bytes[at + literal + 1] &&
        bytes[at + literal] === bytes[at + literal + 2]
      ) {
        break;
      }
      literal += 1;
    }
    out.push(literal - 1);
    for (let i = 0; i < literal; i += 1) out.push(bytes[at + i]);
    at += literal;
  }
  return Uint8Array.from(out);
}

/**
 * How many bytes a packed run would take, without building it. The strip
 * writer needs this to lay out the byte counts before it commits.
 *
 * @param {Uint8Array} bytes
 * @returns {number}
 */
export function packedLength(bytes) {
  return packBits(bytes).length;
}
