import { describe, expect, it } from 'vitest';

import { Insets } from '../../src/geom/insets.js';
import { Rect } from '../../src/geom/rect.js';
import { Sampling } from '../../src/measure/resolution.js';
import { planInDetail, planWith, runRules, without, withRule } from '../../src/plan/engine.js';
import { CropPlan } from '../../src/plan/plan.js';
import { anchorRule } from '../../src/plan/rules/anchor.js';
import { aspectRule } from '../../src/plan/rules/aspect.js';
import { bleedRule } from '../../src/plan/rules/bleed.js';
import { boundsFor, notEmptyRule } from '../../src/plan/rules/bounds.js';
import { resolutionRule } from '../../src/plan/rules/resolution.js';
import { rule } from '../../src/plan/rules/rule.js';
import { roundRule, snapRule } from '../../src/plan/rules/snap.js';

const capture = Rect.of(0, 0, 2000, 1600);
const anchor = Rect.of(200, 200, 800, 600);

/**
 * @param {import('../../src/plan/rules/rule.js').Rule[]} rules
 * @param {object} [options]
 * @returns {import('../../src/plan/plan.js').CropPlan}
 */
function run(rules, options) {
  return planWith(rules, { capture, anchor, sampling: Sampling.of(400) }, options);
}

describe('planWith', () => {
  it('gives the anchor back when the set is empty', () => {
    expect(run([]).rect.equals(anchor)).toBe(true);
  });

  it('applies the rules in phase order whatever order they were given', () => {
    const out = run([
      snapRule(16),
      bleedRule(Insets.uniform(10)),
      anchorRule(anchor, 'sheet'),
      notEmptyRule(),
    ]);
    expect(out.trail[0]).toBe('bleed');
    expect(out.trail).toContain('snap');
  });

  it('refuses an anchor with no area', () => {
    expect(() =>
      planWith([], { capture, anchor: Rect.empty(), sampling: Sampling.of(400) }),
    ).toThrow(/the anchor has no area/);
  });

  it('refuses a set with the same rule twice', () => {
    expect(() => run([snapRule(8), snapRule(16)])).toThrow(/the rule snap is in the set twice/);
  });
});

describe('running until it settles', () => {
  it('settles a set with nothing to fight about', () => {
    const out = planInDetail([bleedRule(Insets.uniform(10)), roundRule('outward')], {
      capture,
      anchor,
      sampling: Sampling.of(400),
    });
    expect(out.settled).toBe(true);
    expect(out.passes).toBe(2);
  });

  it('settles the aspect and the snap together, which the linear order could not', () => {
    const out = planInDetail([aspectRule(1), snapRule(8)], {
      capture,
      anchor: Rect.of(0, 0, 101, 90),
      sampling: Sampling.of(400),
    });
    expect(out.settled).toBe(true);
    expect(out.plan.rect.width).toBe(out.plan.rect.height);
    expect(out.plan.rect.width % 8).toBe(0);
  });

  it('says so when a set will not settle', () => {
    const grow = rule({
      name: 'grow',
      phase: 'shape',
      apply: (plan) => plan.moved(plan.rect.outset(1), { rule: 'grow', note: 'wider' }),
    });
    const shrink = rule({
      name: 'shrink',
      phase: 'grid',
      apply: (plan) => plan.moved(plan.rect.inset(2), { rule: 'shrink', note: 'narrower' }),
    });
    const out = planInDetail([grow, shrink], { capture, anchor, sampling: Sampling.of(400) });
    expect(out.settled).toBe(false);
    expect(out.plan.warnings[0]).toMatch(/did not settle in 6 passes/);
  });

  it('honours a pass limit', () => {
    const grow = rule({
      name: 'grow',
      phase: 'shape',
      apply: (plan) => plan.moved(plan.rect.outset(1), { rule: 'grow', note: 'wider' }),
    });
    const out = planInDetail(
      [grow],
      { capture, anchor, sampling: Sampling.of(400) },
      {
        maxPasses: 2,
      },
    );
    expect(out.passes).toBe(2);
    expect(out.settled).toBe(false);
  });

  it('refuses a pass limit that is not a whole number above zero', () => {
    const start = CropPlan.start({ capture, rect: anchor, sampling: Sampling.of(400) });
    expect(() => runRules([], start, {}, { maxPasses: 0 })).toThrow(/above zero/);
  });
});

describe('the check phase', () => {
  it('runs after everything has settled', () => {
    const out = run([snapRule(8), resolutionRule(600)]);
    expect(out.warnings[0]).toMatch(/against a floor of 600/);
  });

  it('runs once even when the moving rules ran several times', () => {
    const out = planInDetail([aspectRule(1), snapRule(8), resolutionRule(600)], {
      capture,
      anchor: Rect.of(0, 0, 101, 90),
      sampling: Sampling.of(400),
    });
    expect(out.plan.warnings.filter((w) => w.includes('floor of 600'))).toHaveLength(1);
  });

  it('can stop the plan outright', () => {
    expect(() =>
      planWith([boundsFor('refuse')], {
        capture,
        anchor: Rect.of(1900, 1500, 400, 300),
        sampling: Sampling.of(400),
      }),
    ).toThrow(/will not pad/);
  });
});

describe('planInDetail', () => {
  it('names the rules it ran, in the order it ran them', () => {
    const out = planInDetail([notEmptyRule(), snapRule(8), bleedRule(Insets.uniform(4))], {
      capture,
      anchor,
      sampling: Sampling.of(400),
    });
    expect(out.rules).toEqual(['bleed', 'snap', 'notEmpty']);
  });

  it('refuses an empty anchor before it starts', () => {
    expect(() =>
      planInDetail([], { capture, anchor: Rect.empty(), sampling: Sampling.of(400) }),
    ).toThrow(/the anchor has no area/);
  });
});

describe('editing a set', () => {
  const set = [bleedRule(Insets.uniform(10)), snapRule(8), notEmptyRule()];

  it('drops rules by name', () => {
    expect(without(set, ['snap']).map((r) => r.name)).toEqual(['bleed', 'notEmpty']);
    expect(without(set, ['nothing']).map((r) => r.name)).toHaveLength(3);
  });

  it('replaces a rule of the same name in place', () => {
    const swapped = withRule(set, snapRule(64));
    expect(swapped.map((r) => r.name)).toEqual(['bleed', 'snap', 'notEmpty']);
    expect(planWith(swapped, { capture, anchor, sampling: Sampling.of(400) }).rect.width % 64).toBe(
      0,
    );
  });

  it('adds a rule that was not there', () => {
    expect(withRule(set, resolutionRule(300)).map((r) => r.name)).toContain('resolution');
  });
});
