import { describe, expect, it } from 'vitest';

import {
  applyExifOrientation,
  flipHorizontal,
  flipVertical,
  rotateQuarters,
  transpose,
} from '../../../src/raster/ops/orient.js';
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

const sample = gray([
  [1, 2, 3],
  [4, 5, 6],
]);

describe('flipHorizontal', () => {
  it('reverses each row', () => {
    expect(rowsOf(flipHorizontal(sample))).toEqual([
      [3, 2, 1],
      [6, 5, 4],
    ]);
  });

  it('is its own inverse', () => {
    expect(rowsOf(flipHorizontal(flipHorizontal(sample)))).toEqual(rowsOf(sample));
  });

  it('carries every channel of a colour capture', () => {
    const colour = Surface.create({ width: 2, height: 1, layout: 'rgb' });
    colour.setPixel(0, 0, [1, 2, 3]);
    colour.setPixel(1, 0, [4, 5, 6]);
    const out = flipHorizontal(colour);
    expect(out.getPixel(0, 0)).toEqual([4, 5, 6]);
    expect(out.getPixel(1, 0)).toEqual([1, 2, 3]);
  });
});

describe('flipVertical', () => {
  it('reverses the row order', () => {
    expect(rowsOf(flipVertical(sample))).toEqual([
      [4, 5, 6],
      [1, 2, 3],
    ]);
  });

  it('is its own inverse', () => {
    expect(rowsOf(flipVertical(flipVertical(sample)))).toEqual(rowsOf(sample));
  });
});

describe('transpose', () => {
  it('swaps the axes', () => {
    const out = transpose(sample);
    expect(out.size.toJSON()).toEqual({ width: 2, height: 3 });
    expect(rowsOf(out)).toEqual([
      [1, 4],
      [2, 5],
      [3, 6],
    ]);
  });

  it('is its own inverse', () => {
    expect(rowsOf(transpose(transpose(sample)))).toEqual(rowsOf(sample));
  });
});

describe('rotateQuarters', () => {
  it('turns clockwise once', () => {
    const out = rotateQuarters(sample, 1);
    expect(out.size.toJSON()).toEqual({ width: 2, height: 3 });
    expect(rowsOf(out)).toEqual([
      [4, 1],
      [5, 2],
      [6, 3],
    ]);
  });

  it('turns twice', () => {
    expect(rowsOf(rotateQuarters(sample, 2))).toEqual([
      [6, 5, 4],
      [3, 2, 1],
    ]);
  });

  it('turns three times', () => {
    expect(rowsOf(rotateQuarters(sample, 3))).toEqual([
      [3, 6],
      [2, 5],
      [1, 4],
    ]);
  });

  it('folds four turns back to a copy', () => {
    const out = rotateQuarters(sample, 4);
    expect(rowsOf(out)).toEqual(rowsOf(sample));
    expect(out).not.toBe(sample);
  });

  it('reads a negative count as the other way round', () => {
    expect(rowsOf(rotateQuarters(sample, -1))).toEqual(rowsOf(rotateQuarters(sample, 3)));
    expect(rowsOf(rotateQuarters(sample, -5))).toEqual(rowsOf(rotateQuarters(sample, 3)));
  });

  it('loses no samples over a full circuit', () => {
    let out = sample;
    for (let i = 0; i < 4; i += 1) out = rotateQuarters(out, 1);
    expect(rowsOf(out)).toEqual(rowsOf(sample));
  });

  it('refuses a fractional turn count', () => {
    expect(() => rotateQuarters(sample, 0.5)).toThrow(/turns must be a whole number/);
  });
});

describe('applyExifOrientation', () => {
  it('leaves orientation one alone', () => {
    expect(rowsOf(applyExifOrientation(sample, 1))).toEqual(rowsOf(sample));
  });

  it('matches the named transform for each tag', () => {
    expect(rowsOf(applyExifOrientation(sample, 2))).toEqual(rowsOf(flipHorizontal(sample)));
    expect(rowsOf(applyExifOrientation(sample, 3))).toEqual(rowsOf(rotateQuarters(sample, 2)));
    expect(rowsOf(applyExifOrientation(sample, 4))).toEqual(rowsOf(flipVertical(sample)));
    expect(rowsOf(applyExifOrientation(sample, 5))).toEqual(rowsOf(transpose(sample)));
    expect(rowsOf(applyExifOrientation(sample, 6))).toEqual(rowsOf(rotateQuarters(sample, 1)));
    expect(rowsOf(applyExifOrientation(sample, 8))).toEqual(rowsOf(rotateQuarters(sample, 3)));
  });

  it('handles the transverse case', () => {
    const out = applyExifOrientation(sample, 7);
    expect(out.size.toJSON()).toEqual({ width: 2, height: 3 });
    expect(rowsOf(out)).toEqual([
      [6, 3],
      [5, 2],
      [4, 1],
    ]);
  });

  it('treats an unknown tag as no change', () => {
    expect(rowsOf(applyExifOrientation(sample, 99))).toEqual(rowsOf(sample));
  });
});
