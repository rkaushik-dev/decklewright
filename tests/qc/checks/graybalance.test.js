import { describe, expect, it } from 'vitest';

import { Rect } from '../../../src/geom/rect.js';
import { Surface } from '../../../src/raster/surface.js';
import { runCheck } from '../../../src/qc/check.js';
import {
  GRAY_BALANCE_LADDER,
  castDirection,
  grayBalanceCheck,
  neutralError,
} from '../../../src/qc/checks/graybalance.js';
import { paint } from '../../helpers/bench.js';

const PATCH_RECTS = [
  Rect.of(0, 0, 20, 20),
  Rect.of(20, 0, 20, 20),
  Rect.of(40, 0, 20, 20),
  Rect.of(60, 0, 20, 20),
];

/**
 * A wedge of four patches, each with a cast applied to one of them.
 *
 * @param {readonly number[][]} colours
 * @returns {Surface}
 */
function wedge(colours) {
  const surface = Surface.create({ width: 80, height: 20, layout: 'rgb' });
  colours.forEach((colour, i) => paint(surface, PATCH_RECTS[i], colour));
  return surface;
}

/**
 * @param {Surface} surface
 * @param {number} [tolerance]
 * @returns {Record<string, any>}
 */
function context(surface, tolerance = 6) {
  return {
    surface,
    target: { patches: PATCH_RECTS.map((rect) => ({ rect })) },
    limits: { grayBalanceTolerance: tolerance },
  };
}

describe('neutralError', () => {
  it('is zero for a neutral patch', () => {
    expect(neutralError([100, 100, 100])).toBe(0);
  });

  it('is the spread across the three channels', () => {
    expect(neutralError([90, 100, 104])).toBe(14);
  });

  it('ignores an alpha channel', () => {
    expect(neutralError([100, 100, 100, 0])).toBe(0);
  });

  it('is zero for a capture with fewer than three channels', () => {
    expect(neutralError([100])).toBe(0);
  });
});

describe('grayBalanceCheck', () => {
  it('gives four stars to a neutral wedge', () => {
    const neutral = wedge([
      [40, 40, 40],
      [90, 90, 90],
      [150, 150, 150],
      [210, 210, 210],
    ]);
    const out = runCheck(grayBalanceCheck(), context(neutral));
    expect(out.stars).toBe(4);
    expect(out.measured).toBe(0);
  });

  it('takes the worst patch, not the average', () => {
    const mostlyNeutral = wedge([
      [40, 40, 40],
      [90, 90, 90],
      [150, 150, 150],
      [200, 210, 220],
    ]);
    const out = runCheck(grayBalanceCheck(), context(mostlyNeutral, 30));
    expect(out.measured).toBe(20);
    expect(out.stars).toBe(1);
  });

  it('gives no stars past the profile limit', () => {
    const cast = wedge([
      [40, 44, 40],
      [90, 100, 90],
      [150, 160, 150],
      [200, 214, 200],
    ]);
    expect(runCheck(grayBalanceCheck(), context(cast, 6)).stars).toBe(0);
  });

  it('passes a grayscale capture, which cannot have a cast', () => {
    const gray = Surface.create({ width: 80, height: 20, layout: 'gray', fill: 100 });
    const out = runCheck(grayBalanceCheck(), context(gray));
    expect(out.stars).toBe(4);
    expect(out.note).toMatch(/grayscale capture cannot have a cast/);
  });

  it('says it could not run without a target', () => {
    const out = runCheck(grayBalanceCheck(), {
      surface: wedge([[1, 1, 1]]),
      limits: { grayBalanceTolerance: 6 },
    });
    expect(out.ran).toBe(false);
    expect(out.note).toMatch(/no target/);
  });

  it('says so when no patch could be read', () => {
    const out = runCheck(grayBalanceCheck(), {
      surface: wedge([[1, 1, 1]]),
      target: { patches: [{ rect: Rect.of(900, 900, 10, 10) }] },
      limits: { grayBalanceTolerance: 6 },
    });
    expect(out.stars).toBe(0);
    expect(out.note).toMatch(/no patch of the target could be read/);
  });

  it('counts the patches it read', () => {
    const neutral = wedge([
      [40, 40, 40],
      [90, 90, 90],
      [150, 150, 150],
      [210, 210, 210],
    ]);
    expect(runCheck(grayBalanceCheck(), context(neutral)).note).toMatch(/across 4 patches/);
  });
});

describe('castDirection', () => {
  const patches = PATCH_RECTS.map((rect) => ({ rect }));

  it('names the channel that runs high', () => {
    const blueish = wedge([
      [90, 90, 110],
      [120, 120, 140],
      [150, 150, 170],
      [180, 180, 200],
    ]);
    const out = castDirection(blueish, patches);
    expect(out?.channel).toBe('blue');
    expect(out?.amount).toBeGreaterThan(0);
  });

  it('names the channel that runs low with a negative amount', () => {
    const shortRed = wedge([
      [70, 100, 100],
      [90, 120, 120],
      [120, 150, 150],
      [150, 180, 180],
    ]);
    const out = castDirection(shortRed, patches);
    expect(out?.channel).toBe('red');
    expect(out?.amount).toBeLessThan(0);
  });

  it('gives nothing back for a grayscale capture', () => {
    const gray = Surface.create({ width: 80, height: 20, layout: 'gray', fill: 100 });
    expect(castDirection(gray, patches)).toBeNull();
  });

  it('gives nothing back when no patch could be read', () => {
    expect(castDirection(wedge([[1, 1, 1]]), [{ rect: Rect.of(900, 900, 5, 5) }])).toBeNull();
  });
});

describe('the ladder', () => {
  it('is frozen and runs best first', () => {
    expect(Object.isFrozen(GRAY_BALANCE_LADDER)).toBe(true);
    const limits = GRAY_BALANCE_LADDER.map((r) => r.limit);
    expect([...limits].sort((a, b) => a - b)).toEqual(limits);
  });
});
