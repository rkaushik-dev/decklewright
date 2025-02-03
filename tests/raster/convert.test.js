import { describe, expect, it } from 'vitest';

import {
  LUMA_601,
  LUMA_709,
  addAlpha,
  byteSize,
  dropAlpha,
  scaleSample,
  toColour,
  toDepth,
  toGray,
  toLayout,
} from '../../src/raster/convert.js';
import { Surface } from '../../src/raster/surface.js';

describe('scaleSample', () => {
  it('sends full scale to full scale', () => {
    expect(scaleSample(255, 8, 16)).toBe(65535);
    expect(scaleSample(65535, 16, 8)).toBe(255);
  });

  it('keeps black at black', () => {
    expect(scaleSample(0, 8, 16)).toBe(0);
    expect(scaleSample(0, 16, 8)).toBe(0);
  });

  it('is a no op when the depths match', () => {
    expect(scaleSample(123, 8, 8)).toBe(123);
  });

  it('round trips every eight bit value', () => {
    for (let v = 0; v <= 255; v += 1) {
      expect(scaleSample(scaleSample(v, 8, 16), 16, 8)).toBe(v);
    }
  });
});

describe('toDepth', () => {
  it('promotes a capture without moving white', () => {
    const s = Surface.create({ width: 2, height: 1, layout: 'gray', fill: 255 });
    const out = toDepth(s, 16);
    expect(out.depth).toBe(16);
    expect(out.get(0, 0)).toBe(65535);
  });

  it('demotes and rounds', () => {
    const s = Surface.create({ width: 1, height: 1, layout: 'gray', depth: 16 });
    s.set(0, 0, 0, 32896);
    expect(toDepth(s, 8).get(0, 0)).toBe(128);
  });

  it('clones when the depth already matches', () => {
    const s = Surface.create({ width: 1, height: 1, layout: 'gray', fill: 9 });
    const out = toDepth(s, 8);
    expect(out).not.toBe(s);
    expect(out.get(0, 0)).toBe(9);
  });
});

describe('toGray', () => {
  it('uses rec 709 by default', () => {
    const s = Surface.create({ width: 1, height: 1, layout: 'rgb' });
    s.setPixel(0, 0, [255, 0, 0]);
    expect(toGray(s).get(0, 0)).toBe(Math.round(255 * LUMA_709[0]));
  });

  it('takes another set of weights', () => {
    const s = Surface.create({ width: 1, height: 1, layout: 'rgb' });
    s.setPixel(0, 0, [255, 0, 0]);
    expect(toGray(s, { weights: LUMA_601 }).get(0, 0)).toBe(Math.round(255 * LUMA_601[0]));
  });

  it('keeps a neutral value unchanged', () => {
    const s = Surface.create({ width: 1, height: 1, layout: 'rgb', fill: 120 });
    expect(toGray(s).get(0, 0)).toBe(120);
  });

  it('carries alpha through', () => {
    const s = Surface.create({ width: 1, height: 1, layout: 'rgba' });
    s.setPixel(0, 0, [10, 10, 10, 77]);
    const out = toGray(s);
    expect(out.layout).toBe('graya');
    expect(out.getPixel(0, 0)).toEqual([10, 77]);
  });

  it('clones a capture that is already gray', () => {
    const s = Surface.create({ width: 1, height: 1, layout: 'gray', fill: 5 });
    expect(toGray(s).layout).toBe('gray');
  });

  it('refuses a weight list of the wrong length', () => {
    const s = Surface.create({ width: 1, height: 1, layout: 'rgb' });
    expect(() => toGray(s, { weights: [1, 0] })).toThrow(/exactly three weights/);
  });
});

describe('toColour', () => {
  it('copies the value into all three channels', () => {
    const s = Surface.create({ width: 1, height: 1, layout: 'gray', fill: 90 });
    expect(toColour(s).getPixel(0, 0)).toEqual([90, 90, 90]);
  });

  it('keeps alpha', () => {
    const s = Surface.create({ width: 1, height: 1, layout: 'graya' });
    s.setPixel(0, 0, [90, 30]);
    const out = toColour(s);
    expect(out.layout).toBe('rgba');
    expect(out.getPixel(0, 0)).toEqual([90, 90, 90, 30]);
  });

  it('clones a capture that is already colour', () => {
    const s = Surface.create({ width: 1, height: 1, layout: 'rgb', fill: 1 });
    expect(toColour(s).layout).toBe('rgb');
  });
});

