import { describe, expect, it } from 'vitest';

import { Insets } from '../../../src/geom/insets.js';
import { Size } from '../../../src/geom/size.js';
import { pad, padTo, padUniform } from '../../../src/raster/ops/pad.js';
import { Surface } from '../../../src/raster/surface.js';

/**
 * @param {readonly number[][]} rows
 * @returns {Surface}
 */
function gray(rows) {
  const s = Surface.create({ width: rows[0].length, height: rows.length, layout: 'gray' });
  rows.forEach((row, y) => row.forEach((value, x) => s.set(x, y, 0, value)));
  return s;
}

/**
 * @param {Surface} s
 * @returns {number[][]}
 */
function rowsOf(s) {
  const out = [];
  for (let y = 0; y < s.height; y += 1) out.push([...s.row(y)]);
  return out;
}

describe('pad with a constant', () => {
  it('grows by the insets and fills the border', () => {
    const out = pad(gray([[1, 2]]), new Insets(1, 2, 1, 0), { fill: 9 });
    expect(out.size.toJSON()).toEqual({ width: 4, height: 3 });
    expect(rowsOf(out)).toEqual([
      [9, 9, 9, 9],
      [1, 2, 9, 9],
      [9, 9, 9, 9],
    ]);
  });

  it('defaults the fill to zero', () => {
    const out = padUniform(gray([[5]]), 1);
    expect(rowsOf(out)).toEqual([
      [0, 0, 0],
      [0, 5, 0],
      [0, 0, 0],
    ]);
  });

  it('takes a colour fill', () => {
    const s = Surface.create({ width: 1, height: 1, layout: 'rgb', fill: 1 });
    const out = padUniform(s, 1, { fill: [4, 5, 6] });
    expect(out.getPixel(0, 0)).toEqual([4, 5, 6]);
    expect(out.getPixel(1, 1)).toEqual([1, 1, 1]);
  });

  it('refuses a fill with the wrong channel count', () => {
    const s = Surface.create({ width: 1, height: 1, layout: 'rgb' });
    expect(() => padUniform(s, 1, { fill: [1, 2] })).toThrow(/wrong number of channels/);
  });

  it('is a no op for zero insets', () => {
    const out = pad(gray([[1, 2]]), Insets.zero());
    expect(rowsOf(out)).toEqual([[1, 2]]);
  });
});

describe('pad with edge repeat', () => {
  it('repeats the nearest sample', () => {
    const out = padUniform(
      gray([
        [1, 2],
        [3, 4],
      ]),
      1,
      { mode: 'edge' },
    );
    expect(rowsOf(out)).toEqual([
      [1, 1, 2, 2],
      [1, 1, 2, 2],
      [3, 3, 4, 4],
      [3, 3, 4, 4],
    ]);
  });

  it('works when the capture is a single pixel', () => {
    expect(rowsOf(padUniform(gray([[7]]), 2, { mode: 'edge' }))).toEqual([
      [7, 7, 7, 7, 7],
      [7, 7, 7, 7, 7],
      [7, 7, 7, 7, 7],
      [7, 7, 7, 7, 7],
      [7, 7, 7, 7, 7],
    ]);
  });
});

describe('pad with mirror', () => {
  it('reflects without repeating the edge sample', () => {
    const out = pad(gray([[1, 2, 3]]), new Insets(0, 2, 0, 2), { mode: 'mirror' });
    expect(rowsOf(out)).toEqual([[3, 2, 1, 2, 3, 2, 1]]);
  });

  it('falls back to the single sample when there is nothing to reflect', () => {
    expect(rowsOf(pad(gray([[4]]), new Insets(0, 1, 0, 1), { mode: 'mirror' }))).toEqual([
      [4, 4, 4],
    ]);
  });

  it('mirrors on both axes at once', () => {
    const out = padUniform(
      gray([
        [1, 2],
        [3, 4],
      ]),
      1,
      { mode: 'mirror' },
    );
    expect(rowsOf(out)).toEqual([
      [4, 3, 4, 3],
      [2, 1, 2, 1],
      [4, 3, 4, 3],
      [2, 1, 2, 1],
    ]);
  });
});

describe('padTo', () => {
  it('splits the extra border and puts the odd pixel on the trailing edge', () => {
    const out = padTo(gray([[1, 2]]), Size.of(5, 3), { fill: 8 });
    expect(out.size.toJSON()).toEqual({ width: 5, height: 3 });
    expect(rowsOf(out)).toEqual([
      [8, 8, 8, 8, 8],
      [8, 1, 2, 8, 8],
      [8, 8, 8, 8, 8],
    ]);
  });

  it('is a no op when the sizes already match', () => {
    expect(rowsOf(padTo(gray([[1, 2]]), Size.of(2, 1)))).toEqual([[1, 2]]);
  });

  it('refuses to shrink', () => {
    expect(() => padTo(gray([[1, 2, 3]]), Size.of(2, 1))).toThrow(/cannot shrink a capture/);
  });
});

describe('validation', () => {
  it('refuses a negative or fractional inset', () => {
    const s = gray([[1]]);
    expect(() => pad(s, new Insets(-1, 0, 0, 0))).toThrow(/never negative/);
    expect(() => pad(s, new Insets(0.5, 0, 0, 0))).toThrow(/whole pixels/);
  });

  it('refuses a mode it does not know', () => {
    expect(() => padUniform(gray([[1]]), 1, { mode: 'wrap' })).toThrow(/mode must be one of/);
  });
});
