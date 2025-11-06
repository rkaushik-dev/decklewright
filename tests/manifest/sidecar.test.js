import { describe, expect, it } from 'vitest';

import { deriveOne } from '../../src/derive/oneshot.js';
import { readCapture } from '../../src/detect/capture.js';
import { Rect } from '../../src/geom/rect.js';
import {
  SIDECAR_VERSION,
  describeSidecar,
  objectionsToSidecar,
  parseSidecar,
  qualityOf,
  sidecarFor,
  writeSidecar,
} from '../../src/manifest/sidecar.js';
import { Sampling } from '../../src/measure/resolution.js';
import { planCrop } from '../../src/plan/crop.js';
import { ProfileRegistry } from '../../src/profile/registry.js';
import { runQc } from '../../src/qc/runner.js';
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
 * @returns {Parameters<typeof sidecarFor>[0]}
 */
function parts({ graded = true, degrees = 0, id = 'g4181-1907' } = {}) {
  const surface = accession({ degrees });
  const profile = registry.get('house');
  const reading = readCapture(surface);
  const sampling = Sampling.of(400);
  const { plan } = planCrop({ reading, profile, capture: surface.bounds, sampling });
  const derived = deriveOne({ surface, profile, accession: id, sampling });

  return {
    accession: id,
    profile,
    surface,
    reading,
    plan,
    files: derived.files,
    quality: graded ? runQc({ surface, reading, profile, sampling }) : undefined,
    warnings: derived.warnings,
    created: new Date('2025-11-04T09:15:00Z'),
  };
}

describe('sidecarFor', () => {
  it('names the accession and the profile', () => {
    const out = sidecarFor(parts());
    expect(out.accession).toBe('g4181-1907');
    expect(out.profile).toEqual({ name: 'house', title: 'house style' });
  });

  it('says which version wrote it', () => {
    expect(sidecarFor(parts()).version).toBe(SIDECAR_VERSION);
  });

  it('writes the keys in a fixed order', () => {
    expect(Object.keys(sidecarFor(parts())).slice(0, 4)).toEqual([
      'version',
      'accession',
      'profile',
      'created',
    ]);
  });

  it('records the capture as it arrived', () => {
    const out = sidecarFor(parts());
    expect(out.capture.pixels).toEqual({ width: 300, height: 240 });
    expect(out.capture.layout).toBe('rgb');
    expect(out.capture.target).toBe(true);
  });

  it('records the crop and how it got there', () => {
    const out = sidecarFor(parts());
    expect(out.crop.rect.width).toBeGreaterThan(140);
    expect(out.crop.rules[0]).toBe('start');
    expect(out.crop.ppi).toEqual({ x: 400, y: 400 });
  });

  it('records the turn that was taken out', () => {
    expect(sidecarFor(parts({ degrees: 3 })).crop.deskewDegrees).not.toBe(0);
  });

  it('lists every derivative with its own frequency and digest', () => {
    const out = sidecarFor(parts());
    expect(out.derivatives).toHaveLength(3);
    expect(out.derivatives[0].digests.sha256).toHaveLength(64);
    expect(out.derivatives[2].ppi.x).toBeLessThanOrEqual(out.derivatives[0].ppi.x);
  });

  it('carries the grading when there was one', () => {
    const out = sidecarFor(parts());
    expect(out.quality.stars).toBeGreaterThanOrEqual(0);
    expect(out.quality.checks).toHaveLength(8);
  });

  it('says nothing rather than nulls when there was no grading', () => {
    expect(sidecarFor(parts({ graded: false })).quality).toBeNull();
  });

  it('writes the time it was told, not the time it is', () => {
    expect(sidecarFor(parts()).created).toBe('2025-11-04T09:15:00.000Z');
  });

  it('writes numbers as numbers', () => {
    const out = sidecarFor(parts());
    expect(typeof out.crop.rect.width).toBe('number');
    expect(typeof out.derivatives[0].bytes).toBe('number');
  });
});

