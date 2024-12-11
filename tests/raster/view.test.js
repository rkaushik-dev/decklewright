import { describe, expect, it } from 'vitest';

import { Rect } from '../../src/geom/rect.js';
import { Surface } from '../../src/raster/surface.js';
import { SurfaceView } from '../../src/raster/view.js';

/**
 * @param {number} width
 * @param {number} height
 * @returns {Surface}
 */
function ramp(width, height) {
  const s = Surface.create({ width, height, layout: 'gray' });
  for (let y = 0; y < height; y += 1) {
    for (let x = 0; x < width; x += 1) s.set(x, y, 0, y * width + x);
  }
  return s;
}

describe('building a view', () => {
  it('takes the whole surface', () => {
    const s = ramp(4, 3);
    const v = SurfaceView.whole(s);
    expect(v.width).toBe(4);
    expect(v.height).toBe(3);
    expect(v.bounds.toJSON()).toEqual({ x: 0, y: 0, width: 4, height: 3 });
    expect(v.layout).toBe('gray');
    expect(v.depth).toBe(8);
    expect(v.channels).toBe(1);
    expect(v.maxValue).toBe(255);
    expect(v.size.toJSON()).toEqual({ width: 4, height: 3 });
  });

  it('refuses a rect that runs off the surface', () => {
    const s = ramp(4, 3);
    expect(() => new SurfaceView(s, Rect.of(2, 0, 4, 3))).toThrow(/runs off the surface/);
    expect(() => new SurfaceView(s, Rect.of(-1, 0, 2, 2))).toThrow(/runs off the surface/);
  });

  it('refuses a fractional or empty rect', () => {
    const s = ramp(4, 3);
    expect(() => new SurfaceView(s, Rect.of(0.5, 0, 2, 2))).toThrow(/whole pixels/);
    expect(() => new SurfaceView(s, Rect.of(0, 0, 0, 2))).toThrow(/cannot be empty/);
  });

  it('refuses anything that is not a surface', () => {
    expect(() => new SurfaceView({}, Rect.of(0, 0, 1, 1))).toThrow(/needs a surface/);
  });
});

describe('reading through a view', () => {
  const s = ramp(4, 3);
  const v = new SurfaceView(s, Rect.of(1, 1, 2, 2));

  it('offsets the coordinates', () => {
    expect(v.get(0, 0)).toBe(s.get(1, 1));
    expect(v.get(1, 1)).toBe(s.get(2, 2));
  });

  it('knows its own bounds', () => {
    expect(v.contains(1, 1)).toBe(true);
    expect(v.contains(2, 0)).toBe(false);
  });

  it('refuses a pixel outside the window even when it is on the surface', () => {
    expect(() => v.get(2, 0)).toThrow(/outside the view/);
    expect(() => v.get(-1, 0)).toThrow(/outside the view/);
  });

  it('reads whole pixels', () => {
    const colour = Surface.create({ width: 2, height: 1, layout: 'rgb' });
    colour.setPixel(1, 0, [7, 8, 9]);
    const window = new SurfaceView(colour, Rect.of(1, 0, 1, 1));
    expect(window.getPixel(0, 0)).toEqual([7, 8, 9]);
  });
});

describe('writing through a view', () => {
  it('lands on the parent surface', () => {
    const s = ramp(4, 3);
    const v = new SurfaceView(s, Rect.of(1, 1, 2, 2));
    v.set(0, 0, 0, 200);
    expect(s.get(1, 1)).toBe(200);
  });

  it('writes whole pixels through', () => {
    const s = Surface.create({ width: 3, height: 1, layout: 'rgb' });
    const v = new SurfaceView(s, Rect.of(2, 0, 1, 1));
    v.setPixel(0, 0, [1, 2, 3]);
    expect(s.getPixel(2, 0)).toEqual([1, 2, 3]);
  });

  it('refuses a write outside the window', () => {
    const s = ramp(4, 3);
    const v = new SurfaceView(s, Rect.of(1, 1, 2, 2));
    expect(() => v.set(5, 0, 0, 1)).toThrow(/outside the view/);
    expect(() => v.setPixel(5, 0, [1])).toThrow(/outside the view/);
  });
});

describe('row', () => {
  it('covers only the columns the view holds', () => {
    const s = ramp(4, 3);
    const v = new SurfaceView(s, Rect.of(1, 1, 2, 2));
    expect([...v.row(0)]).toEqual([5, 6]);
  });

  it('is live', () => {
    const s = ramp(4, 3);
    const v = new SurfaceView(s, Rect.of(1, 1, 2, 2));
    v.row(1)[0] = 99;
    expect(s.get(1, 2)).toBe(99);
  });

  it('accounts for the channel count', () => {
    const s = Surface.create({ width: 3, height: 1, layout: 'rgb' });
    s.setPixel(1, 0, [4, 5, 6]);
    const v = new SurfaceView(s, Rect.of(1, 0, 2, 1));
    expect([...v.row(0)]).toEqual([4, 5, 6, 0, 0, 0]);
  });

  it('refuses a row outside the window', () => {
    const s = ramp(4, 3);
    const v = new SurfaceView(s, Rect.of(1, 1, 2, 2));
    expect(() => v.row(2)).toThrow(/outside the view/);
  });
});

describe('subview', () => {
  it('nests in view local coordinates', () => {
    const s = ramp(6, 6);
    const outer = new SurfaceView(s, Rect.of(1, 1, 4, 4));
    const inner = outer.subview(Rect.of(1, 1, 2, 2));
    expect(inner.get(0, 0)).toBe(s.get(2, 2));
    expect(inner.rect.toJSON()).toEqual({ x: 2, y: 2, width: 2, height: 2 });
  });

  it('refuses a subview that leaves the parent view', () => {
    const s = ramp(6, 6);
    const outer = new SurfaceView(s, Rect.of(1, 1, 2, 2));
    expect(() => outer.subview(Rect.of(0, 0, 4, 4))).toThrow(/runs off the parent view/);
  });
});

describe('toSurface', () => {
  it('copies the window out', () => {
    const s = ramp(4, 3);
    const out = new SurfaceView(s, Rect.of(1, 1, 2, 2)).toSurface();
    expect(out.toJSON()).toEqual({ width: 2, height: 2, layout: 'gray', depth: 8 });
    expect([...out.data]).toEqual([5, 6, 9, 10]);
  });

  it('no longer shares samples with the parent', () => {
    const s = ramp(4, 3);
    const out = new SurfaceView(s, Rect.of(1, 1, 2, 2)).toSurface();
    out.set(0, 0, 0, 0);
    expect(s.get(1, 1)).toBe(5);
  });

  it('carries a colour window through', () => {
    const s = Surface.create({ width: 2, height: 2, layout: 'rgba', depth: 16 });
    s.setPixel(1, 1, [1000, 2000, 3000, 4000]);
    const out = SurfaceView.whole(s).toSurface();
    expect(out.getPixel(1, 1)).toEqual([1000, 2000, 3000, 4000]);
    expect(out.depth).toBe(16);
  });
});
