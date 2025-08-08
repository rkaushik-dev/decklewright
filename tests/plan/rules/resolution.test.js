import { describe, expect, it } from 'vitest';

import { Rect } from '../../../src/geom/rect.js';
import { Sampling } from '../../../src/measure/resolution.js';
import { CropPlan } from '../../../src/plan/plan.js';
import {
  achievedPpi,
  minimumSizeRule,
  resolutionFor,
  resolutionRule,
  strictResolutionRule,
} from '../../../src/plan/rules/resolution.js';

const capture = Rect.of(0, 0, 4000, 3000);

/**
 * @param {number} ppi
 * @param {Rect} [rect]
 * @returns {CropPlan}
 */
function start(ppi, rect = Rect.of(100, 100, 2000, 1500)) {
  return CropPlan.start({ capture, rect, sampling: Sampling.of(ppi) });
}

describe('resolutionRule', () => {
  it('says nothing when the capture is over the floor', () => {
    const plan = start(600);
    expect(resolutionRule(400).apply(plan, {})).toBe(plan);
  });

  it('accepts a capture exactly on the floor', () => {
    expect(resolutionRule(400).apply(start(400), {}).warnings).toEqual([]);
  });

  it('warns when it is under', () => {
    const out = resolutionRule(400).apply(start(300), {});
    expect(out.warnings[0]).toBe('the capture is 300 ppi against a floor of 400');
  });

  it('reads the worse axis of a non square capture', () => {
    const plan = CropPlan.start({
      capture,
      rect: Rect.of(0, 0, 100, 100),
      sampling: new Sampling(600, 300),
    });
    expect(resolutionRule(400).apply(plan, {}).warnings[0]).toMatch(/300 ppi/);
  });

  it('does not move the crop, because cropping cannot fix it', () => {
    const before = start(200);
    const after = resolutionRule(400).apply(before, {});
    expect(after.rect.equals(before.rect)).toBe(true);
    expect(after.trail).toEqual([]);
  });

  it('warns once even when run twice', () => {
    const once = resolutionRule(400).apply(start(300), {});
    expect(resolutionRule(400).apply(once, {}).warnings).toHaveLength(1);
  });

  it('refuses a floor of zero', () => {
    expect(() => resolutionRule(0)).toThrow(/minimumPpi must be greater than zero/);
  });
});

describe('strictResolutionRule', () => {
  it('lets a capture over the floor through', () => {
    const plan = start(600);
    expect(strictResolutionRule(400).apply(plan, {})).toBe(plan);
  });

  it('refuses one under it', () => {
    expect(() => strictResolutionRule(400).apply(start(300), {})).toThrow(
      /is 300 ppi and this collection will not take under 400/,
    );
  });

  it('carries the numbers in the error', () => {
    try {
      strictResolutionRule(400).apply(start(299.5), {});
      throw new Error('should have thrown');
    } catch (err) {
      expect(err.details.achieved).toBe(299.5);
      expect(err.details.minimumPpi).toBe(400);
    }
  });
});

describe('minimumSizeRule', () => {
  it('says nothing about a large crop', () => {
    const plan = start(400);
    expect(minimumSizeRule(1000).apply(plan, {})).toBe(plan);
  });

  it('warns about a small one and quotes the long side', () => {
    const small = start(400, Rect.of(0, 0, 300, 200));
    expect(minimumSizeRule(1000).apply(small, {}).warnings[0]).toBe(
      'the crop is 300 px on its long side against a floor of 1000',
    );
  });

  it('measures the long side whichever way round it is', () => {
    const tall = start(400, Rect.of(0, 0, 200, 900));
    expect(minimumSizeRule(500).apply(tall, {}).warnings).toEqual([]);
  });

  it('is a check, so it never moves anything', () => {
    const small = start(400, Rect.of(0, 0, 300, 200));
    expect(minimumSizeRule(1000).apply(small, {}).rect.equals(small.rect)).toBe(true);
  });
});

describe('resolutionFor', () => {
  it('picks the shape the collection asked for', () => {
    expect(resolutionFor(400).apply(start(300), {}).warnings).toHaveLength(1);
    expect(() => resolutionFor(400, { strict: true }).apply(start(300), {})).toThrow();
  });

  it('is called resolution in the check phase either way', () => {
    for (const strict of [false, true]) {
      const made = resolutionFor(400, { strict });
      expect(made.name).toBe('resolution');
      expect(made.phase).toBe('check');
    }
  });
});

describe('achievedPpi', () => {
  it('divides the crop by the object it holds', () => {
    const plan = start(400, Rect.of(0, 0, 6800, 100));
    expect(achievedPpi(plan, 17, 'in')).toBe(400);
    expect(achievedPpi(plan, 431.8, 'mm')).toBeCloseTo(400, 6);
  });

  it('refuses a physical width of zero', () => {
    expect(() => achievedPpi(start(400), 0, 'in')).toThrow(/physicalWidth must be greater/);
  });

  it('refuses a unit it does not know', () => {
    expect(() => achievedPpi(start(400), 1, 'px')).toThrow(/unknown unit px/);
  });
});
