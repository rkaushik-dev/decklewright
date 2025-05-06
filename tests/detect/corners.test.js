import { describe, expect, it } from 'vitest';

import {
  cornerResidual,
  cornerShift,
  cornersFromLines,
  intersect,
  lineConfidence,
  quadConfidence,
  refineCorners,
} from '../../src/detect/corners.js';
import { edgeLines, fitLine } from '../../src/detect/edges.js';
import { Mask } from '../../src/detect/mask.js';
import { fitQuad } from '../../src/detect/quadfit.js';
import { Point } from '../../src/geom/point.js';
import { Quad } from '../../src/geom/quad.js';
import { Rect } from '../../src/geom/rect.js';

/**
 * @param {number} degrees
 * @returns {Mask}
 */
function turnedSheet(degrees) {
  const mask = new Mask(140, 120);
  const radians = (degrees * Math.PI) / 180;
  const cos = Math.cos(radians);
  const sin = Math.sin(radians);
  for (let y = 0; y < mask.height; y += 1) {
    for (let x = 0; x < mask.width; x += 1) {
      const dx = x - 70;
      const dy = y - 60;
      if (Math.abs(dx * cos + dy * sin) <= 40 && Math.abs(-dx * sin + dy * cos) <= 30) {
        mask.set(x, y, 1);
      }
    }
  }
  return mask;
}

const horizontal = fitLine([Point.of(0, 10), Point.of(100, 10)]);
const vertical = fitLine([Point.of(20, 0), Point.of(20, 100)]);

describe('intersect', () => {
  it('crosses a horizontal and a vertical line', () => {
    expect(intersect(horizontal, vertical).equals(Point.of(20, 10), 1e-6)).toBe(true);
  });

  it('crosses two slanted lines', () => {
    const a = fitLine([Point.of(0, 0), Point.of(10, 10)]);
    const b = fitLine([Point.of(0, 10), Point.of(10, 0)]);
    expect(intersect(a, b).equals(Point.of(5, 5), 1e-6)).toBe(true);
  });

  it('refuses two parallel lines', () => {
    const other = fitLine([Point.of(0, 40), Point.of(100, 40)]);
    expect(() => intersect(horizontal, other)).toThrow(/parallel and never meet/);
  });
});

describe('cornersFromLines', () => {
  it('builds a quad from four sides', () => {
    const lines = edgeLines(turnedSheet(0));
    const quad = cornersFromLines(lines);
    expect(quad.corners).toHaveLength(4);
    expect(quad.isConvex).toBe(true);
    expect(quad.area).toBeGreaterThan(80 * 60 * 0.9);
  });

  it('agrees with the calipers on a square sheet', () => {
    const mask = turnedSheet(0);
    const caliper = fitQuad(mask).quad;
    const crossed = cornersFromLines(edgeLines(mask));
    expect(cornerShift(caliper, crossed).worst).toBeLessThan(2);
  });
});

describe('cornerShift', () => {
  it('is zero between a quad and itself', () => {
    const quad = Quad.fromRect(Rect.of(0, 0, 10, 10));
    const shift = cornerShift(quad, quad);
    expect(shift.worst).toBe(0);
    expect(shift.mean).toBe(0);
    expect(shift.moves).toEqual([0, 0, 0, 0]);
  });

  it('measures each corner separately', () => {
    const a = Quad.fromRect(Rect.of(0, 0, 10, 10));
    const b = new Quad([Point.of(3, 4), Point.of(10, 0), Point.of(10, 10), Point.of(0, 10)]);
    const shift = cornerShift(a, b);
    expect(shift.moves[0]).toBeCloseTo(5, 9);
    expect(shift.worst).toBeCloseTo(5, 9);
    expect(shift.mean).toBeCloseTo(1.25, 9);
  });
});

describe('lineConfidence', () => {
  it('is one for a perfect fit', () => {
    expect(lineConfidence(horizontal)).toBe(1);
  });

  it('falls as the residual grows', () => {
    const noisy = fitLine([Point.of(0, 0), Point.of(10, 6), Point.of(20, 0)]);
    expect(lineConfidence(noisy)).toBeLessThan(0.5);
  });

  it('is a half at the tolerance', () => {
    const line = { ...horizontal, residual: 2 };
    expect(lineConfidence(line, { tolerance: 2 })).toBeCloseTo(0.5, 9);
  });

  it('is zero for a line with nothing behind it', () => {
    expect(lineConfidence({ ...horizontal, points: [] })).toBe(0);
  });
});

describe('quadConfidence', () => {
  it('reports the weakest of the four sides', () => {
    const lines = edgeLines(turnedSheet(4));
    const confidence = quadConfidence(lines);
    expect(confidence).toBeGreaterThan(0);
    expect(confidence).toBeLessThanOrEqual(1);
  });

  it('is high for a clean sheet', () => {
    expect(quadConfidence(edgeLines(turnedSheet(0)))).toBeGreaterThan(0.6);
  });
});

describe('refineCorners', () => {
  it('moves the corners a little on a clean sheet', () => {
    const mask = turnedSheet(6);
    const caliper = fitQuad(mask).quad;
    const { quad, shift, clamped } = refineCorners(caliper, edgeLines(mask));
    expect(clamped).toBe(false);
    expect(shift.worst).toBeLessThan(8);
    expect(quad.isConvex).toBe(true);
  });

  it('keeps the rectangle when the crossing is wild', () => {
    const mask = turnedSheet(3);
    const caliper = fitQuad(mask).quad;
    const lines = edgeLines(mask);
    const broken = { ...lines, top: fitLine([Point.of(0, 0), Point.of(10, 90)]) };
    const { quad, clamped } = refineCorners(caliper, broken);
    expect(clamped).toBe(true);
    expect(quad).toBe(caliper);
  });

  it('keeps the rectangle when two sides are parallel', () => {
    const mask = turnedSheet(0);
    const caliper = fitQuad(mask).quad;
    const lines = edgeLines(mask);
    const broken = { ...lines, left: lines.top };
    const { quad, clamped } = refineCorners(caliper, broken);
    expect(clamped).toBe(true);
    expect(quad).toBe(caliper);
  });

  it('honours the limit it is given', () => {
    const mask = turnedSheet(6);
    const caliper = fitQuad(mask).quad;
    const lines = edgeLines(mask);
    expect(refineCorners(caliper, lines, { limit: 0 }).clamped).toBe(true);
  });
});

describe('cornerResidual', () => {
  it('is near zero when the quad sits on its own lines', () => {
    const mask = turnedSheet(0);
    const lines = edgeLines(mask);
    expect(cornerResidual(cornersFromLines(lines), lines)).toBeLessThan(1e-6);
  });

  it('grows when the quad does not match the lines', () => {
    const mask = turnedSheet(0);
    const lines = edgeLines(mask);
    const wrong = Quad.fromRect(Rect.of(0, 0, 10, 10));
    expect(cornerResidual(wrong, lines)).toBeGreaterThan(10);
  });
});
