import { describe, expect, it } from 'vitest';

import { Rect } from '../../../src/geom/rect.js';
import { Surface } from '../../../src/raster/surface.js';
import { runCheck } from '../../../src/qc/check.js';
import {
  CLIPPING_LADDER,
  NOISE_LADDER,
  clippingCheck,
  flattestPatch,
  noiseCheck,
  patchView,
} from '../../../src/qc/checks/noise.js';
import { paint } from '../../helpers/bench.js';

const PATCH_RECTS = [
  Rect.of(0, 0, 30, 30),
  Rect.of(30, 0, 30, 30),
  Rect.of(60, 0, 30, 30),
  Rect.of(90, 0, 30, 30),
];

/**
 * A wedge with a given tone per patch and a chosen amount of noise on it.
 *
 * @param {readonly number[]} tones
 * @param {number} [noise]
 * @returns {Surface}
 */
function wedge(tones, noise = 0) {
  const surface = Surface.create({ width: 120, height: 30, layout: 'gray' });
  tones.forEach((tone, i) => paint(surface, PATCH_RECTS[i], tone));
  if (noise > 0) {
    let seed = 5;
    for (let i = 0; i < surface.data.length; i += 1) {
      seed = (seed * 1103515245 + 12345) & 0x7fffffff;
      const jitter = (seed % (noise * 2 + 1)) - noise;
      surface.data[i] = Math.max(0, Math.min(255, surface.data[i] + jitter));
    }
  }
  return surface;
}

/**
 * @param {Surface} surface
 * @param {object} [limits]
 * @returns {Record<string, any>}
 */
function context(surface, limits = {}) {
  return {
    surface,
    target: { patches: PATCH_RECTS.map((rect) => ({ rect })) },
    limits: { minimumSnr: 30, maximumClipping: 0.005, ...limits },
  };
}

describe('patchView', () => {
  it('takes the middle of a patch', () => {
    const view = patchView(wedge([100]), PATCH_RECTS[0]);
    expect(view?.width).toBeLessThan(30);
    expect(view?.width).toBeGreaterThan(10);
  });

  it('gives nothing back for a patch off the capture', () => {
    expect(patchView(wedge([100]), Rect.of(500, 500, 10, 10))).toBeNull();
  });
});

describe('flattestPatch', () => {
  it('picks the lightest patch that is not near clipping', () => {
    const surface = wedge([40, 120, 200, 250]);
    const view = flattestPatch(
      surface,
      PATCH_RECTS.map((rect) => ({ rect })),
    );
    expect(view).not.toBeNull();
    expect(view?.get(0, 0)).toBe(200);
  });

  it('skips patches that are too dark', () => {
    const surface = wedge([10, 20, 30, 40]);
    expect(
      flattestPatch(
        surface,
        PATCH_RECTS.map((rect) => ({ rect })),
      ),
    ).toBeNull();
  });

  it('skips patches that are near white', () => {
    const surface = wedge([250, 252, 254, 255]);
    expect(
      flattestPatch(
        surface,
        PATCH_RECTS.map((rect) => ({ rect })),
      ),
    ).toBeNull();
  });
});

describe('noiseCheck', () => {
  it('gives four stars to a patch with no noise at all', () => {
    const out = runCheck(noiseCheck(), context(wedge([40, 120, 200, 250])));
    expect(out.stars).toBe(4);
    expect(out.note).toMatch(/no measurable dB/);
  });

  it('drops stars as the noise grows', () => {
    const quiet = runCheck(noiseCheck(), context(wedge([40, 120, 200, 250], 2))).stars;
    const loud = runCheck(
      noiseCheck(),
      context(wedge([40, 120, 200, 250], 30), {
        minimumSnr: 10,
      }),
    ).stars;
    expect(loud).toBeLessThan(quiet);
  });

  it('gives no stars under the profile floor', () => {
    const out = runCheck(noiseCheck(), context(wedge([40, 120, 200, 250], 40), { minimumSnr: 40 }));
    expect(out.stars).toBe(0);
    expect(out.passed).toBe(false);
  });

  it('says so when no patch is usable', () => {
    const out = runCheck(noiseCheck(), context(wedge([10, 12, 14, 16])));
    expect(out.stars).toBe(0);
    expect(out.note).toMatch(/no patch of the target is usable/);
  });

  it('says it could not run without a target', () => {
    const out = runCheck(noiseCheck(), {
      surface: wedge([100]),
      limits: { minimumSnr: 30 },
    });
    expect(out.ran).toBe(false);
  });
});

describe('clippingCheck', () => {
  it('gives four stars when nothing is clipped', () => {
    const out = runCheck(clippingCheck(), context(wedge([40, 120, 200, 250])));
    expect(out.stars).toBe(4);
    expect(out.note).toBe('nothing is clipped');
  });

  it('finds clipping in the highlights and says where', () => {
    const surface = wedge([40, 120, 200, 250]);
    paint(surface, Rect.of(0, 0, 20, 30), 255);
    const out = runCheck(clippingCheck(), context(surface, { maximumClipping: 1 }));
    expect(out.note).toMatch(/clipped in the highlights/);
    expect(out.measured).toBeGreaterThan(0.1);
  });

  it('finds clipping in the shadows too', () => {
    const surface = wedge([40, 120, 200, 250]);
    paint(surface, Rect.of(0, 0, 20, 30), 0);
    const out = runCheck(clippingCheck(), context(surface, { maximumClipping: 1 }));
    expect(out.note).toMatch(/clipped in the shadows/);
  });

  it('gives no stars past the profile limit', () => {
    const surface = wedge([40, 120, 200, 250]);
    paint(surface, Rect.of(0, 0, 20, 30), 255);
    expect(runCheck(clippingCheck(), context(surface)).stars).toBe(0);
  });

  it('measures only the crop when there is one', () => {
    const surface = wedge([40, 120, 200, 250]);
    paint(surface, Rect.of(0, 0, 20, 30), 255);
    const out = runCheck(clippingCheck(), {
      ...context(surface, { maximumClipping: 1 }),
      crop: Rect.of(60, 0, 60, 30),
    });
    expect(out.measured).toBe(0);
  });

  it('reads every channel of a colour capture', () => {
    const colour = Surface.create({ width: 20, height: 20, layout: 'rgb', fill: 100 });
    paint(colour, Rect.of(0, 0, 4, 20), [100, 255, 100]);
    const out = runCheck(clippingCheck(), {
      surface: colour,
      limits: { maximumClipping: 1 },
    });
    expect(out.measured).toBeGreaterThan(0.1);
  });
});

describe('the ladders', () => {
  it('are frozen', () => {
    expect(Object.isFrozen(NOISE_LADDER)).toBe(true);
    expect(Object.isFrozen(CLIPPING_LADDER)).toBe(true);
  });

  it('run best first, each in its own direction', () => {
    const noise = NOISE_LADDER.map((r) => r.limit);
    expect([...noise].sort((a, b) => b - a)).toEqual(noise);
    const clipping = CLIPPING_LADDER.map((r) => r.limit);
    expect([...clipping].sort((a, b) => a - b)).toEqual(clipping);
  });
});
