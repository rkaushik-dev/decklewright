import { describe, expect, it } from 'vitest';

import { Insets } from '../../src/geom/insets.js';
import { Rect } from '../../src/geom/rect.js';
import { Sampling } from '../../src/measure/resolution.js';
import { croppedSampling, planLinear } from '../../src/plan/linear.js';

const capture = Rect.of(0, 0, 1200, 900);
const anchor = Rect.of(200, 150, 800, 600);

/**
 * @param {Partial<Record<string, unknown>>} [overrides]
 * @returns {Record<string, any>}
 */
function options(overrides = {}) {
  return {
    bleed: Insets.zero(),
    safeArea: Insets.zero(),
    minimumPpi: 300,
    snapTo: 2,
    snapDirection: 'up',
    rounding: 'outward',
    ...overrides,
  };
}

/**
 * @param {Record<string, any>} [overrides]
 * @returns {import('../../src/plan/plan.js').CropPlan}
 */
function plan(overrides) {
  return planLinear({ capture, anchor, sampling: Sampling.of(400) }, options(overrides));
}

describe('planLinear', () => {
  it('leaves the anchor alone when nothing asks for a change', () => {
    const out = plan();
    expect(out.rect.equals(anchor)).toBe(true);
    expect(out.trail).toEqual([]);
    expect(out.warnings).toEqual([]);
  });

  it('refuses an anchor with no area', () => {
    expect(() =>
      planLinear({ capture, anchor: Rect.empty(), sampling: Sampling.of(400) }, options()),
    ).toThrow(/the anchor has no area/);
  });
});

describe('the bleed step', () => {
  it('pushes every edge out', () => {
    const out = plan({ bleed: Insets.uniform(20) });
    expect(out.rect.toJSON()).toEqual({ x: 180, y: 130, width: 840, height: 640 });
    expect(out.trail).toContain('bleed');
  });

  it('pushes each edge by its own amount', () => {
    const out = plan({ bleed: new Insets(10, 20, 30, 40) });
    expect(out.rect.toJSON()).toEqual({ x: 160, y: 140, width: 860, height: 640 });
  });
});

describe('the safe area step', () => {
  it('pulls every edge in', () => {
    const out = plan({ safeArea: Insets.uniform(50) });
    expect(out.rect.toJSON()).toEqual({ x: 250, y: 200, width: 700, height: 500 });
    expect(out.trail).toContain('safeArea');
  });

  it('runs after the bleed, so the two fight and the safe area wins', () => {
    const out = plan({ bleed: Insets.uniform(20), safeArea: Insets.uniform(20) });
    expect(out.rect.equals(anchor)).toBe(true);
    expect(out.trail).toEqual(['bleed', 'safeArea']);
  });
});

describe('the aspect step', () => {
  it('grows the short side by default', () => {
    const out = plan({ aspect: 1 });
    expect(out.rect.width).toBe(800);
    expect(out.rect.height).toBe(800);
    expect(out.trail).toContain('aspect');
  });

  it('shrinks the long side when told to', () => {
    const out = plan({ aspect: 1, aspectDirection: 'shrink' });
    expect(out.rect.width).toBe(600);
    expect(out.rect.height).toBe(600);
  });

  it('does nothing when the crop already has the aspect', () => {
    const out = plan({ aspect: 800 / 600 });
    expect(out.trail).not.toContain('aspect');
  });
});

describe('the resolution warning', () => {
  it('says nothing when the crop is over the floor', () => {
    expect(plan({ minimumPpi: 300 }).warnings).toEqual([]);
  });

  it('warns when the crop falls under it', () => {
    const out = plan({ minimumPpi: 600 });
    expect(out.warnings[0]).toMatch(/only reaches 400 ppi against a floor of 600/);
  });

  it('warns without refusing, so an operator can still override', () => {
    expect(plan({ minimumPpi: 600 }).rect.equals(anchor)).toBe(true);
  });
});

describe('the snap step', () => {
  it('grows the extents to the multiple', () => {
    const out = planLinear(
      { capture, anchor: Rect.of(10, 10, 101, 99), sampling: Sampling.of(400) },
      options({ snapTo: 8 }),
    );
    expect(out.rect.width % 8).toBe(0);
    expect(out.rect.height % 8).toBe(0);
    expect(out.rect.width).toBe(104);
  });

  it('can be told to snap down instead', () => {
    const out = planLinear(
      { capture, anchor: Rect.of(10, 10, 101, 99), sampling: Sampling.of(400) },
      options({ snapTo: 8, snapDirection: 'down' }),
    );
    expect(out.rect.width).toBe(96);
  });
});

describe('the rounding step', () => {
  it('grows a fractional crop outward', () => {
    const out = planLinear(
      { capture, anchor: Rect.of(10.4, 10.6, 100.2, 100.2), sampling: Sampling.of(400) },
      options({ snapTo: 1, rounding: 'outward' }),
    );
    expect(Number.isInteger(out.rect.x)).toBe(true);
    expect(Number.isInteger(out.rect.width)).toBe(true);
  });
});

describe('the bounds warning', () => {
  it('warns when the crop reaches off the capture', () => {
    const out = planLinear(
      { capture, anchor: Rect.of(1100, 800, 200, 200), sampling: Sampling.of(400) },
      options(),
    );
    expect(out.warnings.join(' ')).toMatch(/falls off the capture/);
  });

  it('says nothing when the crop is inside', () => {
    expect(plan().warnings).toEqual([]);
  });
});

describe('the order is fixed, which is the flaw', () => {
  it('snapping after the aspect breaks the aspect', () => {
    const out = planLinear(
      { capture, anchor: Rect.of(0, 0, 101, 101), sampling: Sampling.of(400) },
      options({ aspect: 1, snapTo: 8 }),
    );
    expect(out.rect.width).toBe(out.rect.height);
    const odd = planLinear(
      { capture, anchor: Rect.of(0, 0, 101, 90), sampling: Sampling.of(400) },
      options({ aspect: 1.5, snapTo: 7 }),
    );
    expect(odd.rect.width / odd.rect.height).not.toBeCloseTo(1.5, 2);
  });
});

describe('croppedSampling', () => {
  it('says what a crop of a known width really reached', () => {
    expect(croppedSampling(Rect.of(0, 0, 6800, 100), 17, 'in').x).toBe(400);
  });
});
