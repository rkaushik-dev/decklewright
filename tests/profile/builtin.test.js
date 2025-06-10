import { describe, expect, it } from 'vitest';

import {
  ATLAS_PLATES,
  BUILT_IN,
  HOUSE,
  MICROFILM,
  QUADRANGLES,
  ROLLED_DRAWINGS,
  builtInNamed,
  builtInNames,
} from '../../src/profile/builtin.js';
import { parseProfile } from '../../src/profile/fields.js';
import { resolveExtends } from '../../src/profile/overlay.js';
import { faultsOnly, inspectProfile } from '../../src/profile/validate.js';

/**
 * @param {Record<string, unknown>} document
 * @returns {Record<string, any>}
 */
function resolved(document) {
  return parseProfile(resolveExtends(document, builtInNamed).document);
}

describe('the shipped profiles', () => {
  it('lists five of them, house first', () => {
    expect(builtInNames()).toEqual([
      'house',
      'atlas-plates',
      'quadrangles',
      'rolled-drawings',
      'microfilm',
    ]);
    expect(BUILT_IN[0]).toBe(HOUSE);
  });

  it('is frozen so a caller cannot edit the shipped settings', () => {
    expect(Object.isFrozen(BUILT_IN)).toBe(true);
    for (const profile of BUILT_IN) expect(Object.isFrozen(profile)).toBe(true);
  });

  it('finds one by name and nothing for a name it does not have', () => {
    expect(builtInNamed('quadrangles')).toBe(QUADRANGLES);
    expect(builtInNamed('plat-books')).toBeUndefined();
  });

  it.each(BUILT_IN.map((profile) => [profile.name, profile]))('%s parses', (_name, document) => {
    expect(() => resolved(document)).not.toThrow();
  });

  it.each(BUILT_IN.map((profile) => [profile.name, profile]))(
    '%s has no faults',
    (_name, document) => {
      expect(faultsOnly(inspectProfile(resolved(document)))).toEqual([]);
    },
  );

  it('every profile but house extends house', () => {
    for (const profile of BUILT_IN.slice(1)) {
      expect(resolveExtends(profile, builtInNamed).chain).toEqual(['house']);
    }
  });
});

describe('the house style', () => {
  const profile = resolved(HOUSE);

  it('crops to the paper edge with no bleed', () => {
    expect(profile.crop.anchor).toBe('sheet');
    expect(profile.crop.bleed.top.isZero).toBe(true);
  });

  it('writes three derivatives', () => {
    expect(profile.derivatives.map((d) => d.name)).toEqual(['master', 'service', 'thumb']);
    expect(profile.derivatives[0].depth).toBe('16');
    expect(profile.derivatives[2].longestSide).toBe(400);
  });
});

describe('atlas plates', () => {
  const profile = resolved(ATLAS_PLATES);

  it('inherits the derivatives and changes only what it says', () => {
    expect(profile.derivatives).toHaveLength(3);
    expect(profile.crop.bleed.top.value).toBe(2);
    expect(profile.crop.minimumPpi).toBe(400);
    expect(profile.qc.samplingPpi).toBe(600);
  });

  it('keeps the house naming', () => {
    expect(profile.naming.pattern).toBe('{accession}_{derivative}');
  });
});

describe('quadrangles', () => {
  const profile = resolved(QUADRANGLES);

  it('crops to the neatline and keeps the legend collar', () => {
    expect(profile.crop.anchor).toBe('neatline');
    expect(profile.capture.expectNeatline).toBe(true);
    expect(profile.crop.bleed.bottom.value).toBe(38);
    expect(profile.crop.bleed.top.value).toBe(3);
  });

  it('asks for a tighter capture than the house does', () => {
    expect(profile.qc.skewTolerance).toBeLessThan(resolved(HOUSE).qc.skewTolerance);
    expect(profile.qc.minimumStars).toBe(4);
  });
});

describe('rolled drawings', () => {
  const profile = resolved(ROLLED_DRAWINGS);

  it('replaces the derivative list rather than adding to it', () => {
    expect(profile.derivatives.map((d) => d.name)).toEqual(['master', 'service']);
  });

  it('is more forgiving about skew and noise', () => {
    expect(profile.qc.skewTolerance).toBeGreaterThan(resolved(HOUSE).qc.skewTolerance);
    expect(profile.qc.minimumSnr).toBeLessThan(resolved(HOUSE).qc.minimumSnr);
  });

  it('uses the longer rule', () => {
    expect(profile.capture.barLength.value).toBe(12);
  });
});

describe('microfilm', () => {
  const profile = resolved(MICROFILM);

  it('writes grayscale everywhere', () => {
    expect(profile.derivatives.every((d) => d.layout === 'gray')).toBe(true);
  });

  it('turns the gray balance check off by opening it all the way', () => {
    expect(profile.qc.grayBalanceTolerance).toBe(255);
  });
});
