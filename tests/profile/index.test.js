import { describe, expect, it } from 'vitest';

import * as profiles from '../../src/profile/index.js';
import { readCapture } from '../../src/detect/capture.js';
import { Rect } from '../../src/geom/rect.js';
import { bench, paint } from '../helpers/bench.js';

/**
 * @returns {import('../../src/raster/surface.js').Surface}
 */
function accession() {
  const surface = bench({
    width: 260,
    height: 220,
    degrees: 3,
    halfWidth: 70,
    halfHeight: 50,
    centreX: 140,
    centreY: 110,
  });
  for (let i = 0; i < 8; i += 1) {
    paint(surface, Rect.of(14 + i * 10, 12, 10, 14), 20 + Math.round((i / 7) * 210));
  }
  paint(surface, Rect.of(20, 200, 120, 7), 190);
  return surface;
}

describe('the barrel', () => {
  it('exports the registry and the one call the cli makes', () => {
    expect(typeof profiles.ProfileRegistry).toBe('function');
    expect(typeof profiles.loadProfile).toBe('function');
    expect(typeof profiles.loadAll).toBe('function');
  });

  it('exports no undefined bindings', () => {
    for (const [name, value] of Object.entries(profiles)) {
      expect(value, name).toBeDefined();
    }
  });
});

describe('loadProfile', () => {
  it('takes an old document all the way to stage settings', () => {
    const written = {
      name: 'plat-books',
      extends: 'atlas-plates',
      crop: { margin: '4mm', roundTo: 4 },
      qc: { dpi: 600 },
    };
    const profile = profiles.loadProfile(written);
    expect(profile.version).toBe(2);
    expect(profile.crop.bleed.top.value).toBe(4);
    expect(profile.crop.snapTo).toBe(4);
    expect(profile.qc.samplingPpi).toBe(600);
    expect(profile.derivatives).toHaveLength(3);
  });

  it('refuses a document that cannot be used and says why', () => {
    expect(() =>
      profiles.loadProfile({ name: 'bad', extends: 'house', crop: { anchor: 'neatline' } }),
    ).toThrow(/the bad profile cannot be used/);
  });

  it('takes a registry to look the parents up in', () => {
    const registry = profiles.registryWith([
      { name: 'site', extends: 'house', crop: { minimumPpi: 200 } },
    ]);
    const profile = profiles.loadProfile({ name: 'child', extends: 'site' }, { registry });
    expect(profile.crop.minimumPpi).toBe(200);
    expect(registry.names()).toContain('child');
  });
});

describe('loadAll', () => {
  it('registers a folder of documents in one go', () => {
    const registry = profiles.loadAll([
      { name: 'a', extends: 'house' },
      { name: 'b', extends: 'a', crop: { margin: '1mm' } },
    ]);
    expect(registry.get('b').crop.bleed.top.value).toBe(1);
  });

  it('leaves the shipped profiles in place', () => {
    const registry = profiles.loadAll([{ name: 'a' }]);
    expect(registry.names()).toContain('quadrangles');
  });
});

describe('a profile driving a real capture', () => {
  it('hands the detection stage its arguments', () => {
    const profile = profiles.loadProfile({
      name: 'bench-test',
      extends: 'house',
      capture: { sigma: 4, claimedPpi: 20 },
    });
    const options = profiles.captureOptions(profile);
    const reading = readCapture(accession(), options);
    expect(Math.abs(reading.sheet.skew.degrees - 3)).toBeLessThan(1);
    expect(reading.scaleBar).not.toBeNull();
    expect(reading.warnings.join(' ')).not.toMatch(/against a claimed/);
  });

  it('turns a millimetre bleed into the pixels the planner will use', () => {
    const profile = profiles.loadProfile({
      name: 'bleedy',
      extends: 'house',
      capture: { claimedPpi: 254 },
      crop: { bleed: '10mm' },
    });
    const settings = profiles.settingsFor(profile);
    expect(settings.crop.bleed.top).toBeCloseTo(100, 6);
  });

  it('says the same thing through the report as through the settings', () => {
    const profile = profiles.loadProfile({
      name: 'reported',
      extends: 'quadrangles',
    });
    const lines = profiles.report(profile);
    const settings = profiles.settingsFor(profile);
    expect(lines.join('\n')).toMatch(new RegExp(`bleed .*${Math.round(settings.crop.bleed.top)}`));
  });
});
