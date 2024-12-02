import { describe, expect, it } from 'vitest';

import { Rect } from '../../src/geom/rect.js';
import { Size } from '../../src/geom/size.js';
import { evenRect, isWholePixel, roundRect, snapRect, snapSize } from '../../src/geom/round.js';

const loose = Rect.of(10.4, 20.6, 30.3, 40.7);

describe('roundRect', () => {
  it('rounds every edge to the nearest by default', () => {
    expect(roundRect(loose).toJSON()).toEqual({ x: 10, y: 21, width: 31, height: 40 });
  });

  it('grows outward', () => {
    expect(roundRect(loose, { policy: 'outward' }).toJSON()).toEqual({
      x: 10,
      y: 20,
      width: 31,
      height: 42,
    });
  });

  it('pulls inward', () => {
    expect(roundRect(loose, { policy: 'inward' }).toJSON()).toEqual({
      x: 11,
      y: 21,
      width: 29,
      height: 40,
    });
  });

  it('floors both edges', () => {
    expect(roundRect(loose, { policy: 'floor' }).toJSON()).toEqual({
      x: 10,
      y: 20,
      width: 30,
      height: 41,
    });
  });

  it('uses banker rounding on an exact half', () => {
    expect(roundRect(Rect.of(0.5, 1.5, 2, 2)).toJSON()).toEqual({
      x: 0,
      y: 2,
      width: 2,
      height: 2,
    });
  });

  it('never inverts a rect it rounded inward past itself', () => {
    const thin = roundRect(Rect.of(10.2, 5, 0.3, 10), { policy: 'inward' });
    expect(thin.width).toBe(0);
  });

  it('refuses a policy it does not know', () => {
    expect(() => roundRect(loose, { policy: 'sideways' })).toThrow(/policy must be one of/);
  });
});

describe('snapSize', () => {
  it('snaps to the nearest multiple by default', () => {
    expect(snapSize(Size.of(101, 99), 4).toJSON()).toEqual({ width: 100, height: 100 });
  });

  it('snaps up and down on request', () => {
    expect(snapSize(Size.of(101, 99), 4, { direction: 'up' }).toJSON()).toEqual({
      width: 104,
      height: 100,
    });
    expect(snapSize(Size.of(101, 99), 4, { direction: 'down' }).toJSON()).toEqual({
      width: 100,
      height: 96,
    });
  });

  it('leaves a size that is already on the grid', () => {
    expect(snapSize(Size.of(96, 128), 32).toJSON()).toEqual({ width: 96, height: 128 });
  });

  it('refuses a multiple that is not a whole number above zero', () => {
    expect(() => snapSize(Size.of(10, 10), 0)).toThrow(/above zero/);
    expect(() => snapSize(Size.of(10, 10), 1.5)).toThrow(/whole number/);
    expect(() => snapSize(Size.of(10, 10), 4, { direction: 'sideways' })).toThrow(
      /direction must be one of/,
    );
  });
});

describe('snapRect', () => {
  it('snaps the extents and keeps the centre near where it was', () => {
    const snapped = snapRect(Rect.of(10, 10, 101, 99), 4);
    expect(snapped.size.toJSON()).toEqual({ width: 100, height: 100 });
    expect(Math.abs(snapped.centre.x - 60.5)).toBeLessThanOrEqual(1);
    expect(isWholePixel(snapped)).toBe(true);
  });

  it('grows to the next multiple when told to', () => {
    const snapped = snapRect(Rect.of(0, 0, 101, 99), 8, { direction: 'up' });
    expect(snapped.size.toJSON()).toEqual({ width: 104, height: 104 });
  });
});

describe('evenRect', () => {
  it('always lands on even extents', () => {
    for (const size of [
      [99, 51],
      [100, 50],
      [1, 1],
    ]) {
      const out = evenRect(Rect.of(0, 0, size[0], size[1]));
      expect(out.width % 2).toBe(0);
      expect(out.height % 2).toBe(0);
      expect(out.width).toBeGreaterThanOrEqual(size[0]);
    }
  });
});

describe('isWholePixel', () => {
  it('separates a planned crop from a rendered one', () => {
    expect(isWholePixel(Rect.of(1, 2, 3, 4))).toBe(true);
    expect(isWholePixel(Rect.of(1.5, 2, 3, 4))).toBe(false);
    expect(isWholePixel(Rect.of(1, 2, 3.25, 4))).toBe(false);
  });
});