describe('addAlpha', () => {
  it('adds a fully opaque channel', () => {
    const s = Surface.create({ width: 1, height: 1, layout: 'rgb', fill: 40 });
    const out = addAlpha(s);
    expect(out.layout).toBe('rgba');
    expect(out.getPixel(0, 0)).toEqual([40, 40, 40, 255]);
  });

  it('takes another coverage value', () => {
    const s = Surface.create({ width: 1, height: 1, layout: 'gray', fill: 40 });
    expect(addAlpha(s, { opaque: 0 }).getPixel(0, 0)).toEqual([40, 0]);
  });

  it('uses full scale for the depth', () => {
    const s = Surface.create({ width: 1, height: 1, layout: 'gray', depth: 16, fill: 40 });
    expect(addAlpha(s).getPixel(0, 0)).toEqual([40, 65535]);
  });
});

describe('dropAlpha', () => {
  it('leaves a fully opaque pixel alone', () => {
    const s = Surface.create({ width: 1, height: 1, layout: 'rgba' });
    s.setPixel(0, 0, [10, 20, 30, 255]);
    expect(dropAlpha(s).getPixel(0, 0)).toEqual([10, 20, 30]);
  });

  it('replaces a fully transparent pixel with the background', () => {
    const s = Surface.create({ width: 1, height: 1, layout: 'rgba' });
    s.setPixel(0, 0, [10, 20, 30, 0]);
    expect(dropAlpha(s, { background: 255 }).getPixel(0, 0)).toEqual([255, 255, 255]);
  });

  it('mixes at half coverage', () => {
    const s = Surface.create({ width: 1, height: 1, layout: 'graya' });
    s.setPixel(0, 0, [0, 128]);
    const out = dropAlpha(s, { background: 200 });
    expect(out.get(0, 0)).toBeGreaterThan(98);
    expect(out.get(0, 0)).toBeLessThan(102);
  });

  it('takes a per channel background', () => {
    const s = Surface.create({ width: 1, height: 1, layout: 'rgba' });
    s.setPixel(0, 0, [0, 0, 0, 0]);
    expect(dropAlpha(s, { background: [1, 2, 3] }).getPixel(0, 0)).toEqual([1, 2, 3]);
  });

  it('refuses a background of the wrong length', () => {
    const s = Surface.create({ width: 1, height: 1, layout: 'rgba' });
    expect(() => dropAlpha(s, { background: [1, 2] })).toThrow(/wrong number of channels/);
  });
});

describe('toLayout', () => {
  it('walks colour and coverage in either direction', () => {
    const rgb = Surface.create({ width: 1, height: 1, layout: 'rgb', fill: 60 });
    expect(toLayout(rgb, 'gray').layout).toBe('gray');
    expect(toLayout(rgb, 'rgba').layout).toBe('rgba');
    expect(toLayout(rgb, 'graya').layout).toBe('graya');

    const graya = Surface.create({ width: 1, height: 1, layout: 'graya', fill: 60 });
    expect(toLayout(graya, 'rgb').layout).toBe('rgb');
    expect(toLayout(graya, 'gray').layout).toBe('gray');
  });

  it('clones when nothing has to change', () => {
    const s = Surface.create({ width: 1, height: 1, layout: 'rgb', fill: 3 });
    const out = toLayout(s, 'rgb');
    expect(out).not.toBe(s);
    expect(out.get(0, 0)).toBe(3);
  });

  it('carries the background through to the drop', () => {
    const s = Surface.create({ width: 1, height: 1, layout: 'rgba' });
    s.setPixel(0, 0, [0, 0, 0, 0]);
    expect(toLayout(s, 'rgb', { background: 255 }).getPixel(0, 0)).toEqual([255, 255, 255]);
  });

  it('refuses a layout it does not know', () => {
    const s = Surface.create({ width: 1, height: 1, layout: 'rgb' });
    expect(() => toLayout(s, 'cmyk')).toThrow(/unknown layout/);
  });
});

describe('byteSize', () => {
  it('multiplies out the geometry', () => {
    expect(byteSize(100, 100, 'rgb', 8)).toBe(30000);
    expect(byteSize(100, 100, 'rgba', 16)).toBe(80000);
  });
});
