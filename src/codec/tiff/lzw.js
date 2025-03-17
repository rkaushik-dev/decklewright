import { CodecError } from '../../errors.js';

/**
 * The tiff flavour of lzw.
 *
 * Codes are packed most significant bit first, the width grows one bit early
 * compared with the gif version, and the dictionary is cleared at code two
 * five six. That early change is the part every implementation gets wrong
 * once, and the symptom is a strip that decodes correctly for a few hundred
 * bytes and then turns to noise.
 */

const CLEAR = 256;
const END = 257;
const FIRST = 258;
const MAX_WIDTH = 12;

class BitReader {
  /**
   * @param {Uint8Array} bytes
   */
  constructor(bytes) {
    this.bytes = bytes;
    this.at = 0;
    this.bit = 0;
  }

  /**
   * @param {number} width
   * @returns {number} the code, or minus one when the data runs out
   */
  read(width) {
    let value = 0;
    for (let i = 0; i < width; i += 1) {
      if (this.at >= this.bytes.length) return -1;
      const bit = (this.bytes[this.at] >> (7 - this.bit)) & 1;
      value = (value << 1) | bit;
      this.bit += 1;
      if (this.bit === 8) {
        this.bit = 0;
        this.at += 1;
      }
    }
    return value;
  }
}

class BitWriter {
  constructor() {
    /** @type {number[]} */
    this.bytes = [];
    this.current = 0;
    this.bit = 0;
  }

  /**
   * @param {number} value
   * @param {number} width
   * @returns {void}
   */
  write(value, width) {
    for (let i = width - 1; i >= 0; i -= 1) {
      this.current = (this.current << 1) | ((value >> i) & 1);
      this.bit += 1;
      if (this.bit === 8) {
        this.bytes.push(this.current & 0xff);
        this.current = 0;
        this.bit = 0;
      }
    }
  }

  /** @returns {Uint8Array} */
  finish() {
    if (this.bit > 0) {
      this.bytes.push((this.current << (8 - this.bit)) & 0xff);
      this.current = 0;
      this.bit = 0;
    }
    return Uint8Array.from(this.bytes);
  }
}

/**
 * @param {number} next the next code that will be assigned
 * @returns {number}
 */
function widthFor(next) {
  if (next >= 2048) return 12;
  if (next >= 1024) return 11;
  if (next >= 512) return 10;
  return 9;
}

/**
 * @param {Uint8Array} bytes
 * @param {number} [expected]
 * @returns {Uint8Array}
 */
export function lzwDecode(bytes, expected) {
  const reader = new BitReader(bytes);
  /** @type {number[][]} */
  let table = [];
  /** @type {number[]} */
  const out = [];

  const reset = () => {
    table = new Array(FIRST);
    for (let i = 0; i < 256; i += 1) table[i] = [i];
    table[CLEAR] = [];
    table[END] = [];
  };
  reset();

  let width = 9;
  /** @type {number[]|null} */
  let previous = null;

  for (;;) {
    const code = reader.read(width);
    if (code < 0 || code === END) break;
    if (code === CLEAR) {
      reset();
      width = 9;
      previous = null;
      continue;
    }

    /** @type {number[]} */
    let entry;
    if (code < table.length && table[code]) {
      entry = table[code];
    } else if (previous && code === table.length) {
      entry = [...previous, previous[0]];
    } else {
      throw new CodecError('the compressed data holds a code the table does not have', {
        code,
        size: table.length,
      });
    }

    for (const value of entry) out.push(value);
    // a well behaved encoder clears before the table fills, and one that does
    // not would desync here rather than at some later strip
    if (previous && table.length < 4094) table.push([...previous, entry[0]]);
    previous = entry;
    width = Math.min(MAX_WIDTH, widthFor(table.length + 1));

    if (expected !== undefined && out.length > expected) {
      throw new CodecError('the decompressed data is longer than the strip claims', {
        expected,
        found: out.length,
      });
    }
  }

  if (expected !== undefined && out.length !== expected) {
    throw new CodecError('the decompressed data is shorter than the strip claims', {
      expected,
      found: out.length,
    });
  }
  return Uint8Array.from(out);
}

/**
 * A matching encoder. The lab never writes lzw, but a decoder with no encoder
 * can only ever be tested against fixtures somebody else made, and then a
 * disagreement tells you nothing about which side is wrong.
 *
 * @param {Uint8Array} bytes
 * @returns {Uint8Array}
 */
export function lzwEncode(bytes) {
  const writer = new BitWriter();
  /** @type {Map<string, number>} */
  let table = new Map();
  let next = FIRST;
  let width = 9;

  const reset = () => {
    table = new Map();
    for (let i = 0; i < 256; i += 1) table.set(String.fromCharCode(i), i);
    next = FIRST;
    width = 9;
  };

  reset();
  writer.write(CLEAR, width);

  let current = '';
  for (const byte of bytes) {
    const symbol = String.fromCharCode(byte);
    const candidate = current + symbol;
    if (table.has(candidate)) {
      current = candidate;
      continue;
    }
    writer.write(/** @type {number} */ (table.get(current)), width);
    table.set(candidate, next);
    next += 1;
    width = Math.min(MAX_WIDTH, widthFor(next));
    if (next >= 4094) {
      writer.write(CLEAR, width);
      reset();
    }
    current = symbol;
  }
  if (current.length > 0) writer.write(/** @type {number} */ (table.get(current)), width);
  writer.write(END, width);
  return writer.finish();
}
