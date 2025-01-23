import { describe, expect, it } from 'vitest';

import { Histogram, histogramOf, histogramsOf } from '../../src/raster/histogram.js';
import { Surface } from '../../src/raster/surface.js';

/**
 * @param {readonly number[]} values
 * @param {number} [depth]
 * @returns {Surface}
 */
function strip(values, depth = 8) {
  const s = Surface.create({ width: values.length, height: 1, layout: 'gray', depth });
  values.forEach((value, x) => s.set(x, 0, 0, value));
  return s;
}

describe('histogramOf', () => {
  it('counts one bin per sample value by default', () => {
    const h = histogramOf(strip([0, 0, 5, 255]));
    expect(h.bins).toBe(256);
    expect(h.total).toBe(4);
    expect(h.counts[0]).toBe(2);
    expect(h.counts[5]).toBe(1);
    expect(h.counts[255]).toBe(1);
  });

  it('folds into a smaller number of bins', () => {
    const h = histogramOf(strip([0, 1, 128, 255]), { bins: 2 });
    expect(h.bins).toBe(2);
    expect([...h.counts]).toEqual([2, 2]);
  });

  it('covers the full range of a sixteen bit capture', () => {
    const h = histogramOf(strip([0, 65535], 16), { bins: 4 });
    expect([...h.counts]).toEqual([1, 0, 0, 1]);
    expect(h.maxValue).toBe(65535);
  });

  it('reads one channel of a colour capture', () => {
    const s = Surface.create({ width: 2, height: 1, layout: 'rgb' });
    s.setPixel(0, 0, [10, 20, 30]);
    s.setPixel(1, 0, [10, 40, 30]);
    expect(histogramOf(s, { channel: 1 }).counts[20]).toBe(1);
    expect(histogramOf(s, { channel: 0 }).counts[10]).toBe(2);
  });

  it('refuses a channel outside the layout', () => {
    expect(() => histogramOf(strip([1]), { channel: 2 })).toThrow(/outside the layout/);
  });

  it('refuses a bin count that is not a whole number above zero', () => {
    expect(() => histogramOf(strip([1]), { bins: 0 })).toThrow(/above zero/);
  });
});

describe('bin arithmetic', () => {
  const h = histogramOf(strip([0, 64, 128, 192, 255]), { bins: 4 });

  it('maps a value to its bin', () => {
    expect(h.binFor(0)).toBe(0);
    expect(h.binFor(64)).toBe(1);
    expect(h.binFor(255)).toBe(3);
  });

  it('reports the value at the centre of a bin', () => {
    expect(h.valueAt(0)).toBe(32);
    expect(h.valueAt(3)).toBe(224);
  });

  it('round trips a value through its bin', () => {
    for (const value of [0, 30, 100, 200, 255]) {
      expect(h.binFor(h.valueAt(h.binFor(value)))).toBe(h.binFor(value));
    }
  });
});

describe('summaries', () => {
  it('averages using the bin centres', () => {
    const h = histogramOf(strip([10, 10, 10, 10]));
    expect(h.mean).toBeCloseTo(10.5, 6);
  });

  it('finds the busiest bin and breaks a tie downward', () => {
    expect(histogramOf(strip([7, 7, 9])).modeBin).toBe(7);
    expect(histogramOf(strip([7, 9])).modeBin).toBe(7);
  });

  it('finds the first and last occupied bins', () => {
    const h = histogramOf(strip([40, 100, 200]));
    expect(h.minBin).toBe(40);
    expect(h.maxBin).toBe(200);
  });

  it('reports minus one for an empty distribution', () => {
    const empty = new Histogram(new Uint32Array(8), 255);
    expect(empty.minBin).toBe(-1);
    expect(empty.maxBin).toBe(-1);
    expect(empty.mean).toBeNaN();
    expect(empty.percentile(0.5)).toBeNaN();
    expect(empty.otsu()).toBeNaN();
    expect(empty.clipping()).toEqual({ low: 0, high: 0 });
  });
});

describe('percentile and clipping', () => {
  it('walks the distribution', () => {
    const h = histogramOf(strip([0, 10, 20, 30, 250]));
    expect(h.percentile(0)).toBeCloseTo(0.5, 6);
    expect(h.percentile(1)).toBeCloseTo(250.5, 6);
    expect(h.percentile(0.5)).toBeCloseTo(20.5, 6);
  });

  it('measures the share at each end', () => {
    const h = histogramOf(strip([0, 0, 128, 255]));
    expect(h.clipping()).toEqual({ low: 0.5, high: 0.25 });
  });

  it('builds a running total', () => {
    const h = histogramOf(strip([0, 1, 1]), { bins: 4 });
    expect([...h.cumulative()]).toEqual([3, 3, 3, 3]);
  });
});

describe('otsu', () => {
  it('splits a two lobed distribution between the lobes', () => {
    const values = [];
    for (let i = 0; i < 50; i += 1) values.push(20 + (i % 5));
    for (let i = 0; i < 50; i += 1) values.push(200 + (i % 5));
    const threshold = histogramOf(strip(values)).otsu();
    expect(values.filter((v) => v <= threshold)).toHaveLength(50);
    expect(values.filter((v) => v > threshold)).toHaveLength(50);
  });

  it('does not crash on a single valued distribution', () => {
    const threshold = histogramOf(strip([100, 100, 100])).otsu();
    expect(Number.isFinite(threshold)).toBe(true);
  });

  it('finds a threshold that separates a bed from a sheet', () => {
    const s = Surface.create({ width: 20, height: 20, layout: 'gray', fill: 30 });
    for (let y = 5; y < 15; y += 1) {
      for (let x = 5; x < 15; x += 1) s.set(x, y, 0, 210);
    }
    const threshold = histogramOf(s).otsu();
    expect(threshold).toBeGreaterThan(30);
    expect(threshold).toBeLessThan(210);
  });
});

describe('histogramsOf', () => {
  it('gives one per channel', () => {
    const s = Surface.create({ width: 1, height: 1, layout: 'rgb' });
    s.setPixel(0, 0, [1, 2, 3]);
    const all = histogramsOf(s);
    expect(all).toHaveLength(3);
    expect(all[0].counts[1]).toBe(1);
    expect(all[2].counts[3]).toBe(1);
  });

  it('passes the bin count through', () => {
    const s = Surface.create({ width: 1, height: 1, layout: 'rgb' });
    expect(histogramsOf(s, { bins: 8 })[0].bins).toBe(8);
  });
});

describe('serialisation', () => {
  it('reports its shape and the counts', () => {
    const json = histogramOf(strip([0, 1]), { bins: 2 }).toJSON();
    expect(json.bins).toBe(2);
    expect(json.total).toBe(2);
    expect(json.counts).toEqual([2, 0]);
  });
});
