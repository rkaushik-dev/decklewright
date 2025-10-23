import { describe, expect, it } from 'vitest';

import { readImage } from '../../src/codec/registry.js';
import { frequencyOf, textOf } from '../../src/derive/recipe.js';
import { deriveMany, deriveOne, describeDerived } from '../../src/derive/oneshot.js';
import { Rect } from '../../src/geom/rect.js';
import { Sampling } from '../../src/measure/resolution.js';
import { ProfileRegistry } from '../../src/profile/registry.js';
import { Surface } from '../../src/raster/surface.js';
import { bench, paint } from '../helpers/bench.js';

const registry = new ProfileRegistry();

/**
 * @param {object} [options]
 * @returns {Surface}
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
 * @returns {Parameters<typeof deriveOne>[0]}
 */
function args({ profile = 'house', degrees = 0, id = 'g4181-1907', ppi = 400 } = {}) {
  return {
    surface: accession({ degrees }),
    profile: registry.get(profile),
    accession: id,
    sampling: Sampling.of(ppi),
  };
}

/**
 * Two sheets overlapping in an L, which is what a folded plan looks like from
 * above. The detector finds one object and fits a quad round it that fits
 * badly, so it says so rather than pretending.
 */
function twoSheets() {
  const surface = Surface.create({ width: 300, height: 240, layout: 'rgb', fill: 38 });
  paint(surface, Rect.of(60, 60, 160, 110), [214, 214, 214]);
  paint(surface, Rect.of(60, 60, 100, 170), [214, 214, 214]);
  return surface;
}

describe('deriveOne', () => {
  it('gives back one file per derivative the profile asked for', () => {
    const out = deriveOne(args());
    expect(out.files.map((f) => f.name)).toEqual(['master', 'service', 'thumb']);
  });

  it('names each file under the profile pattern', () => {
    const out = deriveOne(args());
    expect(out.files.map((f) => f.fileName)).toEqual([
      'g4181-1907_master.png',
      'g4181-1907_service.png',
      'g4181-1907_thumb.png',
    ]);
  });

  it('hands back bytes that read back as an image', () => {
    const out = deriveOne(args());
    const master = readImage(out.files[0].bytes);
    expect(master.width).toBeGreaterThan(100);
    expect(master.depth).toBe(16);
  });

  it('cuts the sheet out rather than the whole bed', () => {
    const out = deriveOne(args());
    expect(out.plan.rect.width).toBeLessThan(200);
    expect(out.plan.rect.width).toBeGreaterThan(140);
  });

  it('carries the frequency into the file it wrote', () => {
    const out = deriveOne(args({ ppi: 600 }));
    expect(frequencyOf(out.files[0].bytes)?.x).toBeCloseTo(600, 0);
  });

  it('scales the frequency down for the smaller copies', () => {
    const out = deriveOne(args({ ppi: 600 }));
    const thumb = out.files[out.files.length - 1];
    expect(frequencyOf(thumb.bytes)?.x).toBeLessThan(600);
  });

  it('writes the accession into the file', () => {
    const out = deriveOne(args({ id: 'g4181-1907' }));
    expect(textOf(out.files[0].bytes).Source).toBe('g4181-1907');
  });

  it('takes the turn out of a sheet that sat off square', () => {
    const out = deriveOne(args({ degrees: 3 }));
    expect(Math.abs(out.plan.deskew.degrees)).toBeGreaterThan(1);
    expect(out.steps.join(' ')).toMatch(/straightened by/);
  });

  it('says what it did, per derivative', () => {
    const out = deriveOne(args());
    expect(out.steps.some((step) => step === 'master: to 16 bit')).toBe(true);
  });

  it('adds the set up', () => {
    const out = deriveOne(args());
    expect(out.bytes).toBe(out.files.reduce((sum, f) => sum + f.bytes.length, 0));
  });

  it('follows another profile when it is given one', () => {
    const out = deriveOne(args({ profile: 'rolled-drawings', ppi: 300 }));
    expect(out.files.map((f) => f.name)).toEqual(['master', 'service']);
  });

  it('says so when a profile wants an anchor this capture does not have', () => {
    expect(() => deriveOne(args({ profile: 'quadrangles', ppi: 600 }))).toThrow(/neatline/);
  });

  it('refuses a capture with nothing on the bed', () => {
    const blank = Surface.create({ width: 120, height: 100, layout: 'rgb', fill: 40 });
    expect(() => deriveOne({ ...args(), surface: blank })).toThrow(/stands out from it/);
  });

  it('refuses a sheet the detector fitted badly', () => {
    expect(() => deriveOne({ ...args(), surface: twoSheets() })).toThrow(/not sure enough/);
  });

  it('crops it anyway when the caller says to', () => {
    const out = deriveOne({ ...args(), surface: twoSheets() }, { requireUsable: false });
    expect(out.files).toHaveLength(3);
  });

  it('refuses a capture with no frequency to work from', () => {
    expect(() => deriveOne({ ...args(), sampling: undefined })).toThrow(/sampling frequency/);
  });
});

describe('deriveMany', () => {
  it('keeps going past a capture it could not read', () => {
    const blank = Surface.create({ width: 120, height: 100, layout: 'rgb', fill: 40 });
    const out = deriveMany([
      args({ id: 'a' }),
      { ...args({ id: 'b' }), surface: blank },
      args({ id: 'c' }),
    ]);
    expect(out.done.map((d) => d.accession)).toEqual(['a', 'c']);
    expect(out.failed.map((f) => f.accession)).toEqual(['b']);
  });

  it('keeps the error rather than only the fact of it', () => {
    const blank = Surface.create({ width: 120, height: 100, layout: 'rgb', fill: 40 });
    const out = deriveMany([{ ...args({ id: 'b' }), surface: blank }]);
    expect(out.failed[0].error.message).toMatch(/stands out from it/);
  });

  it('has nothing to report for an empty batch', () => {
    expect(deriveMany([])).toEqual({ done: [], failed: [] });
  });
});

describe('describeDerived', () => {
  it('writes one line per file', () => {
    const lines = describeDerived(deriveOne(args()));
    expect(lines).toHaveLength(3);
    expect(lines[0]).toMatch(/^g4181-1907_master\.png, png, \d+ ppi, \d+ bytes$/);
  });
});
