import { describe, expect, it } from 'vitest';

import {
  collarWidths,
  darkness,
  findNeatline,
  looksLikeQuadrangle,
  strongestLine,
} from '../../src/detect/neatline.js';
import { Rect } from '../../src/geom/rect.js';
import { Surface } from '../../src/raster/surface.js';
import { paint } from '../helpers/bench.js';

const SHEET = Rect.of(20, 20, 160, 120);

/**
 * A sheet of paper with a ruled border drawn on it, and a wider collar at the
 * bottom the way a quadrangle carries its legend.
 *
 * @param {object} [options]
 * @param {number} [options.inset] collar on the top, left and right
 * @param {number} [options.bottomInset]
 * @param {number} [options.ink] how dark the ruled line is
 * @param {readonly string[]} [options.omit] sides to leave undrawn
 * @returns {Surface}
 */
function mapSheet({ inset = 12, bottomInset = 30, ink = 30, omit = [] } = {}) {
  const surface = Surface.create({ width: 200, height: 160, layout: 'gray', fill: 40 });
  paint(surface, SHEET, 226);

  const line = Rect.fromEdges(
    SHEET.left + inset,
    SHEET.top + inset,
    SHEET.right - inset,
    SHEET.bottom - bottomInset,
  );
  if (!omit.includes('top')) paint(surface, Rect.of(line.left, line.top, line.width, 2), ink);
  if (!omit.includes('bottom')) {
    paint(surface, Rect.of(line.left, line.bottom - 2, line.width, 2), ink);
  }
  if (!omit.includes('left')) paint(surface, Rect.of(line.left, line.top, 2, line.height), ink);
  if (!omit.includes('right')) {
    paint(surface, Rect.of(line.right - 2, line.top, 2, line.height), ink);
  }
  return surface;
}

describe('darkness', () => {
  it('is high where the paper is dark', () => {
    const surface = Surface.create({ width: 10, height: 4, layout: 'gray', fill: 200 });
    paint(surface, Rect.of(0, 1, 10, 1), 20);
    const profile = darkness(surface, surface.bounds, 'row');
    expect(profile[1]).toBeGreaterThan(profile[0]);
    expect(profile[0]).toBeCloseTo(55, 6);
  });

  it('reads only the region it is given', () => {
    const surface = Surface.create({ width: 10, height: 4, layout: 'gray', fill: 200 });
    paint(surface, Rect.of(0, 0, 10, 1), 0);
    const profile = darkness(surface, Rect.of(0, 1, 10, 3), 'row');
    expect(profile).toHaveLength(3);
    expect(Math.max(...profile)).toBeCloseTo(55, 6);
  });
});

describe('strongestLine', () => {
  it('finds the trough and reports its contrast', () => {
    const profile = Float64Array.from([0, 0, 40, 0, 0]);
    expect(strongestLine(profile, 0, 5)).toEqual({ at: 2, contrast: 40 });
  });

  it('looks only inside the window', () => {
    const profile = Float64Array.from([0, 0, 40, 0, 0]);
    expect(strongestLine(profile, 3, 5).at).toBe(-1);
  });

  it('takes the window either way round', () => {
    const profile = Float64Array.from([0, 0, 40, 0, 0]);
    expect(strongestLine(profile, 5, 0).at).toBe(2);
  });

  it('reports nothing for a window with no room', () => {
    expect(strongestLine(Float64Array.from([1, 2, 3]), 1, 1)).toEqual({ at: -1, contrast: 0 });
  });

  it('reports no contrast on a flat run', () => {
    expect(strongestLine(Float64Array.from([5, 5, 5, 5]), 0, 4).contrast).toBe(0);
  });
});

describe('findNeatline', () => {
  it('lands on the ruled border', () => {
    const found = findNeatline(mapSheet(), SHEET);
    expect(found.complete).toBe(true);
    expect(found.rect.left).toBe(32);
    expect(found.rect.top).toBe(32);
    expect(Math.abs(found.rect.right - 168)).toBeLessThanOrEqual(2);
    expect(Math.abs(found.rect.bottom - 110)).toBeLessThanOrEqual(2);
  });

  it('reports the contrast it found on each side', () => {
    const { contrast } = findNeatline(mapSheet(), SHEET);
    for (const side of ['top', 'right', 'bottom', 'left']) {
      expect(contrast[side]).toBeGreaterThan(12);
    }
  });

  it('names the sides it could not find', () => {
    const found = findNeatline(mapSheet({ omit: ['right'] }), SHEET);
    expect(found.complete).toBe(false);
    expect(found.missing).toEqual(['right']);
    expect(found.rect.right).toBe(SHEET.right);
  });

  it('falls back to the paper edge on every missing side', () => {
    const plain = Surface.create({ width: 200, height: 160, layout: 'gray', fill: 40 });
    paint(plain, SHEET, 226);
    const found = findNeatline(plain, SHEET);
    expect(found.missing).toHaveLength(4);
    expect(found.rect.equals(SHEET)).toBe(true);
  });

  it('can be told to demand more contrast', () => {
    const faint = mapSheet({ ink: 200 });
    expect(findNeatline(faint, SHEET, { minimumContrast: 5 }).complete).toBe(true);
    expect(findNeatline(faint, SHEET, { minimumContrast: 40 }).complete).toBe(false);
  });

  it('does not look further in than the band allows', () => {
    const found = findNeatline(mapSheet({ inset: 50, bottomInset: 50 }), SHEET, { band: 0.1 });
    expect(found.missing.length).toBeGreaterThan(0);
  });

  it('refuses a band outside the sensible range', () => {
    expect(() => findNeatline(mapSheet(), SHEET, { band: 0.6 })).toThrow(/band must be between/);
  });
});

describe('collarWidths', () => {
  it('measures each side', () => {
    const widths = collarWidths(Rect.of(0, 0, 100, 100), Rect.of(10, 5, 80, 70));
    expect(widths).toEqual({ top: 5, right: 10, bottom: 25, left: 10 });
  });

  it('is zero all round when the neatline is the paper edge', () => {
    const sheet = Rect.of(0, 0, 100, 100);
    expect(collarWidths(sheet, sheet)).toEqual({ top: 0, right: 0, bottom: 0, left: 0 });
  });
});

describe('looksLikeQuadrangle', () => {
  it('accepts a wide bottom collar', () => {
    expect(looksLikeQuadrangle({ top: 10, right: 10, bottom: 30, left: 10 })).toBe(true);
  });

  it('rejects a collar that is widest somewhere else', () => {
    expect(looksLikeQuadrangle({ top: 30, right: 10, bottom: 10, left: 10 })).toBe(false);
    expect(looksLikeQuadrangle({ top: 0, right: 0, bottom: 0, left: 0 })).toBe(false);
  });

  it('agrees with what the detector found on a real looking sheet', () => {
    const found = findNeatline(mapSheet(), SHEET);
    expect(looksLikeQuadrangle(collarWidths(SHEET, found.rect))).toBe(true);
  });
});
