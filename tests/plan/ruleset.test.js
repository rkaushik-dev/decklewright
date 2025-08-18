import { describe, expect, it } from 'vitest';

import { Rect } from '../../src/geom/rect.js';
import { Sampling } from '../../src/measure/resolution.js';
import { ProfileRegistry } from '../../src/profile/registry.js';
import { cropOptions } from '../../src/profile/settings.js';
import { planWith } from '../../src/plan/engine.js';
import {
  KNOWN_TENSIONS,
  describeSet,
  houseRules,
  rulesFor,
  tensionsIn,
} from '../../src/plan/ruleset.js';

const registry = new ProfileRegistry();
const capture = Rect.of(0, 0, 4000, 3200);
const sheet = Rect.of(400, 400, 3000, 2200);
const neatline = Rect.of(600, 600, 2600, 1700);

/**
 * @param {string} name
 * @returns {Record<string, any>}
 */
function cropFor(name) {
  const profile = registry.get(name);
  return cropOptions(profile, Sampling.of(profile.capture.claimedPpi));
}

describe('rulesFor', () => {
  it('builds the house set', () => {
    const rules = rulesFor(cropFor('house'), { sheet });
    expect(rules.map((r) => r.name)).toEqual([
      'anchor',
      'snap',
      'round',
      'bounds',
      'resolution',
      'notEmpty',
    ]);
  });

  it('adds a bleed rule only when the profile has one', () => {
    expect(rulesFor(cropFor('house'), { sheet }).map((r) => r.name)).not.toContain('bleed');
    expect(rulesFor(cropFor('atlas-plates'), { sheet }).map((r) => r.name)).toContain('bleed');
  });

  it('adds a safe area rule only when the profile has one', () => {
    const site = new ProfileRegistry().add({
      name: 'gutter',
      extends: 'house',
      crop: { safeArea: { left: '10mm' } },
    });
    const crop = cropOptions(site.get('gutter'), Sampling.of(400));
    expect(rulesFor(crop, { sheet }).map((r) => r.name)).toContain('safeArea');
  });

  it('adds an aspect rule only when the profile set one', () => {
    const site = new ProfileRegistry().add({
      name: 'square',
      extends: 'house',
      crop: { aspect: 1 },
    });
    const crop = cropOptions(site.get('square'), Sampling.of(400));
    expect(rulesFor(crop, { sheet }).map((r) => r.name)).toContain('aspect');
  });

  it('skips the snap when the profile snaps to one pixel', () => {
    const site = new ProfileRegistry().add({
      name: 'nosnap',
      extends: 'house',
      crop: { snapTo: 1 },
    });
    const crop = cropOptions(site.get('nosnap'), Sampling.of(400));
    expect(rulesFor(crop, { sheet }).map((r) => r.name)).not.toContain('snap');
  });

  it('anchors a quadrangle to the neatline', () => {
    const rules = rulesFor(cropFor('quadrangles'), { sheet, neatline });
    const out = planWith(rules, { capture, anchor: sheet, sampling: Sampling.of(600) });
    expect(out.rect.left).toBeLessThan(neatline.left);
    expect(out.rect.top).toBeLessThan(neatline.top);
    expect(out.trail[0]).toBe('anchor');
  });

  it('refuses to build a neatline set when there is no neatline', () => {
    expect(() => rulesFor(cropFor('quadrangles'), { sheet })).toThrow(/none was found/);
  });

  it('falls back when it is told it may', () => {
    const rules = rulesFor(cropFor('quadrangles'), { sheet }, { fallBackToSheet: true });
    const out = planWith(rules, { capture, anchor: sheet, sampling: Sampling.of(600) });
    expect(out.warnings.join(' ')).toMatch(/fell back to the paper edge/);
  });

  it('always ends with notEmpty', () => {
    for (const name of registry.names()) {
      const rules = rulesFor(cropFor(name), { sheet, neatline });
      expect(rules.at(-1)?.name).toBe('notEmpty');
    }
  });

  it('never puts the same rule in twice', () => {
    for (const name of registry.names()) {
      expect(() => rulesFor(cropFor(name), { sheet, neatline })).not.toThrow();
    }
  });
});

describe('the bounds mode', () => {
  const wide = Rect.of(3900, 3100, 400, 300);

  it('warns by default', () => {
    const rules = rulesFor(cropFor('house'), { sheet: wide });
    const out = planWith(rules, { capture, anchor: wide, sampling: Sampling.of(400) });
    expect(out.warnings.join(' ')).toMatch(/falls off the capture/);
    expect(out.rect.width).toBeGreaterThan(100);
  });

  it('clamps when asked', () => {
    const rules = rulesFor(cropFor('house'), { sheet: wide }, { boundsMode: 'clamp' });
    const out = planWith(rules, { capture, anchor: wide, sampling: Sampling.of(400) });
    expect(capture.containsRect(out.rect)).toBe(true);
  });

  it('refuses when asked', () => {
    const rules = rulesFor(cropFor('house'), { sheet: wide }, { boundsMode: 'refuse' });
    expect(() => planWith(rules, { capture, anchor: wide, sampling: Sampling.of(400) })).toThrow(
      /will not pad/,
    );
  });

  it('uses a trimmed bleed under the clamp mode', () => {
    const rules = rulesFor(cropFor('atlas-plates'), { sheet: wide }, { boundsMode: 'clamp' });
    const out = planWith(rules, { capture, anchor: wide, sampling: Sampling.of(600) });
    expect(capture.containsRect(out.rect)).toBe(true);
  });
});

describe('the resolution mode', () => {
  it('warns by default and stops when strict', () => {
    const crop = { ...cropFor('house'), minimumPpi: 800 };
    const warn = planWith(rulesFor(crop, { sheet }), {
      capture,
      anchor: sheet,
      sampling: Sampling.of(400),
    });
    expect(warn.warnings.join(' ')).toMatch(/against a floor of 800/);

    const strict = rulesFor(crop, { sheet }, { strictResolution: true });
    expect(() => planWith(strict, { capture, anchor: sheet, sampling: Sampling.of(400) })).toThrow(
      /will not take under 800/,
    );
  });
});

describe('tensionsIn', () => {
  it('names the pairs that are known to argue', () => {
    const site = new ProfileRegistry().add({
      name: 'busy',
      extends: 'quadrangles',
      crop: { aspect: 1.4, safeArea: '2mm' },
    });
    const crop = cropOptions(site.get('busy'), Sampling.of(600));
    const found = tensionsIn(rulesFor(crop, { sheet, neatline }));
    const pairs = found.map((t) => t.between.join('+'));
    expect(pairs).toContain('aspect+snap');
    expect(pairs).toContain('bleed+safeArea');
  });

  it('finds nothing in a plain set', () => {
    expect(tensionsIn(rulesFor(cropFor('house'), { sheet }))).toEqual([]);
  });

  it('has a frozen table', () => {
    expect(Object.isFrozen(KNOWN_TENSIONS)).toBe(true);
  });
});

describe('describeSet', () => {
  it('lists the rules by phase in the order they run', () => {
    const lines = describeSet(rulesFor(cropFor('quadrangles'), { sheet, neatline }));
    expect(lines[0]).toBe('anchor: anchor');
    expect(lines).toContain('grid: snap');
    expect(lines.at(-1)).toBe('check: notEmpty');
  });
});

describe('houseRules', () => {
  it('is the warning set', () => {
    expect(houseRules(cropFor('house'), { sheet }).map((r) => r.name)).toContain('bounds');
  });
});
