import { describe, expect, it } from 'vitest';

import { Angle } from '../../src/geom/angle.js';
import { Matrix, compose } from '../../src/geom/matrix.js';
import { Point } from '../../src/geom/point.js';
import { Rect } from '../../src/geom/rect.js';

describe('the basic transforms', () => {
  it('has an identity that changes nothing', () => {
    const m = Matrix.identity();
    expect(m.isIdentity).toBe(true);
    expect(m.apply(Point.of(3, 4)).toJSON()).toEqual({ x: 3, y: 4 });
    expect(Object.isFrozen(m)).toBe(true);
  });

  it('translates', () => {
    expect(Matrix.translation(10, -5).apply(Point.of(1, 1)).toJSON()).toEqual({ x: 11, y: -4 });
  });

  it('scales uniformly and per axis', () => {
    expect(Matrix.scaling(2).apply(Point.of(3, 4)).toJSON()).toEqual({ x: 6, y: 8 });
    expect(Matrix.scaling(2, 0.5).apply(Point.of(3, 4)).toJSON()).toEqual({ x: 6, y: 2 });
  });

  it('rotates clockwise about the origin', () => {
    const p = Matrix.rotation(Angle.degrees(90)).apply(Point.of(1, 0));
    expect(p.x).toBeCloseTo(0, 12);
    expect(p.y).toBeCloseTo(1, 12);
  });

  it('rotates about a given centre', () => {
    const centre = Point.of(10, 10);
    const m = Matrix.rotation(Angle.degrees(180), centre);
    expect(m.apply(centre).equals(centre)).toBe(true);
    expect(m.apply(Point.of(11, 10)).equals(Point.of(9, 10), 1e-9)).toBe(true);
  });

  it('flips on either axis', () => {
    expect(Matrix.flip(true, false).apply(Point.of(3, 4)).toJSON()).toEqual({ x: -3, y: 4 });
    expect(Matrix.flip(false, true).apply(Point.of(3, 4)).toJSON()).toEqual({ x: 3, y: -4 });
    expect(Matrix.flip(false, false).isIdentity).toBe(true);
  });

  it('refuses a component that is not finite', () => {
    expect(() => new Matrix(Number.NaN, 0, 0, 1, 0, 0)).toThrow(/a must be a finite number/);
  });
});

describe('composition', () => {
  it('applies the argument after the receiver', () => {
    const m = Matrix.translation(10, 0).then(Matrix.scaling(2));
    expect(m.apply(Point.of(1, 0)).toJSON()).toEqual({ x: 22, y: 0 });
  });

  it('is not commutative', () => {
    const a = Matrix.translation(10, 0).then(Matrix.scaling(2));
    const b = Matrix.scaling(2).then(Matrix.translation(10, 0));
    expect(a.equals(b)).toBe(false);
    expect(b.apply(Point.of(1, 0)).toJSON()).toEqual({ x: 12, y: 0 });
  });

  it('folds a list left to right', () => {
    const folded = compose([Matrix.translation(1, 1), Matrix.scaling(2), Matrix.translation(3, 3)]);
    expect(folded.apply(Point.of(0, 0)).toJSON()).toEqual({ x: 5, y: 5 });
    expect(compose([]).isIdentity).toBe(true);
  });

  it('keeps identity as the neutral element', () => {
    const m = Matrix.rotation(Angle.degrees(17));
    expect(m.then(Matrix.identity()).equals(m)).toBe(true);
    expect(Matrix.identity().then(m).equals(m)).toBe(true);
  });
});

describe('applyToRect', () => {
  it('leaves an axis aligned rect alone under translation', () => {
    const r = Rect.of(0, 0, 10, 20);
    expect(Matrix.translation(5, 5).applyToRect(r).toJSON()).toEqual({
      x: 5,
      y: 5,
      width: 10,
      height: 20,
    });
  });

  it('grows the box when the rect is rotated', () => {
    const r = Rect.of(0, 0, 10, 10);
    const box = Matrix.rotation(Angle.degrees(45), r.centre).applyToRect(r);
    expect(box.width).toBeCloseTo(Math.SQRT2 * 10, 9);
    expect(box.centre.equals(r.centre, 1e-9)).toBe(true);
  });

  it('swaps the extents on a quarter turn', () => {
    const r = Rect.of(0, 0, 10, 20);
    const box = Matrix.rotation(Angle.degrees(90), r.centre).applyToRect(r);
    expect(box.width).toBeCloseTo(20, 9);
    expect(box.height).toBeCloseTo(10, 9);
  });
});

describe('inverse', () => {
  it('undoes a composed transform', () => {
    const m = Matrix.rotation(Angle.degrees(13), Point.of(4, 4)).then(Matrix.scaling(3, 2));
    const back = m.then(m.inverse());
    expect(back.equals(Matrix.identity(), 1e-9)).toBe(true);
  });

  it('brings a point home again', () => {
    const m = Matrix.translation(100, 50).then(Matrix.rotation(Angle.degrees(30)));
    const p = Point.of(7, 9);
    expect(m.inverse().apply(m.apply(p)).equals(p, 1e-9)).toBe(true);
  });

  it('refuses a degenerate transform', () => {
    const flat = Matrix.scaling(1, 0);
    expect(flat.isInvertible).toBe(false);
    expect(() => flat.inverse()).toThrow(/collapses the plane/);
  });

  it('reports the determinant', () => {
    expect(Matrix.scaling(2, 3).determinant).toBe(6);
    expect(Matrix.rotation(Angle.degrees(37)).determinant).toBeCloseTo(1, 12);
  });
});

describe('decompose', () => {
  it('pulls back the pieces that went in', () => {
    const m = compose([
      Matrix.scaling(3, 2),
      Matrix.rotation(Angle.degrees(20)),
      Matrix.translation(40, 10),
    ]);
    const parts = m.decompose();
    expect(parts.translation.toJSON()).toEqual({ x: 40, y: 10 });
    expect(parts.rotation.degrees).toBeCloseTo(20, 9);
    expect(parts.scaleX).toBeCloseTo(3, 9);
    expect(parts.scaleY).toBeCloseTo(2, 9);
    expect(parts.shear).toBeCloseTo(0, 9);
  });

  it('reports no rotation and no shear for a plain scale', () => {
    const parts = Matrix.scaling(2, 5).decompose();
    expect(parts.rotation.degrees).toBeCloseTo(0, 12);
    expect(parts.shear).toBeCloseTo(0, 12);
  });
});

describe('serialisation', () => {
  it('lists the six components in the usual order', () => {
    expect(Matrix.scaling(2, 3).toArray()).toEqual([2, 0, 0, 3, 0, 0]);
    expect(Matrix.translation(1, 2).toJSON()).toEqual({ a: 1, b: 0, c: 0, d: 1, e: 1, f: 2 });
    expect(String(Matrix.identity())).toBe('matrix(1, 0, 0, 1, 0, 0)');
  });

  it('is not equal to a bare array', () => {
    expect(Matrix.identity().equals([1, 0, 0, 1, 0, 0])).toBe(false);
  });
});
