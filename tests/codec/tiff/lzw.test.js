import { describe, expect, it } from 'vitest';

import { lzwDecode, lzwEncode } from '../../../src/codec/tiff/lzw.js';
import { readTiff } from '../../../src/codec/tiff/reader.js';
import { makeTiff } from '../../helpers/make-tiff.js';

/**
 * @param {readonly number[]} values
 * @returns {Uint8Array}
 */
function u8(values) {
  return Uint8Array.from(values);
}

/**
 * @param {number} count
 * @param {number} seed
 * @returns {number[]}
 */
function noise(count, seed) {
  let state = seed;
  return Array.from({ length: count }, () => {
    state = (state * 1103515245 + 12345) & 0x7fffffff;
    return state % 256;
  });
}

describe('round trips', () => {
  const cases = [
    [],
    [0],
    [1, 2, 3],
    Array(4).fill(7),
    Array(1000).fill(0),
    Array.from({ length: 512 }, (_, i) => i % 256),
    Array.from({ length: 5000 }, (_, i) => (i % 3 === 0 ? 200 : i % 256)),
    noise(3000, 11),
    noise(20000, 5),
  ];

  it.each(cases.map((values, i) => [i, values]))('case %i survives both ways', (_i, values) => {
    const encoded = lzwEncode(u8(values));
    expect([...lzwDecode(encoded, values.length)]).toEqual(values);
  });

  it('crosses the nine to ten bit boundary correctly', () => {
    const values = Array.from({ length: 600 }, (_, i) => i % 251);
    expect([...lzwDecode(lzwEncode(u8(values)))]).toEqual(values);
  });

  it('crosses every width boundary on a long run', () => {
    const values = Array.from({ length: 60000 }, (_, i) => (i * 7) % 256);
    expect([...lzwDecode(lzwEncode(u8(values)))]).toEqual(values);
  });

  it('handles the repeated pattern that needs the not yet defined code', () => {
    const values = [1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2];
    expect([...lzwDecode(lzwEncode(u8(values)))]).toEqual(values);
  });
});

describe('lzwEncode', () => {
  it('compresses a flat run hard', () => {
    expect(lzwEncode(u8(Array(10000).fill(3))).length).toBeLessThan(500);
  });

  it('opens with a clear code', () => {
    const encoded = lzwEncode(u8([1, 2, 3]));
    const first = (encoded[0] << 1) | (encoded[1] >> 7);
    expect(first).toBe(256);
  });

  it('gives back only the clear and end codes for nothing', () => {
    expect(lzwDecode(lzwEncode(u8([])), 0)).toHaveLength(0);
  });
});

describe('lzwDecode', () => {
  it('checks the length when it is told what to expect', () => {
    const encoded = lzwEncode(u8([1, 2, 3]));
    expect(() => lzwDecode(encoded, 4)).toThrow(/shorter than the strip claims/);
    expect(() => lzwDecode(encoded, 2)).toThrow(/longer than the strip claims/);
  });

  it('stops at the end code and ignores what follows', () => {
    const encoded = lzwEncode(u8([1, 2, 3]));
    const padded = new Uint8Array(encoded.length + 4);
    padded.set(encoded);
    padded.fill(0xff, encoded.length);
    expect([...lzwDecode(padded, 3)]).toEqual([1, 2, 3]);
  });

  it('refuses a code the table cannot hold', () => {
    // a clear code followed by code three hundred, which nothing has defined
    expect(() => lzwDecode(u8([0x80, 0x4b, 0x00]))).toThrow(/the table does not have/);
  });

  it('stops cleanly when the data runs out mid code', () => {
    const encoded = lzwEncode(u8([1, 2, 3, 4, 5]));
    const cut = encoded.subarray(0, 2);
    expect(lzwDecode(cut).length).toBeLessThanOrEqual(5);
  });
});

describe('through the tiff reader', () => {
  it('reads an lzw strip', () => {
    const samples = Array.from({ length: 64 }, (_, i) => (i * 4) % 256);
    const tiff = makeTiff({ width: 8, height: 8, samples, compression: 5 });
    expect([...readTiff(tiff).surface.data]).toEqual(samples);
  });

  it('reads lzw split across strips', () => {
    const samples = Array.from({ length: 64 }, (_, i) => (i * 4) % 256);
    const tiff = makeTiff({ width: 8, height: 8, samples, compression: 5, rowsPerStrip: 3 });
    expect([...readTiff(tiff).surface.data]).toEqual(samples);
  });

  it('reads an lzw colour strip', () => {
    const samples = Array.from({ length: 48 }, (_, i) => (i * 5) % 256);
    const tiff = makeTiff({
      width: 4,
      height: 4,
      samples,
      channels: 3,
      photometric: 2,
      compression: 5,
    });
    const { surface } = readTiff(tiff);
    expect(surface.layout).toBe('rgb');
    expect([...surface.data]).toEqual(samples);
  });

  it('reads a sixteen bit lzw strip', () => {
    const samples = Array.from({ length: 16 }, (_, i) => i * 4000);
    const tiff = makeTiff({ width: 4, height: 4, samples, depth: 16, compression: 5 });
    expect([...readTiff(tiff).surface.data]).toEqual(samples);
  });
});
