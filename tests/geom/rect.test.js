import { describe, expect, it } from 'vitest';

import { Point } from '../../src/geom/point.js';
import { Rect } from '../../src/geom/rect.js';
import { Size } from '../../src/geom/size.js';

describe('building a rect', () => {
  it('keeps origin and extent and is frozen', () => {
    const r = Rect.of(10, 20, 30, 40);
    expect(r.toJSON()).toEqual({ x: 10, y: 20, width: 30, height: 40 });
    expect(Object.isFrozen(r)).toBe(true);
  });

  it('allows a negative origin but not a negative extent', () => {
    expect(Rect.of(-5, -5, 10, 10).left).toBe(-5);
    expect(() => Rect.of(0, 0, -1, 10)).toThrow(/width must not be negative/);
  });

  it('builds from edges and refuses inside out ones', () => {
    expect(Rect.fromEdges(10, 20, 40, 60).toJSON()).toEqual({
      x: 10,
      y: 20,
      width: 30,
      height: 40,
    });
    expect(() => Rect.fromEdges(40, 20, 10, 60)).toThrow(/edges are inside out/);
  });

  it('builds from corners in any order', () => {
    const a = Rect.fromCorners(Point.of(40, 60), Point.of(10, 20));
    expect(a.toJSON()).toEqual({ x: 10, y: 20, width: 30, height: 40 });
  });

  it('builds from a centre and from an origin', () => {
    expect(Rect.fromCentre(Point.of(50, 50), Size.of(20, 10)).toJSON()).toEqual({
      x: 40,
      y: 45,
      width: 20,
      height: 10,
    });
    expect(Rect.fromOrigin(Point.of(1, 2), Size.of(3, 4)).toJSON()).toEqual({
      x: 1,
      y: 2,
      width: 3,
      height: 4,
    });
  });

  it('bounds a scatter of points', () => {
    const r = Rect.bounding([Point.of(5, 9), Point.of(-1, 2), Point.of(3, 20)]);
    expect(r.toJSON()).toEqual({ x: -1, y: 2, width: 6, height: 18 });
    expect(() => Rect.bounding([])).toThrow(/no points to bound/);
  });
});

describe('edges and derived values', () => {
  const r = Rect.of(10, 20, 30, 40);

  it('reports the four edges with the right one exclusive', () => {
    expect([r.left, r.top, r.right, r.bottom]).toEqual([10, 20, 40, 60]);
  });

  it('reports origin, size, centre and area', () => {
    expect(r.origin.toJSON()).toEqual({ x: 10, y: 20 });
    expect(r.size.toJSON()).toEqual({ width: 30, height: 40 });
    expect(r.centre.toJSON()).toEqual({ x: 25, y: 40 });
    expect(r.area).toBe(1200);
    expect(r.isEmpty).toBe(false);
    expect(Rect.empty().isEmpty).toBe(true);
  });

  it('lists corners clockwise from the top left', () => {
    expect(r.corners().map((p) => p.toJSON())).toEqual([
      { x: 10, y: 20 },
      { x: 40, y: 20 },
      { x: 40, y: 60 },
      { x: 10, y: 60 },
    ]);
  });
});

describe('moving and resizing', () => {
  const r = Rect.of(10, 20, 30, 40);

  it('translates and scales', () => {
    expect(r.translated(5, -5).toJSON()).toEqual({ x: 15, y: 15, width: 30, height: 40 });
    expect(r.scaled(2).toJSON()).toEqual({ x: 20, y: 40, width: 60, height: 80 });
    expect(r.scaled(2, 1).toJSON()).toEqual({ x: 20, y: 20, width: 60, height: 40 });
  });

  it('replaces one part at a time', () => {
    expect(r.withSize(Size.of(4, 4)).toJSON()).toEqual({ x: 10, y: 20, width: 4, height: 4 });
    expect(r.withOrigin(Point.origin()).toJSON()).toEqual({ x: 0, y: 0, width: 30, height: 40 });
    expect(r.withCentre(Point.origin()).toJSON()).toEqual({
      x: -15,
      y: -20,
      width: 30,
      height: 40,
    });
  });
});

