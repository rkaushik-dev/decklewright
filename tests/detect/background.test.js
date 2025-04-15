import { describe, expect, it } from 'vitest';

import {
  borderStrips,
  describeBackground,
  distanceFrom,
  estimateBackground,
  isUniform,
  objectMask,
} from '../../src/detect/background.js';
import { Surface } from '../../src/raster/surface.js';

/**
 * A bed with a sheet on it, both flat, so the numbers are checkable by hand.
 *
 * @param {object} [options]
 * @param {number} [options.bed]
 * @param {number} [options.sheet]
 * @param {number} [options.noise]
 * @returns {Surface}
 */
function bench({ bed = 40, sheet = 210, noise = 0 } = {}) {
  const s = Surface.create({ width: 100, height: 80, layout: 'gray', fill: bed });
  let seed = 17;
  if (noise > 0) {
    for (let i = 0; i < s.data.length; i += 1) {
      seed = (seed * 1103515245 + 12345) & 0x7fffffff;
      s.data[i] = bed + (seed % (noise * 2 + 1)) - noise;
    }
  }
  for (let y = 20; y < 60; y += 1) {
    for (let x = 25; x < 75; x += 1) s.set(x, y, 0, sheet);
  }
  return s;
}

describe('borderStrips', () => {
  it('returns four strips that do not overlap', () => {
    const strips = borderStrips(bench(), 0.1);
    expect(strips).toHaveLength(4);
    expect(strips[0].intersects(strips[1])).toBe(false);
    expect(strips[2].intersects(strips[3])).toBe(false);
  });

  it('keeps every strip inside the capture', () => {
    const surface = bench();
    for (const strip of borderStrips(surface, 0.2)) {
      expect(surface.bounds.containsRect(strip)).toBe(true);
    }
  });

  it('never produces a strip thinner than a pixel', () => {
    const tiny = Surface.create({ width: 10, height: 10, layout: 'gray' });
    for (const strip of borderStrips(tiny, 0.001)) {
      expect(strip.width).toBeGreaterThanOrEqual(1);
      expect(strip.height).toBeGreaterThanOrEqual(1);
    }
  });

  it('refuses a fraction that would swallow the capture', () => {
    expect(() => borderStrips(bench(), 0.6)).toThrow(/fraction must be between/);
    expect(() => borderStrips(bench(), 0)).toThrow(/fraction must be between/);
  });
});

describe('estimateBackground', () => {
  it('reads the bed and not the sheet', () => {
    const background = estimateBackground(bench());
    expect(background.mean[0]).toBeCloseTo(40, 6);
    expect(background.stdDev[0]).toBeCloseTo(0, 6);
    expect(background.samples).toBeGreaterThan(0);
  });

  it('reports the spread of a noisy bed', () => {
    const background = estimateBackground(bench({ noise: 10 }));
    expect(background.mean[0]).toBeGreaterThan(30);
    expect(background.mean[0]).toBeLessThan(50);
    expect(background.stdDev[0]).toBeGreaterThan(3);
  });

  it('reads every channel of a colour bed', () => {
    const s = Surface.create({ width: 60, height: 60, layout: 'rgb' });
    for (let y = 0; y < 60; y += 1) {
      for (let x = 0; x < 60; x += 1) s.setPixel(x, y, [20, 40, 60]);
    }
    const background = estimateBackground(s);
    expect(background.mean.map(Math.round)).toEqual([20, 40, 60]);
  });

  it('is not fooled by a sheet that reaches the middle of an edge', () => {
    const s = bench();
    for (let y = 0; y < 80; y += 1) {
      for (let x = 45; x < 55; x += 1) s.set(x, y, 0, 210);
    }
    const background = estimateBackground(s, { fraction: 0.02 });
    expect(background.mean[0]).toBeLessThan(90);
  });
});

describe('distanceFrom', () => {
  const background = { mean: [40, 40, 40], stdDev: [4, 4, 4], samples: 100 };

  it('is zero at the background itself', () => {
    expect(distanceFrom([40, 40, 40], background)).toBe(0);
  });

  it('counts spreads away', () => {
    expect(distanceFrom([48, 40, 40], background)).toBe(2);
    expect(distanceFrom([32, 40, 40], background)).toBe(2);
  });

  it('takes the worst channel', () => {
    expect(distanceFrom([40, 44, 60], background)).toBe(5);
  });

  it('never divides by a spread below one', () => {
    const flat = { mean: [40], stdDev: [0], samples: 10 };
    expect(distanceFrom([45], flat)).toBe(5);
  });
});

describe('objectMask', () => {
  it('finds the sheet and nothing else', () => {
    const { mask } = objectMask(bench());
    expect(mask.extent().toJSON()).toEqual({ x: 25, y: 20, width: 50, height: 40 });
    expect(mask.count).toBe(50 * 40);
  });

  it('still finds it on a noisy bed', () => {
    const { mask } = objectMask(bench({ noise: 8 }), { sigma: 4 });
    const extent = mask.extent();
    expect(extent.width).toBeGreaterThan(40);
    expect(extent.width).toBeLessThan(60);
  });

  it('finds nothing when the sheet is the same tone as the bed', () => {
    const { mask } = objectMask(bench({ sheet: 40 }));
    expect(mask.count).toBe(0);
    expect(mask.extent().isEmpty).toBe(true);
  });

  it('takes a background that was already measured', () => {
    const background = estimateBackground(bench());
    const { mask, background: used } = objectMask(bench(), { background });
    expect(used).toBe(background);
    expect(mask.count).toBe(2000);
  });

  it('lets the threshold be loosened', () => {
    const loose = objectMask(bench({ noise: 20 }), { sigma: 2 }).mask.count;
    const tight = objectMask(bench({ noise: 20 }), { sigma: 8 }).mask.count;
    expect(loose).toBeGreaterThanOrEqual(tight);
  });

  it('works on a colour capture', () => {
    const s = Surface.create({ width: 40, height: 40, layout: 'rgb' });
    for (let y = 0; y < 40; y += 1) {
      for (let x = 0; x < 40; x += 1) s.setPixel(x, y, [30, 30, 30]);
    }
    for (let y = 10; y < 30; y += 1) {
      for (let x = 10; x < 30; x += 1) s.setPixel(x, y, [30, 200, 30]);
    }
    expect(objectMask(s).mask.count).toBe(400);
  });
});

describe('isUniform', () => {
  it('accepts a flat bed and rejects a mottled one', () => {
    expect(isUniform({ mean: [40], stdDev: [3], samples: 10 })).toBe(true);
    expect(isUniform({ mean: [40], stdDev: [40], samples: 10 })).toBe(false);
  });

  it('scales to the depth it is told about', () => {
    const background = { mean: [10000], stdDev: [500], samples: 10 };
    expect(isUniform(background)).toBe(false);
    expect(isUniform(background, { fullScale: 65535 })).toBe(true);
  });
});

describe('describeBackground', () => {
  it('writes one line for the log', () => {
    const out = describeBackground({ mean: [40.4, 41], stdDev: [3.25, 3], samples: 1200 });
    expect(out).toBe('bed at 40, 41 give or take 3.3, 3.0 over 1200 samples');
  });
});
