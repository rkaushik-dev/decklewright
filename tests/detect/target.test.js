import { describe, expect, it } from 'vitest';

import { objectMask } from '../../src/detect/background.js';
import {
  endPatches,
  locateTarget,
  patchRange,
  readPatches,
  stepsMonotonically,
} from '../../src/detect/target.js';
import { Rect } from '../../src/geom/rect.js';
import { Surface } from '../../src/raster/surface.js';
import { bench, paint } from '../helpers/bench.js';

const WEDGE = Rect.of(12, 12, 80, 16);

/**
 * A bed with a sheet on it and a step wedge beside it.
 *
 * @param {object} [options]
 * @param {number} [options.steps]
 * @param {boolean} [options.scrambled] put the patches in a random order
 * @returns {Surface}
 */
function benchWithTarget({ steps = 8, scrambled = false } = {}) {
  const surface = bench({ halfWidth: 50, halfHeight: 34, centreX: 120, centreY: 100 });
  const order = scrambled ? [3, 7, 1, 5, 0, 6, 2, 4] : [0, 1, 2, 3, 4, 5, 6, 7];
  const step = WEDGE.width / steps;
  for (let i = 0; i < steps; i += 1) {
    const tone = 20 + Math.round((order[i % order.length] / (steps - 1)) * 210);
    paint(surface, Rect.of(WEDGE.left + i * step, WEDGE.top, step, WEDGE.height), tone);
  }
  return surface;
}

describe('readPatches', () => {
  it('divides a wide rect along its width', () => {
    const surface = Surface.create({ width: 40, height: 10, layout: 'gray', fill: 0 });
    paint(surface, Rect.of(0, 0, 20, 10), 100);
    paint(surface, Rect.of(20, 0, 20, 10), 200);
    const patches = readPatches(surface, Rect.of(0, 0, 40, 10), 2);
    expect(patches).toHaveLength(2);
    expect(patches[0].mean[0]).toBeCloseTo(100, 6);
    expect(patches[1].mean[0]).toBeCloseTo(200, 6);
  });

  it('divides a tall rect along its height', () => {
    const surface = Surface.create({ width: 10, height: 40, layout: 'gray', fill: 0 });
    paint(surface, Rect.of(0, 20, 10, 20), 200);
    const patches = readPatches(surface, Rect.of(0, 0, 10, 40), 2);
    expect(patches[0].mean[0]).toBeCloseTo(0, 6);
    expect(patches[1].mean[0]).toBeCloseTo(200, 6);
  });

  it('ignores the edges of each cell', () => {
    const surface = Surface.create({ width: 40, height: 10, layout: 'gray', fill: 100 });
    paint(surface, Rect.of(19, 0, 2, 10), 0);
    const patches = readPatches(surface, Rect.of(0, 0, 40, 10), 2);
    expect(patches[0].mean[0]).toBeCloseTo(100, 6);
    expect(patches[1].mean[0]).toBeCloseTo(100, 6);
  });

  it('reads every channel', () => {
    const surface = Surface.create({ width: 20, height: 10, layout: 'rgb' });
    paint(surface, surface.bounds, [10, 20, 30]);
    const patches = readPatches(surface, surface.bounds, 1);
    expect(patches[0].mean.map(Math.round)).toEqual([10, 20, 30]);
  });

  it('reports an empty patch for a cell that fell off the capture', () => {
    const surface = Surface.create({ width: 20, height: 10, layout: 'gray' });
    const patches = readPatches(surface, Rect.of(100, 0, 40, 10), 2);
    expect(patches[0].mean).toEqual([]);
  });

  it('refuses a count that is not a whole number above zero', () => {
    const surface = Surface.create({ width: 20, height: 10, layout: 'gray' });
    expect(() => readPatches(surface, surface.bounds, 0)).toThrow(/above zero/);
  });
});