describe('qualityOf', () => {
  it('keeps a measurement that is not a number out of the document', () => {
    const result = {
      stars: 0,
      wanted: 3,
      passed: false,
      worst: 'noise',
      missing: [],
      findings: [{ name: 'noise', ran: false, stars: 0, measured: Number.NaN, note: 'no target' }],
    };
    expect(qualityOf(result).checks[0].measured).toBeNull();
  });

  it('copies the missing list rather than sharing it', () => {
    const missing = ['sampling'];
    const out = qualityOf({ stars: 0, wanted: 3, passed: false, missing, findings: [] });
    missing.push('skew');
    expect(out.missing).toEqual(['sampling']);
  });
});

describe('writeSidecar', () => {
  it('ends with a newline', () => {
    expect(writeSidecar(sidecarFor(parts())).endsWith('}\n')).toBe(true);
  });

  it('gives the same bytes for the same run twice', () => {
    expect(writeSidecar(sidecarFor(parts()))).toBe(writeSidecar(sidecarFor(parts())));
  });

  it('puts the keys in order even for a document assembled by hand', () => {
    const text = writeSidecar({ accession: 'a', version: 2 });
    expect(text.indexOf('version')).toBeLessThan(text.indexOf('accession'));
  });
});

describe('parseSidecar', () => {
  it('reads back what it wrote', () => {
    const document = sidecarFor(parts());
    expect(parseSidecar(writeSidecar(document)).accession).toBe('g4181-1907');
  });

  it('refuses text that is not json', () => {
    expect(() => parseSidecar('not json at all')).toThrow(/not even json/);
  });

  it('refuses json that is not an object', () => {
    expect(() => parseSidecar('[1, 2]')).toThrow(/has to be an object/);
  });

  it('refuses a document with no version', () => {
    expect(() => parseSidecar('{"accession": "a"}')).toThrow(/does not say what version/);
  });

  it('refuses a document from a later version rather than half reading it', () => {
    expect(() => parseSidecar('{"version": 99, "accession": "a"}')).toThrow(/later version/);
  });

  it('reads a document from an earlier version', () => {
    expect(parseSidecar('{"version": 1, "accession": "a"}').accession).toBe('a');
  });

  it('refuses a document that names no accession', () => {
    expect(() => parseSidecar('{"version": 2}')).toThrow(/names no accession/);
  });
});

describe('objectionsToSidecar', () => {
  it('has nothing to say about one it wrote itself', () => {
    expect(objectionsToSidecar(sidecarFor(parts()))).toEqual([]);
  });

  it('says so when there are no derivatives', () => {
    expect(objectionsToSidecar({ version: 2, accession: 'a' })[0]).toMatch(
      /no list of derivatives/,
    );
  });

  it('says so when the list is empty', () => {
    const out = objectionsToSidecar({
      derivatives: [],
      crop: { rect: {} },
      capture: { pixels: {} },
    });
    expect(out).toEqual(['the list of derivatives is empty']);
  });

  it('names a derivative with no digest', () => {
    const out = objectionsToSidecar({
      derivatives: [{ name: 'master', file: 'a.png', digests: {} }],
      crop: { rect: {} },
      capture: { pixels: {} },
    });
    expect(out).toEqual(['a.png carries no sha256']);
  });

  it('collects every fault rather than stopping at the first', () => {
    expect(objectionsToSidecar({ derivatives: [{ name: 'master' }] }).length).toBeGreaterThan(2);
  });
});

describe('describeSidecar', () => {
  it('opens with the accession and the profile', () => {
    expect(describeSidecar(sidecarFor(parts()))[0]).toBe('g4181-1907, profile house');
  });

  it('says whether it was graded', () => {
    expect(describeSidecar(sidecarFor(parts({ graded: false })))[1]).toBe('not graded');
    expect(describeSidecar(sidecarFor(parts()))[1]).toMatch(/of 4 stars/);
  });

  it('writes one line per file', () => {
    const lines = describeSidecar(sidecarFor(parts()));
    expect(lines.filter((line) => line.includes('.png'))).toHaveLength(3);
  });
});
