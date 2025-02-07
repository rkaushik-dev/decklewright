import { describe, expect, it } from 'vitest';

import { Angle } from '../../../src/geom/angle.js';
import { Rect } from '../../../src/geom/rect.js';
import { deskew, rotate, rotatedBounds } from '../../../src/raster/ops/rotate.js';
import { Surface } from '../../../src/raster/surface.js';

/**
 * @param {number} width
 * @param {number} height
 * @returns {Surface}
 */
function block(width, height) {
  const s = Surface.create({ width, height, layout: 'gray', fill: 200 });
  for (let y = 2; y < height - 2; y += 1) {
    for (let x = 2; x < width - 2; x += 1) s.set(x, y, 0, 40);
  }
  return s;
}

describe('rotate by whole quarters', () => {
  it('does no resampling at all', () => {
    const s = block(9, 5);
    const { surface, residual, quarterTurns } = rotate(s, Angle.degrees(90));
    expect(quarterTurns).toBe(1);
    expect(residual.isZero).toBe(true);
    expect(surface.size.toJSON()).toEqual({ width: 5, height: 9 });
    expect([...new Set(surface.data)].sort((a, b) => a - b)).toEqual([40, 200]);
  });

  it('leaves a zero angle alone but still copies', () => {
    const s = block(6, 6);
    const { surface, quarterTurns } = rotate(s, Angle.zero());
    expect(quarterTurns).toBe(0);
    expect(surface).toBe(s);
  });

  it('folds a full turn back to nothing', () => {
    const { quarterTurns, residual } = rotate(block(6, 6), Angle.degrees(360));
    expect(quarterTurns).toBe(0);
    expect(residual.isZero).toBe(true);
  });
});

describe('rotate by a small angle', () => {
  it('grows the canvas when expanding', () => {
    const s = block(20, 20);
    const { surface } = rotate(s, Angle.degrees(10));
    expect(surface.width).toBeGreaterThan(20);
    expect(surface.height).toBeGreaterThan(20);
  });

  it('keeps the canvas when told not to expand', () => {
    const s = block(20, 20);
    const { surface } = rotate(s, Angle.degrees(10), { expand: false });
    expect(surface.size.toJSON()).toEqual({ width: 20, height: 20 });
  });

  it('fills the new corners with the background', () => {
    const s = Surface.create({ width: 20, height: 20, layout: 'gray', fill: 200 });
    const { surface } = rotate(s, Angle.degrees(20), { background: 7 });
    expect(surface.get(0, 0)).toBe(7);
    expect(surface.get(Math.floor(surface.width / 2), Math.floor(surface.height / 2))).toBe(200);
  });

  it('reports the transform that put the samples where they are', () => {
    const s = block(20, 20);
    const { transform } = rotate(s, Angle.degrees(15));
    expect(transform.decompose().rotation.degrees).toBeCloseTo(15, 6);
  });

  it('leaves a flat capture flat', () => {
    const flat = Surface.create({ width: 24, height: 24, layout: 'gray', fill: 111 });
    const { surface } = rotate(flat, Angle.degrees(7), { expand: false, edge: 'clamp' });
    expect([...new Set(surface.data)]).toEqual([111]);
  });

  it('comes close to the original after turning back', () => {
    const s = block(30, 30);
    const once = rotate(s, Angle.degrees(6), { expand: false, edge: 'clamp' }).surface;
    const back = rotate(once, Angle.degrees(-6), { expand: false, edge: 'clamp' }).surface;
    let worst = 0;
    for (let y = 8; y < 22; y += 1) {
      for (let x = 8; x < 22; x += 1) {
        worst = Math.max(worst, Math.abs(back.get(x, y) - s.get(x, y)));
      }
    }
    expect(worst).toBeLessThan(30);
  });

  it('takes the nearest sampler when asked', () => {
    const s = block(20, 20);
    const { surface } = rotate(s, Angle.degrees(12), { sample: 'nearest' });
    const values = new Set(surface.data);
    expect([...values].every((v) => v === 0 || v === 40 || v === 200)).toBe(true);
  });

  it('pulls a quarter turn out before the resample', () => {
    const s = block(20, 10);
    const { quarterTurns, residual, surface } = rotate(s, Angle.degrees(92));
    expect(quarterTurns).toBe(1);
    expect(residual.degrees).toBeCloseTo(2, 9);
    expect(surface.height).toBeGreaterThan(surface.width);
  });
});

describe('deskew', () => {
  it('turns the other way from the measured skew', () => {
    const s = block(20, 20);
    const measured = Angle.degrees(3);
    const a = deskew(s, measured, { expand: false }).surface;
    const b = rotate(s, Angle.degrees(-3), { expand: false }).surface;
    expect([...a.data]).toEqual([...b.data]);
  });
});

describe('rotatedBounds', () => {
  it('agrees with what rotate produced', () => {
    const s = block(20, 14);
    const angle = Angle.degrees(11);
    const predicted = rotatedBounds(s.bounds, angle);
    const { surface } = rotate(s, angle);
    expect(surface.width).toBe(Math.round(predicted.width));
    expect(surface.height).toBe(Math.round(predicted.height));
  });

  it('swaps the extents on an odd quarter turn', () => {
    const out = rotatedBounds(Rect.of(0, 0, 20, 10), Angle.degrees(90));
    expect(out.width).toBe(10);
    expect(out.height).toBe(20);
  });

  it('is the identity for a zero angle', () => {
    const bounds = Rect.of(0, 0, 20, 10);
    expect(rotatedBounds(bounds, Angle.zero())).toBe(bounds);
  });
});
