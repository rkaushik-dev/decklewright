import { describe, expect, it } from 'vitest';

import { Tolerance, toTolerance } from '../../src/measure/tolerance.js';

describe('building a band', () => {
  it('keeps the two sides apart', () => {
    const t = new Tolerance(1, 3);
    expect(t.minus).toBe(1);
    expect(t.plus).toBe(3);
    expect(t.isSymmetric).toBe(false);
    expect(Object.isFrozen(t)).toBe(true);
  });

  it('has the shorthands the profiles use', () => {
    expect(Tolerance.symmetric(2).isSymmetric).toBe(true);
    expect(Tolerance.exact().isExact).toBe(true);
    expect(Tolerance.upOnly(2).minus).toBe(0);
    expect(Tolerance.downOnly(2).plus).toBe(0);
    expect(Tolerance.percent(5).plus).toBeCloseTo(0.05, 12);
    expect(Tolerance.percent(5).kind).toBe('relative');
  });

  it('refuses a negative side, since the sides are magnitudes', () => {
    expect(() => new Tolerance(-1, 1)).toThrow(/minus must not be negative/);
    expect(() => new Tolerance(1, -1)).toThrow(/plus must not be negative/);
  });

  it('refuses an unknown kind', () => {
    expect(() => new Tolerance(1, 1, 'fractional')).toThrow(/kind must be one of/);
  });
});

describe('parse', () => {
  it('reads a bare number as symmetric and absolute', () => {
    const t = Tolerance.parse('0.5');
    expect(t.toJSON()).toEqual({ minus: 0.5, plus: 0.5, kind: 'absolute' });
  });

  it('reads a signed number as one sided', () => {
    expect(Tolerance.parse('+2').toJSON()).toEqual({ minus: 0, plus: 2, kind: 'absolute' });
    expect(Tolerance.parse('-2').toJSON()).toEqual({ minus: 2, plus: 0, kind: 'absolute' });
  });

  it('reads a percentage', () => {
    const t = Tolerance.parse('5%');
    expect(t.kind).toBe('relative');
    expect(t.plus).toBeCloseTo(0.05, 12);
  });

  it('reads the two sided form', () => {
    const t = Tolerance.parse('+3/-1');
    expect(t.plus).toBe(3);
    expect(t.minus).toBe(1);
  });

  it('reads the two sided form as a percentage', () => {
    const t = Tolerance.parse('+10/-5%');
    expect(t.kind).toBe('relative');
    expect(t.plus).toBeCloseTo(0.1, 12);
    expect(t.minus).toBeCloseTo(0.05, 12);
  });

  it('tolerates surrounding space', () => {
    expect(Tolerance.parse('  0.5  ').plus).toBe(0.5);
  });

  it('refuses junk', () => {
    expect(() => Tolerance.parse('about a bit')).toThrow(/cannot read about a bit as a tolerance/);
    expect(() => Tolerance.parse('+/-1')).toThrow(/cannot read/);
  });
});

describe('bounds and membership on an absolute band', () => {
  const t = new Tolerance(1, 3);

  it('offsets the target by each side', () => {
    expect(t.boundsFor(10)).toEqual({ low: 9, high: 13 });
  });

  it('is inclusive at both edges', () => {
    expect(t.contains(10, 9)).toBe(true);
    expect(t.contains(10, 13)).toBe(true);
    expect(t.contains(10, 8.999)).toBe(false);
    expect(t.contains(10, 13.001)).toBe(false);
  });

  it('reports a signed excursion and zero inside', () => {
    expect(t.excursion(10, 8)).toBe(-1);
    expect(t.excursion(10, 15)).toBe(2);
    expect(t.excursion(10, 11)).toBe(0);
  });

  it('clamps into the band', () => {
    expect(t.clampInto(10, 5)).toBe(9);
    expect(t.clampInto(10, 20)).toBe(13);
    expect(t.clampInto(10, 11)).toBe(11);
  });
});

describe('a relative band', () => {
  const t = Tolerance.percent(10);

  it('scales with the target', () => {
    expect(t.boundsFor(100)).toEqual({ low: 90, high: 110 });
    expect(t.boundsFor(400)).toEqual({ low: 360, high: 440 });
  });

  it('uses the magnitude of a negative target so the band does not invert', () => {
    const bounds = t.boundsFor(-100);
    expect(bounds.low).toBeCloseTo(-110, 9);
    expect(bounds.high).toBeCloseTo(-90, 9);
  });

  it('collapses to the target when the target is zero', () => {
    expect(t.boundsFor(0)).toEqual({ low: 0, high: 0 });
  });
});

describe('exact', () => {
  it('admits only the target itself', () => {
    const t = Tolerance.exact();
    expect(t.contains(400, 400)).toBe(true);
    expect(t.contains(400, 400.0001)).toBe(false);
  });
});

describe('widened', () => {
  it('scales both sides and keeps the kind', () => {
    const wide = new Tolerance(1, 3, 'relative').widened(2);
    expect(wide.toJSON()).toEqual({ minus: 2, plus: 6, kind: 'relative' });
  });

  it('collapses to exact at a factor of zero', () => {
    expect(new Tolerance(1, 3).widened(0).isExact).toBe(true);
  });

  it('refuses a negative factor', () => {
    expect(() => new Tolerance(1, 3).widened(-1)).toThrow(/factor must not be negative/);
  });
});

describe('equality and printing', () => {
  it('compares all three fields', () => {
    expect(new Tolerance(1, 1).equals(Tolerance.symmetric(1))).toBe(true);
    expect(new Tolerance(1, 1).equals(new Tolerance(1, 1, 'relative'))).toBe(false);
    expect(new Tolerance(1, 1).equals(1)).toBe(false);
  });

  it('prints the short form when symmetric', () => {
    expect(String(Tolerance.symmetric(0.5))).toBe('+/-0.5');
    expect(String(new Tolerance(1, 3))).toBe('+3/-1');
    expect(String(Tolerance.percent(5))).toBe('+/-5%');
    expect(String(new Tolerance(0.05, 0.1, 'relative'))).toBe('+10%/-5%');
  });
});

describe('toTolerance', () => {
  it('takes the shapes a profile may hold', () => {
    const t = new Tolerance(1, 3);
    expect(toTolerance(t)).toBe(t);
    expect(toTolerance(2).equals(Tolerance.symmetric(2))).toBe(true);
    expect(toTolerance('+3/-1').equals(t)).toBe(true);
    expect(toTolerance({ minus: 1, plus: 3 }).equals(t)).toBe(true);
    expect(toTolerance({ minus: 1, plus: 3, kind: 'relative' }).kind).toBe('relative');
  });

  it('rejects anything else', () => {
    expect(() => toTolerance(null)).toThrow(/cannot read a tolerance from that/);
    expect(() => toTolerance([1, 3])).toThrow(/cannot read a tolerance from that/);
  });
});
