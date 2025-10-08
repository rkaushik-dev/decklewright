import { describe, expect, it } from 'vitest';

import { readCapture } from '../../src/detect/capture.js';
import { Rect } from '../../src/geom/rect.js';
import { Sampling } from '../../src/measure/resolution.js';
import { ProfileRegistry } from '../../src/profile/registry.js';
import * as qc from '../../src/qc/index.js';
import { bench, paint } from '../helpers/bench.js';

const registry = new ProfileRegistry();

/**
 * A capture off the stand, with the wedge and the scale bar the lab lays
 * beside every sheet.
 *
 * @param {object} [options]
 * @param {number} [options.degrees]
 * @param {number} [options.tint] how much red to add to the whole frame
 * @param {number} [options.falloff] how much darker the far side of the bed is
 * @returns {import('../../src/raster/surface.js').Surface}
 */
function accession({ degrees = 0, tint = 0, falloff = 0 } = {}) {
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
    const tone = 20 + Math.round((i / 7) * 210);
    paint(surface, Rect.of(14 + i * 10, 12, 10, 14), tone);
  }
  paint(surface, Rect.of(20, 220, 120, 7), 190);

  if (tint > 0) {
    for (let y = 0; y < surface.height; y += 1) {
      for (let x = 0; x < surface.width; x += 1) {
        surface.set(x, y, 0, Math.min(255, surface.get(x, y, 0) + tint));
      }
    }
  }
  if (falloff > 0) {
    for (let y = 0; y < surface.height; y += 1) {
      for (let x = 0; x < surface.width; x += 1) {
        const share = 1 - (x / (surface.width - 1)) * falloff;
        for (let c = 0; c < 3; c += 1) {
          surface.set(x, y, c, Math.round(surface.get(x, y, c) * share));
        }
      }
    }
  }
  return surface;
}

/**
 * @param {object} [options]
 * @returns {Parameters<typeof qc.runQc>[0]}
 */
function args({ profile = 'house', degrees = 0, tint = 0, falloff = 0, ppi = 400 } = {}) {
  const surface = accession({ degrees, tint, falloff });
  return {
    surface,
    reading: readCapture(surface, { sigma: 4 }),
    profile: registry.get(profile),
    sampling: Sampling.of(ppi),
  };
}

describe('the barrel', () => {
  it('exports the one call a stage makes', () => {
    expect(typeof qc.gradeCapture).toBe('function');
  });

  it('exports every check, so a lab can run one on its own', () => {
    for (const name of [
      'samplingCheck',
      'samplingFloorCheck',
      'skewCheck',
      'skewAgreementCheck',
      'illuminationCheck',
      'grayBalanceCheck',
      'noiseCheck',
      'clippingCheck',
      'sharpnessCheck',
    ]) {
      expect(typeof qc[name], name).toBe('function');
    }
  });

  it('exports every ladder, because the ladders are the documentation', () => {
    for (const name of [
      'SAMPLING_LADDER',
      'SKEW_LADDER',
      'ILLUMINATION_LADDER',
      'GRAY_BALANCE_LADDER',
      'NOISE_LADDER',
      'CLIPPING_LADDER',
      'SHARPNESS_LADDER',
    ]) {
      expect(Array.isArray(qc[name]), name).toBe(true);
      expect(Object.isFrozen(qc[name]), name).toBe(true);
    }
  });

  it('exports the sampler the checks share', () => {
    expect(typeof qc.patchView).toBe('function');
    expect(qc.PATCH_MARGIN).toBeGreaterThan(0);
  });

  it('does not leak a check under two names', () => {
    const names = qc.standardChecks().map((c) => c.name);
    expect(new Set(names).size).toBe(names.length);
  });
});

describe('gradeCapture', () => {
  it('grades and advises in one call', () => {
    const out = qc.gradeCapture(args());
    expect(out.findings).toHaveLength(8);
    expect(out.passed).toBe(true);
    expect(out.advice).toEqual([]);
  });

  it('says what to change when a check falls short', () => {
    const out = qc.gradeCapture(args({ degrees: 4 }));
    expect(out.passed).toBe(false);
    expect(out.advice.join(' ')).toMatch(/square the sheet/);
  });

  it('says which side is dark, not only that the lighting is uneven', () => {
    const out = qc.gradeCapture(args({ falloff: 0.5 }));
    expect(out.advice.join(' ')).toMatch(/move a lamp/);
    expect(out.advice.join(' ')).toMatch(/dark side/);
  });

  it('says which way a cast goes', () => {
    const out = qc.gradeCapture(args({ tint: 30 }));
    expect(out.advice.join(' ')).toMatch(/the cast is towards red/);
  });

  it('advises nothing about a check that could not run', () => {
    const base = args();
    const out = qc.gradeCapture({ ...base, sampling: undefined });
    expect(out.missing).toContain('sampling');
    expect(out.advice.join(' ')).not.toMatch(/dark side/);
  });

  it('takes the same options runQc does', () => {
    const out = qc.gradeCapture(args(), {
      checks: qc.withoutChecks(qc.standardChecks(), ['sharpness']),
    });
    expect(out.findings).toHaveLength(7);
  });
});

describe('reportOn', () => {
  it('opens with the item and the level', () => {
    expect(qc.reportOn(args(), { item: 'q17' })[0]).toMatch(/^q17: /);
  });

  it('puts the advice under the findings', () => {
    const lines = qc.reportOn(args({ degrees: 4 }), { item: 'q17' });
    const findings = lines.filter((line) => /: (\*|not measured)/.test(line));
    const advice = lines.slice(lines.indexOf(findings[findings.length - 1]) + 1);
    expect(advice.join(' ')).toMatch(/square the sheet/);
  });

  it('says nothing about remedies when there is nothing to remedy', () => {
    expect(qc.reportOn(args()).join('\n')).not.toMatch(/look at the capture/);
  });
});

describe('gradingToJSON', () => {
  it('carries the level, the measurements and the advice', () => {
    const out = qc.gradingToJSON(args({ degrees: 4 }), { item: 'q17' });
    expect(out.item).toBe('q17');
    expect(out.passed).toBe(false);
    expect(Object.keys(/** @type {any} */ (out.measurements))).toHaveLength(8);
    expect(/** @type {string[]} */ (out.advice).length).toBeGreaterThan(0);
  });

  it('names the required checks that could not be run', () => {
    const base = args();
    const out = qc.gradingToJSON({ ...base, sampling: undefined });
    expect(out.missing).toEqual(['sampling']);
    expect(out.stars).toBe(0);
    expect(out.level).toBe(qc.LEVEL_NAMES[0]);
    expect(out.passed).toBe(false);
  });

  it('is plain data, so it can go straight into a sidecar', () => {
    const out = qc.gradingToJSON(args());
    expect(JSON.parse(JSON.stringify(out))).toEqual(out);
  });
});
