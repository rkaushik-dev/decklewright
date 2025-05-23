import { describe, expect, it } from 'vitest';

import { Mask } from '../../src/detect/mask.js';
import { Rect } from '../../src/geom/rect.js';
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

/**
 * @param {Mask} mask
 * @returns {number[][]}
 */
function rowsOf(mask) {
  const out = [];
  for (let y = 0; y < mask.height; y += 1) out.push([...mask.row(y)]);
  return out;
}

describe('building a mask', () => {
  it('starts empty at the stated geometry', () => {
    const mask = new Mask(3, 2);
    expect(mask.width).toBe(3);
    expect(mask.count).toBe(0);
    expect(mask.coverage).toBe(0);
    expect(mask.size.toJSON()).toEqual({ width: 3, height: 2 });
    expect(mask.bounds.toJSON()).toEqual({ x: 0, y: 0, width: 3, height: 2 });
  });

  it('refuses a buffer of the wrong length', () => {
    expect(() => new Mask(3, 2, new Uint8Array(5))).toThrow(/does not match the stated geometry/);
  });

  it('refuses an extent that is not a whole number above zero', () => {
    expect(() => new Mask(0, 2)).toThrow(/width must be a whole number/);
  });
});

describe('threshold', () => {
  const source = gray([
    [10, 200],
    [30, 250],
  ]);

  it('sets where the sample is above', () => {
    expect(rowsOf(Mask.threshold(source, 100))).toEqual([
      [0, 1],
      [0, 1],
    ]);
  });

  it('sets where the sample is below when asked', () => {
    expect(rowsOf(Mask.threshold(source, 100, { above: false }))).toEqual([
      [1, 0],
      [1, 0],
    ]);
  });

  it('treats the threshold itself as not set either way', () => {
    expect(Mask.threshold(gray([[100]]), 100).count).toBe(0);
    expect(Mask.threshold(gray([[100]]), 100, { above: false }).count).toBe(0);
  });

  it('reads the channel it is told to', () => {
    const colour = Surface.create({ width: 2, height: 1, layout: 'rgb' });
    colour.setPixel(0, 0, [0, 250, 0]);
    colour.setPixel(1, 0, [250, 0, 0]);
    expect([...Mask.threshold(colour, 100, { channel: 1 }).row(0)]).toEqual([1, 0]);
  });

  it('runs over a view in view local coordinates', () => {
    const view = new SurfaceView(source, Rect.of(1, 0, 1, 2));
    const mask = Mask.threshold(view, 100);
    expect(mask.width).toBe(1);
    expect(mask.count).toBe(2);
  });
});

describe('reading and writing', () => {
  it('reads zero outside the mask rather than throwing', () => {
    const mask = new Mask(2, 2);
    mask.set(0, 0, 1);
    expect(mask.get(0, 0)).toBe(1);
    expect(mask.get(-1, 0)).toBe(0);
    expect(mask.get(5, 5)).toBe(0);
  });

  it('ignores a write outside the mask', () => {
    const mask = new Mask(2, 2);
    mask.set(9, 9, 1);
    expect(mask.count).toBe(0);
  });

  it('normalises any truthy value to one', () => {
    const mask = new Mask(2, 2);
    mask.set(0, 0, 7);
    expect(mask.get(0, 0)).toBe(1);
  });

  it('hands back live rows', () => {
    const mask = new Mask(2, 2);
    mask.row(1)[0] = 1;
    expect(mask.get(0, 1)).toBe(1);
  });
});

describe('combining', () => {
  const a = Mask.threshold(gray([[10, 200, 200]]), 100);
  const b = Mask.threshold(gray([[200, 200, 10]]), 100);

  it('intersects, unions and subtracts', () => {
    expect([...a.and(b).row(0)]).toEqual([0, 1, 0]);
    expect([...a.or(b).row(0)]).toEqual([1, 1, 1]);
    expect([...a.without(b).row(0)]).toEqual([0, 0, 1]);
  });

  it('inverts', () => {
    expect([...a.inverted().row(0)]).toEqual([1, 0, 0]);
  });

  it('clones without sharing', () => {
    const copy = a.clone();
    copy.set(0, 0, 1);
    expect(a.get(0, 0)).toBe(0);
  });

  it('refuses masks of different shapes', () => {
    expect(() => a.and(new Mask(1, 1))).toThrow(/not the same shape/);
    expect(() => a.or(new Mask(1, 1))).toThrow(/not the same shape/);
    expect(() => a.without(new Mask(1, 1))).toThrow(/not the same shape/);
  });
});

describe('extent', () => {
  it('bounds the set pixels', () => {
    const mask = new Mask(5, 5);
    mask.set(1, 2, 1);
    mask.set(3, 4, 1);
    expect(mask.extent().toJSON()).toEqual({ x: 1, y: 2, width: 3, height: 3 });
  });

  it('is empty when nothing is set', () => {
    expect(new Mask(5, 5).extent().isEmpty).toBe(true);
  });

  it('covers the whole mask when everything is set', () => {
    const mask = new Mask(3, 2);
    mask.data.fill(1);
    expect(mask.extent().toJSON()).toEqual({ x: 0, y: 0, width: 3, height: 2 });
    expect(mask.coverage).toBe(1);
  });
});

describe('printing', () => {
  it('says the shape and how much is set', () => {
    const mask = new Mask(4, 2);
    mask.set(0, 0, 1);
    expect(String(mask)).toBe('mask 4x2, 1 set');
  });
});
