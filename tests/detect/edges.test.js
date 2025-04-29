import { describe, expect, it } from 'vitest';

import {
  SIDES,
  distanceToLine,
  edgeLines,
  edgePoints,
  fitLine,
  fitLineRobust,
  skewFromLines,
} from '../../src/detect/edges.js';
import { Mask } from '../../src/detect/mask.js';
import { Point } from '../../src/geom/point.js';

/**
 * A filled rectangle, optionally sheared so its sides are not axis aligned.
 *
 * @param {object} [options]
 * @param {number} [options.shear] columns the top edge moves per row
 * @returns {Mask}
 */
function sheet({ shear = 0 } = {}) {
  const mask = new Mask(80, 60);
  for (let y = 10; y < 50; y += 1) {
    const offset = Math.round((y - 30) * shear);
    for (let x = 15 + offset; x < 65 + offset; x += 1) mask.set(x, y, 1);
  }
  return mask;
}

/**
 * The same sheet turned about its centre, which is what a real capture looks
 * like. A shear is not a rotation and the two read differently, so the skew
 * tests use this one.
 *
 * @param {number} degrees
 * @returns {Mask}
 */
function turnedSheet(degrees) {
  const mask = new Mask(120, 100);
  const radians = (degrees * Math.PI) / 180;
  const cos = Math.cos(radians);
  const sin = Math.sin(radians);
  for (let y = 0; y < mask.height; y += 1) {
    for (let x = 0; x < mask.width; x += 1) {
      const dx = x - 60;
      const dy = y - 50;
      const ux = dx * cos + dy * sin;
      const uy = -dx * sin + dy * cos;
      if (Math.abs(ux) <= 35 && Math.abs(uy) <= 25) mask.set(x, y, 1);
    }
  }
  return mask;
}

describe('edgePoints', () => {
  it('finds the first set pixel scanning inward', () => {
    const points = edgePoints(sheet(), 'top');
    const inside = points.filter((p) => p.x >= 15 && p.x < 65);
    expect(inside).toHaveLength(50);
    expect(inside.every((p) => p.y === 10)).toBe(true);
  });

  it('scans from the far side for the opposite edge', () => {
    const points = edgePoints(sheet(), 'bottom').filter((p) => p.x >= 15 && p.x < 65);
    expect(points.every((p) => p.y === 49)).toBe(true);
  });

  it('scans across for the vertical sides', () => {
    const left = edgePoints(sheet(), 'left').filter((p) => p.y >= 10 && p.y < 50);
    const right = edgePoints(sheet(), 'right').filter((p) => p.y >= 10 && p.y < 50);
    expect(left.every((p) => p.x === 15)).toBe(true);
    expect(right.every((p) => p.x === 64)).toBe(true);
  });

  it('skips scan lines that hold nothing', () => {
    expect(edgePoints(sheet(), 'top')).toHaveLength(50);
  });

  it('honours a step', () => {
    expect(edgePoints(sheet(), 'top', { step: 5 }).length).toBeLessThan(50);
  });

  it('refuses a side it does not know', () => {
    expect(() => edgePoints(sheet(), 'front')).toThrow(/side must be one of/);
  });
});

describe('fitLine', () => {
  it('reads a horizontal run as zero degrees', () => {
    const points = [Point.of(0, 5), Point.of(10, 5), Point.of(20, 5)];
    const line = fitLine(points);
    expect(line.angle.degrees).toBeCloseTo(0, 9);
    expect(line.residual).toBeCloseTo(0, 9);
    expect(line.through.toJSON()).toEqual({ x: 10, y: 5 });
  });

  it('handles a vertical run, which a plain regression cannot', () => {
    const points = [Point.of(5, 0), Point.of(5, 10), Point.of(5, 20)];
    const line = fitLine(points);
    expect(Math.abs(line.angle.degrees)).toBeCloseTo(90, 6);
    expect(line.residual).toBeCloseTo(0, 9);
  });

  it('reads a slope', () => {
    const points = [Point.of(0, 0), Point.of(10, 1), Point.of(20, 2)];
    expect(fitLine(points).angle.degrees).toBeCloseTo(5.71, 1);
  });

  it('reports a residual when the points are not collinear', () => {
    const points = [Point.of(0, 0), Point.of(10, 4), Point.of(20, 0)];
    expect(fitLine(points).residual).toBeGreaterThan(1);
  });

  it('refuses fewer than two points', () => {
    expect(() => fitLine([Point.origin()])).toThrow(/at least two points/);
  });
});

