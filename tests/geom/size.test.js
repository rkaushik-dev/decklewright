import { describe, expect, it } from 'vitest';

import { Size, assertNotEmpty, sizeFromAspect } from '../../src/geom/size.js';

describe('building a size', () => {
  it('allows zero but not negative', () => {
    expect(Size.of(0, 0).isEmpty).toBe(true);
    expect(() => Size.of(-1, 10)).toThrow(/width must not be negative/);
    expect(() => Size.of(10, -1)).toThrow(/height must not be negative/);
  });

  it('has the shorthands', () => {
    expect(Size.square(5).toJSON()).toEqual({ width: 5, height: 5 });
    expect(Size.empty().area).toBe(0);
  });

  it('reads the shapes a caller may hand it', () => {
    const s = Size.of(4, 3);
    expect(Size.from(s)).toBe(s);
    expect(Size.from([4, 3]).equals(s)).toBe(true);
    expect(Size.from({ width: 4, height: 3 }).equals(s)).toBe(true);
    expect(() => Size.from('4x3')).toThrow(/cannot read a size from that/);
  });
});

describe('orientation and aspect', () => {
  it('separates landscape, portrait and square', () => {
    expect(Size.of(4, 3).isLandscape).toBe(true);
    expect(Size.of(3, 4).isPortrait).toBe(true);
    expect(Size.square(4).isLandscape).toBe(false);
    expect(Size.square(4).isPortrait).toBe(false);
  });

  it('reports width over height', () => {
    expect(Size.of(4, 2).aspect).toBe(2);
  });

  it('refuses an aspect when there is no height', () => {
    expect(() => Size.of(4, 0).aspect).toThrow(/no height has no aspect/);
  });

  it('swaps the axes', () => {
    expect(Size.of(4, 3).swapped.toJSON()).toEqual({ width: 3, height: 4 });
  });
});

describe('scaling and growing', () => {
  it('scales uniformly or per axis', () => {
    expect(Size.of(4, 3).scaled(2).toJSON()).toEqual({ width: 8, height: 6 });
    expect(Size.of(4, 3).scaled(2, 1).toJSON()).toEqual({ width: 8, height: 3 });
  });

  it('refuses a negative factor', () => {
    expect(() => Size.of(4, 3).scaled(-1)).toThrow(/factor must not be negative/);
  });

  it('clamps a shrink at zero rather than going negative', () => {
    expect(Size.of(4, 3).grown(-10).toJSON()).toEqual({ width: 0, height: 0 });
    expect(Size.of(4, 3).grown(2, 0).toJSON()).toEqual({ width: 6, height: 3 });
  });

  it('rounds without dipping below zero', () => {
    expect(Size.of(4.6, 3.4).rounded().toJSON()).toEqual({ width: 5, height: 3 });
    expect(Size.of(0.2, 0.2).rounded(Math.floor).toJSON()).toEqual({ width: 0, height: 0 });
  });
});

describe('containedIn and covering', () => {
  const plate = Size.of(400, 300);

  it('fits inside the bound without distortion', () => {
    const fitted = plate.containedIn(Size.of(200, 200));
    expect(fitted.toJSON()).toEqual({ width: 200, height: 150 });
    expect(fitted.fitsInside(Size.of(200, 200))).toBe(true);
  });

  it('covers the bound without distortion', () => {
    const covered = plate.covering(Size.of(200, 200));
    expect(covered.toJSON()).toEqual({ width: 266.66666666666663, height: 200 });
  });

  it('gives up on an empty side', () => {
    expect(plate.containedIn(Size.empty()).isEmpty).toBe(true);
    expect(Size.empty().covering(plate).isEmpty).toBe(true);
  });
});

describe('ratioLabel', () => {
  it('reduces whole numbers', () => {
    expect(Size.of(1200, 800).ratioLabel()).toBe('3:2');
    expect(Size.of(1920, 1080).ratioLabel()).toBe('16:9');
  });

  it('falls back to the plain ratio when the size is fractional', () => {
    expect(Size.of(4.5, 3).ratioLabel()).toBe('4.5:3');
  });

  it('does not divide by zero on an empty size', () => {
    expect(Size.of(0, 0).ratioLabel()).toBe('0:0');
  });
});

describe('equals and printing', () => {
  it('compares within a tolerance', () => {
    expect(Size.of(4, 3).equals(Size.of(4, 3))).toBe(true);
    expect(Size.of(4, 3).equals(Size.of(4.4, 3))).toBe(false);
    expect(Size.of(4, 3).equals(Size.of(4.4, 3), 0.5)).toBe(true);
    expect(Size.of(4, 3).equals({ width: 4, height: 3 })).toBe(false);
  });

  it('prints with an x between', () => {
    expect(String(Size.of(1.23456, 3))).toBe('1.235x3');
  });
});

describe('the helpers', () => {
  it('rejects an empty size by name', () => {
    expect(() => assertNotEmpty(Size.of(4, 0), 'crop')).toThrow(/crop must have both extents/);
    const good = Size.of(4, 3);
    expect(assertNotEmpty(good, 'crop')).toBe(good);
  });

  it('builds a size from an aspect and a width', () => {
    expect(sizeFromAspect(2, 400).toJSON()).toEqual({ width: 400, height: 200 });
    expect(() => sizeFromAspect(0, 400)).toThrow(/aspect must be greater than zero/);
    expect(() => sizeFromAspect(2, 0)).toThrow(/width must be greater than zero/);
  });
});
