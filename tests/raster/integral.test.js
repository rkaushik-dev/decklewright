import { describe, expect, it } from 'vitest';

import { Rect } from '../../src/geom/rect.js';
import { IntegralImage, integralOf } from '../../src/raster/integral.js';
import { channelStats } from '../../src/raster/stats.js';
import { Surface } from '../../src/raster/surface.js';
import { SurfaceView } from '../../src/raster/view.js';

/**
 * @param {readonly number[][]} rows
 * @returns {Surface}
 */
function gray(rows) {
  const s = Surface.create({ width: rows[0].length, height: rows.length, layout: 'gray' });
  rows.forEach((row, y) => row.forEach((value, x) => s.set(x, y, 0, value)));
  return s;
}

const sample = gray([
  [1, 2, 3],
  [4, 5, 6],
  [7, 8, 9],
]);

describe('building a table', () => {
  it('records the geometry of the source', () => {
    const table = integralOf(sample);
    expect(table.width).toBe(3);
    expect(table.height).toBe(3);
    expect(Object.isFrozen(table)).toBe(true);
  });

  it('refuses a channel outside the layout', () => {
    expect(() => integralOf(sample, 1)).toThrow(/outside the layout/);
  });

  it('reads the channel it is told to', () => {
    const colour = Surface.create({ width: 1, height: 1, layout: 'rgb' });
    colour.setPixel(0, 0, [10, 20, 30]);
    expect(integralOf(colour, 2).sum(Rect.of(0, 0, 1, 1))).toBe(30);
  });
});

describe('sum', () => {
  const table = integralOf(sample);

  it('adds a single pixel', () => {
    expect(table.sum(Rect.of(1, 1, 1, 1))).toBe(5);
    expect(table.sum(Rect.of(0, 0, 1, 1))).toBe(1);
  });

  it('adds the whole surface', () => {
    expect(table.sum(Rect.of(0, 0, 3, 3))).toBe(45);
  });

  it('adds an interior block', () => {
    expect(table.sum(Rect.of(1, 0, 2, 2))).toBe(2 + 3 + 5 + 6);
  });

  it('adds a full row and a full column', () => {
    expect(table.sum(Rect.of(0, 2, 3, 1))).toBe(24);
    expect(table.sum(Rect.of(2, 0, 1, 3))).toBe(18);
  });

  it('is zero for an empty window', () => {
    expect(table.sum(Rect.of(1, 1, 0, 2))).toBe(0);
  });

  it('refuses a window that leaves the table', () => {
    expect(() => table.sum(Rect.of(2, 2, 2, 2))).toThrow(/not a whole rect inside the table/);
    expect(() => table.sum(Rect.of(-1, 0, 2, 2))).toThrow(/not a whole rect inside the table/);
    expect(() => table.sum(Rect.of(0.5, 0, 2, 2))).toThrow(/not a whole rect inside the table/);
  });
});

describe('mean and window', () => {
  const table = integralOf(sample);

  it('averages a block', () => {
    expect(table.mean(Rect.of(0, 0, 3, 3))).toBe(5);
    expect(table.mean(Rect.of(0, 0, 2, 2))).toBe(3);
  });

  it('answers not a number for an empty window', () => {
    expect(table.mean(Rect.of(0, 0, 0, 3))).toBeNaN();
    const empty = table.window(Rect.of(0, 0, 0, 3));
    expect(empty.count).toBe(0);
    expect(empty.stdDev).toBeNaN();
  });

  it('agrees with a direct pass over the same region', () => {
    for (const rect of [Rect.of(0, 0, 3, 3), Rect.of(1, 1, 2, 2), Rect.of(0, 1, 3, 2)]) {
      const view = new SurfaceView(sample, rect);
      const direct = channelStats(view, 0);
      const fast = table.window(rect);
      expect(fast.mean).toBeCloseTo(direct.mean, 9);
      expect(fast.stdDev).toBeCloseTo(direct.stdDev, 9);
      expect(fast.count).toBe(direct.count);
    }
  });

  it('reports zero deviation on a flat window without going negative', () => {
    const flat = Surface.create({ width: 16, height: 16, layout: 'gray', depth: 16, fill: 60000 });
    const stats = integralOf(flat).window(Rect.of(0, 0, 16, 16));
    expect(stats.variance).toBe(0);
    expect(stats.stdDev).toBe(0);
  });

  it('sums the squares as well as the values', () => {
    expect(table.sumOfSquares(Rect.of(0, 0, 2, 1))).toBe(1 + 4);
  });
});

describe('over a view', () => {
  it('indexes in view local coordinates', () => {
    const view = new SurfaceView(sample, Rect.of(1, 1, 2, 2));
    const table = integralOf(view);
    expect(table.width).toBe(2);
    expect(table.sum(Rect.of(0, 0, 2, 2))).toBe(5 + 6 + 8 + 9);
  });
});

describe('the class itself', () => {
  it('can be built from prepared tables', () => {
    const table = new IntegralImage(new Float64Array(4), new Float64Array(4), 1, 1);
    expect(table.sum(Rect.of(0, 0, 1, 1))).toBe(0);
  });
});
