import { describe, expect, it } from 'vitest';

import { Angle } from '../../src/geom/angle.js';
import { Matrix } from '../../src/geom/matrix.js';
import { Point } from '../../src/geom/point.js';
import { Quad } from '../../src/geom/quad.js';
import { Rect } from '../../src/geom/rect.js';

const square = Rect.of(0, 0, 100, 100);

describe('building a quad', () => {
  it('takes four corners and freezes them', () => {
    const q = Quad.fromRect(square);
    expect(q.corners).toHaveLength(4);
    expect(Object.isFrozen(q.corners)).toBe(true);
    expect(q.topLeft.toJSON()).toEqual({ x: 0, y: 0 });
    expect(q.bottomRight.toJSON()).toEqual({ x: 100, y: 100 });
  });

  it('accepts loose pairs', () => {
    const q = new Quad([
      [0, 0],
      [10, 0],
      [10, 10],
      [0, 10],
    ]);
    expect(q.topRight.toJSON()).toEqual({ x: 10, y: 0 });
  });

  it('refuses any count but four', () => {
    expect(() => new Quad([Point.origin()])).toThrow(/exactly four corners/);
    expect(() => Quad.ordered([Point.origin(), Point.of(1, 1)])).toThrow(/exactly four corners/);
  });
});

describe('Quad.ordered', () => {
  it('sorts a scrambled set into top left then clockwise', () => {
    const q = Quad.ordered([
      Point.of(100, 100),
      Point.of(0, 100),
      Point.of(100, 0),
      Point.origin(),
    ]);
    expect(q.toJSON()).toEqual([
      { x: 0, y: 0 },
      { x: 100, y: 0 },
      { x: 100, y: 100 },
      { x: 0, y: 100 },
    ]);
  });

  it('keeps an already ordered quad in place', () => {
    const q = Quad.fromRect(square);
    expect(Quad.ordered(q.corners).equals(q)).toBe(true);
  });

  it('orders a skewed sheet the same way', () => {
    const skewed = Quad.fromRect(square).transformedBy(
      Matrix.rotation(Angle.degrees(3), square.centre),
    );
    expect(Quad.ordered([...skewed.corners].reverse()).equals(skewed, 1e-9)).toBe(true);
  });
});

describe('measurements', () => {
  const q = Quad.fromRect(Rect.of(10, 20, 100, 50));

  it('reports the area by the shoelace rule', () => {
    expect(q.area).toBe(5000);
  });

  it('reports the centre and the bounding rect', () => {
    expect(q.centre.toJSON()).toEqual({ x: 60, y: 45 });
    expect(q.boundingRect.toJSON()).toEqual({ x: 10, y: 20, width: 100, height: 50 });
  });

  it('lists edge lengths top, right, bottom then left', () => {
    expect(q.edgeLengths()).toEqual([100, 50, 100, 50]);
  });

  it('lists edge directions in the same order', () => {
    const angles = q.edgeAngles().map((a) => a.degrees);
    expect(angles[0]).toBeCloseTo(0, 9);
    expect(angles[1]).toBeCloseTo(90, 9);
    expect(Math.abs(angles[2])).toBeCloseTo(180, 9);
    expect(angles[3]).toBeCloseTo(-90, 9);
  });

  it('is convex and not degenerate', () => {
    expect(q.isConvex).toBe(true);
    expect(q.isDegenerate()).toBe(false);
  });

  it('spots a flattened quad', () => {
    const flat = new Quad([Point.of(0, 0), Point.of(10, 0), Point.of(20, 0), Point.of(5, 0)]);
    expect(flat.isDegenerate()).toBe(true);
    expect(flat.area).toBe(0);
  });

  it('spots a bow tie as not convex', () => {
    const crossed = new Quad([Point.of(0, 0), Point.of(10, 0), Point.of(0, 10), Point.of(10, 10)]);
    expect(crossed.isConvex).toBe(false);
  });
});

describe('skew', () => {
  it('reads zero for a square sheet', () => {
    expect(Quad.fromRect(square).skew().degrees).toBeCloseTo(0, 9);
  });

  it('reads the rotation that was applied', () => {
    const turned = Quad.fromRect(square).transformedBy(
      Matrix.rotation(Angle.degrees(2.4), square.centre),
    );
    expect(turned.skew().degrees).toBeCloseTo(2.4, 6);
  });

  it('reads a negative turn the other way', () => {
    const turned = Quad.fromRect(square).transformedBy(
      Matrix.rotation(Angle.degrees(-1.75), square.centre),
    );
    expect(turned.skew().degrees).toBeCloseTo(-1.75, 6);
  });

  it('is not fooled by the bottom edge running the other way', () => {
    const turned = Quad.fromRect(Rect.of(0, 0, 400, 50)).transformedBy(
      Matrix.rotation(Angle.degrees(0.9)),
    );
    expect(turned.skew().degrees).toBeCloseTo(0.9, 6);
  });
});

describe('isRectangular', () => {
  it('accepts a plain rect', () => {
    expect(Quad.fromRect(square).isRectangular()).toBe(true);
  });

  it('accepts a rotated rect, since rotation keeps the corners square', () => {
    const turned = Quad.fromRect(square).transformedBy(
      Matrix.rotation(Angle.degrees(7), square.centre),
    );
    expect(turned.isRectangular()).toBe(true);
  });

  it('rejects a keystoned capture', () => {
    const keystone = new Quad([
      Point.of(10, 0),
      Point.of(90, 0),
      Point.of(100, 100),
      Point.of(0, 100),
    ]);
    expect(keystone.isRectangular()).toBe(false);
  });

  it('can be told to accept a slack corner', () => {
    const nearly = new Quad([
      Point.of(0, 0),
      Point.of(100, 0),
      Point.of(101.5, 100),
      Point.of(1.5, 100),
    ]);
    expect(nearly.isRectangular()).toBe(false);
    expect(nearly.isRectangular({ lengthPixels: 2, cornerDegrees: 1 })).toBe(true);
  });
});

describe('transformedBy', () => {
  it('carries every corner through the matrix', () => {
    const moved = Quad.fromRect(square).transformedBy(Matrix.translation(5, 5));
    expect(moved.topLeft.toJSON()).toEqual({ x: 5, y: 5 });
    expect(moved.area).toBeCloseTo(10000, 9);
  });

  it('scales the area by the determinant', () => {
    const bigger = Quad.fromRect(square).transformedBy(Matrix.scaling(2, 3));
    expect(bigger.area).toBeCloseTo(60000, 9);
  });
});

describe('equality and printing', () => {
  it('compares corner by corner', () => {
    expect(Quad.fromRect(square).equals(Quad.fromRect(square))).toBe(true);
    expect(Quad.fromRect(square).equals(Quad.fromRect(Rect.of(0, 0, 100, 99)))).toBe(false);
    expect(Quad.fromRect(square).equals(square)).toBe(false);
  });

  it('serialises to four pairs', () => {
    expect(Quad.fromRect(Rect.of(0, 0, 1, 1)).toJSON()).toEqual([
      { x: 0, y: 0 },
      { x: 1, y: 0 },
      { x: 1, y: 1 },
      { x: 0, y: 1 },
    ]);
    expect(String(Quad.fromRect(Rect.of(0, 0, 1, 1)))).toBe('(0, 0) (1, 0) (1, 1) (0, 1)');
  });
});
