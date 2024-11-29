import { describe, expect, it } from 'vitest';

import { Matrix } from '../../src/geom/matrix.js';
import { Point } from '../../src/geom/point.js';
import { Polygon } from '../../src/geom/polygon.js';
import { Rect } from '../../src/geom/rect.js';

const box = Rect.of(0, 0, 100, 50);

describe('building a polygon', () => {
  it('keeps the vertices frozen', () => {
    const p = Polygon.fromRect(box);
    expect(p.count).toBe(4);
    expect(Object.isFrozen(p.vertices)).toBe(true);
  });

  it('refuses fewer than three vertices', () => {
    expect(() => new Polygon([Point.origin(), Point.of(1, 1)])).toThrow(/at least three vertices/);
  });

  it('accepts loose pairs', () => {
    const p = new Polygon([
      [0, 0],
      [4, 0],
      [0, 3],
    ]);
    expect(p.area).toBe(6);
  });
});

describe('area, winding and perimeter', () => {
  it('measures a rectangle', () => {
    const p = Polygon.fromRect(box);
    expect(p.area).toBe(5000);
    expect(p.perimeter).toBe(300);
    expect(p.isClockwise).toBe(true);
  });

  it('flips the sign when the winding reverses', () => {
    const p = Polygon.fromRect(box).reversed();
    expect(p.isClockwise).toBe(false);
    expect(p.signedArea).toBe(-5000);
    expect(p.area).toBe(5000);
  });

  it('normalises the winding on request', () => {
    const p = Polygon.fromRect(box).reversed().clockwise();
    expect(p.isClockwise).toBe(true);
    const already = Polygon.fromRect(box);
    expect(already.clockwise()).toBe(already);
  });

  it('measures an L shape correctly', () => {
    const l = new Polygon([
      Point.of(0, 0),
      Point.of(10, 0),
      Point.of(10, 4),
      Point.of(4, 4),
      Point.of(4, 10),
      Point.of(0, 10),
    ]);
    expect(l.area).toBe(64);
  });
});

describe('centroid and bounds', () => {
  it('centres a rectangle', () => {
    expect(Polygon.fromRect(box).centroid.toJSON()).toEqual({ x: 50, y: 25 });
  });

  it('weights by area rather than by vertex', () => {
    const l = new Polygon([
      Point.of(0, 0),
      Point.of(12, 0),
      Point.of(12, 2),
      Point.of(2, 2),
      Point.of(2, 12),
      Point.of(0, 12),
    ]);
    expect(l.centroid.x).toBeCloseTo(164 / 44, 9);
    expect(l.centroid.y).toBeCloseTo(164 / 44, 9);
    expect(l.centroid.x).toBeGreaterThan(l.boundingRect.centre.x - 6);
  });

  it('refuses a centroid when there is no area', () => {
    const flat = new Polygon([Point.of(0, 0), Point.of(5, 0), Point.of(10, 0)]);
    expect(() => flat.centroid).toThrow(/no area has no centroid/);
  });

  it('reports the bounding rect', () => {
    expect(Polygon.fromRect(box).boundingRect.equals(box)).toBe(true);
  });
});

describe('contains', () => {
  const p = Polygon.fromRect(Rect.of(0, 0, 10, 10));

  it('finds an interior point', () => {
    expect(p.contains(Point.of(5, 5))).toBe(true);
  });

  it('rejects an exterior point', () => {
    expect(p.contains(Point.of(15, 5))).toBe(false);
    expect(p.contains(Point.of(-1, 5))).toBe(false);
    expect(p.contains(Point.of(5, 20))).toBe(false);
  });

  it('counts a point on the boundary as inside', () => {
    expect(p.contains(Point.of(0, 5))).toBe(true);
    expect(p.contains(Point.of(10, 5))).toBe(true);
    expect(p.contains(Point.of(0, 0))).toBe(true);
  });

  it('handles a concave notch', () => {
    const notched = new Polygon([
      Point.of(0, 0),
      Point.of(10, 0),
      Point.of(10, 10),
      Point.of(5, 5),
      Point.of(0, 10),
    ]);
    expect(notched.contains(Point.of(5, 8))).toBe(false);
    expect(notched.contains(Point.of(2, 2))).toBe(true);
  });
});

describe('convexHull', () => {
  it('drops the interior points', () => {
    const hull = Polygon.convexHull([
      Point.of(0, 0),
      Point.of(10, 0),
      Point.of(10, 10),
      Point.of(0, 10),
      Point.of(5, 5),
      Point.of(3, 7),
    ]);
    expect(hull.count).toBe(4);
    expect(hull.area).toBe(100);
  });

  it('comes back clockwise on screen', () => {
    const hull = Polygon.convexHull([
      Point.of(0, 10),
      Point.of(10, 10),
      Point.of(10, 0),
      Point.of(0, 0),
    ]);
    expect(hull.isClockwise).toBe(true);
  });

  it('refuses too few points and collinear ones', () => {
    expect(() => Polygon.convexHull([Point.origin(), Point.of(1, 1)])).toThrow(
      /at least three points/,
    );
    expect(() =>
      Polygon.convexHull([Point.of(0, 0), Point.of(1, 1), Point.of(2, 2), Point.of(3, 3)]),
    ).toThrow(/collinear/);
  });
});

describe('simplified', () => {
  it('drops vertices that sit on a straight run', () => {
    const dense = new Polygon([
      Point.of(0, 0),
      Point.of(5, 0),
      Point.of(10, 0),
      Point.of(10, 10),
      Point.of(5, 10),
      Point.of(0, 10),
    ]);
    const thin = dense.simplified(0.5);
    expect(thin.count).toBe(4);
    expect(thin.area).toBe(100);
  });

  it('keeps a real corner that sits outside the tolerance', () => {
    const bumped = new Polygon([
      Point.of(0, 0),
      Point.of(5, 3),
      Point.of(10, 0),
      Point.of(10, 10),
      Point.of(0, 10),
    ]);
    expect(bumped.simplified(0.5).count).toBe(5);
    expect(bumped.simplified(5).count).toBeLessThan(5);
  });

  it('is a no op at zero tolerance and on a triangle', () => {
    const triangle = new Polygon([Point.of(0, 0), Point.of(10, 0), Point.of(0, 10)]);
    expect(triangle.simplified(5)).toBe(triangle);
    const p = Polygon.fromRect(box);
    expect(p.simplified(0)).toBe(p);
  });

  it('refuses a negative tolerance', () => {
    expect(() => Polygon.fromRect(box).simplified(-1)).toThrow(/tolerance must not be negative/);
  });
});

describe('transformedBy and printing', () => {
  it('carries every vertex through', () => {
    const moved = Polygon.fromRect(box).transformedBy(Matrix.translation(10, 10));
    expect(moved.boundingRect.toJSON()).toEqual({ x: 10, y: 10, width: 100, height: 50 });
  });

  it('serialises to a list of pairs', () => {
    expect(Polygon.fromRect(Rect.of(0, 0, 1, 1)).toJSON()).toHaveLength(4);
    expect(String(Polygon.fromRect(box))).toBe('polygon(4 vertices, area 5000)');
  });
});
