import { describe, expect, it } from 'vitest';

import { Rect } from '../../../src/geom/rect.js';
import { Surface } from '../../../src/raster/surface.js';
import { runCheck } from '../../../src/qc/check.js';
import {
  SHARPNESS_LADDER,
  edgeProfile,
  edgeStrips,
  riseDistance,
  sharpnessCheck,
} from '../../../src/qc/checks/sharpness.js';

const SHEET = Rect.of(60, 50, 180, 140);

/**
 * A sheet on a bed with a chosen amount of blur across every edge.
 *
 * @param {number} blur in pixels of transition
 * @returns {Surface}
 */
function bench(blur) {
  const surface = Surface.create({ width: 300, height: 240, layout: 'gray', fill: 40 });

  for (let y = 0; y < 240; y += 1) {
    for (let x = 0; x < 300; x += 1) {
      const inside = Math.min(
        x - SHEET.left,
        SHEET.right - 1 - x,
        y - SHEET.top,
        SHEET.bottom - 1 - y,
      );
      const share =
        blur <= 0 ? (inside >= 0 ? 1 : 0) : Math.max(0, Math.min(1, inside / blur + 0.5));
      surface.set(x, y, 0, Math.round(40 + (210 - 40) * share));
    }
  }
  return surface;
}

describe('riseDistance', () => {
  it('is small for a step that happens in one sample', () => {
    expect(riseDistance([0, 0, 0, 100, 100, 100])).toBeCloseTo(0.8, 1);
  });

  it('grows with the width of the transition', () => {
    const sharp = riseDistance([0, 0, 50, 100, 100]);
    const soft = riseDistance([0, 20, 40, 60, 80, 100]);
    expect(soft).toBeGreaterThan(sharp);
  });

  it('reads a falling step as well as a rising one', () => {
    expect(riseDistance([100, 100, 50, 0, 0])).toBeCloseTo(riseDistance([0, 0, 50, 100, 100]), 6);
  });

  it('gives nothing back for a flat profile', () => {
    expect(riseDistance([50, 50, 50, 50])).toBeNaN();
  });

  it('gives nothing back for a profile too short to have a step', () => {
    expect(riseDistance([1, 2])).toBeNaN();
  });
});

describe('edgeProfile', () => {
  it('averages along the edge and across it', () => {
    const profile = edgeProfile(bench(1), Rect.of(100, 42, 60, 16), 'y');
    expect(profile).not.toBeNull();
    expect(profile?.length).toBe(16);
    expect(profile?.[0]).toBeLessThan(profile?.[15] ?? 0);
  });

  it('gives nothing back for a strip off the capture', () => {
    expect(edgeProfile(bench(1), Rect.of(900, 900, 10, 10), 'y')).toBeNull();
  });

  it('gives nothing back for a strip too thin to measure', () => {
    expect(edgeProfile(bench(1), Rect.of(100, 40, 60, 2), 'y')).toBeNull();
  });
});

describe('edgeStrips', () => {
  it('makes one strip per side', () => {
    expect(edgeStrips(SHEET)).toHaveLength(4);
  });

  it('straddles each edge', () => {
    const [top] = edgeStrips(SHEET, { reach: 6 });
    expect(top.strip.top).toBe(SHEET.top - 6);
    expect(top.strip.bottom).toBe(SHEET.top + 6);
  });

  it('averages over the middle of each edge', () => {
    const [top] = edgeStrips(SHEET, { along: 0.5 });
    expect(top.strip.width).toBe(90);
    expect(Math.abs(top.strip.centre.x - SHEET.centre.x)).toBeLessThanOrEqual(1);
  });

  it('never makes a strip too thin to measure', () => {
    for (const { strip } of edgeStrips(Rect.of(0, 0, 4, 4))) {
      expect(strip.width).toBeGreaterThanOrEqual(3);
      expect(strip.height).toBeGreaterThanOrEqual(3);
    }
  });
});

describe('sharpnessCheck', () => {
  /**
   * @param {number} blur
   * @returns {import('../../../src/qc/check.js').Finding}
   */
  function run(blur) {
    return runCheck(sharpnessCheck(), { surface: bench(blur), sheet: SHEET });
  }

  it('gives four stars to a hard edge', () => {
    const out = run(0);
    expect(out.stars).toBe(4);
    expect(out.note).toMatch(/across 4 edges/);
  });

  it('still passes a capture with one pixel of softness', () => {
    expect(run(1).stars).toBeGreaterThanOrEqual(3);
  });

  it('drops stars as the edge softens', () => {
    expect(run(3).stars).toBeLessThan(4);
    expect(run(8).stars).toBeLessThan(run(3).stars);
  });

  it('gives no stars to a badly out of focus capture', () => {
    expect(run(20).stars).toBe(0);
  });

  it('reports the softest edge, not the average', () => {
    const out = run(4);
    expect(out.measured).toBeGreaterThan(2);
  });

  it('says it could not run without a sheet', () => {
    const out = runCheck(sharpnessCheck(), { surface: bench(1) });
    expect(out.ran).toBe(false);
    expect(out.note).toMatch(/no sheet/);
  });

  it('says so when no edge gave a step', () => {
    const flat = Surface.create({ width: 300, height: 240, layout: 'gray', fill: 100 });
    const out = runCheck(sharpnessCheck(), { surface: flat, sheet: SHEET });
    expect(out.stars).toBe(0);
    expect(out.note).toMatch(/no edge of the sheet gave a usable step/);
  });
});

describe('the ladder', () => {
  it('is frozen and runs best first', () => {
    expect(Object.isFrozen(SHARPNESS_LADDER)).toBe(true);
    const limits = SHARPNESS_LADDER.map((r) => r.limit);
    expect([...limits].sort((a, b) => a - b)).toEqual(limits);
  });
});
