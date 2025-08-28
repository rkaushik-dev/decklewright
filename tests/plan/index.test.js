import { describe, expect, it } from 'vitest';

import { readCapture } from '../../src/detect/capture.js';
import { Rect } from '../../src/geom/rect.js';
import { Sampling } from '../../src/measure/resolution.js';
import * as plan from '../../src/plan/index.js';
import { ProfileRegistry } from '../../src/profile/registry.js';
import { cropOptions } from '../../src/profile/settings.js';
import { crop } from '../../src/raster/ops/crop.js';
import { deskew } from '../../src/raster/ops/rotate.js';
import { bench, paint } from '../helpers/bench.js';

const registry = new ProfileRegistry();

/**
 * @param {number} degrees
 * @returns {import('../../src/raster/surface.js').Surface}
 */
function accession(degrees) {
  const surface = bench({
    width: 320,
    height: 260,
    degrees,
    halfWidth: 84,
    halfHeight: 60,
    centreX: 170,
    centreY: 130,
  });
  paint(surface, Rect.of(20, 230, 120, 7), 190);
  return surface;
}

describe('the barrel', () => {
  it('exports the one call the pipeline makes', () => {
    expect(typeof plan.planCrop).toBe('function');
  });

  it('exports every rule builder', () => {
    for (const name of [
      'anchorRule',
      'bleedRule',
      'safeAreaRule',
      'aspectRule',
      'resolutionRule',
      'snapRule',
      'boundsFor',
      'notEmptyRule',
    ]) {
      expect(typeof plan[name]).toBe('function');
    }
  });

  it('exports no undefined bindings', () => {
    for (const [name, value] of Object.entries(plan)) {
      expect(value, name).toBeDefined();
    }
  });
});

describe('a plan carried through to a cut', () => {
  it('cuts a straight sheet where the plan said', () => {
    const surface = accession(0);
    const reading = readCapture(surface, { sigma: 4 });
    const out = plan.planCrop({
      reading,
      profile: registry.get('house'),
      capture: surface.bounds,
      sampling: Sampling.of(400),
    });

    const cut = crop(surface, out.plan.rect.intersect(surface.bounds));
    expect(cut.width).toBeGreaterThan(160);
    expect(cut.height).toBeGreaterThan(110);
  });

  it('straightens first and then cuts, and the sheet fills the cut', () => {
    const surface = accession(6);
    const reading = readCapture(surface, { sigma: 4 });
    const out = plan.planCrop({
      reading,
      profile: registry.get('house'),
      capture: surface.bounds,
      sampling: Sampling.of(400),
    });

    const straight = deskew(surface, out.plan.deskew, { edge: 'clamp' }).surface;
    const held = out.plan.rect.intersect(straight.bounds);
    expect(held.isEmpty).toBe(false);

    const cut = crop(straight, held);
    const middle = cut.get(Math.floor(cut.width / 2), Math.floor(cut.height / 2));
    expect(middle).toBeGreaterThan(180);
  });
});

describe('overriding one rule for one item', () => {
  it('drops the bleed rule and the crop comes back smaller', () => {
    const surface = accession(0);
    const reading = readCapture(surface, { sigma: 4 });
    const profile = registry.get('atlas-plates');
    const capture = surface.bounds;
    const sampling = Sampling.of(400);
    const anchor = reading.sheet.quad.boundingRect.outward();
    const settings = cropOptions(profile, sampling);

    const full = plan.rulesFor(settings, { sheet: anchor });
    expect(full.map((r) => r.name)).toContain('bleed');

    const withBleed = plan.planWith(full, { capture, anchor, sampling });
    const without = plan.planWith(plan.without(full, ['bleed']), { capture, anchor, sampling });
    expect(without.rect.width).toBeLessThan(withBleed.rect.width);
  });

  it('replaces the snap and the crop lands on the new multiple', () => {
    const anchor = Rect.of(100, 100, 501, 401);
    const capture = Rect.of(0, 0, 2000, 1600);
    const rules = [plan.anchorRule(anchor, 'sheet'), plan.snapRule(8), plan.notEmptyRule()];
    const swapped = plan.withRule(rules, plan.snapRule(64));
    const out = plan.planWith(swapped, { capture, anchor, sampling: Sampling.of(400) });
    expect(out.rect.width % 64).toBe(0);
  });
});
