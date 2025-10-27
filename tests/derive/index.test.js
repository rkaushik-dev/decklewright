import { describe, expect, it } from 'vitest';

import { readCapture } from '../../src/detect/capture.js';
import * as derive from '../../src/derive/index.js';
import { Rect } from '../../src/geom/rect.js';
import { Sampling } from '../../src/measure/resolution.js';
import { planCrop } from '../../src/plan/crop.js';
import { ProfileRegistry } from '../../src/profile/registry.js';
import { derivativeRecipes } from '../../src/profile/settings.js';
import { bench, paint } from '../helpers/bench.js';

const registry = new ProfileRegistry();

/**
 * @param {object} [options]
 * @returns {import('../../src/raster/surface.js').Surface}
 */
function accession({ degrees = 0 } = {}) {
  const surface = bench({
    width: 300,
    height: 240,
    degrees,
    halfWidth: 80,
    halfHeight: 56,
    centreX: 170,
    centreY: 130,
    layout: 'rgb',
  });
  for (let i = 0; i < 8; i += 1) {
    paint(surface, Rect.of(14 + i * 10, 12, 10, 14), 20 + Math.round((i / 7) * 210));
  }
  paint(surface, Rect.of(20, 220, 120, 7), 190);
  return surface;
}

/**
 * @param {object} [options]
 * @returns {Parameters<typeof derive.deriveFromPlan>[0]}
 */
function planned({ profileName = 'house', degrees = 0, ppi = 400 } = {}) {
  const surface = accession({ degrees });
  const profile = registry.get(profileName);
  const reading = readCapture(surface);
  const sampling = Sampling.of(ppi);
  const { plan } = planCrop({ reading, profile, capture: surface.bounds, sampling });
  return {
    surface,
    plan,
    profile,
    accession: 'g4181-1907',
    recipes: derivativeRecipes(profile),
  };
}

describe('the barrel', () => {
  it('exports the one call a stage makes', () => {
    expect(typeof derive.deriveFromPlan).toBe('function');
  });

  it('exports each step, so a bad file can be traced to one of them', () => {
    for (const name of ['cutMaster', 'renderDerivative', 'encodeRendered', 'nameFor']) {
      expect(typeof derive[name], name).toBe('function');
    }
  });

  it('still exports the whole job in one call', () => {
    expect(typeof derive.deriveOne).toBe('function');
    expect(typeof derive.deriveMany).toBe('function');
  });

  it('exports the read back helpers, which is how a delivery is checked later', () => {
    expect(typeof derive.frequencyOf).toBe('function');
    expect(typeof derive.textOf).toBe('function');
  });
});

describe('deriveFromPlan', () => {
  it('renders, encodes and names, given a plan somebody else made', () => {
    const out = derive.deriveFromPlan(planned());
    expect(out.files.map((f) => f.fileName)).toEqual([
      'g4181-1907_master.png',
      'g4181-1907_service.png',
      'g4181-1907_thumb.png',
    ]);
  });

  it('does not read the capture again', () => {
    const args = planned();
    const before = args.plan.rect;
    derive.deriveFromPlan(args);
    expect(args.plan.rect).toBe(before);
  });

  it('takes the plan it was given rather than making a better one', () => {
    const args = planned();
    const moved = args.plan.moved(Rect.of(100, 100, 40, 30), { rule: 'test', note: 'by hand' });
    const narrow = { ...args, plan: moved };
    const out = derive.deriveFromPlan(narrow);
    expect(derive.frequencyOf(out.files[0].bytes)?.x).toBeCloseTo(400, 2);
    expect(out.steps.join(' ')).toMatch(/cut 40 by 30/);
  });

  it('adds the set up', () => {
    const out = derive.deriveFromPlan(planned());
    expect(out.bytes).toBe(out.files.reduce((sum, f) => sum + f.bytes.length, 0));
  });

  it('gives the same answer the one call path does', () => {
    const args = planned();
    const one = derive.deriveOne({
      surface: args.surface,
      profile: args.profile,
      accession: args.accession,
      sampling: Sampling.of(400),
    });
    const two = derive.deriveFromPlan(args);
    expect(two.files.map((f) => f.fileName)).toEqual(one.files.map((f) => f.fileName));
    expect(two.bytes).toBe(one.bytes);
  });

  it('refuses a set whose names would collide', () => {
    const args = planned();
    const clashing = {
      ...args,
      profile: { ...args.profile, naming: { pattern: '{accession}', lowercase: true } },
    };
    expect(() => derive.deriveFromPlan(clashing)).toThrow(/differ only in case/);
  });
});

describe('preservationCopy', () => {
  it('picks the master out of the set', () => {
    const out = derive.deriveFromPlan(planned());
    expect(derive.preservationCopy(out.files).name).toBe('master');
  });

  it('falls back to the first file when nothing is called master', () => {
    const files = [{ name: 'archive' }, { name: 'access' }];
    expect(derive.preservationCopy(/** @type {any} */ (files)).name).toBe('archive');
  });

  it('refuses an empty set rather than handing back nothing', () => {
    expect(() => derive.preservationCopy([])).toThrow(/nothing was derived/);
  });
});
