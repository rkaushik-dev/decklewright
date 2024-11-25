import { describe, expect, it } from 'vitest';

import { Insets } from '../../src/geom/insets.js';
import { Rect } from '../../src/geom/rect.js';

describe('building insets', () => {
  it('keeps four independent edges', () => {
    const i = new Insets(1, 2, 3, 4);
    expect(i.toJSON()).toEqual({ top: 1, right: 2, bottom: 3, left: 4 });
    expect(Object.isFrozen(i)).toBe(true);
  });

  it('has uniform, symmetric and zero shorthands', () => {
    expect(Insets.uniform(5).isUniform).toBe(true);
    expect(Insets.symmetric(1, 2).toJSON()).toEqual({ top: 1, right: 2, bottom: 1, left: 2 });
    expect(Insets.zero().isZero).toBe(true);
  });

  it('allows negative edges, since an outset is a negative inset', () => {
    expect(new Insets(-1, -1, -1, -1).largest).toBe(-1);
  });
});

describe('Insets.from', () => {
  it('reads a number', () => {
    expect(Insets.from(3).equals(Insets.uniform(3))).toBe(true);
  });

  it('reads the css style lists', () => {
    expect(Insets.from([3]).equals(Insets.uniform(3))).toBe(true);
    expect(Insets.from([1, 2]).equals(Insets.symmetric(1, 2))).toBe(true);
    expect(Insets.from([1, 2, 3, 4]).toJSON()).toEqual({ top: 1, right: 2, bottom: 3, left: 4 });
  });

  it('refuses a list of three', () => {
    expect(() => Insets.from([1, 2, 3])).toThrow(/one, two or four numbers/);
  });

  it('reads a partial bag and defaults the rest to zero', () => {
    expect(Insets.from({ bottom: 10 }).toJSON()).toEqual({
      top: 0,
      right: 0,
      bottom: 10,
      left: 0,
    });
  });

  it('passes an instance through and rejects junk', () => {
    const i = Insets.uniform(2);
    expect(Insets.from(i)).toBe(i);
    expect(() => Insets.from('2px')).toThrow(/cannot read insets from that/);
  });
});

describe('the derived sums', () => {
  const i = new Insets(1, 2, 3, 4);

  it('adds opposite edges', () => {
    expect(i.horizontal).toBe(6);
    expect(i.vertical).toBe(4);
    expect(i.largest).toBe(4);
  });
});

describe('applyTo', () => {
  const rect = Rect.of(0, 0, 100, 80);

  it('pulls each edge in by its own amount', () => {
    const inner = new Insets(5, 10, 15, 20).applyTo(rect);
    expect(inner.toJSON()).toEqual({ x: 20, y: 5, width: 70, height: 60 });
  });

  it('is a no op for zero', () => {
    expect(Insets.zero().applyTo(rect).equals(rect)).toBe(true);
  });

  it('collapses at the centre when the demand exceeds the rect', () => {
    const collapsed = Insets.uniform(60).applyTo(rect);
    expect(collapsed.isEmpty).toBe(true);
    expect(collapsed.origin.toJSON()).toEqual({ x: 50, y: 40 });
  });

  it('collapses when exactly consumed rather than leaving a zero width rect in place', () => {
    const collapsed = Insets.symmetric(40, 50).applyTo(rect);
    expect(collapsed.isEmpty).toBe(true);
  });
});

describe('expandFrom', () => {
  it('pushes the edges outward', () => {
    const outer = new Insets(5, 10, 15, 20).expandFrom(Rect.of(20, 5, 70, 60));
    expect(outer.toJSON()).toEqual({ x: 0, y: 0, width: 100, height: 80 });
  });
});

describe('combining', () => {
  const a = new Insets(1, 2, 3, 4);
  const b = new Insets(4, 3, 2, 1);

  it('adds edge by edge', () => {
    expect(a.plus(b).toJSON()).toEqual({ top: 5, right: 5, bottom: 5, left: 5 });
  });

  it('scales every edge', () => {
    expect(a.times(2).toJSON()).toEqual({ top: 2, right: 4, bottom: 6, left: 8 });
    expect(() => a.times(Number.NaN)).toThrow(/factor must be a finite number/);
  });

  it('takes the larger demand on each edge independently', () => {
    expect(a.maxWith(b).toJSON()).toEqual({ top: 4, right: 3, bottom: 3, left: 4 });
  });

  it('negates every edge', () => {
    expect(a.negated().toJSON()).toEqual({ top: -1, right: -2, bottom: -3, left: -4 });
  });

  it('rounds every edge', () => {
    expect(new Insets(1.4, 1.6, 1.5, 2.5).rounded(Math.floor).toJSON()).toEqual({
      top: 1,
      right: 1,
      bottom: 1,
      left: 2,
    });
  });
});

describe('equality and printing', () => {
  it('compares within a tolerance', () => {
    expect(Insets.uniform(1).equals(Insets.uniform(1))).toBe(true);
    expect(Insets.uniform(1).equals(Insets.uniform(1.4))).toBe(false);
    expect(Insets.uniform(1).equals(Insets.uniform(1.4), 0.5)).toBe(true);
    expect(Insets.uniform(1).equals({ top: 1, right: 1, bottom: 1, left: 1 })).toBe(false);
  });

  it('prints the short form when every edge matches', () => {
    expect(String(Insets.uniform(3))).toBe('3 all round');
    expect(String(new Insets(1, 2, 3, 4))).toBe('1 2 3 4');
  });
});
