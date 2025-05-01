import { describe, expect, it } from 'vitest';

import { Mask } from '../../src/detect/mask.js';
import {
  fitQuad,
  foldToBand,
  minimumAreaRect,
  outlineHull,
  quadSkew,
} from '../../src/detect/quadfit.js';
import { Angle } from '../../src/geom/angle.js';
import { Polygon } from '../../src/geom/polygon.js';
import { Rect } from '../../src/geom/rect.js';

/**
 * @param {number} degrees
 * @param {object} [options]
 * @param {number} [options.halfWidth]
 * @param {number} [options.halfHeight]
 * @returns {Mask}
 */
function turnedSheet(degrees, { halfWidth = 35, halfHeight = 25 } = {}) {
  const mask = new Mask(140, 120);
  const radians = (degrees * Math.PI) / 180;
  const cos = Math.cos(radians);
  const sin = Math.sin(radians);
  for (let y = 0; y < mask.height; y += 1) {
    for (let x = 0; x < mask.width; x += 1) {
      const dx = x - 70;
      const dy = y - 60;
      const along = dx * cos + dy * sin;
      const across = -dx * sin + dy * cos;
      if (Math.abs(along) <= halfWidth && Math.abs(across) <= halfHeight) mask.set(x, y, 1);
    }
  }
  return mask;
}

describe('outlineHull', () => {
  it('hulls the outline of a square sheet', () => {
    const hull = outlineHull(turnedSheet(0));
    expect(hull.count).toBeGreaterThanOrEqual(4);
    expect(hull.area).toBeGreaterThan(70 * 50 * 0.95);
  });

  it('takes every nth boundary pixel when told to', () => {
    const dense = outlineHull(turnedSheet(7));
    const sparse = outlineHull(turnedSheet(7), { step: 5 });
    expect(sparse.area).toBeGreaterThan(dense.area * 0.9);
  });

  it('refuses a mask with nothing in it', () => {
    expect(() => outlineHull(new Mask(20, 20))).toThrow(/no outline to fit/);
  });
});

describe('minimumAreaRect', () => {
  it('wraps an axis aligned rectangle exactly', () => {
    const hull = Polygon.fromRect(Rect.of(10, 20, 40, 30));
    const { quad, area } = minimumAreaRect(hull);
    expect(area).toBeCloseTo(1200, 6);
    expect(quad.topLeft.x).toBeCloseTo(10, 6);
    expect(quad.bottomRight.y).toBeCloseTo(50, 6);
  });

  it('beats the axis aligned box on a turned rectangle', () => {
    const hull = outlineHull(turnedSheet(20));
    const { area } = minimumAreaRect(hull);
    expect(area).toBeLessThan(hull.boundingRect.area * 0.95);
  });

  it('reports an orientation inside the band', () => {
    for (const degrees of [-40, -20, -5, 0, 5, 20, 40]) {
      const { angle } = minimumAreaRect(outlineHull(turnedSheet(degrees)));
      expect(angle.degrees).toBeGreaterThanOrEqual(-45);
      expect(angle.degrees).toBeLessThanOrEqual(45);
    }
  });
});

describe('foldToBand', () => {
  it('leaves a small angle alone', () => {
    expect(foldToBand(Angle.degrees(3)).degrees).toBeCloseTo(3, 9);
    expect(foldToBand(Angle.degrees(-3)).degrees).toBeCloseTo(-3, 9);
  });

  it('folds a quarter turn onto zero', () => {
    expect(foldToBand(Angle.degrees(90)).degrees).toBeCloseTo(0, 9);
    expect(foldToBand(Angle.degrees(180)).degrees).toBeCloseTo(0, 9);
    expect(foldToBand(Angle.degrees(-90)).degrees).toBeCloseTo(0, 9);
  });

  it('folds an angle just past the band', () => {
    expect(foldToBand(Angle.degrees(46)).degrees).toBeCloseTo(-44, 9);
    expect(foldToBand(Angle.degrees(93)).degrees).toBeCloseTo(3, 9);
  });

  it('always lands inside the band', () => {
    for (let d = -360; d <= 360; d += 7) {
      const folded = foldToBand(Angle.degrees(d)).degrees;
      expect(folded).toBeGreaterThanOrEqual(-45);
      expect(folded).toBeLessThanOrEqual(45);
    }
  });
});

describe('fitQuad', () => {
  it('reads the turn that was applied', () => {
    for (const degrees of [-20, -12, -6, -2, 0, 2, 6, 12, 20]) {
      const { angle } = fitQuad(turnedSheet(degrees));
      expect(Math.abs(angle.degrees - degrees)).toBeLessThan(1);
    }
  });

  it('lands the quad on the sheet', () => {
    const { quad } = fitQuad(turnedSheet(8));
    const lengths = quad.edgeLengths();
    expect(Math.max(...lengths)).toBeGreaterThan(65);
    expect(Math.max(...lengths)).toBeLessThan(76);
    expect(Math.min(...lengths)).toBeGreaterThan(45);
    expect(Math.min(...lengths)).toBeLessThan(56);
  });

  it('reports a high fill for a solid sheet', () => {
    expect(fitQuad(turnedSheet(5)).fill).toBeGreaterThan(0.9);
  });

  it('reports a low fill for a shape that is nothing like a rectangle', () => {
    const mask = new Mask(70, 70);
    for (let i = 5; i < 65; i += 1) {
      for (let k = 0; k < 4; k += 1) {
        mask.set(i, 33 + k, 1);
        mask.set(33 + k, i, 1);
      }
    }
    expect(fitQuad(mask).fill).toBeLessThan(0.3);
  });

  it('handles a nearly square sheet', () => {
    const { angle } = fitQuad(turnedSheet(7, { halfWidth: 30, halfHeight: 30 }));
    expect(Math.abs(angle.degrees - 7)).toBeLessThan(1.5);
  });
});

describe('quadSkew', () => {
  it('uses the longer pair of sides', () => {
    const { quad } = fitQuad(turnedSheet(9));
    expect(Math.abs(quadSkew(quad).degrees - 9)).toBeLessThan(1);
  });

  it('reads zero for an axis aligned quad', () => {
    const { quad } = minimumAreaRect(Polygon.fromRect(Rect.of(0, 0, 40, 20)));
    expect(quadSkew(quad).degrees).toBeCloseTo(0, 6);
  });
});
