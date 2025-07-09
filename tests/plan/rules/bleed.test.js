import { describe, expect, it } from 'vitest';

import { Insets } from '../../../src/geom/insets.js';
import { Rect } from '../../../src/geom/rect.js';
import { Sampling } from '../../../src/measure/resolution.js';
import { CropPlan } from '../../../src/plan/plan.js';
import {
  bleedFor,
  bleedRule,
  bleedWithinSheetRule,
  noBleedRule,
  trimmedBleedRule,
} from '../../../src/plan/rules/bleed.js';
import {
  PHASES,
  assertNoDuplicates,
  inPhase,
  isRule,
  ordered,
  rule,
} from '../../../src/plan/rules/rule.js';

const capture = Rect.of(0, 0, 1000, 800);
const anchor = Rect.of(100, 100, 400, 300);

/**
 * @param {Rect} [rect]
 * @returns {CropPlan}
 */
function start(rect = anchor) {
  return CropPlan.start({ capture, rect, sampling: Sampling.of(400) });
}

describe('the rule protocol', () => {
  it('builds a rule and freezes it', () => {
    const made = rule({ name: 'x', phase: 'shape', apply: (plan) => plan });
    expect(Object.isFrozen(made)).toBe(true);
    expect(isRule(made)).toBe(true);
    expect(isRule({})).toBe(false);
  });

  it('refuses a phase it does not know or a rule with no body', () => {
    expect(() => rule({ name: 'x', phase: 'later', apply: (p) => p })).toThrow(
      /phase must be one of/,
    );
    expect(() => rule({ name: 'x', phase: 'shape', apply: null })).toThrow(/nothing to apply/);
    expect(() => rule({ name: '', phase: 'shape', apply: (p) => p })).toThrow(/non empty string/);
  });

  it('filters and orders by phase', () => {
    const set = [
      rule({ name: 'c', phase: 'check', apply: (p) => p }),
      rule({ name: 'a', phase: 'anchor', apply: (p) => p }),
      rule({ name: 'g', phase: 'grid', apply: (p) => p }),
      rule({ name: 's', phase: 'shape', apply: (p) => p }),
    ];
    expect(ordered(set).map((r) => r.name)).toEqual(['a', 's', 'g', 'c']);
    expect(inPhase(set, 'grid').map((r) => r.name)).toEqual(['g']);
    expect(PHASES).toHaveLength(4);
  });

  it('keeps the written order inside a phase', () => {
    const set = [
      rule({ name: 'first', phase: 'shape', apply: (p) => p }),
      rule({ name: 'second', phase: 'shape', apply: (p) => p }),
    ];
    expect(ordered(set).map((r) => r.name)).toEqual(['first', 'second']);
  });

  it('catches the same rule twice', () => {
    const set = [
      rule({ name: 'bleed', phase: 'anchor', apply: (p) => p }),
      rule({ name: 'bleed', phase: 'shape', apply: (p) => p }),
    ];
    expect(() => assertNoDuplicates(set)).toThrow(/the rule bleed is in the set twice/);
    expect(() => assertNoDuplicates(set.slice(0, 1))).not.toThrow();
  });
});

describe('bleedRule', () => {
  it('pushes the anchor out by the bleed', () => {
    const out = bleedRule(Insets.uniform(20)).apply(start(), { anchor });
    expect(out.rect.toJSON()).toEqual({ x: 80, y: 80, width: 440, height: 340 });
    expect(out.trail).toEqual(['bleed']);
  });

  it('does nothing at all for a zero bleed', () => {
    const plan = start();
    expect(noBleedRule().apply(plan, { anchor })).toBe(plan);
  });

  it('measures from the anchor, not from the crop as it stands', () => {
    const moved = start().moved(Rect.of(0, 0, 900, 700), { rule: 'other', note: 'x' });
    const out = bleedRule(Insets.uniform(20)).apply(moved, { anchor });
    expect(out.rect.toJSON()).toEqual({ x: 80, y: 80, width: 440, height: 340 });
  });

  it('gives the same answer twice, so the engine may run it again', () => {
    const once = bleedRule(Insets.uniform(20)).apply(start(), { anchor });
    const twice = bleedRule(Insets.uniform(20)).apply(once, { anchor });
    expect(twice.rect.equals(once.rect)).toBe(true);
    expect(twice).toBe(once);
  });

  it('names what it was measured from', () => {
    const out = bleedRule(Insets.uniform(5)).apply(start(), { anchor, anchorName: 'neatline' });
    expect(out.steps[1].note).toMatch(/beyond the neatline/);
  });
});

describe('trimmedBleedRule', () => {
  it('is the plain bleed when there is room', () => {
    const out = trimmedBleedRule(Insets.uniform(20)).apply(start(), { anchor });
    expect(out.rect.toJSON()).toEqual({ x: 80, y: 80, width: 440, height: 340 });
    expect(out.warnings).toEqual([]);
  });

  it('trims at the capture edge and says so', () => {
    const edge = Rect.of(0, 0, 200, 200);
    const out = trimmedBleedRule(Insets.uniform(20)).apply(start(edge), { anchor: edge });
    expect(out.rect.left).toBe(0);
    expect(out.warnings[0]).toMatch(/trimmed at the edge of the capture/);
  });
});

describe('bleedWithinSheetRule', () => {
  const sheet = Rect.of(110, 110, 380, 280);

  it('holds the bleed inside the paper', () => {
    const out = bleedWithinSheetRule(Insets.uniform(20), sheet).apply(start(), { anchor });
    expect(out.rect.equals(sheet.intersect(Rect.of(80, 80, 440, 340)))).toBe(true);
    expect(out.warnings[0]).toMatch(/not have enough paper/);
  });

  it('says nothing when the paper reaches far enough', () => {
    const wide = Rect.of(0, 0, 900, 700);
    const out = bleedWithinSheetRule(Insets.uniform(20), wide).apply(start(), { anchor });
    expect(out.warnings).toEqual([]);
  });
});

describe('bleedFor', () => {
  it('picks the rule the mode names', () => {
    const edge = Rect.of(0, 0, 200, 200);
    expect(bleedFor(Insets.uniform(20)).apply(start(edge), { anchor: edge }).rect.left).toBe(-20);
    expect(
      bleedFor(Insets.uniform(20), { mode: 'trimmed' }).apply(start(edge), { anchor: edge }).rect
        .left,
    ).toBe(0);
  });

  it('falls back to the plain rule when a sheet was not given', () => {
    const out = bleedFor(Insets.uniform(20), { mode: 'withinSheet' }).apply(start(), { anchor });
    expect(out.rect.left).toBe(80);
  });

  it('every shape of it is called bleed and sits in the anchor phase', () => {
    for (const mode of ['plain', 'trimmed', 'withinSheet']) {
      const made = bleedFor(Insets.uniform(1), { mode, sheet: capture });
      expect(made.name).toBe('bleed');
      expect(made.phase).toBe('anchor');
    }
  });
});
