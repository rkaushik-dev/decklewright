import { describe, expect, it } from 'vitest';

import { Angle, meanAngle, meanLineAngle } from '../../src/geom/angle.js';
import { Point } from '../../src/geom/point.js';

describe('building an angle', () => {
  it('stores degrees and is frozen', () => {
    const a = Angle.degrees(30);
    expect(a.degrees).toBe(30);
    expect(Object.isFrozen(a)).toBe(true);
  });

  it('converts from and to radians', () => {
    expect(Angle.radians(Math.PI).degrees).toBeCloseTo(180, 12);
    expect(Angle.degrees(90).radians).toBeCloseTo(Math.PI / 2, 12);
    expect(Angle.zero().isZero).toBe(true);
  });

  it('measures the direction between two points', () => {
    expect(Angle.between(Point.of(0, 0), Point.of(1, 0)).degrees).toBe(0);
    expect(Angle.between(Point.of(0, 0), Point.of(0, 1)).degrees).toBeCloseTo(90, 12);
    expect(Angle.between(Point.of(0, 0), Point.of(-1, 0)).degrees).toBeCloseTo(180, 12);
  });

  it('refuses two identical points', () => {
    expect(() => Angle.between(Point.of(3, 3), Point.of(3, 3))).toThrow(/no direction/);
  });
});

describe('wrapped', () => {
  it('folds into the half open turn', () => {
    expect(Angle.degrees(370).wrapped().degrees).toBe(10);
    expect(Angle.degrees(-370).wrapped().degrees).toBe(-10);
    expect(Angle.degrees(180).wrapped().degrees).toBe(180);
    expect(Angle.degrees(-180).wrapped().degrees).toBe(180);
    expect(Angle.degrees(540).wrapped().degrees).toBe(180);
  });

  it('never reports a negative zero', () => {
    expect(Object.is(Angle.degrees(-360).wrapped().degrees, 0)).toBe(true);
  });
});

describe('split', () => {
  it('leaves a small angle alone', () => {
    const { quarterTurns, residual } = Angle.degrees(1.4).split();
    expect(quarterTurns).toBe(0);
    expect(residual.degrees).toBeCloseTo(1.4, 12);
  });

  it('pulls out a quarter turn', () => {
    const { quarterTurns, residual } = Angle.degrees(91.4).split();
    expect(quarterTurns).toBe(1);
    expect(residual.degrees).toBeCloseTo(1.4, 12);
  });

  it('handles a negative angle by turning the other way', () => {
    const { quarterTurns, residual } = Angle.degrees(-89).split();
    expect(quarterTurns).toBe(3);
    expect(residual.degrees).toBeCloseTo(1, 12);
  });

  it('puts the boundary at plus forty five rather than minus', () => {
    expect(Angle.degrees(45).split()).toEqual({ quarterTurns: 0, residual: Angle.degrees(45) });
    const low = Angle.degrees(-45).split();
    expect(low.quarterTurns).toBe(3);
    expect(low.residual.degrees).toBeCloseTo(45, 12);
  });

  it('reports quarter turns in the range zero to three', () => {
    for (const d of [-720, -181, -90, 0, 90, 181, 359, 720]) {
      const { quarterTurns } = Angle.degrees(d).split();
      expect(quarterTurns).toBeGreaterThanOrEqual(0);
      expect(quarterTurns).toBeLessThanOrEqual(3);
    }
  });

  it('reassembles to the original angle', () => {
    for (const d of [-173.2, -45.5, 0, 12.5, 88.8, 179.9]) {
      const { quarterTurns, residual } = Angle.degrees(d).split();
      const rebuilt = Angle.degrees(quarterTurns * 90 + residual.degrees);
      expect(rebuilt.equals(Angle.degrees(d), 1e-9)).toBe(true);
    }
  });
});

describe('arithmetic', () => {
  it('adds, subtracts, scales and negates', () => {
    expect(Angle.degrees(30).plus(Angle.degrees(15)).degrees).toBe(45);
    expect(Angle.degrees(30).minus(Angle.degrees(45)).degrees).toBe(-15);
    expect(Angle.degrees(30).times(3).degrees).toBe(90);
    expect(Angle.degrees(30).negated().degrees).toBe(-30);
  });

  it('takes an absolute value and keeps identity when already positive', () => {
    const a = Angle.degrees(30);
    expect(a.abs()).toBe(a);
    expect(Angle.degrees(-30).abs().degrees).toBe(30);
  });

  it('finds the short way round across the wrap', () => {
    expect(Angle.degrees(170).shortestTo(Angle.degrees(-170)).degrees).toBe(20);
    expect(Angle.degrees(-170).shortestTo(Angle.degrees(170)).degrees).toBe(-20);
  });
});

describe('comparison', () => {
  it('treats angles a full turn apart as equal', () => {
    expect(Angle.degrees(10).equals(Angle.degrees(370))).toBe(true);
    expect(Angle.degrees(10).equals(Angle.degrees(10.4))).toBe(false);
    expect(Angle.degrees(10).equals(Angle.degrees(10.4), 0.5)).toBe(true);
    expect(Angle.degrees(10).equals(10)).toBe(false);
  });

  it('answers whether a residual is inside a skew tolerance', () => {
    expect(Angle.degrees(0.3).isNearZero(0.5)).toBe(true);
    expect(Angle.degrees(-0.3).isNearZero(0.5)).toBe(true);
    expect(Angle.degrees(0.6).isNearZero(0.5)).toBe(false);
    expect(Angle.degrees(360.1).isNearZero(0.5)).toBe(true);
  });
});

describe('printing', () => {
  it('quotes degrees to four places', () => {
    expect(String(Angle.degrees(1.23456))).toBe('1.2346 deg');
    expect(Angle.degrees(2).toJSON()).toBe(2);
  });
});

describe('meanAngle', () => {
  it('averages the ordinary case', () => {
    expect(meanAngle([Angle.degrees(10), Angle.degrees(20)]).degrees).toBeCloseTo(15, 9);
  });

  it('averages across the wrap instead of through the middle', () => {
    const m = meanAngle([Angle.degrees(179), Angle.degrees(-179)]);
    expect(Math.abs(m.degrees)).toBeCloseTo(180, 9);
  });

  it('refuses an empty run and a set that cancels', () => {
    expect(() => meanAngle([])).toThrow(/no angles to average/);
    expect(() => meanAngle([Angle.degrees(0), Angle.degrees(180)])).toThrow(/cancel out/);
  });
});

describe('meanLineAngle', () => {
  it('treats a half turn as the same line', () => {
    const m = meanLineAngle([Angle.degrees(1), Angle.degrees(-179)]);
    expect(m.degrees).toBeCloseTo(1, 6);
  });

  it('matches the plain mean when every angle points the same way', () => {
    const angles = [Angle.degrees(10), Angle.degrees(12), Angle.degrees(14)];
    expect(meanLineAngle(angles).degrees).toBeCloseTo(12, 6);
  });
});
