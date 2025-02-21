import { describe, expect, it } from 'vitest';

import { readPnm, readPnmHeader, writePnm } from '../../src/codec/pnm.js';
import { Surface } from '../../src/raster/surface.js';

/**
 * @param {Uint8Array} bytes
 * @returns {string}
 */
function asText(bytes) {
  let out = '';
  for (const byte of bytes) out += String.fromCharCode(byte);
  return out;
}

/**
 * @param {readonly number[][]} rows
 * @param {number} [depth]
 * @returns {Surface}
 */
function gray(rows, depth = 8) {
  const s = Surface.create({ width: rows[0].length, height: rows.length, layout: 'gray', depth });
  rows.forEach((row, y) => row.forEach((value, x) => s.set(x, y, 0, value)));
  return s;
}

describe('writePnm in binary', () => {
  it('writes a grayscale header and the samples', () => {
    const out = writePnm(gray([[0, 255]]));
    expect(asText(out.subarray(0, 11))).toBe('P5\n2 1\n255\n');
    expect([...out.subarray(11)]).toEqual([0, 255]);
  });

  it('writes a colour header', () => {
    const s = Surface.create({ width: 1, height: 1, layout: 'rgb' });
    s.setPixel(0, 0, [1, 2, 3]);
    const out = writePnm(s);
    expect(asText(out).startsWith('P6\n1 1\n255\n')).toBe(true);
  });

  it('writes sixteen bit samples big endian', () => {
    const out = writePnm(gray([[1, 65535]], 16));
    const body = out.subarray(out.length - 4);
    expect([...body]).toEqual([0x00, 0x01, 0xff, 0xff]);
    expect(asText(out).startsWith('P5\n2 1\n65535\n')).toBe(true);
  });

  it('puts a comment in the header', () => {
    const out = writePnm(gray([[0]]), { comment: 'stand two, plate 14' });
    expect(asText(out).startsWith('P5\n# stand two, plate 14\n1 1\n255\n')).toBe(true);
    expect(readPnmHeader(out).width).toBe(1);
  });

  it('refuses a comment that would break the header', () => {
    expect(() => writePnm(gray([[0]]), { comment: 'two\nlines' })).toThrow(/cannot span lines/);
  });

  it('refuses a layout netpbm cannot carry', () => {
    const s = Surface.create({ width: 1, height: 1, layout: 'rgba' });
    expect(() => writePnm(s)).toThrow(/gray or rgb and nothing else/);
  });
});

describe('writePnm in ascii', () => {
  it('writes one line per row', () => {
    const out = asText(
      writePnm(
        gray([
          [0, 1],
          [2, 3],
        ]),
        { ascii: true },
      ),
    );
    expect(out).toBe('P2\n2 2\n255\n0 1\n2 3\n');
  });

  it('writes colour samples in order', () => {
    const s = Surface.create({ width: 2, height: 1, layout: 'rgb' });
    s.setPixel(0, 0, [1, 2, 3]);
    s.setPixel(1, 0, [4, 5, 6]);
    expect(asText(writePnm(s, { ascii: true }))).toBe('P3\n2 1\n255\n1 2 3 4 5 6\n');
  });
});

describe('round trips', () => {
  /**
   * @param {Surface} original
   * @param {object} [options]
   */
  function roundTrip(original, options) {
    const back = readPnm(writePnm(original, options));
    expect(back.toJSON()).toEqual(original.toJSON());
    expect([...back.data]).toEqual([...original.data]);
  }

  it('survives a grayscale file both ways', () => {
    const s = gray([
      [0, 1, 254],
      [255, 128, 7],
    ]);
    roundTrip(s);
    roundTrip(s, { ascii: true });
  });

  it('survives a colour file both ways', () => {
    const s = Surface.create({ width: 3, height: 2, layout: 'rgb' });
    for (let y = 0; y < 2; y += 1) {
      for (let x = 0; x < 3; x += 1) s.setPixel(x, y, [x * 10, y * 20, 255 - x]);
    }
    roundTrip(s);
    roundTrip(s, { ascii: true });
  });

  it('survives sixteen bits', () => {
    roundTrip(gray([[0, 1, 32768, 65535]], 16));
    roundTrip(gray([[0, 1, 32768, 65535]], 16), { ascii: true });
  });

  it('survives a comment in the header', () => {
    roundTrip(gray([[9, 9]]), { comment: 'plate 14' });
  });

  it('survives a large flat capture', () => {
    const s = Surface.create({ width: 64, height: 48, layout: 'rgb', fill: 77 });
    roundTrip(s);
  });
});
