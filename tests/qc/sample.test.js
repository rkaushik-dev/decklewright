import { describe, expect, it } from 'vitest';

import { Rect } from '../../src/geom/rect.js';
import {
  PATCH_MARGIN,
  channelMeans,
  gridOver,
  lightestFlatPatch,
  patchGrid,
  patchMeans,
  patchView,
} from '../../src/qc/sample.js';
import { Surface } from '../../src/raster/surface.js';
import { paint } from '../helpers/bench.js';

const WEDGE_RECTS = [
  Rect.of(0, 0, 30, 30),
  Rect.of(30, 0, 30, 30),
  Rect.of(60, 0, 30, 30),
  Rect.of(90, 0, 30, 30),
];

/**
 * @param {readonly (number|number[])[]} tones
 * @param {string} [layout]
 * @returns {Surface}
 */
function wedge(tones, layout = 'gray') {
  const surface = Surface.create({ width: 120, height: 30, layout });
  tones.forEach((tone, i) => paint(surface, WEDGE_RECTS[i], tone));
  return surface;
}

describe('patchView', () => {
  it('takes the middle of a patch', () => {
    const view = patchView(wedge([100]), WEDGE_RECTS[0]);
    expect(view?.width).toBeLessThan(30);
    expect(view?.width).toBeGreaterThan(10);
  });

  it('leaves the same share off both sides', () => {
    const view = patchView(wedge([100]), Rect.of(0, 0, 30, 30));
    expect(view?.width).toBe(30 - 2 * Math.floor(30 * PATCH_MARGIN));
  });

  it('takes the whole patch when asked for no margin', () => {
    expect(patchView(wedge([100]), WEDGE_RECTS[0], { margin: 0 })?.width).toBe(30);
  });

  it('gives nothing back for a patch off the capture', () => {
    expect(patchView(wedge([100]), Rect.of(500, 500, 10, 10))).toBeNull();
  });
});

describe('channelMeans', () => {
  it('reads every channel of a flat patch', () => {
    const surface = wedge([[10, 20, 30]], 'rgb');
    expect(channelMeans(surface, WEDGE_RECTS[0]).map(Math.round)).toEqual([10, 20, 30]);
  });

  it('ignores the edges of the patch', () => {
    const surface = wedge([[100, 100, 100]], 'rgb');
    paint(surface, Rect.of(0, 0, 30, 1), [0, 0, 0]);
    expect(channelMeans(surface, WEDGE_RECTS[0])[0]).toBe(100);
  });

  it('gives nothing back for a patch off the capture', () => {
    expect(channelMeans(wedge([[1, 1, 1]], 'rgb'), Rect.of(500, 500, 20, 20))).toEqual([]);
  });
});

describe('patchGrid', () => {
  it('makes one patch per cell', () => {
    expect(patchGrid(Rect.of(0, 0, 100, 100), 5, 4)).toHaveLength(20);
  });

  it('keeps every patch inside the region', () => {
    const within = Rect.of(10, 20, 100, 80);
    for (const patch of patchGrid(within, 4, 3)) {
      expect(within.containsRect(patch)).toBe(true);
    }
  });

  it('leaves a gap between patches', () => {
    const patches = patchGrid(Rect.of(0, 0, 100, 100), 2, 1);
    expect(patches[0].right).toBeLessThan(patches[1].left);
  });

  it('never makes a patch with no area', () => {
    for (const patch of patchGrid(Rect.of(0, 0, 5, 5), 4, 4)) {
      expect(patch.isEmpty).toBe(false);
    }
  });
});

describe('patchMeans', () => {
  it('reads one number per patch', () => {
    const surface = Surface.create({ width: 40, height: 40, layout: 'gray', fill: 100 });
    expect(patchMeans(surface, patchGrid(surface.bounds, 2, 2))).toEqual([100, 100, 100, 100]);
  });

  it('drops a patch that fell off the capture', () => {
    const surface = Surface.create({ width: 40, height: 40, layout: 'gray', fill: 100 });
    expect(patchMeans(surface, [Rect.of(0, 0, 10, 10), Rect.of(100, 100, 10, 10)])).toHaveLength(1);
  });

  it('reads the whole patch unless it is told a margin', () => {
    const surface = Surface.create({ width: 40, height: 40, layout: 'gray', fill: 100 });
    paint(surface, Rect.of(10, 10, 20, 20), 0);
    expect(patchMeans(surface, [Rect.of(0, 0, 40, 40)])[0]).toBeCloseTo(75, 6);
    expect(patchMeans(surface, [Rect.of(0, 0, 40, 40)], { margin: 0.4 })[0]).toBe(0);
  });
});

describe('gridOver', () => {
  it('is the plain grid when there is nothing to keep off', () => {
    const within = Rect.of(0, 0, 100, 100);
    expect(gridOver(within, 4, 4)).toHaveLength(patchGrid(within, 4, 4).length);
  });

  it('drops a patch that touches something it was told to avoid', () => {
    const within = Rect.of(0, 0, 100, 100);
    const kept = gridOver(within, 4, 4, { avoid: [Rect.of(0, 0, 50, 50)] });
    expect(kept.length).toBeLessThan(16);
    for (const patch of kept) {
      expect(patch.intersects(Rect.of(0, 0, 50, 50))).toBe(false);
    }
  });

  it('drops a patch whole rather than trimming it', () => {
    const within = Rect.of(0, 0, 100, 100);
    const sizes = new Set(
      gridOver(within, 4, 4, { avoid: [Rect.of(0, 0, 30, 30)] }).map((p) => p.width),
    );
    expect(sizes.size).toBe(1);
  });

  it('can end up with nothing left', () => {
    expect(gridOver(Rect.of(0, 0, 100, 100), 2, 2, { avoid: [Rect.of(0, 0, 100, 100)] })).toEqual(
      [],
    );
  });

  it('passes the margin through to the grid', () => {
    const wide = gridOver(Rect.of(0, 0, 100, 100), 2, 2, { margin: 0.05 });
    const tight = gridOver(Rect.of(0, 0, 100, 100), 2, 2, { margin: 0.4 });
    expect(wide[0].width).toBeGreaterThan(tight[0].width);
  });
});

describe('lightestFlatPatch', () => {
  it('picks the lightest patch that is not near clipping', () => {
    const view = lightestFlatPatch(
      wedge([40, 120, 200, 250]),
      WEDGE_RECTS.map((rect) => ({ rect })),
    );
    expect(view).not.toBeNull();
    expect(view?.get(0, 0)).toBe(200);
  });

  it('skips patches that are too dark', () => {
    expect(
      lightestFlatPatch(
        wedge([10, 20, 30, 40]),
        WEDGE_RECTS.map((rect) => ({ rect })),
      ),
    ).toBeNull();
  });

  it('skips patches that are near white', () => {
    expect(
      lightestFlatPatch(
        wedge([250, 252, 254, 255]),
        WEDGE_RECTS.map((rect) => ({ rect })),
      ),
    ).toBeNull();
  });

  it('takes another window when the collection wants one', () => {
    const view = lightestFlatPatch(
      wedge([10, 20, 30, 40]),
      WEDGE_RECTS.map((rect) => ({ rect })),
      { floor: 0.02, ceiling: 0.5 },
    );
    expect(view?.get(0, 0)).toBe(40);
  });

  it('gives nothing back when there are no patches at all', () => {
    expect(lightestFlatPatch(wedge([100]), [])).toBeNull();
  });
});
