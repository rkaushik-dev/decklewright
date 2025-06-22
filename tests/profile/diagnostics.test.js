import { describe, expect, it } from 'vitest';

import {
  describeDerivative,
  describeInPixels,
  describeProfile,
  report,
} from '../../src/profile/diagnostics.js';
import { ProfileRegistry } from '../../src/profile/registry.js';

const registry = new ProfileRegistry();
const house = registry.get('house');
const quads = registry.get('quadrangles');

describe('describeProfile', () => {
  it('opens with the name and the title', () => {
    expect(describeProfile(house)[0]).toBe('house: house style (version 2)');
  });

  it('says what the crop is anchored to', () => {
    expect(describeProfile(house).join('\n')).toMatch(/crops to the sheet, straightened first/);
    expect(describeProfile(quads).join('\n')).toMatch(/crops to the neatline/);
  });

  it('says nothing about a bleed a profile does not have', () => {
    expect(describeProfile(house).join('\n')).not.toMatch(/keeps/);
  });

  it('spells out an uneven bleed side by side', () => {
    const line = describeProfile(quads).find((l) => l.startsWith('keeps'));
    expect(line).toMatch(/top 3.00 mm/);
    expect(line).toMatch(/bottom 38.00 mm/);
  });

  it('shortens an even bleed to one figure', () => {
    const plates = registry.get('atlas-plates');
    expect(describeProfile(plates).find((l) => l.startsWith('keeps'))).toBe(
      'keeps 2.00 mm beyond it',
    );
  });

  it('lists every derivative', () => {
    const lines = describeProfile(house);
    expect(lines.some((l) => l.startsWith('master:'))).toBe(true);
    expect(lines.some((l) => l.startsWith('service:'))).toBe(true);
    expect(lines.some((l) => l.startsWith('thumb:'))).toBe(true);
  });

  it('says so when a profile writes nothing', () => {
    const quiet = new ProfileRegistry().add({ name: 'quiet', extends: 'house', derivatives: [] });
    expect(describeProfile(quiet.get('quiet'))).toContain('writes nothing');
  });

  it('mentions a safe area and an aspect when they are set', () => {
    const site = new ProfileRegistry().add({
      name: 'framed',
      extends: 'house',
      crop: { safeArea: '4mm', aspect: 1.5, aspectDirection: 'shrink' },
    });
    const lines = describeProfile(site.get('framed')).join('\n');
    expect(lines).toMatch(/holds a safe area of 4.00 mm/);
    expect(lines).toMatch(/forces an aspect of 1.5, shrinking/);
  });

  it('mentions a neatline hunt only when the capture does one', () => {
    expect(describeProfile(quads)).toContain('looks for a neatline');
    expect(describeProfile(house)).not.toContain('looks for a neatline');
  });

  it('ends with the quality bar', () => {
    const last = describeProfile(quads).at(-1);
    expect(last).toMatch(/^quality: 600 ppi \+\/-1%, skew under 0.15 deg, 4 stars$/);
  });
});

describe('describeDerivative', () => {
  it('says full size when neither a side nor a scale is set', () => {
    expect(describeDerivative(house.derivatives[0])).toBe(
      'master: full size, rgb at 16 bit, png by lanczos3, keeping metadata',
    );
  });

  it('says the longest side when one is set', () => {
    expect(describeDerivative(house.derivatives[1])).toMatch(/longest side 3000 px/);
  });

  it('says the scale when one is set', () => {
    const site = new ProfileRegistry().add({
      name: 'half',
      extends: 'house',
      derivatives: [{ name: 'half', scale: 0.5 }],
    });
    expect(describeDerivative(site.get('half').derivatives[0])).toMatch(/scaled to 0.5/);
  });
});

describe('describeInPixels', () => {
  it('works the bleed out at the claimed frequency', () => {
    const lines = describeInPixels(quads);
    expect(lines[0]).toBe('at 600 ppi');
    expect(lines[1]).toBe('bleed 71/71/898/71 px');
  });

  it('works it out at another frequency when given one', () => {
    const lines = describeInPixels(quads, 300);
    expect(lines[0]).toBe('at 300 ppi');
    expect(lines[1]).toBe('bleed 35/35/449/35 px');
  });

  it('reports zero for a profile with no bleed', () => {
    expect(describeInPixels(house)[1]).toBe('bleed 0/0/0/0 px');
  });

  it('repeats the snap settings, which is what a hairline problem usually is', () => {
    expect(describeInPixels(quads).at(-1)).toBe('snap to 8 px up');
  });
});

describe('report', () => {
  it('puts everything together', () => {
    const lines = report(quads);
    expect(lines[0]).toMatch(/^quadrangles:/);
    expect(lines.join('\n')).toMatch(/bleed 71/);
  });

  it('adds the problems at the end when there are any', () => {
    const noisy = new ProfileRegistry().add({
      name: 'noisy',
      extends: 'house',
      capture: { expectNeatline: true },
    });
    expect(report(noisy.get('noisy')).at(-1)).toMatch(/^note: capture.expectNeatline:/);
  });

  it('says nothing extra about a clean profile', () => {
    expect(report(house).some((line) => line.startsWith('fault:'))).toBe(false);
  });
});
