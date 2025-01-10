import { describe, expect, it } from 'vitest';

import { Sampler, resolveIndex, samplerFor } from '../../src/raster/sampler.js';
import { Surface } from '../../src/raster/surface.js';

/**
 * @param {readonly number[][]} rows
 * @returns {Surface}
 */
function gray(rows) {
  const s = Surface.create({ width: rows[0].length, height: rows.length, layout: 'gray' });
  rows.forEach((row, y) => row.forEach((value, x) => s.set(x, y, 0, value)));
  return s;
}

const quad = gray([
  [0, 100],
  [200, 255],
]);

describe('resolveIndex', () => {
  it('passes an interior index through', () => {
    expect(resolveIndex(2, 5, 'clamp')).toBe(2);
    expect(resolveIndex(0, 5, 'constant')).toBe(0);
  });

  it('clamps to the nearest column', () => {
    expect(resolveIndex(-3, 5, 'clamp')).toBe(0);
    expect(resolveIndex(9, 5, 'clamp')).toBe(4);
  });

  it('mirrors without repeating the edge', () => {
    expect(resolveIndex(-1, 4, 'mirror')).toBe(1);
    expect(resolveIndex(4, 4, 'mirror')).toBe(2);
    expect(resolveIndex(-4, 4, 'mirror')).toBe(2);
  });

  it('mirrors a single column onto itself', () => {
    expect(resolveIndex(7, 1, 'mirror')).toBe(0);
  });

  it('reports no column under the constant mode', () => {
    expect(resolveIndex(-1, 5, 'constant')).toBe(-1);
    expect(resolveIndex(5, 5, 'constant')).toBe(-1);
  });
});

describe('building a sampler', () => {
  it('defaults to clamping and a black background', () => {
    const s = new Sampler(quad);
    expect(s.edge).toBe('clamp');
    expect(s.background).toEqual([0]);
    expect(Object.isFrozen(s)).toBe(true);
  });

  it('takes a per channel background', () => {
    const colour = Surface.create({ width: 1, height: 1, layout: 'rgb' });
    expect(new Sampler(colour, { background: [1, 2, 3] }).background).toEqual([1, 2, 3]);
    expect(new Sampler(colour, { background: 8 }).background).toEqual([8, 8, 8]);
  });

  it('refuses an edge mode it does not know', () => {
    expect(() => new Sampler(quad, { edge: 'wrap' })).toThrow(/edge must be one of/);
  });
});

describe('at', () => {
  it('reads inside the surface', () => {
    expect(samplerFor(quad).at(1, 0, 0)).toBe(100);
  });

  it('clamps outside by default', () => {
    expect(samplerFor(quad).at(-5, 0, 0)).toBe(0);
    expect(samplerFor(quad).at(9, 1, 0)).toBe(255);
  });

  it('answers the background under the constant mode', () => {
    const s = samplerFor(quad, { edge: 'constant', background: 42 });
    expect(s.at(-1, 0, 0)).toBe(42);
    expect(s.at(0, 5, 0)).toBe(42);
  });
});

describe('nearest', () => {
  it('lands on the pixel centre', () => {
    const s = samplerFor(quad);
    expect(s.nearest(0, 0, 0)).toBe(0);
    expect(s.nearest(0.4, 0, 0)).toBe(0);
    expect(s.nearest(0.6, 0, 0)).toBe(100);
  });
});

describe('bilinear', () => {
  const s = samplerFor(quad);

  it('returns the sample exactly at a pixel centre', () => {
    expect(s.bilinear(0, 0, 0)).toBe(0);
    expect(s.bilinear(1, 1, 0)).toBe(255);
  });

  it('interpolates along one axis', () => {
    expect(s.bilinear(0.5, 0, 0)).toBe(50);
    expect(s.bilinear(0, 0.5, 0)).toBe(100);
  });

  it('interpolates across both', () => {
    expect(s.bilinear(0.5, 0.5, 0)).toBeCloseTo((0 + 100 + 200 + 255) / 4, 9);
  });

  it('uses the edge policy when it reaches past the surface', () => {
    expect(s.bilinear(1.5, 0, 0)).toBe(100);
    const constant = samplerFor(quad, { edge: 'constant', background: 0 });
    expect(constant.bilinear(1.5, 0, 0)).toBe(50);
  });

  it('does not interpolate when both fractions are zero', () => {
    const constant = samplerFor(quad, { edge: 'constant', background: 7 });
    expect(constant.bilinear(1, 1, 0)).toBe(255);
  });
});

describe('sample and pixel', () => {
  it('dispatches on the named mode', () => {
    const s = samplerFor(quad);
    expect(s.sample(0.5, 0, 0, 'nearest')).toBe(100);
    expect(s.sample(0.5, 0, 0, 'bilinear')).toBe(50);
    expect(s.sample(0.5, 0, 0)).toBe(50);
  });

  it('refuses a mode it does not know', () => {
    expect(() => samplerFor(quad).sample(0, 0, 0, 'cubic')).toThrow(/mode must be one of/);
  });

  it('reads every channel of a pixel', () => {
    const colour = Surface.create({ width: 2, height: 1, layout: 'rgb' });
    colour.setPixel(0, 0, [0, 10, 20]);
    colour.setPixel(1, 0, [100, 110, 120]);
    expect(samplerFor(colour).pixel(0.5, 0)).toEqual([50, 60, 70]);
  });
});

describe('quantised', () => {
  it('rounds and clamps to the depth', () => {
    const s = samplerFor(quad);
    expect(s.quantised(0.5, 0.5, 0)).toBe(139);
  });

  it('never leaves the range even with a wild background', () => {
    const s = samplerFor(quad, { edge: 'constant', background: 1e6 });
    expect(s.quantised(-5, -5, 0)).toBe(255);
  });
});
