import { describe, expect, it } from 'vitest';

import { Rect } from '../../../src/geom/rect.js';
import { Sampling } from '../../../src/measure/resolution.js';
import { CropPlan } from '../../../src/plan/plan.js';
import {
  aspectFor,
  aspectRule,
  nearestAspectRule,
  nudgeAspectRule,
} from '../../../src/plan/rules/aspect.js';

const capture = Rect.of(0, 0, 2000, 2000);

/**
 * @param {number} width
 * @param {number} height
 * @returns {CropPlan}
 */
function start(width, height) {
  return CropPlan.start({
    capture,
    rect: Rect.of(100, 100, width, height),
    sampling: Sampling.of(400),
  });
}

describe('aspectRule', () => {
  it('grows the short side by default', () => {
    const out = aspectRule(1).apply(start(400, 300), {});
    expect(out.rect.width).toBe(400);
    expect(out.rect.height).toBe(400);
    expect(out.trail).toEqual(['aspect']);
  });

  it('shrinks the long side when told to', () => {
    const out = aspectRule(1, { direction: 'shrink' }).apply(start(400, 300), {});
    expect(out.rect.width).toBe(300);
    expect(out.rect.height).toBe(300);
  });

  it('keeps the centre where it was', () => {
    const before = start(400, 300);
    const after = aspectRule(1).apply(before, {});
    expect(after.rect.centre.equals(before.rect.centre)).toBe(true);
  });

  it('does nothing when the crop already has the ratio', () => {
    const plan = start(400, 200);
    expect(aspectRule(2).apply(plan, {})).toBe(plan);
  });

  it('lands in the same place when run twice', () => {
    const once = aspectRule(1.5).apply(start(400, 300), {});
    expect(aspectRule(1.5).apply(once, {})).toBe(once);
  });

  it('leaves an empty crop alone rather than dividing by zero', () => {
    const empty = CropPlan.start({
      capture,
      rect: Rect.of(10, 10, 0, 10),
      sampling: Sampling.of(400),
    });
    expect(aspectRule(1).apply(empty, {})).toBe(empty);
  });

  it('refuses a ratio or direction it cannot use', () => {
    expect(() => aspectRule(0)).toThrow(/aspect must be greater than zero/);
    expect(() => aspectRule(1, { direction: 'sideways' })).toThrow(/direction must be one of/);
  });
});

describe('nearestAspectRule', () => {
  it('picks the closest allowed ratio', () => {
    const out = nearestAspectRule([1, 1.5, 2]).apply(start(400, 260), {});
    expect(out.rect.width / out.rect.height).toBeCloseTo(1.5, 9);
    expect(out.steps[1].note).toMatch(/nearest allowed ratio, 1.5/);
  });

  it('picks the same ratio for a crop the other side of it', () => {
    const out = nearestAspectRule([1, 1.5, 2]).apply(start(400, 280), {});
    expect(out.rect.width / out.rect.height).toBeCloseTo(1.5, 9);
  });

  it('takes a single ratio as well as a list', () => {
    const out = nearestAspectRule([1]).apply(start(400, 300), {});
    expect(out.rect.width).toBe(out.rect.height);
  });

  it('refuses an empty list or a bad ratio', () => {
    expect(() => nearestAspectRule([])).toThrow(/at least one ratio/);
    expect(() => nearestAspectRule([1, -1])).toThrow(/aspect must be greater than zero/);
  });

  it('lands in the same place when run twice', () => {
    const once = nearestAspectRule([1, 1.5, 2]).apply(start(400, 260), {});
    expect(nearestAspectRule([1, 1.5, 2]).apply(once, {})).toBe(once);
  });
});

describe('nudgeAspectRule', () => {
  it('forces a crop that is already close', () => {
    const out = nudgeAspectRule(1.5, { within: 0.1 }).apply(start(400, 260), {});
    expect(out.rect.width / out.rect.height).toBeCloseTo(1.5, 9);
    expect(out.warnings).toEqual([]);
  });

  it('leaves a crop that is genuinely another shape and says so', () => {
    const out = nudgeAspectRule(1.5, { within: 0.05 }).apply(start(400, 400), {});
    expect(out.rect.width).toBe(400);
    expect(out.rect.height).toBe(400);
    expect(out.warnings[0]).toMatch(/off the 1.5 ratio and was left alone/);
  });

  it('leaves an empty crop alone', () => {
    const empty = CropPlan.start({
      capture,
      rect: Rect.of(0, 0, 10, 0),
      sampling: Sampling.of(400),
    });
    expect(nudgeAspectRule(1).apply(empty, {})).toBe(empty);
  });
});

describe('aspectFor', () => {
  it('gives nothing back when the profile set no aspect', () => {
    expect(aspectFor(undefined)).toBeNull();
  });

  it('gives an aspect rule when it did', () => {
    const made = aspectFor(1.5, { direction: 'shrink' });
    expect(made?.name).toBe('aspect');
    expect(made?.phase).toBe('shape');
  });
});
