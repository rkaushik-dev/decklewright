import { describe, expect, it } from 'vitest';

import { readCapture } from '../../src/detect/capture.js';
import { Rect } from '../../src/geom/rect.js';
import { Sampling } from '../../src/measure/resolution.js';
import { ProfileRegistry } from '../../src/profile/registry.js';
import {
  REQUIRED,
  contextFor,
  runBatch,
  runQc,
  standardChecks,
  withoutChecks,
} from '../../src/qc/runner.js';
import { oneLine } from '../../src/qc/report.js';
import { bench, paint } from '../helpers/bench.js';

const registry = new ProfileRegistry();

/**
 * @param {object} [options]
 * @param {number} [options.degrees]
 * @param {boolean} [options.wedge]
 * @returns {import('../../src/raster/surface.js').Surface}
 */
function accession({ degrees = 0, wedge = true } = {}) {
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
  if (wedge) {
    for (let i = 0; i < 8; i += 1) {
      const tone = 20 + Math.round((i / 7) * 210);
      paint(surface, Rect.of(14 + i * 10, 12, 10, 14), tone);
    }
  }
  paint(surface, Rect.of(20, 220, 120, 7), 190);
  return surface;
}

/**
 * @param {object} [options]
 * @returns {Parameters<typeof runQc>[0]}
 */
function args({ profile = 'house', degrees = 0, wedge = true, ppi = 400 } = {}) {
  const surface = accession({ degrees, wedge });
  const reading = readCapture(surface, { sigma: 4 });
  return {
    surface,
    reading,
    profile: registry.get(profile),
    sampling: Sampling.of(ppi),
  };
}

describe('standardChecks', () => {
  it('holds every check the lab runs, once each', () => {
    const names = standardChecks().map((c) => c.name);
    expect(names).toEqual([
      'sampling',
      'skew',
      'skewAgreement',
      'illumination',
      'grayBalance',
      'noise',
      'clipping',
      'sharpness',
    ]);
    expect(new Set(names).size).toBe(names.length);
  });

  it('names the three a delivery cannot be graded without', () => {
    expect([...REQUIRED]).toEqual(['sampling', 'skew', 'clipping']);
  });
});

describe('contextFor', () => {
  it('gathers what the checks ask for', () => {
    const context = contextFor(args());
    expect(context.surface).toBeDefined();
    expect(context.sheet.width).toBeGreaterThan(100);
    expect(context.skew).toBeDefined();
    expect(context.limits.sampling.x).toBe(400);
    expect(context.spread).toBeGreaterThanOrEqual(0);
  });

  it('takes a measured frequency over the one the reading carried', () => {
    expect(contextFor(args({ ppi: 800 })).sampling.x).toBe(800);
  });

  it('carries a null frequency rather than inventing one', () => {
    const base = args();
    const context = contextFor({ ...base, sampling: undefined });
    expect(context.sampling).toBeNull();
  });
});

describe('runQc', () => {
  it('runs every check and reports a level', () => {
    const out = runQc(args());
    expect(out.findings).toHaveLength(8);
    expect(out.stars).toBeGreaterThanOrEqual(0);
    expect(out.wanted).toBe(3);
  });

  it('passes a clean capture', () => {
    const out = runQc(args());
    expect(out.missing).toEqual([]);
    expect(out.passed).toBe(true);
  });

  it('fails a capture that is well off square', () => {
    const out = runQc(args({ degrees: 4 }));
    expect(out.passed).toBe(false);
    expect(out.worst).toBe('skew');
  });

  it('does not grade a capture with no frequency', () => {
    const base = args();
    const out = runQc({ ...base, sampling: undefined });
    expect(out.missing).toContain('sampling');
    expect(out.stars).toBe(0);
  });

  it('still grades a capture with no colour target', () => {
    const out = runQc(args({ wedge: false }));
    expect(out.missing).toEqual([]);
    expect(out.findings.find((f) => f.name === 'grayBalance')?.ran).toBe(false);
  });

  it('measures against the level the profile asked for', () => {
    const strict = runQc(args({ profile: 'quadrangles', ppi: 600 }));
    expect(strict.wanted).toBe(4);
  });

  it('takes a set of checks of its own', () => {
    const out = runQc(args(), { checks: withoutChecks(standardChecks(), ['sharpness', 'noise']) });
    expect(out.findings).toHaveLength(6);
  });

  it('refuses a set with the same check twice', () => {
    const twice = [...standardChecks(), standardChecks()[0]];
    expect(() => runQc(args(), { checks: twice })).toThrow(/is in the set twice/);
  });

  it('takes another list of required checks', () => {
    const base = args();
    const out = runQc({ ...base, sampling: undefined }, { required: ['skew'] });
    expect(out.missing).toEqual([]);
  });
});

describe('runBatch', () => {
  it('runs the set over several items', () => {
    const out = runBatch([args(), args({ degrees: 4 })]);
    expect(out.results).toHaveLength(2);
    expect(out.passed).toBe(1);
    expect(out.failed).toBe(1);
  });

  it('grades the batch at its worst item', () => {
    const out = runBatch([args(), args({ degrees: 4 })]);
    expect(out.stars).toBe(Math.min(...out.results.map((r) => r.stars)));
  });

  it('grades an empty batch at nothing', () => {
    expect(runBatch([]).stars).toBe(0);
  });
});

describe('the report reads off the same findings', () => {
  it('says the same level the runner did', () => {
    const out = runQc(args({ degrees: 4 }));
    expect(oneLine(out.findings, { wanted: out.wanted, item: 'q17' })).toMatch(/held down by skew/);
  });
});
