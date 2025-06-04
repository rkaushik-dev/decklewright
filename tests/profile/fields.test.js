import { describe, expect, it } from 'vitest';

import { Length } from '../../src/measure/length.js';
import {
  ANCHORS,
  OUTPUT_FORMATS,
  PROFILE_FIELDS,
  parseProfile,
  settingAt,
  settingPaths,
} from '../../src/profile/fields.js';

describe('parseProfile', () => {
  it('reads a bare profile and fills everything else in', () => {
    const profile = parseProfile({ name: 'atlas' });
    expect(profile.name).toBe('atlas');
    expect(profile.title).toBe('untitled collection');
    expect(profile.version).toBe(1);
    expect(profile.crop.anchor).toBe('sheet');
    expect(profile.crop.minimumPpi).toBe(300);
    expect(profile.capture.sigma).toBe(6);
    expect(profile.qc.minimumStars).toBe(3);
    expect(profile.derivatives).toEqual([]);
    expect(profile.naming.pattern).toBe('{accession}_{derivative}');
  });

  it('demands a name', () => {
    expect(() => parseProfile({})).toThrow(/name: is required/);
  });

  it('reads the crop settings a quadrangle collection uses', () => {
    const profile = parseProfile({
      name: 'quads',
      crop: {
        anchor: 'neatline',
        bleed: { bottom: '6mm' },
        safeArea: '2mm',
        minimumPpi: 400,
        snapTo: 8,
        rounding: 'inward',
      },
    });
    expect(profile.crop.anchor).toBe('neatline');
    expect(profile.crop.bleed.bottom).toBeInstanceOf(Length);
    expect(profile.crop.bleed.bottom.value).toBe(6);
    expect(profile.crop.bleed.top.isZero).toBe(true);
    expect(profile.crop.safeArea.left.value).toBe(2);
    expect(profile.crop.snapTo).toBe(8);
    expect(profile.crop.rounding).toBe('inward');
  });

  it('reads a list of derivatives', () => {
    const profile = parseProfile({
      name: 'atlas',
      derivatives: [
        { name: 'master', depth: '16', kernel: 'lanczos3', keepMetadata: true },
        { name: 'service', longestSide: 3000 },
        { name: 'thumb', longestSide: 400, format: 'pnm', layout: 'gray' },
      ],
    });
    expect(profile.derivatives).toHaveLength(3);
    expect(profile.derivatives[0].depth).toBe('16');
    expect(profile.derivatives[0].keepMetadata).toBe(true);
    expect(profile.derivatives[1].format).toBe('png');
    expect(profile.derivatives[2].layout).toBe('gray');
  });

  it('names the path of a bad setting inside a derivative', () => {
    expect(() =>
      parseProfile({ name: 'a', derivatives: [{ name: 'm' }, { name: 'x', format: 'jpeg' }] }),
    ).toThrow(/derivatives\[1\].format: expected one of png, pnm/);
  });

  it('refuses a derivative with no name', () => {
    expect(() => parseProfile({ name: 'a', derivatives: [{}] })).toThrow(
      /derivatives\[0\].name: is required/,
    );
  });

  it('refuses a setting it does not know', () => {
    expect(() => parseProfile({ name: 'a', cropp: {} })).toThrow(/cropp: is not a setting/);
    expect(() => parseProfile({ name: 'a', crop: { bleeding: '1mm' } })).toThrow(
      /crop.bleeding: is not a setting/,
    );
  });

  it('enforces the bounds on the capture settings', () => {
    expect(() => parseProfile({ name: 'a', capture: { sigma: 0 } })).toThrow(
      /capture.sigma: must be at least 1/,
    );
    expect(() => parseProfile({ name: 'a', capture: { speckle: 1.5 } })).toThrow(/whole number/);
  });

  it('reads a tolerance in the quality settings', () => {
    const profile = parseProfile({ name: 'a', qc: { samplingTolerance: '+2/-0' } });
    expect(profile.qc.samplingTolerance.plus).toBe(2);
    expect(profile.qc.samplingTolerance.minus).toBe(0);
  });

  it('leaves an optional setting out when it was not written', () => {
    const profile = parseProfile({ name: 'a' });
    expect(profile.crop.aspect).toBeUndefined();
    expect(profile.extends).toBeUndefined();
  });
});

describe('the tables', () => {
  it('lists the anchors and formats', () => {
    expect([...ANCHORS]).toEqual(['sheet', 'neatline']);
    expect([...OUTPUT_FORMATS]).toEqual(['png', 'pnm']);
    expect(Object.isFrozen(PROFILE_FIELDS)).toBe(true);
  });
});

describe('settingPaths', () => {
  it('walks the whole tree', () => {
    const paths = settingPaths();
    expect(paths).toContain('name');
    expect(paths).toContain('crop');
    expect(paths).toContain('crop.minimumPpi');
    expect(paths).toContain('capture.expectNeatline');
    expect(paths).toContain('qc.minimumStars');
    expect(paths).toContain('naming.pattern');
  });

  it('has no duplicates', () => {
    const paths = settingPaths();
    expect(new Set(paths).size).toBe(paths.length);
  });
});

describe('settingAt', () => {
  const profile = parseProfile({ name: 'atlas', crop: { minimumPpi: 500 } });

  it('reads a dotted path', () => {
    expect(settingAt(profile, 'name')).toBe('atlas');
    expect(settingAt(profile, 'crop.minimumPpi')).toBe(500);
  });

  it('gives nothing back for a path that is not there', () => {
    expect(settingAt(profile, 'crop.nothing')).toBeUndefined();
    expect(settingAt(profile, 'nothing.at.all')).toBeUndefined();
  });

  it('reaches every path the tree lists', () => {
    for (const path of settingPaths()) {
      expect(() => settingAt(profile, path)).not.toThrow();
    }
  });
});