describe('distanceToLine', () => {
  it('is zero on the line and grows off it', () => {
    const line = fitLine([Point.of(0, 5), Point.of(10, 5)]);
    expect(distanceToLine(line, Point.of(5, 5))).toBeCloseTo(0, 9);
    expect(distanceToLine(line, Point.of(5, 8))).toBeCloseTo(3, 9);
    expect(distanceToLine(line, Point.of(5, 2))).toBeCloseTo(-3, 9);
  });
});

describe('fitLineRobust', () => {
  it('ignores a handful of outliers', () => {
    const points = [];
    for (let x = 0; x < 40; x += 1) points.push(Point.of(x, 10));
    points.push(Point.of(20, 40), Point.of(21, 45));
    const plain = fitLine(points);
    const robust = fitLineRobust(points);
    expect(robust.residual).toBeLessThan(plain.residual);
    expect(robust.angle.degrees).toBeCloseTo(0, 3);
  });

  it('falls back to the plain fit on a short run', () => {
    const points = [Point.of(0, 0), Point.of(5, 0), Point.of(10, 3)];
    expect(fitLineRobust(points).points).toHaveLength(3);
  });

  it('keeps every point when told to drop nothing', () => {
    const points = [];
    for (let x = 0; x < 20; x += 1) points.push(Point.of(x, x % 3));
    expect(fitLineRobust(points, { drop: 0 }).points).toHaveLength(20);
  });
});

describe('edgeLines', () => {
  it('fits all four sides of a square sheet', () => {
    const lines = edgeLines(sheet());
    for (const side of SIDES) {
      expect(lines[side].residual).toBeLessThan(1);
    }
  });

  it('refuses a mask with nothing in it', () => {
    expect(() => edgeLines(new Mask(20, 20))).toThrow(/no edge to fit/);
  });
});

describe('skewFromLines', () => {
  it('reads zero for a square sheet', () => {
    expect(skewFromLines(edgeLines(sheet())).degrees).toBeCloseTo(0, 1);
  });

  it('reads the turn that was applied, to about a degree', () => {
    for (const degrees of [-12, -6, -3, 3, 6, 12]) {
      const skew = skewFromLines(edgeLines(turnedSheet(degrees))).degrees;
      expect(Math.abs(skew - degrees)).toBeLessThan(1.5);
    }
  });

  it('reads the other direction as the other sign', () => {
    const positive = skewFromLines(edgeLines(turnedSheet(4))).degrees;
    const negative = skewFromLines(edgeLines(turnedSheet(-4))).degrees;
    expect(positive).toBeGreaterThan(0);
    expect(negative).toBeLessThan(0);
  });

  it('grows with the turn', () => {
    const small = skewFromLines(edgeLines(turnedSheet(3))).degrees;
    const large = skewFromLines(edgeLines(turnedSheet(10))).degrees;
    expect(large).toBeGreaterThan(small);
  });

  it('lands inside the half turn band', () => {
    for (const degrees of [-12, -3, 0, 3, 12]) {
      const skew = skewFromLines(edgeLines(turnedSheet(degrees))).degrees;
      expect(skew).toBeGreaterThanOrEqual(-45);
      expect(skew).toBeLessThanOrEqual(45);
    }
  });

  it('is not fooled by a shear, which is not a turn at all', () => {
    const skew = skewFromLines(edgeLines(sheet({ shear: 0.1 }))).degrees;
    expect(Math.abs(skew)).toBeLessThan(6);
  });
});
