import { describe, expect, it } from 'vitest';

import { Insets } from '../../../src/geom/insets.js';
import { Rect } from '../../../src/geom/rect.js';
import { Sampling } from '../../../src/measure/resolution.js';
import { CropPlan } from '../../../src/plan/plan.js';
import { anchorFor, anchorOrSheetRule, anchorRule } from '../../../src/plan/rules/anchor.js';
import { edgeSafeAreaRule, safeAreaFor, safeAreaRule } from '../../../src/plan/rules/safearea.js';

const capture = Rect.of(0, 0, 1000, 800);
const sheet = Rect.of(100, 100, 600, 500);
const neatline = Rect.of(150, 150, 500, 380);

/**
 * @param {Rect} [rect]
 * @returns {CropPlan}
 */
function start(rect = sheet) {
  return CropPlan.start({ capture, rect, sampling: Sampling.of(400) });
}

describe('anchorRule', () => {
  it('sets the crop to the rect and names it in the trail', () => {
    const out = anchorRule(neatline, 'neatline').apply(start(), {});
    expect(out.rect.equals(neatline)).toBe(true);
    expect(out.steps[1].note).toBe('measured from the neatline');
  });

  it('records the anchor in the context for the later rules', () => {
    const context = {};
    anchorRule(neatline, 'neatline').apply(start(), context);
    expect(context.anchor).toBe(neatline);
    expect(context.anchorName).toBe('neatline');
  });

  it('lands in the same place when run twice', () => {
    const once = anchorRule(neatline, 'neatline').apply(start(), {});
    const twice = anchorRule(neatline, 'neatline').apply(once, {});
    expect(twice).toBe(once);
  });

  it('refuses an anchor with no area', () => {
    expect(() => anchorRule(Rect.empty(), 'neatline')).toThrow(/the neatline anchor has no area/);
  });
});

describe('anchorFor', () => {
  it('picks the sheet or the neatline as the profile says', () => {
    expect(anchorFor('sheet', { sheet, neatline }).apply(start(), {}).rect.equals(sheet)).toBe(
      true,
    );
    expect(
      anchorFor('neatline', { sheet, neatline }).apply(start(), {}).rect.equals(neatline),
    ).toBe(true);
  });

  it('refuses to fall back quietly when the neatline is missing', () => {
    expect(() => anchorFor('neatline', { sheet })).toThrow(/crops to the neatline and none/);
    expect(() => anchorFor('neatline', { sheet, neatline: Rect.empty() })).toThrow(
      /none was found/,
    );
  });

  it('refuses an anchor it does not know', () => {
    expect(() => anchorFor('collar', { sheet })).toThrow(/there is no anchor called collar/);
  });
});

describe('anchorOrSheetRule', () => {
  it('uses the neatline when there is one', () => {
    const out = anchorOrSheetRule('neatline', { sheet, neatline }).apply(start(), {});
    expect(out.rect.equals(neatline)).toBe(true);
    expect(out.warnings).toEqual([]);
  });

  it('falls back to the paper edge and says so loudly', () => {
    const out = anchorOrSheetRule('neatline', { sheet }).apply(start(), {});
    expect(out.rect.equals(sheet)).toBe(true);
    expect(out.warnings[0]).toMatch(/fell back to the paper edge/);
  });

  it('behaves like anchorFor when the anchor is the sheet', () => {
    const out = anchorOrSheetRule('sheet', { sheet }).apply(start(), {});
    expect(out.warnings).toEqual([]);
  });
});

describe('safeAreaRule', () => {
  it('pulls the crop in from the anchor', () => {
    const out = safeAreaRule(Insets.uniform(50)).apply(start(), { anchor: sheet });
    expect(out.rect.toJSON()).toEqual({ x: 150, y: 150, width: 500, height: 400 });
  });

  it('does nothing at all for a zero safe area', () => {
    const plan = start();
    expect(safeAreaRule(Insets.zero()).apply(plan, { anchor: sheet })).toBe(plan);
  });

  it('does not let a bleed put the crop back outside the safe area', () => {
    const bled = start().moved(Rect.of(80, 80, 640, 540), { rule: 'bleed', note: 'x' });
    const out = safeAreaRule(Insets.uniform(50)).apply(bled, { anchor: sheet });
    expect(out.rect.toJSON()).toEqual({ x: 150, y: 150, width: 500, height: 400 });
  });

  it('warns when it consumes the anchor entirely', () => {
    const out = safeAreaRule(Insets.uniform(400)).apply(start(), { anchor: sheet });
    expect(out.warnings[0]).toMatch(/leaves no crop at all/);
  });

  it('lands in the same place when run twice', () => {
    const context = { anchor: sheet };
    const once = safeAreaRule(Insets.uniform(50)).apply(start(), context);
    const twice = safeAreaRule(Insets.uniform(50)).apply(once, context);
    expect(twice).toBe(once);
  });
});

describe('edgeSafeAreaRule', () => {
  it('pulls in only the edges it was given', () => {
    const bled = start().moved(Rect.of(80, 80, 640, 540), { rule: 'bleed', note: 'x' });
    const out = edgeSafeAreaRule(new Insets(0, 0, 0, 60)).apply(bled, { anchor: sheet });
    expect(out.rect.left).toBe(160);
    expect(out.rect.top).toBe(80);
    expect(out.rect.right).toBe(720);
  });

  it('does nothing for a zero safe area', () => {
    const plan = start();
    expect(edgeSafeAreaRule(Insets.zero()).apply(plan, { anchor: sheet })).toBe(plan);
  });

  it('warns rather than inverting when the edges meet', () => {
    const out = edgeSafeAreaRule(Insets.uniform(400)).apply(start(), { anchor: sheet });
    expect(out.warnings[0]).toMatch(/leaves no crop at all/);
    expect(out.rect.equals(sheet)).toBe(true);
  });
});

describe('safeAreaFor', () => {
  it('picks the shape the mode names', () => {
    const bled = start().moved(Rect.of(80, 80, 640, 540), { rule: 'bleed', note: 'x' });
    expect(safeAreaFor(new Insets(0, 0, 0, 60)).apply(bled, { anchor: sheet }).rect.top).toBe(100);
    expect(
      safeAreaFor(new Insets(0, 0, 0, 60), { mode: 'edges' }).apply(bled, { anchor: sheet }).rect
        .top,
    ).toBe(80);
  });

  it('is called safeArea in the shape phase either way', () => {
    for (const mode of ['whole', 'edges']) {
      const made = safeAreaFor(Insets.uniform(1), { mode });
      expect(made.name).toBe('safeArea');
      expect(made.phase).toBe('shape');
    }
  });
});