describe('inset and outset', () => {
  const r = Rect.of(0, 0, 100, 100);

  it('pulls the edges in about the centre', () => {
    expect(r.inset(10).toJSON()).toEqual({ x: 10, y: 10, width: 80, height: 80 });
    expect(r.inset(10, 20).toJSON()).toEqual({ x: 10, y: 20, width: 80, height: 60 });
  });

  it('pushes the edges out', () => {
    expect(r.outset(10).toJSON()).toEqual({ x: -10, y: -10, width: 120, height: 120 });
  });

  it('collapses instead of inverting when the inset is too big', () => {
    const collapsed = r.inset(80);
    expect(collapsed.width).toBe(0);
    expect(collapsed.centre.x).toBe(50);
  });
});

describe('containment and overlap', () => {
  const r = Rect.of(0, 0, 10, 10);

  it('treats the right and bottom edges as outside', () => {
    expect(r.contains(Point.of(0, 0))).toBe(true);
    expect(r.contains(Point.of(9.999, 9.999))).toBe(true);
    expect(r.contains(Point.of(10, 5))).toBe(false);
    expect(r.contains(Point.of(5, 10))).toBe(false);
  });

  it('contains a rect that shares an edge', () => {
    expect(r.containsRect(Rect.of(0, 0, 10, 10))).toBe(true);
    expect(r.containsRect(Rect.of(0, 0, 11, 10))).toBe(false);
  });

  it('intersects to the overlap and empty when there is none', () => {
    expect(r.intersect(Rect.of(5, 5, 20, 20)).toJSON()).toEqual({
      x: 5,
      y: 5,
      width: 5,
      height: 5,
    });
    expect(r.intersect(Rect.of(20, 20, 5, 5)).isEmpty).toBe(true);
    expect(r.intersects(Rect.of(20, 20, 5, 5))).toBe(false);
  });

  it('counts a touching edge as no overlap', () => {
    expect(r.intersects(Rect.of(10, 0, 5, 5))).toBe(false);
  });

  it('unions to the bounding box and ignores empties', () => {
    expect(r.union(Rect.of(20, 20, 5, 5)).toJSON()).toEqual({
      x: 0,
      y: 0,
      width: 25,
      height: 25,
    });
    expect(r.union(Rect.empty())).toBe(r);
    expect(Rect.empty().union(r)).toBe(r);
  });
});

describe('nudgedInto', () => {
  const bed = Rect.of(0, 0, 100, 100);

  it('slides a smaller rect back inside', () => {
    expect(Rect.of(-10, 50, 20, 20).nudgedInto(bed).toJSON()).toEqual({
      x: 0,
      y: 50,
      width: 20,
      height: 20,
    });
    expect(Rect.of(95, 50, 20, 20).nudgedInto(bed).x).toBe(80);
  });

  it('leaves a rect that is already inside where it is', () => {
    const inside = Rect.of(10, 10, 20, 20);
    expect(inside.nudgedInto(bed).equals(inside)).toBe(true);
  });

  it('centres an oversized rect on the axis it overflows', () => {
    const big = Rect.of(0, 10, 200, 20).nudgedInto(bed);
    expect(big.x).toBe(-50);
    expect(big.y).toBe(10);
  });
});

describe('rounding', () => {
  it('rounds each edge and never inverts', () => {
    expect(Rect.of(10.4, 20.6, 30.4, 40.6).rounded().toJSON()).toEqual({
      x: 10,
      y: 21,
      width: 31,
      height: 40,
    });
  });

  it('grows outward to whole pixels', () => {
    expect(Rect.of(10.4, 20.6, 30.4, 40.6).outward().toJSON()).toEqual({
      x: 10,
      y: 20,
      width: 31,
      height: 42,
    });
  });

  it('keeps a degenerate rect at zero width', () => {
    const flat = Rect.of(10.2, 5, 0.1, 10).rounded();
    expect(flat.width).toBe(0);
  });
});

describe('equality and printing', () => {
  it('compares within a tolerance', () => {
    expect(Rect.of(0, 0, 10, 10).equals(Rect.of(0, 0, 10, 10))).toBe(true);
    expect(Rect.of(0, 0, 10, 10).equals(Rect.of(0.4, 0, 10, 10))).toBe(false);
    expect(Rect.of(0, 0, 10, 10).equals(Rect.of(0.4, 0, 10, 10), 0.5)).toBe(true);
    expect(Rect.of(0, 0, 10, 10).equals({ x: 0, y: 0, width: 10, height: 10 })).toBe(false);
  });

  it('prints in the geometry form the log uses', () => {
    expect(String(Rect.of(10, 20, 30, 40))).toBe('30x40+10+20');
  });
});
