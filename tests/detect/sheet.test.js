import { describe, expect, it } from 'vitest';

import { describeFinding, findSheet, objections, spreadOf } from '../../src/detect/sheet.js';
import { Angle } from '../../src/geom/angle.js';
import { Rect } from '../../src/geom/rect.js';
import { Surface } from '../../src/raster/surface.js';
import { bench, paint } from '../helpers/bench.js';

/**
 * A sheet with a tab that reaches the right hand edge of the bed, which is
 * what a badly placed oversize drawing looks like.
 *
 * @returns {Surface}
 */
function overrunning() {
  const surface = bench({ halfWidth: 40, halfHeight: 30 });
  return paint(surface, Rect.of(140, 70, 60, 20), 214);
}

describe('findSheet on a clean bench', () => {
  it('finds a square sheet', () => {
    const finding = findSheet(bench());
    expect(finding.skew.degrees).toBeCloseTo(0, 1);
    expect(finding.confidence).toBeGreaterThan(0.5);
    expect(finding.touchesEdge).toBe(false);
  });

  it('lands the quad on the sheet', () => {
    const finding = findSheet(bench());
    const box = finding.quad.boundingRect;
    expect(box.width).toBeGreaterThan(120);
    expect(box.width).toBeLessThan(130);
    expect(box.height).toBeGreaterThan(85);
    expect(box.height).toBeLessThan(93);
  });

  it('reads the turn that was applied', () => {
    for (const degrees of [-8, -3, 3, 8]) {
      const finding = findSheet(bench({ degrees }));
      expect(Math.abs(finding.skew.degrees - degrees)).toBeLessThan(0.6);
    }
  });

  it('has the three measures agree', () => {
    for (const degrees of [-5, 0, 5]) {
      expect(findSheet(bench({ degrees })).spread).toBeLessThan(1.5);
    }
  });

  it('reports how much of the bed the sheet takes', () => {
    const finding = findSheet(bench());
    expect(finding.coverage).toBeGreaterThan(0.3);
    expect(finding.coverage).toBeLessThan(0.4);
  });

  it('works on a colour capture', () => {
    const finding = findSheet(bench({ layout: 'rgb' }));
    expect(finding.confidence).toBeGreaterThan(0.5);
  });

  it('survives a noisy bed', () => {
    const finding = findSheet(bench({ noise: 9, degrees: 4 }), { sigma: 4 });
    expect(Math.abs(finding.skew.degrees - 4)).toBeLessThan(1);
  });
});

describe('what findSheet refuses', () => {
  it('refuses an empty bed', () => {
    const flat = Surface.create({ width: 80, height: 60, layout: 'gray', fill: 40 });
    expect(() => findSheet(flat)).toThrow(/stands out from it/);
  });

  it('refuses a speck of dust', () => {
    const surface = bench({ halfWidth: 3, halfHeight: 3 });
    expect(() => findSheet(surface)).toThrow(/too small to be a sheet/);
  });

  it('can be told to accept a smaller object', () => {
    const surface = bench({ halfWidth: 12, halfHeight: 10 });
    expect(() => findSheet(surface)).toThrow(/too small to be a sheet/);
    expect(findSheet(surface, { minimumCoverage: 0.001 }).coverage).toBeGreaterThan(0);
  });

  it('reports a sheet that runs off the bed rather than refusing it', () => {
    expect(findSheet(overrunning(), { sigma: 2 }).touchesEdge).toBe(true);
  });

  it('refuses a bed the sheet covers completely, since there is no bed to read', () => {
    expect(() => findSheet(bench({ halfWidth: 130, halfHeight: 100 }))).toThrow(
      /stands out from it/,
    );
  });
});

describe('spreadOf', () => {
  it('is the distance between the furthest apart pair', () => {
    const readings = {
      calipers: Angle.degrees(1),
      projection: Angle.degrees(1.4),
      sides: Angle.degrees(-0.2),
    };
    expect(spreadOf(readings)).toBeCloseTo(1.6, 9);
  });

  it('is zero when all three agree', () => {
    const one = Angle.degrees(2);
    expect(spreadOf({ calipers: one, projection: one, sides: one })).toBe(0);
  });
});

describe('describeFinding', () => {
  it('writes one line for the run log', () => {
    const line = describeFinding(findSheet(bench()));
    expect(line).toMatch(/^\d+x\d+, skew -?\d+\.\d\d deg, spread \d+\.\d\d, confidence \d\.\d\d$/);
  });

  it('says when the sheet runs off the bed', () => {
    expect(describeFinding(findSheet(overrunning(), { sigma: 2 }))).toContain('runs off the bed');
  });
});

describe('objections', () => {
  it('has nothing to say about a clean finding', () => {
    expect(objections(findSheet(bench()))).toEqual([]);
  });

  it('objects to a sheet that runs off the bed', () => {
    const finding = findSheet(overrunning(), { sigma: 2 });
    expect(objections(finding)).toContain('the sheet runs off the bed');
  });

  it('objects to a weak fit', () => {
    const finding = { ...findSheet(bench()), confidence: 0.1 };
    expect(objections(finding)).toContain('the sides do not fit well');
  });

  it('takes another confidence floor', () => {
    const finding = { ...findSheet(bench()), confidence: 0.6 };
    expect(objections(finding, { minimumConfidence: 0.9 })).toHaveLength(1);
  });
});
