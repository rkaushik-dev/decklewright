import { describe, expect, it } from 'vitest';

import { Rect } from '../../../src/geom/rect.js';
import { crop, cropClamped, cropPadded, cropView } from '../../../src/raster/ops/crop.js';
import { Surface } from '../../../src/raster/surface.js';

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

describe('crop', () => {
  const s = ramp(4, 4);

  it('cuts the rect out', () => {
    const out = crop(s, Rect.of(1, 1, 2, 2));
    expect(out.size.toJSON()).toEqual({ width: 2, height: 2 });
    expect([...out.data]).toEqual([5, 6, 9, 10]);
  });

  it('grows a fractional rect outward by default', () => {
    const out = crop(s, Rect.of(1.2, 1.2, 1.6, 1.6));
    expect(out.size.toJSON()).toEqual({ width: 2, height: 2 });
  });

  it('honours another rounding policy', () => {
    const out = crop(s, Rect.of(0.4, 0.4, 3.2, 3.2), { round: 'inward' });
    expect(out.size.toJSON()).toEqual({ width: 2, height: 2 });
    expect([...out.data]).toEqual([5, 6, 9, 10]);
  });

  it('refuses a crop that leaves the capture', () => {
    expect(() => crop(s, Rect.of(3, 3, 4, 4))).toThrow(/runs off the capture/);
    expect(() => crop(s, Rect.of(-1, 0, 2, 2))).toThrow(/runs off the capture/);
  });

  it('refuses an empty crop', () => {
    expect(() => crop(s, Rect.of(1, 1, 0, 2))).toThrow(/has no area/);
  });

  it('does not share samples with the capture', () => {
    const out = crop(s, Rect.of(0, 0, 2, 2));
    out.set(0, 0, 0, 200);
    expect(s.get(0, 0)).toBe(0);
  });
});

describe('cropView', () => {
  it('gives a live window rather than a copy', () => {
    const s = ramp(4, 4);
    const v = cropView(s, Rect.of(1, 1, 2, 2));
    v.set(0, 0, 0, 88);
    expect(s.get(1, 1)).toBe(88);
  });

  it('rounds the same way crop does', () => {
    const s = ramp(4, 4);
    expect(cropView(s, Rect.of(1.2, 1.2, 1.6, 1.6)).width).toBe(2);
  });
});

describe('cropClamped', () => {
  const s = ramp(4, 4);

  it('keeps only what overlaps', () => {
    const out = cropClamped(s, Rect.of(2, 2, 4, 4));
    expect(out.size.toJSON()).toEqual({ width: 2, height: 2 });
    expect([...out.data]).toEqual([10, 11, 14, 15]);
  });

  it('is the same as crop when the rect is inside', () => {
    expect([...cropClamped(s, Rect.of(1, 1, 2, 2)).data]).toEqual([5, 6, 9, 10]);
  });

  it('refuses a rect that misses the capture entirely', () => {
    expect(() => cropClamped(s, Rect.of(10, 10, 2, 2))).toThrow(/does not touch the capture/);
  });
});

describe('cropPadded', () => {
  const s = ramp(4, 4);

  it('keeps the planned size and reports nothing was filled', () => {
    const { surface, filled, covered } = cropPadded(s, Rect.of(1, 1, 2, 2));
    expect(surface.size.toJSON()).toEqual({ width: 2, height: 2 });
    expect(filled).toBe(false);
    expect(covered.toJSON()).toEqual({ x: 1, y: 1, width: 2, height: 2 });
  });

  it('pads the part that fell off the bed', () => {
    const { surface, filled, covered } = cropPadded(s, Rect.of(3, 3, 2, 2), { fill: 200 });
    expect(surface.size.toJSON()).toEqual({ width: 2, height: 2 });
    expect(filled).toBe(true);
    expect(covered.area).toBe(1);
    expect([...surface.data]).toEqual([15, 200, 200, 200]);
  });

  it('pads on the leading edge too', () => {
    const { surface } = cropPadded(s, Rect.of(-1, -1, 2, 2), { fill: 7 });
    expect([...surface.data]).toEqual([7, 7, 7, 0]);
  });

  it('fills everything when the rect misses the capture', () => {
    const { surface, filled, covered } = cropPadded(s, Rect.of(20, 20, 2, 2), { fill: 3 });
    expect(filled).toBe(true);
    expect(covered.isEmpty).toBe(true);
    expect([...surface.data]).toEqual([3, 3, 3, 3]);
  });

  it('takes a colour fill for a colour capture', () => {
    const colour = Surface.create({ width: 2, height: 2, layout: 'rgb', fill: 1 });
    const { surface } = cropPadded(colour, Rect.of(1, 1, 2, 2), { fill: [9, 8, 7] });
    expect(surface.getPixel(0, 0)).toEqual([1, 1, 1]);
    expect(surface.getPixel(1, 1)).toEqual([9, 8, 7]);
  });

  it('refuses a fill with the wrong channel count', () => {
    const colour = Surface.create({ width: 2, height: 2, layout: 'rgb' });
    expect(() => cropPadded(colour, Rect.of(0, 0, 2, 2), { fill: [1, 2] })).toThrow(
      /wrong number of channels/,
    );
  });

  it('refuses an empty crop', () => {
    expect(() => cropPadded(s, Rect.of(0, 0, 0, 0))).toThrow(/has no area/);
  });
});
