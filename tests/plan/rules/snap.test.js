import { describe, expect, it } from 'vitest';

import { Rect } from '../../../src/geom/rect.js';
import { isWholePixel } from '../../../src/geom/round.js';
import { Sampling } from '../../../src/measure/resolution.js';
import { CropPlan } from '../../../src/plan/plan.js';
import {
  evenExtentsRule,
  gridAlignRule,
  roundRule,
  snapFor,
  snapRule,
} from '../../../src/plan/rules/snap.js';

const capture = Rect.of(0, 0, 4000, 3000);

/**
 * @param {Rect} rect
 * @returns {CropPlan}
 */
function start(rect) {
  return CropPlan.start({ capture, rect, sampling: Sampling.of(400) });
}

describe('snapRule', () => {
  it('grows the extents to the multiple', () => {
    const out = snapRule(8).apply(start(Rect.of(10, 10, 101, 99)), {});
    expect(out.rect.width).toBe(104);
    expect(out.rect.height).toBe(104);
    expect(out.trail).toEqual(['snap']);
  });

  it('shrinks when told to', () => {
    const out = snapRule(8, { direction: 'down' }).apply(start(Rect.of(10, 10, 101, 99)), {});
    expect(out.rect.width).toBe(96);
    expect(out.rect.height).toBe(96);
  });

  it('goes the nearer way when told to', () => {
    const out = snapRule(8, { direction: 'nearest' }).apply(start(Rect.of(10, 10, 101, 99)), {});
    expect(out.rect.width).toBe(104);
    expect(out.rect.height).toBe(96);
  });

  it('keeps the centre near where it was', () => {
    const before = start(Rect.of(10, 10, 101, 99));
    const after = snapRule(8).apply(before, {});
    expect(Math.abs(after.rect.centre.x - before.rect.centre.x)).toBeLessThanOrEqual(1);
  });

  it('does nothing at a multiple of one', () => {
    const plan = start(Rect.of(10, 10, 101, 99));
    expect(snapRule(1).apply(plan, {})).toBe(plan);
  });

  it('does nothing to a crop already on the multiple', () => {
    const plan = start(Rect.of(10, 10, 96, 96));
    expect(snapRule(8).apply(plan, {})).toBe(plan);
  });

  it('lands in the same place when run twice', () => {
    const once = snapRule(8).apply(start(Rect.of(10, 10, 101, 99)), {});
    expect(snapRule(8).apply(once, {})).toBe(once);
  });

  it('refuses a multiple that is not a whole number above zero', () => {
    expect(() => snapRule(0)).toThrow(/above zero/);
    expect(() => snapRule(1.5)).toThrow(/whole number/);
    expect(() => snapRule(8, { direction: 'sideways' })).toThrow(/direction must be one of/);
  });
});

describe('roundRule', () => {
  it('puts a fractional crop on whole pixels', () => {
    const out = roundRule('outward').apply(start(Rect.of(10.4, 10.6, 100.2, 100.2)), {});
    expect(isWholePixel(out.rect)).toBe(true);
    expect(out.rect.left).toBe(10);
    expect(out.rect.right).toBe(111);
  });

  it('honours the policy it was given', () => {
    const rect = Rect.of(10.4, 10.6, 100.2, 100.2);
    expect(roundRule('inward').apply(start(rect), {}).rect.left).toBe(11);
    expect(roundRule('floor').apply(start(rect), {}).rect.right).toBe(110);
  });

  it('does nothing to a crop already on whole pixels', () => {
    const plan = start(Rect.of(10, 10, 100, 100));
    expect(roundRule('outward').apply(plan, {})).toBe(plan);
  });

  it('lands in the same place when run twice', () => {
    const once = roundRule('outward').apply(start(Rect.of(10.4, 10.6, 100.2, 100.2)), {});
    expect(roundRule('outward').apply(once, {})).toBe(once);
  });

  it('refuses a policy it does not know', () => {
    expect(() => roundRule('sideways')).toThrow(/policy must be one of/);
  });
});

describe('gridAlignRule', () => {
  it('moves the origin onto the grid as well as the extents', () => {
    const out = gridAlignRule(64).apply(start(Rect.of(100, 100, 300, 300)), {});
    expect(out.rect.left % 64).toBe(0);
    expect(out.rect.top % 64).toBe(0);
    expect(out.rect.width % 64).toBe(0);
    expect(out.rect.height % 64).toBe(0);
  });

  it('only ever grows the crop', () => {
    const before = start(Rect.of(100, 100, 300, 300));
    const after = gridAlignRule(64).apply(before, {});
    expect(after.rect.containsRect(before.rect)).toBe(true);
  });

  it('does nothing at a multiple of one', () => {
    const plan = start(Rect.of(100, 100, 300, 300));
    expect(gridAlignRule(1).apply(plan, {})).toBe(plan);
  });

  it('lands in the same place when run twice', () => {
    const once = gridAlignRule(64).apply(start(Rect.of(100, 100, 300, 300)), {});
    expect(gridAlignRule(64).apply(once, {})).toBe(once);
  });
});

describe('evenExtentsRule', () => {
  it('grows an odd crop by a pixel', () => {
    const out = evenExtentsRule().apply(start(Rect.of(10, 10, 101, 99)), {});
    expect(out.rect.width % 2).toBe(0);
    expect(out.rect.height % 2).toBe(0);
    expect(out.steps[1].note).toBe('grown to even extents');
  });

  it('does nothing to a crop that is already even', () => {
    const plan = start(Rect.of(10, 10, 100, 100));
    expect(evenExtentsRule().apply(plan, {})).toBe(plan);
  });
});

describe('snapFor', () => {
  it('picks the plain snap or the grid align', () => {
    const rect = Rect.of(100, 100, 300, 300);
    const plain = snapFor(64).apply(start(rect), {}).rect;
    expect(plain.width % 64).toBe(0);
    expect(plain.left % 64).not.toBe(0);

    const aligned = snapFor(64, { alignOrigin: true }).apply(start(rect), {}).rect;
    expect(aligned.left).toBe(64);
    expect(aligned.width % 64).toBe(0);
  });

  it('sits in the grid phase either way', () => {
    for (const alignOrigin of [false, true]) {
      expect(snapFor(8, { alignOrigin }).phase).toBe('grid');
    }
  });
});
