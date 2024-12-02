import { describe, expect, it } from 'vitest';

import { alignIn, fitIn, fitScale, forceAspect } from '../../src/geom/fit.js';
import { Rect } from '../../src/geom/rect.js';
import { Size } from '../../src/geom/size.js';

const bound = Rect.of(0, 0, 200, 100);

describe('alignIn', () => {
  it('centres by default', () => {
    expect(alignIn(Size.of(100, 50), bound).toJSON()).toEqual({
      x: 50,
      y: 25,
      width: 100,
      height: 50,
    });
  });

  it('honours each alignment', () => {
    expect(
      alignIn(Size.of(100, 50), bound, { alignX: 'start', alignY: 'start' }).origin.toJSON(),
    ).toEqual({ x: 0, y: 0 });
    expect(
      alignIn(Size.of(100, 50), bound, { alignX: 'end', alignY: 'end' }).origin.toJSON(),
    ).toEqual({ x: 100, y: 50 });
  });

  it('offsets from the bound rather than the origin', () => {
    const moved = alignIn(Size.of(100, 50), Rect.of(10, 10, 200, 100), { alignX: 'start' });
    expect(moved.x).toBe(10);
  });

  it('refuses an alignment it does not know', () => {
    expect(() => alignIn(Size.of(1, 1), bound, { alignX: 'middle' })).toThrow(
      /alignX must be one of/,
    );
  });
});

describe('fitIn', () => {
  it('contains by default and leaves margin on one axis', () => {
    const placed = fitIn(Size.of(100, 100), bound);
    expect(placed.toJSON()).toEqual({ x: 50, y: 0, width: 100, height: 100 });
  });

  it('covers and overhangs', () => {
    const placed = fitIn(Size.of(100, 100), bound, { mode: 'cover' });
    expect(placed.width).toBe(200);
    expect(placed.height).toBe(200);
    expect(placed.y).toBe(-50);
  });

  it('stretches to the bound exactly', () => {
    expect(fitIn(Size.of(100, 100), bound, { mode: 'stretch' }).toJSON()).toEqual({
      x: 0,
      y: 0,
      width: 200,
      height: 100,
    });
  });

  it('places without scaling under none', () => {
    expect(fitIn(Size.of(40, 20), bound, { mode: 'none' }).toJSON()).toEqual({
      x: 80,
      y: 40,
      width: 40,
      height: 20,
    });
  });

  it('refuses to upscale when told not to', () => {
    const small = Size.of(50, 25);
    expect(fitIn(small, bound).width).toBe(200);
    expect(fitIn(small, bound, { allowUpscale: false }).width).toBe(50);
  });

  it('still downscales when upscaling is off', () => {
    const big = Size.of(400, 200);
    expect(fitIn(big, bound, { allowUpscale: false }).width).toBe(200);
  });

  it('gives an empty rect for an empty source or bound', () => {
    expect(fitIn(Size.empty(), bound).isEmpty).toBe(true);
    expect(fitIn(Size.of(10, 10), Rect.empty()).isEmpty).toBe(true);
  });

  it('refuses a mode it does not know', () => {
    expect(() => fitIn(Size.of(1, 1), bound, { mode: 'squash' })).toThrow(/mode must be one of/);
  });
});

describe('fitScale', () => {
  it('matches what fitIn did', () => {
    expect(fitScale(Size.of(100, 100), bound.size)).toBe(1);
    expect(fitScale(Size.of(100, 100), bound.size, 'cover')).toBe(2);
    expect(fitScale(Size.of(100, 100), bound.size, 'none')).toBe(1);
    expect(fitScale(Size.of(400, 200), bound.size)).toBe(0.5);
  });

  it('is zero when either side is empty', () => {
    expect(fitScale(Size.empty(), bound.size)).toBe(0);
    expect(fitScale(bound.size, Size.empty())).toBe(0);
  });
});

describe('forceAspect', () => {
  const wide = Rect.of(0, 0, 200, 100);

  it('grows the short axis by default', () => {
    const squared = forceAspect(wide, 1);
    expect(squared.width).toBe(200);
    expect(squared.height).toBe(200);
    expect(squared.centre.equals(wide.centre)).toBe(true);
  });

  it('shrinks the long axis when asked', () => {
    const squared = forceAspect(wide, 1, { direction: 'shrink' });
    expect(squared.width).toBe(100);
    expect(squared.height).toBe(100);
  });

  it('works the other way for a tall rect', () => {
    const tall = Rect.of(0, 0, 100, 200);
    expect(forceAspect(tall, 1).width).toBe(200);
    expect(forceAspect(tall, 1, { direction: 'shrink' }).height).toBe(100);
  });

  it('leaves a rect that already matches', () => {
    const square = Rect.of(0, 0, 100, 100);
    expect(forceAspect(square, 1).equals(square)).toBe(true);
  });

  it('passes an empty rect through', () => {
    expect(forceAspect(Rect.empty(), 1.5).isEmpty).toBe(true);
  });

  it('refuses a direction it does not know', () => {
    expect(() => forceAspect(wide, 1, { direction: 'sideways' })).toThrow(
      /direction must be one of/,
    );
  });
});
