import { describe, expect, it } from 'vitest';

import {
  asciiBytes,
  concatBytes,
  crc32,
  latin1Bytes,
  readAscii,
  readAsciiZ,
  readUint16BE,
  readUint32BE,
  uint32BE,
  writeUint32BE,
} from '../../src/codec/bytes.js';

/**
 * @param {string} text
 * @returns {Uint8Array}
 */
function ascii(text) {
  return Uint8Array.from([...text].map((ch) => ch.charCodeAt(0)));
}

describe('crc32', () => {
  it('matches the published check values', () => {
    expect(crc32(ascii('123456789'))).toBe(0xcbf43926);
    expect(crc32(new Uint8Array())).toBe(0);
    expect(crc32(ascii('a'))).toBe(0xe8b7be43);
  });

  it('can be continued from a seed', () => {
    const whole = crc32(ascii('abcd'));
    const part = crc32(ascii('ab'));
    expect(crc32(ascii('cd'), part)).toBe(whole);
  });

  it('works on a subarray of a larger buffer', () => {
    const whole = ascii('xx123456789xx');
    expect(crc32(whole.subarray(2, 11))).toBe(0xcbf43926);
  });

  it('never comes back negative', () => {
    expect(crc32(ascii('\xff\xff\xff\xff'))).toBeGreaterThanOrEqual(0);
  });
});

describe('the big endian readers', () => {
  const bytes = Uint8Array.from([0x12, 0x34, 0x56, 0x78, 0xff, 0xff, 0xff, 0xff]);

  it('reads two and four byte words', () => {
    expect(readUint16BE(bytes, 0)).toBe(0x1234);
    expect(readUint32BE(bytes, 0)).toBe(0x12345678);
  });

  it('reads the top of the range without going negative', () => {
    expect(readUint32BE(bytes, 4)).toBe(4294967295);
  });
});

describe('the big endian writers', () => {
  it('writes into an existing buffer', () => {
    const out = new Uint8Array(6);
    writeUint32BE(out, 1, 0x12345678);
    expect([...out]).toEqual([0, 0x12, 0x34, 0x56, 0x78, 0]);
  });

  it('builds a fresh four byte word', () => {
    expect([...uint32BE(1)]).toEqual([0, 0, 0, 1]);
    expect([...uint32BE(4294967295)]).toEqual([255, 255, 255, 255]);
  });

  it('round trips through the reader', () => {
    for (const value of [0, 1, 255, 65535, 16777215, 4294967295]) {
      expect(readUint32BE(uint32BE(value), 0)).toBe(value);
    }
  });
});

describe('the text helpers', () => {
  it('reads a fixed run of characters', () => {
    expect(readAscii(ascii('IHDRxx'), 0, 4)).toBe('IHDR');
    expect(readAscii(ascii('xxIEND'), 2, 4)).toBe('IEND');
  });

  it('stops a zero terminated run at the terminator', () => {
    const bytes = Uint8Array.from([65, 66, 0, 67]);
    expect(readAsciiZ(bytes, 0, 4)).toBe('AB');
    expect(readAsciiZ(bytes, 3, 1)).toBe('C');
  });

  it('turns text into bytes and back', () => {
    expect(readAscii(asciiBytes('plate 14'), 0, 8)).toBe('plate 14');
  });

  it('accepts every latin one code point', () => {
    expect([...latin1Bytes('ÿ')]).toEqual([255]);
  });

  it('refuses anything wider and names what it was writing', () => {
    expect(() => latin1Bytes('plate — 14', 'a png text key')).toThrow(
      /a png text key holds latin one/,
    );
  });
});

describe('concatBytes', () => {
  it('glues buffers in order', () => {
    const out = concatBytes([ascii('ab'), ascii('cd'), ascii('e')]);
    expect(readAscii(out, 0, 5)).toBe('abcde');
  });

  it('copes with an empty list and empty members', () => {
    expect(concatBytes([])).toHaveLength(0);
    expect(concatBytes([new Uint8Array(), ascii('x')])).toHaveLength(1);
  });

  it('copies rather than aliasing', () => {
    const first = ascii('ab');
    const out = concatBytes([first]);
    out[0] = 0;
    expect(first[0]).toBe(97);
  });
});
