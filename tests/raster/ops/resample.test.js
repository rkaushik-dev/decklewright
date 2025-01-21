import { describe, expect, it } from 'vitest';

import { Size } from '../../../src/geom/size.js';
import {
  fitLongestSide,
  reduceInSteps,
  resample,
  scaleBy,
} from '../../../src/raster/ops/resample.js';
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

/**
 * @param {number} width
 * @param {number} height
 * @returns {Surface}
 */
function checker(width, height) {
  const s = Surface.create({ width, height, layout: 'gray' });
  for (let y = 0; y < height; y += 1) {
    for (let x = 0; x < width; x += 1) s.set(x, y, 0, (x + y) % 2 === 0 ? 255 : 0);
  }
  return s;
}

describe('resample', () => {
  it('halves a flat block to its average', () => {
    const out = resample(
      gray([
        [0, 100],
        [200, 60],
      ]),
      Size.of(1, 1),
      { kernel: 'box' },
    );
    expect(out.get(0, 0)).toBe(90);
  });

  it('keeps a constant field constant at any size', () => {
    const flat = Surface.create({ width: 8, height: 8, layout: 'gray', fill: 137 });
    for (const size of [Size.of(3, 5), Size.of(16, 2), Size.of(8, 8)]) {
      const out = resample(flat, size, { kernel: 'catmullRom' });
      expect([...new Set(out.data)]).toEqual([137]);
    }
  });

  it('clones rather than working when the size already matches', () => {
    const s = gray([[1, 2]]);
    const out = resample(s, Size.of(2, 1));
    expect(rowsOf(out)).toEqual([[1, 2]]);
    expect(out).not.toBe(s);
  });

  it('doubles with a box by repeating samples', () => {
    const out = resample(gray([[0, 255]]), Size.of(4, 1), { kernel: 'box' });
    expect(rowsOf(out)).toEqual([[0, 0, 255, 255]]);
  });

  it('doubles with a triangle by interpolating between them', () => {
    const out = resample(gray([[0, 240]]), Size.of(4, 1), { kernel: 'triangle' });
    const row = rowsOf(out)[0];
    expect(row[0]).toBe(0);
    expect(row[3]).toBe(240);
    expect(row[1]).toBeGreaterThan(0);
    expect(row[1]).toBeLessThan(row[2]);
  });

  it('averages a checker to mid gray when reduced hard', () => {
    const out = resample(checker(16, 16), Size.of(1, 1), { kernel: 'box' });
    expect(out.get(0, 0)).toBeGreaterThan(120);
    expect(out.get(0, 0)).toBeLessThan(136);
  });

  it('handles a colour capture channel by channel', () => {
    const s = Surface.create({ width: 2, height: 1, layout: 'rgb' });
    s.setPixel(0, 0, [0, 100, 200]);
    s.setPixel(1, 0, [100, 200, 0]);
    const out = resample(s, Size.of(1, 1), { kernel: 'box' });
    expect(out.getPixel(0, 0)).toEqual([50, 150, 100]);
  });

  it('keeps the depth of the source', () => {
    const s = Surface.create({ width: 4, height: 4, layout: 'gray', depth: 16, fill: 40000 });
    const out = resample(s, Size.of(2, 2), { kernel: 'box' });
    expect(out.depth).toBe(16);
    expect(out.get(0, 0)).toBe(40000);
  });

  it('never leaves the range even when the kernel undershoots', () => {
    const s = gray([[0, 0, 255, 255, 0, 0]]);
    const out = resample(s, Size.of(12, 1), { kernel: 'lanczos3' });
    for (const value of out.data) {
      expect(value).toBeGreaterThanOrEqual(0);
      expect(value).toBeLessThanOrEqual(255);
    }
  });

  it('refuses a target with no pixels', () => {
    expect(() => resample(gray([[1, 2]]), Size.of(0, 1))).toThrow(/at least one pixel/);
  });
});

describe('scaleBy', () => {
  it('scales both axes and rounds the extents', () => {
    const out = scaleBy(checker(10, 6), 0.5, { kernel: 'box' });
    expect(out.size.toJSON()).toEqual({ width: 5, height: 3 });
  });

  it('never falls below one pixel', () => {
    const out = scaleBy(checker(10, 6), 0.01, { kernel: 'box' });
    expect(out.size.toJSON()).toEqual({ width: 1, height: 1 });
  });

  it('refuses a factor of zero', () => {
    expect(() => scaleBy(checker(4, 4), 0)).toThrow(/factor must be greater than zero/);
  });
});

describe('fitLongestSide', () => {
  it('reduces so the long side lands on the target', () => {
    const out = fitLongestSide(checker(40, 20), 10, { kernel: 'box' });
    expect(out.size.toJSON()).toEqual({ width: 10, height: 5 });
  });

  it('leaves a capture that is already small enough', () => {
    const s = checker(8, 4);
    const out = fitLongestSide(s, 10);
    expect(out.size.toJSON()).toEqual({ width: 8, height: 4 });
    expect(out).not.toBe(s);
  });

  it('refuses a target of zero', () => {
    expect(() => fitLongestSide(checker(4, 4), 0)).toThrow(/longest must be greater than zero/);
  });
});

describe('reduceInSteps', () => {
  it('halves its way down before the final pass', () => {
    const { surface, halvings } = reduceInSteps(checker(64, 64), Size.of(7, 7));
    expect(surface.size.toJSON()).toEqual({ width: 7, height: 7 });
    expect(halvings).toBeGreaterThan(0);
  });

  it('takes no halvings for a small reduction', () => {
    const { halvings } = reduceInSteps(checker(16, 16), Size.of(12, 12));
    expect(halvings).toBe(0);
  });

  it('lands on the same flat value a single pass would', () => {
    const flat = Surface.create({ width: 64, height: 64, layout: 'gray', fill: 90 });
    const { surface } = reduceInSteps(flat, Size.of(5, 5));
    expect([...new Set(surface.data)]).toEqual([90]);
  });
});
