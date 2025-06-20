import { describe, expect, it } from 'vitest';

import { Sampling } from '../../src/measure/resolution.js';
import { ProfileRegistry } from '../../src/profile/registry.js';
import {
  captureOptions,
  claimedSampling,
  cropOptions,
  derivativeRecipes,
  edgesInPixels,
  fillName,
  qcLimits,
  settingsFor,
} from '../../src/profile/settings.js';

const registry = new ProfileRegistry();
const house = registry.get('house');
const quads = registry.get('quadrangles');

describe('claimedSampling', () => {
  it('reads what the scanner was set to', () => {
    expect(claimedSampling(house).x).toBe(400);
    expect(claimedSampling(quads).x).toBe(600);
  });
});

describe('captureOptions', () => {
  it('hands the detection stage what it takes', () => {
    const options = captureOptions(quads);
    expect(options.sigma).toBe(6);
    expect(options.speckle).toBe(2);
    expect(options.gap).toBe(3);
    expect(options.expectNeatline).toBe(true);
    expect(options.barLength).toBe(6);
    expect(options.barUnit).toBe('in');
    expect(options.claimed.x).toBe(600);
  });

  it('carries a collection that changed the defaults', () => {
    const rolled = captureOptions(registry.get('rolled-drawings'));
    expect(rolled.sigma).toBe(4);
    expect(rolled.gap).toBe(6);
    expect(rolled.barLength).toBe(12);
  });
});

describe('edgesInPixels', () => {
  it('turns millimetres into pixels at the frequency', () => {
    const insets = edgesInPixels(quads.crop.bleed, Sampling.of(254));
    expect(insets.top).toBeCloseTo(30, 6);
    expect(insets.bottom).toBeCloseTo(380, 6);
  });

  it('uses each axis of a non square frequency', () => {
    const insets = edgesInPixels(quads.crop.bleed, new Sampling(254, 508));
    expect(insets.left).toBeCloseTo(30, 6);
    expect(insets.top).toBeCloseTo(60, 6);
  });

  it('is zero for a profile with no bleed', () => {
    expect(edgesInPixels(house.crop.bleed, Sampling.of(400)).isZero).toBe(true);
  });
});

describe('cropOptions', () => {
  it('gives the planner pixels and nothing else', () => {
    const options = cropOptions(quads, Sampling.of(600));
    expect(options.anchor).toBe('neatline');
    expect(options.bleed.bottom).toBeCloseTo((38 / 25.4) * 600, 6);
    expect(options.snapTo).toBe(8);
    expect(options.rounding).toBe('outward');
    expect(options.deskew).toBe(true);
  });

  it('leaves an aspect out when the profile did not set one', () => {
    expect(cropOptions(house, Sampling.of(400)).aspect).toBeUndefined();
  });

  it('carries an aspect the profile did set', () => {
    const site = new ProfileRegistry().add({
      name: 'square',
      extends: 'house',
      crop: { aspect: 1, aspectDirection: 'shrink' },
    });
    const options = cropOptions(site.get('square'), Sampling.of(400));
    expect(options.aspect).toBe(1);
    expect(options.aspectDirection).toBe('shrink');
  });
});

describe('derivativeRecipes', () => {
  it('turns the written depth into a number', () => {
    const recipes = derivativeRecipes(house);
    expect(recipes.map((r) => r.name)).toEqual(['master', 'service', 'thumb']);
    expect(recipes[0].depth).toBe(16);
    expect(recipes[1].depth).toBe(8);
  });

  it('carries the size instruction through, whichever it was', () => {
    const recipes = derivativeRecipes(house);
    expect(recipes[0].longestSide).toBeUndefined();
    expect(recipes[1].longestSide).toBe(3000);
    expect(recipes[0].scale).toBeUndefined();
  });

  it('gives an empty list for a profile that writes nothing', () => {
    const site = new ProfileRegistry().add({ name: 'quiet', extends: 'house', derivatives: [] });
    expect(derivativeRecipes(site.get('quiet'))).toEqual([]);
  });
});

describe('qcLimits', () => {
  it('reads the funder numbers off the profile', () => {
    const limits = qcLimits(quads);
    expect(limits.sampling.x).toBe(600);
    expect(limits.skewTolerance).toBe(0.15);
    expect(limits.minimumStars).toBe(4);
    expect(limits.maximumClipping).toBe(0.001);
    expect(limits.samplingTolerance.kind).toBe('relative');
  });
});

describe('fillName', () => {
  it('fills the placeholders', () => {
    expect(fillName(house, { accession: 'MS2019-004-017', derivative: 'service' })).toBe(
      'ms2019-004-017_service',
    );
  });

  it('leaves a placeholder it does not have a value for', () => {
    expect(fillName(house, { derivative: 'service' })).toBe('{accession}_service');
  });

  it('keeps the case when the profile says to', () => {
    const site = new ProfileRegistry().add({
      name: 'cased',
      extends: 'house',
      naming: { lowercase: false },
    });
    expect(fillName(site.get('cased'), { accession: 'MS2019', derivative: 'M' })).toBe('MS2019_M');
  });

  it('takes a pattern the profile set', () => {
    const site = new ProfileRegistry().add({
      name: 'other',
      extends: 'house',
      naming: { pattern: '{collection}/{accession}-{derivative}' },
    });
    expect(
      fillName(site.get('other'), { collection: 'Quads', accession: 'A1', derivative: 'thumb' }),
    ).toBe('quads/a1-thumb');
  });
});

describe('settingsFor', () => {
  it('bundles every stage together', () => {
    const settings = settingsFor(quads);
    expect(settings.profile).toBe(quads);
    expect(settings.sampling.x).toBe(600);
    expect(settings.capture.expectNeatline).toBe(true);
    expect(settings.crop.anchor).toBe('neatline');
    expect(settings.derivatives).toHaveLength(3);
    expect(settings.qc.minimumStars).toBe(4);
  });

  it('uses a measured frequency over the claimed one when given', () => {
    const settings = settingsFor(quads, Sampling.of(300));
    expect(settings.sampling.x).toBe(300);
    expect(settings.crop.bleed.bottom).toBeCloseTo((38 / 25.4) * 300, 6);
  });
});
