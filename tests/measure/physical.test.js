import { describe, expect, it } from 'vitest';

import { Size } from '../../src/geom/size.js';
import {
  achievedFrom,
  describeSize,
  inUnit,
  matchNominal,
  nominalInPixels,
  physicalSize,
  pixelSize,
  sizeError,
} from '../../src/measure/physical.js';
import { Sampling } from '../../src/measure/resolution.js';

describe('physicalSize', () => {
  it('turns pixels into inches at a frequency', () => {
    const size = physicalSize(Size.of(6800, 5600), 400);
    expect(size).toEqual({ width: 17, height: 14, unit: 'in' });
  });

  it('answers in whatever unit is asked for', () => {
    const size = physicalSize(Size.of(400, 400), 400, 'mm');
    expect(size.width).toBeCloseTo(25.4, 9);
    expect(size.unit).toBe('mm');
  });

  it('uses each axis of a non square frequency', () => {
    const size = physicalSize(Size.of(600, 300), new Sampling(600, 300));
    expect(size).toEqual({ width: 1, height: 1, unit: 'in' });
  });

  it('refuses a unit it does not know', () => {
    expect(() => physicalSize(Size.of(1, 1), 400, 'px')).toThrow(/unknown unit px/);
  });
});

describe('pixelSize', () => {
  it('is the inverse of physicalSize', () => {
    const pixels = Size.of(6800, 5600);
    expect(pixelSize(physicalSize(pixels, 400), 400).equals(pixels, 1e-6)).toBe(true);
  });

  it('works from millimetres', () => {
    const pixels = pixelSize({ width: 25.4, height: 25.4, unit: 'mm' }, 400);
    expect(pixels.width).toBeCloseTo(400, 6);
  });
});

describe('inUnit', () => {
  it('returns the same object when the unit already matches', () => {
    const size = { width: 17, height: 14, unit: /** @type {const} */ ('in') };
    expect(inUnit(size, 'in')).toBe(size);
  });

  it('converts both sides', () => {
    const size = inUnit({ width: 17, height: 14, unit: 'in' }, 'mm');
    expect(size.width).toBeCloseTo(431.8, 6);
    expect(size.height).toBeCloseTo(355.6, 6);
  });
});

describe('sizeError', () => {
  it('is zero for an exact match', () => {
    const size = { width: 17, height: 14, unit: /** @type {const} */ ('in') };
    expect(sizeError(size, size)).toEqual({ error: 0, rotated: false });
  });

  it('takes the worse of the two sides', () => {
    const out = sizeError(
      { width: 17.1, height: 14.4, unit: 'in' },
      { width: 17, height: 14, unit: 'in' },
    );
    expect(out.error).toBeCloseTo(0.4, 9);
    expect(out.rotated).toBe(false);
  });

  it('spots a sheet placed the other way round', () => {
    const out = sizeError(
      { width: 14, height: 17, unit: 'in' },
      { width: 17, height: 14, unit: 'in' },
    );
    expect(out.error).toBeCloseTo(0, 9);
    expect(out.rotated).toBe(true);
  });

  it('compares across units', () => {
    const out = sizeError(
      { width: 431.8, height: 355.6, unit: 'mm' },
      { width: 17, height: 14, unit: 'in' },
    );
    expect(out.error).toBeLessThan(0.001);
  });
});

describe('matchNominal', () => {
  it('names an atlas plate', () => {
    const found = matchNominal({ width: 17.1, height: 13.9, unit: 'in' });
    expect(found?.name).toBe('atlasPlate');
    expect(found?.rotated).toBe(false);
  });

  it('names a plat book, which is the same plate turned', () => {
    const found = matchNominal({ width: 14, height: 17, unit: 'in' });
    expect(found?.name).toBeTruthy();
    expect(found?.error).toBeLessThan(0.01);
  });

  it('names an architectural sheet', () => {
    expect(matchNominal({ width: 24, height: 36, unit: 'in' })?.name).toBe('archD');
    expect(matchNominal({ width: 36, height: 48, unit: 'in' })?.name).toBe('archE');
  });

  it('names nothing for an odd size', () => {
    expect(matchNominal({ width: 9, height: 6, unit: 'in' })).toBeNull();
  });

  it('honours the tolerance', () => {
    const measured = { width: 17.3, height: 14, unit: /** @type {const} */ ('in') };
    expect(matchNominal(measured)).not.toBeNull();
    expect(matchNominal(measured, { tolerance: 0.2 })).toBeNull();
  });

  it('picks the closer of two candidates', () => {
    const found = matchNominal({ width: 21.4, height: 27, unit: 'in' }, { tolerance: 1 });
    expect(found?.name).toBe('quad15');
  });
});

describe('nominalInPixels', () => {
  it('sizes a nominal sheet at a frequency', () => {
    expect(nominalInPixels('archD', 400).toJSON()).toEqual({ width: 9600, height: 14400 });
  });

  it('refuses a name it does not know', () => {
    expect(() => nominalInPixels('tabloid', 400)).toThrow(/no nominal size called tabloid/);
  });
});

describe('achievedFrom', () => {
  it('reports what a capture of a known object really reached', () => {
    const sampling = achievedFrom(Size.of(6800, 5600), { width: 17, height: 14, unit: 'in' });
    expect(sampling.x).toBe(400);
    expect(sampling.y).toBe(400);
  });

  it('reports each axis separately when they differ', () => {
    const sampling = achievedFrom(Size.of(6800, 2800), { width: 17, height: 14, unit: 'in' });
    expect(sampling.x).toBe(400);
    expect(sampling.y).toBe(200);
    expect(sampling.isSquare).toBe(false);
  });

  it('refuses a physical size with a side at zero', () => {
    expect(() => achievedFrom(Size.of(10, 10), { width: 0, height: 1, unit: 'in' })).toThrow(
      /both sides above zero/,
    );
  });
});

describe('describeSize', () => {
  it('writes both sides with the unit', () => {
    expect(describeSize({ width: 17, height: 14, unit: 'in' })).toBe('17.0000 in by 14.0000 in');
    expect(describeSize({ width: 431.8, height: 355.6, unit: 'mm' }, 1)).toBe(
      '431.8 mm by 355.6 mm',
    );
  });
});
