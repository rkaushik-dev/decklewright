import { describe, expect, it } from 'vitest';

import { Rect } from '../../../src/geom/rect.js';
import { Surface } from '../../../src/raster/surface.js';
import { runCheck } from '../../../src/qc/check.js';
import {
  ILLUMINATION_LADDER,
  darkestCorner,
  illuminationCheck,
} from '../../../src/qc/checks/illumination.js';

/**
 * A bed lit unevenly from one side.
 *
 * @param {number} falloff how much darker the far side is, as a share
 * @returns {Surface}
 */
function litBed(falloff) {
  const surface = Surface.create({ width: 200, height: 160, layout: 'gray' });
  for (let y = 0; y < 160; y += 1) {
    for (let x = 0; x < 200; x += 1) {
      const share = 1 - (x / 199) * falloff;
      surface.set(x, y, 0, Math.round(200 * share));
    }
  }
  return surface;
}

/**
 * @param {Surface} surface
 * @param {number} [tolerance]
 * @returns {Record<string, any>}
 */
function context(surface, tolerance = 0.1) {
  return { surface, limits: { illuminationTolerance: tolerance } };
}

describe('illuminationCheck', () => {
  it('gives four stars to an evenly lit bed', () => {
    const flat = Surface.create({ width: 200, height: 160, layout: 'gray', fill: 180 });
    const out = runCheck(illuminationCheck(), context(flat));
    expect(out.stars).toBe(4);
    expect(out.measured).toBeCloseTo(0, 6);
  });

  it('drops stars as the falloff grows', () => {
    expect(runCheck(illuminationCheck(), context(litBed(0.03))).stars).toBe(4);
    expect(runCheck(illuminationCheck(), context(litBed(0.08), 0.2)).stars).toBe(3);
    expect(runCheck(illuminationCheck(), context(litBed(0.3), 0.4)).stars).toBeLessThanOrEqual(2);
  });

  it('gives no stars once the profile limit is broken', () => {
    const out = runCheck(illuminationCheck(), context(litBed(0.3), 0.1));
    expect(out.stars).toBe(0);
    expect(out.passed).toBe(false);
  });

  it('says how many patches it read and what the limit was', () => {
    const out = runCheck(illuminationCheck({ across: 3, down: 2 }), context(litBed(0.05)));
    expect(out.note).toMatch(/across 6 patches, against a limit of 10.0/);
  });

  it('measures only the region it is given', () => {
    const surface = litBed(0.4);
    const whole = runCheck(illuminationCheck(), context(surface, 1)).measured;
    const corner = runCheck(illuminationCheck(), {
      ...context(surface, 1),
      bed: Rect.of(0, 0, 40, 160),
    }).measured;
    expect(corner).toBeLessThan(whole);
  });

  it('says it could not run without a capture', () => {
    const out = runCheck(illuminationCheck(), { limits: { illuminationTolerance: 0.1 } });
    expect(out.ran).toBe(false);
  });

  it('says so when the region is too small to grid', () => {
    const tiny = Surface.create({ width: 2, height: 2, layout: 'gray', fill: 100 });
    const out = runCheck(illuminationCheck({ across: 1, down: 1 }), context(tiny));
    expect(out.stars).toBe(0);
    expect(out.note).toMatch(/not enough of the bed/);
  });
});

describe('darkestCorner', () => {
  it('names the corner a lamp has to move towards', () => {
    expect(darkestCorner(litBed(0.4)).corner).toMatch(/right/);
  });

  it('reports the mean it found there', () => {
    const out = darkestCorner(litBed(0.4));
    expect(out.mean).toBeGreaterThan(0);
    expect(out.mean).toBeLessThan(200);
  });

  it('picks the first corner on a flat bed', () => {
    const flat = Surface.create({ width: 40, height: 40, layout: 'gray', fill: 120 });
    expect(darkestCorner(flat).corner).toBe('top left');
  });
});

describe('the ladder', () => {
  it('is frozen and runs best first', () => {
    expect(Object.isFrozen(ILLUMINATION_LADDER)).toBe(true);
    const limits = ILLUMINATION_LADDER.map((r) => r.limit);
    expect([...limits].sort((a, b) => a - b)).toEqual(limits);
  });
});
