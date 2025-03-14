import { describe, expect, it } from 'vitest';

import { packBits, packedLength, unpackBits } from '../../../src/codec/tiff/packbits.js';

/**
 * @param {readonly number[]} values
 * @returns {Uint8Array}
 */
function u8(values) {
  return Uint8Array.from(values);
}

describe('unpackBits', () => {
  it('expands a literal run', () => {
    expect([...unpackBits(u8([2, 10, 20, 30]))]).toEqual([10, 20, 30]);
  });

  it('expands a repeat run', () => {
    expect([...unpackBits(u8([254, 7]))]).toEqual([7, 7, 7]);
  });

  it('handles the longest runs', () => {
    const longLiteral = [127, ...Array.from({ length: 128 }, (_, i) => i)];
    expect(unpackBits(u8(longLiteral))).toHaveLength(128);
    expect(unpackBits(u8([129, 9]))).toHaveLength(128);
  });

  it('skips the no operation control byte', () => {
    expect([...unpackBits(u8([128, 1, 5, 6, 128]))]).toEqual([5, 6]);
  });

  it('expands the example from the specification', () => {
    const packed = u8([
      0xfe, 0xaa, 0x02, 0x80, 0x00, 0x2a, 0xfd, 0xaa, 0x03, 0x80, 0x00, 0x2a, 0x22, 0xf7, 0xaa,
    ]);
    const out = [...unpackBits(packed)];
    expect(out).toHaveLength(24);
    expect(out.slice(0, 3)).toEqual([0xaa, 0xaa, 0xaa]);
    expect(out.slice(3, 6)).toEqual([0x80, 0x00, 0x2a]);
    expect(out.slice(-3)).toEqual([0xaa, 0xaa, 0xaa]);
  });

  it('checks the length when it is told what to expect', () => {
    expect(unpackBits(u8([2, 1, 2, 3]), 3)).toHaveLength(3);
    expect(() => unpackBits(u8([2, 1, 2, 3]), 4)).toThrow(/shorter than the strip claims/);
    expect(() => unpackBits(u8([2, 1, 2, 3]), 2)).toThrow(/longer than the strip claims/);
  });

  it('refuses a literal run that runs off the end', () => {
    expect(() => unpackBits(u8([5, 1, 2]))).toThrow(/reaches past the end/);
  });

  it('refuses a repeat run with nothing to repeat', () => {
    expect(() => unpackBits(u8([254]))).toThrow(/no byte to repeat/);
  });

  it('gives nothing back for nothing', () => {
    expect(unpackBits(u8([]))).toHaveLength(0);
  });
});

describe('packBits', () => {
  it('turns a long repeat into a repeat run', () => {
    const packed = packBits(u8(Array(10).fill(9)));
    expect([...packed]).toEqual([247, 9]);
  });

  it('leaves varied bytes as a literal run', () => {
    expect([...packBits(u8([1, 2, 3]))]).toEqual([2, 1, 2, 3]);
  });

  it('breaks a literal run when a repeat starts', () => {
    const packed = packBits(u8([1, 2, 3, 3, 3, 3]));
    expect([...packed]).toEqual([1, 1, 2, 253, 3]);
  });

  it('does not use a repeat run for a pair', () => {
    expect([...packBits(u8([4, 4]))]).toEqual([1, 4, 4]);
  });

  it('splits a repeat longer than the maximum', () => {
    const packed = packBits(u8(Array(200).fill(3)));
    expect(packed[0]).toBe(129);
    expect(packed).toHaveLength(4);
  });

  it('splits a literal run longer than the maximum', () => {
    const values = Array.from({ length: 300 }, (_, i) => i % 251);
    const packed = packBits(u8(values));
    expect(packed[0]).toBe(127);
    expect([...unpackBits(packed)]).toEqual(values);
  });

  it('gives nothing back for nothing', () => {
    expect(packBits(u8([]))).toHaveLength(0);
  });
});

describe('round trips', () => {
  const cases = [
    [],
    [1],
    [5, 5],
    [5, 5, 5],
    Array(500).fill(0),
    Array.from({ length: 400 }, (_, i) => (i % 7 === 0 ? 200 : i % 251)),
    Array.from({ length: 129 }, (_, i) => i),
  ];

  it.each(cases.map((c, i) => [i, c]))('case %i survives both ways', (_i, values) => {
    const packed = packBits(u8(values));
    expect([...unpackBits(packed, values.length)]).toEqual(values);
  });

  it('compresses a flat run hard', () => {
    const flat = u8(Array(1000).fill(77));
    expect(packBits(flat).length).toBeLessThan(20);
  });

  it('grows a random run only slightly', () => {
    let seed = 3;
    const values = Array.from({ length: 1000 }, () => {
      seed = (seed * 1103515245 + 12345) & 0x7fffffff;
      return seed % 256;
    });
    expect(packBits(u8(values)).length).toBeLessThan(1100);
  });
});

describe('packedLength', () => {
  it('agrees with what packBits produced', () => {
    const values = u8([1, 1, 1, 2, 3, 4]);
    expect(packedLength(values)).toBe(packBits(values).length);
  });
});