describe('stepsMonotonically', () => {
  const rising = [0, 40, 80, 120].map((mean) => ({
    rect: Rect.empty(),
    mean: [mean],
    stdDev: [0],
  }));

  it('accepts a rising and a falling run', () => {
    expect(stepsMonotonically(rising)).toBe(true);
    expect(stepsMonotonically([...rising].reverse())).toBe(true);
  });

  it('tolerates one step the wrong way', () => {
    const wobbly = [0, 40, 38, 120].map((mean) => ({
      rect: Rect.empty(),
      mean: [mean],
      stdDev: [0],
    }));
    expect(stepsMonotonically(wobbly)).toBe(true);
    expect(stepsMonotonically(wobbly, { slack: 0 })).toBe(false);
  });

  it('rejects a scrambled run', () => {
    const scrambled = [0, 120, 40, 80, 20].map((mean) => ({
      rect: Rect.empty(),
      mean: [mean],
      stdDev: [0],
    }));
    expect(stepsMonotonically(scrambled)).toBe(false);
  });

  it('rejects a run with an unreadable patch', () => {
    expect(stepsMonotonically([{ rect: Rect.empty(), mean: [], stdDev: [] }])).toBe(false);
  });
});

describe('patchRange', () => {
  it('is the spread between the ends', () => {
    const patches = [10, 200, 90].map((mean) => ({
      rect: Rect.empty(),
      mean: [mean],
      stdDev: [0],
    }));
    expect(patchRange(patches)).toBe(190);
  });

  it('is zero when nothing could be read', () => {
    expect(patchRange([{ rect: Rect.empty(), mean: [], stdDev: [] }])).toBe(0);
  });
});

describe('locateTarget', () => {
  it('finds the wedge and not the sheet', () => {
    const surface = benchWithTarget();
    const { mask } = objectMask(surface, { sigma: 4 });
    const found = locateTarget(surface, mask);
    expect(found).not.toBeNull();
    expect(found?.rect.left).toBeGreaterThanOrEqual(WEDGE.left - 2);
    expect(found?.rect.right).toBeLessThanOrEqual(WEDGE.right + 2);
    expect(found?.monotone).toBe(true);
    expect(found?.patches).toHaveLength(8);
  });

  it('reports the range across the wedge', () => {
    const surface = benchWithTarget();
    const { mask } = objectMask(surface, { sigma: 4 });
    expect(locateTarget(surface, mask)?.range).toBeGreaterThan(150);
  });

  it('finds nothing when the patches are scrambled', () => {
    const surface = benchWithTarget({ scrambled: true });
    const { mask } = objectMask(surface, { sigma: 4 });
    expect(locateTarget(surface, mask)).toBeNull();
  });

  it('finds nothing on a bench with no wedge', () => {
    const surface = bench();
    const { mask } = objectMask(surface, { sigma: 4 });
    expect(locateTarget(surface, mask)).toBeNull();
  });

  it('honours a minimum area', () => {
    const surface = benchWithTarget();
    const { mask } = objectMask(surface, { sigma: 4 });
    expect(locateTarget(surface, mask, { minimumArea: 100000 })).toBeNull();
  });

  it('honours a minimum range', () => {
    const surface = benchWithTarget();
    const { mask } = objectMask(surface, { sigma: 4 });
    expect(locateTarget(surface, mask, { minimumRange: 250 })).toBeNull();
  });
});

describe('endPatches', () => {
  const patches = [2, 40, 120, 250].map((mean) => ({
    rect: Rect.empty(),
    mean: [mean],
    stdDev: [0],
  }));

  it('skips the clipped ends', () => {
    const { light, dark } = endPatches(patches, 255);
    expect(dark?.mean[0]).toBe(40);
    expect(light?.mean[0]).toBe(120);
  });

  it('gives nothing back when every patch is clipped', () => {
    const clipped = [0, 255].map((mean) => ({ rect: Rect.empty(), mean: [mean], stdDev: [0] }));
    expect(endPatches(clipped, 255)).toEqual({ light: null, dark: null });
  });
});
