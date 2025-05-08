import { describe, expect, it } from 'vitest';

import {
  coarseSkew,
  measureSkew,
  projectionScore,
  refineSkew,
  sampleSetPixels,
} from '../../src/detect/deskew.js';
import { Mask } from '../../src/detect/mask.js';
import { fitQuad } from '../../src/detect/quadfit.js';
import { Angle } from '../../src/geom/angle.js';

/**
 * @param {number} degrees
 * @param {object} [options]
 * @param {number} [options.halfWidth]
 * @param {number} [options.halfHeight]
 * @returns {Mask}
 */
function turnedSheet(degrees, { halfWidth = 45, halfHeight = 32 } = {}) {
  const mask = new Mask(160, 130);
  const radians = (degrees * Math.PI) / 180;
  const cos = Math.cos(radians);
  const sin = Math.sin(radians);
  for (let y = 0; y < mask.height; y += 1) {
    for (let x = 0; x < mask.width; x += 1) {
      const dx = x - 80;
      const dy = y - 65;
      if (
        Math.abs(dx * cos + dy * sin) <= halfWidth &&
        Math.abs(-dx * sin + dy * cos) <= halfHeight
      ) {
        mask.set(x, y, 1);
      }
    }
  }
  return mask;
}

describe('sampleSetPixels', () => {
  it('collects every set pixel when told to skip the outline', () => {
    const mask = new Mask(4, 4);
    mask.set(1, 1, 1);
    mask.set(2, 3, 1);
    const { xs, ys } = sampleSetPixels(mask, { outline: false });
    expect([...xs]).toEqual([1, 2]);
    expect([...ys]).toEqual([1, 3]);
  });

  it('takes the outline by default, which is far fewer pixels', () => {
    const mask = turnedSheet(0);
    const filled = sampleSetPixels(mask, { outline: false }).xs.length;
    const outline = sampleSetPixels(mask).xs.length;
    expect(outline).toBeLessThan(filled / 4);
  });

  it('honours a step', () => {
    const mask = turnedSheet(0);
    expect(sampleSetPixels(mask, { step: 4 }).xs.length).toBeLessThan(mask.count / 3);
  });

  it('refuses an empty mask', () => {
    expect(() => sampleSetPixels(new Mask(4, 4))).toThrow(/nothing to measure skew from/);
  });
});

describe('projectionScore', () => {
  it('peaks at the angle the sheet is turned to', () => {
    const pixels = sampleSetPixels(turnedSheet(5));
    const atTruth = projectionScore(pixels, Angle.degrees(5));
    expect(atTruth).toBeGreaterThan(projectionScore(pixels, Angle.degrees(0)));
    expect(atTruth).toBeGreaterThan(projectionScore(pixels, Angle.degrees(10)));
  });

  it('is symmetric about the truth', () => {
    const pixels = sampleSetPixels(turnedSheet(0));
    const left = projectionScore(pixels, Angle.degrees(-4));
    const right = projectionScore(pixels, Angle.degrees(4));
    expect(Math.abs(left - right) / left).toBeLessThan(0.15);
  });
});

describe('coarseSkew', () => {
  it('lands within a step of the truth', () => {
    for (const degrees of [-8, -3, 0, 3, 8]) {
      const { angle } = coarseSkew(turnedSheet(degrees), { step: 0.5 });
      expect(Math.abs(angle.degrees - degrees)).toBeLessThanOrEqual(1);
    }
  });

  it('reports how many candidates it tried', () => {
    const { tried } = coarseSkew(turnedSheet(0), { range: 5, step: 1 });
    expect(tried).toBe(11);
  });

  it('sees nothing outside the range it was given', () => {
    const { angle } = coarseSkew(turnedSheet(9), { range: 3, step: 0.5 });
    expect(Math.abs(angle.degrees)).toBeLessThanOrEqual(3);
  });

  it('refuses a range or step of zero', () => {
    expect(() => coarseSkew(turnedSheet(0), { range: 0 })).toThrow(/range must be greater/);
    expect(() => coarseSkew(turnedSheet(0), { step: 0 })).toThrow(/step must be greater/);
  });
});

describe('refineSkew', () => {
  it('lands on a better score than the coarse candidate', () => {
    const mask = turnedSheet(4.3);
    const coarse = coarseSkew(mask, { step: 1 });
    const fine = refineSkew(mask, coarse.angle, { window: 1.5 });
    expect(fine.score).toBeGreaterThanOrEqual(coarse.score);
    expect(fine.angle.degrees).not.toBe(coarse.angle.degrees);
  });

  it('stays inside the window it was given', () => {
    const mask = turnedSheet(3);
    const fine = refineSkew(mask, Angle.degrees(3), { window: 0.4 });
    expect(Math.abs(fine.angle.degrees - 3)).toBeLessThanOrEqual(0.4);
  });

  it('stops at the tolerance it was given', () => {
    const mask = turnedSheet(2);
    const loose = refineSkew(mask, Angle.degrees(2), { tolerance: 0.5 });
    const tight = refineSkew(mask, Angle.degrees(2), { tolerance: 0.001 });
    expect(tight.tried).toBeGreaterThan(loose.tried);
  });
});

describe('measureSkew', () => {
  it('reads a small turn to a fraction of a degree', () => {
    for (const degrees of [-6, -2.5, 0, 2.5, 6]) {
      const { angle } = measureSkew(turnedSheet(degrees));
      expect(Math.abs(angle.degrees - degrees)).toBeLessThan(0.6);
    }
  });

  it('reports the coarse answer alongside the fine one', () => {
    const out = measureSkew(turnedSheet(3.2));
    expect(Math.abs(out.coarse.degrees - 3.2)).toBeLessThan(1);
    expect(out.tried).toBeGreaterThan(10);
  });

  it('agrees with the caliper fit', () => {
    for (const degrees of [-7, -1, 1, 7]) {
      const mask = turnedSheet(degrees);
      const projection = measureSkew(mask).angle.degrees;
      const calipers = fitQuad(mask).angle.degrees;
      expect(Math.abs(projection - calipers)).toBeLessThan(0.6);
    }
  });

  it('works on a narrow sheet as well as a wide one', () => {
    const mask = turnedSheet(4, { halfWidth: 50, halfHeight: 12 });
    expect(Math.abs(measureSkew(mask).angle.degrees - 4)).toBeLessThan(0.6);
  });

  it('measures the fill directly when the outline is turned off', () => {
    const out = measureSkew(turnedSheet(0), { outline: false });
    expect(Math.abs(out.angle.degrees)).toBeLessThan(1);
  });
});
