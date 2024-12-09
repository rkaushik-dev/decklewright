import { describe, expect, it } from 'vitest';

import { Surface } from '../../src/raster/surface.js';

describe('creating a surface', () => {
  it('allocates the right buffer for the layout and depth', () => {
    const s = Surface.create({ width: 4, height: 3, layout: 'rgb', depth: 8 });
    expect(s.data).toBeInstanceOf(Uint8Array);
    expect(s.sampleCount).toBe(36);
    expect(s.channels).toBe(3);
    expect(s.maxValue).toBe(255);
    expect(s.rowStride).toBe(12);
  });

  it('allocates sixteen bit buffers when asked', () => {
    const s = Surface.create({ width: 2, height: 2, layout: 'gray', depth: 16 });
    expect(s.data).toBeInstanceOf(Uint16Array);
    expect(s.maxValue).toBe(65535);
  });

  it('fills on creation', () => {
    const s = Surface.create({ width: 2, height: 2, layout: 'gray', fill: 200 });
    expect([...s.data]).toEqual([200, 200, 200, 200]);
  });

  it('reports its geometry', () => {
    const s = Surface.create({ width: 4, height: 3, layout: 'gray' });
    expect(s.size.toJSON()).toEqual({ width: 4, height: 3 });
    expect(s.bounds.toJSON()).toEqual({ x: 0, y: 0, width: 4, height: 3 });
    expect(String(s)).toBe('4x3 gray8');
    expect(s.toJSON()).toEqual({ width: 4, height: 3, layout: 'gray', depth: 8 });
  });

  it('refuses a zero or fractional extent', () => {
    expect(() => Surface.create({ width: 0, height: 4 })).toThrow(/width must be a whole number/);
    expect(() => Surface.create({ width: 4.5, height: 4 })).toThrow(/whole number/);
  });
});

describe('wrapping an existing buffer', () => {
  it('takes a buffer that matches the geometry', () => {
    const data = new Uint8Array(12);
    const s = Surface.wrap(data, { width: 2, height: 2, layout: 'rgb' });
    expect(s.data).toBe(data);
  });

  it('refuses a buffer of the wrong length', () => {
    expect(() => Surface.wrap(new Uint8Array(11), { width: 2, height: 2, layout: 'rgb' })).toThrow(
      /does not match the stated geometry/,
    );
  });

  it('refuses a buffer of the wrong element type', () => {
    expect(() =>
      Surface.wrap(new Uint8Array(4), { width: 2, height: 2, layout: 'gray', depth: 16 }),
    ).toThrow(/16 bit surface needs a Uint16Array/);
  });
});

describe('addressing', () => {
  const s = Surface.create({ width: 4, height: 3, layout: 'rgb' });

  it('walks the interleaved buffer row by row', () => {
    expect(s.indexOf(0, 0, 0)).toBe(0);
    expect(s.indexOf(1, 0, 0)).toBe(3);
    expect(s.indexOf(0, 1, 0)).toBe(12);
    expect(s.indexOf(3, 2, 2)).toBe(35);
  });

  it('answers whether a pixel is inside', () => {
    expect(s.contains(0, 0)).toBe(true);
    expect(s.contains(3, 2)).toBe(true);
    expect(s.contains(4, 0)).toBe(false);
    expect(s.contains(-1, 0)).toBe(false);
  });

  it('refuses a pixel outside the surface', () => {
    expect(() => s.indexOf(4, 0)).toThrow(/outside the surface/);
    expect(() => s.indexOf(0, -1)).toThrow(/outside the surface/);
  });

  it('refuses a channel outside the layout', () => {
    expect(() => s.indexOf(0, 0, 3)).toThrow(/outside the layout/);
    expect(() => s.indexOf(0, 0, -1)).toThrow(/outside the layout/);
  });
});

describe('reading and writing', () => {
  it('round trips a single sample', () => {
    const s = Surface.create({ width: 2, height: 2, layout: 'rgb' });
    s.set(1, 1, 2, 77);
    expect(s.get(1, 1, 2)).toBe(77);
    expect(s.get(1, 1, 0)).toBe(0);
  });

  it('round trips a whole pixel', () => {
    const s = Surface.create({ width: 2, height: 2, layout: 'rgba' });
    s.setPixel(0, 1, [1, 2, 3, 4]);
    expect(s.getPixel(0, 1)).toEqual([1, 2, 3, 4]);
  });

  it('refuses a pixel with the wrong channel count', () => {
    const s = Surface.create({ width: 2, height: 2, layout: 'rgb' });
    expect(() => s.setPixel(0, 0, [1, 2])).toThrow(/wrong number of channels/);
  });

  it('wraps a value that does not fit the depth, as a typed array does', () => {
    const s = Surface.create({ width: 1, height: 1, layout: 'gray', depth: 8 });
    s.set(0, 0, 0, 300);
    expect(s.get(0, 0, 0)).toBe(44);
  });
});

describe('row', () => {
  it('hands back a live view', () => {
    const s = Surface.create({ width: 3, height: 2, layout: 'gray' });
    const row = s.row(1);
    expect(row).toHaveLength(3);
    row[0] = 9;
    expect(s.get(0, 1)).toBe(9);
  });

  it('refuses a row outside the surface', () => {
    const s = Surface.create({ width: 3, height: 2, layout: 'gray' });
    expect(() => s.row(2)).toThrow(/outside the surface/);
    expect(() => s.row(1.5)).toThrow(/whole number/);
  });
});

describe('fill', () => {
  it('fills every sample', () => {
    const s = Surface.create({ width: 2, height: 1, layout: 'rgb' });
    s.fill(5);
    expect([...s.data]).toEqual([5, 5, 5, 5, 5, 5]);
  });

  it('fills one channel and leaves the rest', () => {
    const s = Surface.create({ width: 2, height: 1, layout: 'rgb' });
    s.fill(9, 1);
    expect([...s.data]).toEqual([0, 9, 0, 0, 9, 0]);
  });

  it('refuses a channel outside the layout', () => {
    const s = Surface.create({ width: 2, height: 1, layout: 'rgb' });
    expect(() => s.fill(1, 5)).toThrow(/outside the layout/);
  });
});

describe('clone and like', () => {
  it('copies the samples into a new buffer', () => {
    const s = Surface.create({ width: 2, height: 2, layout: 'gray', fill: 3 });
    const copy = s.clone();
    copy.set(0, 0, 0, 9);
    expect(s.get(0, 0)).toBe(3);
    expect(copy.get(1, 1)).toBe(3);
  });

  it('makes an empty surface of the same shape', () => {
    const s = Surface.create({ width: 2, height: 2, layout: 'rgb', depth: 16, fill: 7 });
    const blank = s.like();
    expect(blank.toJSON()).toEqual(s.toJSON());
    expect(blank.get(0, 0)).toBe(0);
  });

  it('takes overrides for the parts that change', () => {
    const s = Surface.create({ width: 2, height: 2, layout: 'rgb' });
    const gray = s.like({ layout: 'gray', width: 4 });
    expect(gray.toJSON()).toEqual({ width: 4, height: 2, layout: 'gray', depth: 8 });
  });
});
