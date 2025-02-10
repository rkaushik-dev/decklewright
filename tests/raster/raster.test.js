import { describe, expect, it } from 'vitest';

import { Rect } from '../../src/geom/rect.js';
import { Raster } from '../../src/raster/raster.js';
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

describe('both readable regions share one base', () => {
  it('a surface and a view are both rasters', () => {
    const s = ramp(4, 4);
    expect(s).toBeInstanceOf(Raster);
    expect(SurfaceView.whole(s)).toBeInstanceOf(Raster);
  });

  it('they agree on the derived values', () => {
    const s = Surface.create({ width: 4, height: 4, layout: 'rgba', depth: 16 });
    const v = SurfaceView.whole(s);
    expect(v.layout).toBe(s.layout);
    expect(v.depth).toBe(s.depth);
    expect(v.channels).toBe(s.channels);
    expect(v.maxValue).toBe(s.maxValue);
    expect(v.rowStride).toBe(s.rowStride);
    expect(v.toJSON()).toEqual(s.toJSON());
  });

  it('a window reports its own extent rather than the parent one', () => {
    const s = ramp(8, 8);
    const v = new SurfaceView(s, Rect.of(2, 2, 3, 4));
    expect(v.size.toJSON()).toEqual({ width: 3, height: 4 });
    expect(v.bounds.toJSON()).toEqual({ x: 0, y: 0, width: 3, height: 4 });
    expect(v.rowStride).toBe(3);
  });
});

describe('the shared pixel accessors', () => {
  it('read and write whole pixels through either region', () => {
    const s = Surface.create({ width: 2, height: 2, layout: 'rgb' });
    const v = new SurfaceView(s, Rect.of(1, 1, 1, 1));
    v.setPixel(0, 0, [7, 8, 9]);
    expect(s.getPixel(1, 1)).toEqual([7, 8, 9]);
    expect(v.getPixel(0, 0)).toEqual([7, 8, 9]);
  });

  it('name the region they are complaining about', () => {
    const s = ramp(4, 4);
    expect(() => s.get(9, 0)).toThrow(/outside the surface/);
    expect(() => SurfaceView.whole(s).get(9, 0)).toThrow(/outside the view/);
  });

  it('reject a channel outside the layout the same way', () => {
    const s = ramp(4, 4);
    expect(() => s.get(0, 0, 2)).toThrow(/outside the layout/);
    expect(() => SurfaceView.whole(s).get(0, 0, 2)).toThrow(/outside the layout/);
  });

  it('reject a pixel with the wrong channel count the same way', () => {
    const s = Surface.create({ width: 1, height: 1, layout: 'rgb' });
    expect(() => s.setPixel(0, 0, [1])).toThrow(/wrong number of channels/);
    expect(() => SurfaceView.whole(s).setPixel(0, 0, [1])).toThrow(/wrong number of channels/);
  });
});

describe('rows', () => {
  it('walks every row of a surface', () => {
    const s = ramp(3, 2);
    expect([...s.rows()].map((row) => [...row])).toEqual([
      [0, 1, 2],
      [3, 4, 5],
    ]);
  });

  it('walks only the rows a view covers', () => {
    const s = ramp(3, 3);
    const v = new SurfaceView(s, Rect.of(1, 1, 2, 2));
    expect([...v.rows()].map((row) => [...row])).toEqual([
      [4, 5],
      [7, 8],
    ]);
  });

  it('yields live rows', () => {
    const s = ramp(2, 2);
    for (const row of s.rows()) row[0] = 200;
    expect(s.get(0, 0)).toBe(200);
    expect(s.get(0, 1)).toBe(200);
  });
});

describe('printing', () => {
  it('says the geometry for a surface and the window for a view', () => {
    const s = Surface.create({ width: 4, height: 3, layout: 'gray', depth: 16 });
    expect(String(s)).toBe('4x3 gray16');
    expect(String(new SurfaceView(s, Rect.of(1, 0, 2, 2)))).toBe('view 2x2+1+0 of 4x3 gray16');
  });
});
