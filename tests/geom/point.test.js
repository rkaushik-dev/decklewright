import { describe, expect, it } from 'vitest';

import { Point, centroid } from '../../src/geom/point.js';

describe('building a point', () => {
  it('is frozen and keeps its coordinates', () => {
    const p = Point.of(3, -4);
    expect(p.x).toBe(3);
    expect(p.y).toBe(-4);
    expect(Object.isFrozen(p)).toBe(true);
  });

  it('has an origin', () => {
    expect(Point.origin().toJSON()).toEqual({ x: 0, y: 0 });
  });

  it('refuses a coordinate that is not finite', () => {
    expect(() => Point.of(Number.NaN, 0)).toThrow(/x must be a finite number/);
    expect(() => Point.of(0, Number.POSITIVE_INFINITY)).toThrow(/y must be a finite number/);
  });
});

describe('Point.from', () => {
  it('takes an instance, a pair and a bag', () => {
    const p = Point.of(1, 2);
    expect(Point.from(p)).toBe(p);
    expect(Point.from([1, 2]).equals(p)).toBe(true);
    expect(Point.from({ x: 1, y: 2 }).equals(p)).toBe(true);
  });

  it('rejects anything else', () => {
    expect(() => Point.from([1, 2, 3])).toThrow(/cannot read a point from that/);
    expect(() => Point.from('1,2')).toThrow(/cannot read a point from that/);
  });
});

describe('arithmetic', () => {
  const a = Point.of(10, 20);
  const b = Point.of(4, 5);

  it('adds and subtracts', () => {
    expect(a.plus(b).toJSON()).toEqual({ x: 14, y: 25 });
    expect(a.minus(b).toJSON()).toEqual({ x: 6, y: 15 });
  });

  it('scales uniformly or per axis', () => {
    expect(a.times(2).toJSON()).toEqual({ x: 20, y: 40 });
    expect(a.times(2, 0.5).toJSON()).toEqual({ x: 20, y: 10 });
  });

  it('offsets by a loose pair', () => {
    expect(a.offset(-10, 0).toJSON()).toEqual({ x: 0, y: 20 });
  });

  it('interpolates towards another point', () => {
    expect(a.lerpTo(b, 0).equals(a)).toBe(true);
    expect(a.lerpTo(b, 1).equals(b)).toBe(true);
    expect(a.lerpTo(b, 0.5).toJSON()).toEqual({ x: 7, y: 12.5 });
  });
});

describe('distance', () => {
  it('measures the straight line', () => {
    expect(Point.of(0, 0).distanceTo(Point.of(3, 4))).toBe(5);
    expect(Point.of(0, 0).distanceSquaredTo(Point.of(3, 4))).toBe(25);
  });

  it('is zero to itself', () => {
    const p = Point.of(7, 7);
    expect(p.distanceTo(p)).toBe(0);
  });
});

describe('rotatedAround', () => {
  it('turns clockwise for a positive angle', () => {
    const rotated = Point.of(1, 0).rotatedAround(Point.origin(), Math.PI / 2);
    expect(rotated.x).toBeCloseTo(0, 12);
    expect(rotated.y).toBeCloseTo(1, 12);
  });

  it('leaves the centre where it is', () => {
    const centre = Point.of(100, 50);
    expect(centre.rotatedAround(centre, 0.3).equals(centre)).toBe(true);
  });

  it('comes back after a full turn', () => {
    const p = Point.of(13, -7);
    const round = p.rotatedAround(Point.of(2, 2), Math.PI * 2);
    expect(round.equals(p, 1e-9)).toBe(true);
  });
});

describe('rounded', () => {
  it('uses Math.round by default', () => {
    expect(Point.of(1.4, 1.6).rounded().toJSON()).toEqual({ x: 1, y: 2 });
  });

  it('accepts another rounder', () => {
    expect(Point.of(1.4, 1.6).rounded(Math.floor).toJSON()).toEqual({ x: 1, y: 1 });
  });
});

describe('equals and printing', () => {
  it('compares within a pixel tolerance', () => {
    expect(Point.of(1, 1).equals(Point.of(1.0000000001, 1))).toBe(true);
    expect(Point.of(1, 1).equals(Point.of(1.5, 1))).toBe(false);
    expect(Point.of(1, 1).equals(Point.of(1.5, 1), 1)).toBe(true);
    expect(Point.of(1, 1).equals({ x: 1, y: 1 })).toBe(false);
  });

  it('prints to three places', () => {
    expect(String(Point.of(1.23456, -2))).toBe('(1.235, -2)');
  });
});

describe('centroid', () => {
  it('averages the corners', () => {
    const points = [Point.of(0, 0), Point.of(10, 0), Point.of(10, 10), Point.of(0, 10)];
    expect(centroid(points).toJSON()).toEqual({ x: 5, y: 5 });
  });

  it('refuses an empty run', () => {
    expect(() => centroid([])).toThrow(/no points to average/);
  });
});
