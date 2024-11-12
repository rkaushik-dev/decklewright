import { describe, expect, it } from 'vitest';

import {
  Sampling,
  achievedSampling,
  requiredPixels,
  toSampling,
} from '../../src/measure/resolution.js';

describe('Sampling', () => {
  it('mirrors a single figure onto both axes', () => {
    const s = Sampling.of(600);
    expect(s.x).toBe(600);
    expect(s.y).toBe(600);
    expect(s.isSquare).toBe(true);
  });

  it('keeps non square pixels apart', () => {
    const s = new Sampling(600, 400);
    expect(s.isSquare).toBe(false);
    expect(s.along('x')).toBe(600);
    expect(s.along('y')).toBe(400);
  });

  it('is frozen once built', () => {
    const s = Sampling.of(400);
    expect(Object.isFrozen(s)).toBe(true);
  });

  it('refuses a frequency of zero or less', () => {
    expect(() => Sampling.of(0)).toThrow(/x must be greater than zero/);
    expect(() => new Sampling(600, -1)).toThrow(/y must be greater than zero/);
  });

  it('refuses an axis it does not have', () => {
    expect(() => Sampling.of(600).along('z')).toThrow(/axis must be one of x, y/);
  });
});

describe('converting between pixels and length', () => {
  const s = Sampling.of(400);

  it('turns inches into pixels', () => {
    expect(s.toPixels(1, 'in')).toBe(400);
    expect(s.toPixels(25.4, 'mm')).toBeCloseTo(400, 9);
  });

  it('turns pixels back into length', () => {
    expect(s.toLength(400, 'in')).toBeCloseTo(1, 12);
    expect(s.toLength(400, 'mm')).toBeCloseTo(25.4, 9);
  });

  it('uses the right axis when the pixels are not square', () => {
    const odd = new Sampling(600, 300);
    expect(odd.toPixels(1, 'in', 'x')).toBe(600);
    expect(odd.toPixels(1, 'in', 'y')).toBe(300);
    expect(odd.toLength(600, 'in', 'y')).toBe(2);
  });

  it('reports samples per unit', () => {
    expect(Sampling.of(254).per('mm')).toBeCloseTo(10, 9);
    expect(Sampling.of(72).per('pt')).toBeCloseTo(1, 9);
  });
});

describe('ppiFrom', () => {
  it('calibrates from a measured object', () => {
    expect(Sampling.ppiFrom({ pixels: 6800, length: 17, unit: 'in' })).toBe(400);
    expect(Sampling.ppiFrom({ pixels: 1000, length: 25.4, unit: 'mm' })).toBeCloseTo(1000, 9);
  });

  it('refuses a zero length or zero pixel count', () => {
    expect(() => Sampling.ppiFrom({ pixels: 0, length: 17, unit: 'in' })).toThrow(
      /pixels must be greater than zero/,
    );
    expect(() => Sampling.ppiFrom({ pixels: 10, length: 0, unit: 'in' })).toThrow(
      /length must be greater than zero/,
    );
  });
});

describe('scaled and equals', () => {
  it('scales both axes together', () => {
    const half = new Sampling(600, 300).scaled(0.5);
    expect(half.x).toBe(300);
    expect(half.y).toBe(150);
  });

  it('compares within a tolerance in ppi', () => {
    expect(Sampling.of(400).equals(Sampling.of(400))).toBe(true);
    expect(Sampling.of(400).equals(Sampling.of(400.5))).toBe(false);
    expect(Sampling.of(400).equals(Sampling.of(400.5), 1)).toBe(true);
    expect(Sampling.of(400).equals(400)).toBe(false);
  });
});

describe('serialisation', () => {
  it('prints one figure for square pixels and two otherwise', () => {
    expect(String(Sampling.of(600))).toBe('600 ppi');
    expect(String(new Sampling(600, 300))).toBe('600x300 ppi');
    expect(String(Sampling.of(599.996))).toBe('600 ppi');
  });

  it('serialises to a flat pair', () => {
    expect(new Sampling(600, 300).toJSON()).toEqual({ x: 600, y: 300 });
  });
});

describe('toSampling', () => {
  it('passes an instance through untouched', () => {
    const s = Sampling.of(400);
    expect(toSampling(s)).toBe(s);
  });

  it('accepts a bare number and a plain pair', () => {
    expect(toSampling(400).x).toBe(400);
    expect(toSampling({ x: 600, y: 300 }).y).toBe(300);
  });

  it('refuses anything else', () => {
    expect(() => toSampling('400')).toThrow(/cannot read a sampling frequency/);
    expect(() => toSampling({ x: 400 })).toThrow(/cannot read a sampling frequency/);
  });
});

describe('requiredPixels', () => {
  it('rounds up so the floor is never missed by a pixel', () => {
    expect(requiredPixels(17, 'in', 400)).toBe(6800);
    expect(requiredPixels(17.001, 'in', 400)).toBe(6801);
  });

  it('works from millimetres', () => {
    expect(requiredPixels(431.8, 'mm', 400)).toBe(6800);
  });
});

describe('achievedSampling', () => {
  it('says what a rendered derivative actually reached', () => {
    expect(achievedSampling(3400, 17, 'in').x).toBe(200);
  });
});
