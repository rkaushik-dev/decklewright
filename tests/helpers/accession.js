import { readCapture } from '../../src/detect/capture.js';
import { deriveFromPlan } from '../../src/derive/index.js';
import { Rect } from '../../src/geom/rect.js';
import { deliveryFor } from '../../src/manifest/index.js';
import { Sampling } from '../../src/measure/resolution.js';
import { planCrop } from '../../src/plan/crop.js';
import { ProfileRegistry } from '../../src/profile/registry.js';
import { derivativeRecipes } from '../../src/profile/settings.js';
import { runQc } from '../../src/qc/runner.js';
import { bench, paint } from './bench.js';

/**
 * A whole accession, worked the way the pipeline works one.
 *
 * Six test files were building this by hand and four of them were doing it
 * through the one shot path, which is now gone. Having one copy is worth more
 * than the duplication saved: when the order of the steps changes, the tests
 * that are supposed to notice are the ones about the order, not every test
 * that happens to need a delivery.
 */

const registry = new ProfileRegistry();

/**
 * A capture off the stand, with the wedge and the scale bar beside the sheet.
 *
 * @param {object} [options]
 * @param {number} [options.degrees]
 * @param {boolean} [options.wedge]
 * @returns {import('../../src/raster/surface.js').Surface}
 */
export function capture({ degrees = 0, wedge = true } = {}) {
  const surface = bench({
    width: 300,
    height: 240,
    degrees,
    halfWidth: 80,
    halfHeight: 56,
    centreX: 170,
    centreY: 130,
    layout: 'rgb',
  });
  if (wedge) {
    for (let i = 0; i < 8; i += 1) {
      paint(surface, Rect.of(14 + i * 10, 12, 10, 14), 20 + Math.round((i / 7) * 210));
    }
  }
  paint(surface, Rect.of(20, 220, 120, 7), 190);
  return surface;
}

/**
 * Read, plan, derive and grade one, and hand back every piece.
 *
 * @param {object} [options]
 * @param {string} [options.id]
 * @param {string} [options.profile]
 * @param {number} [options.degrees]
 * @param {number} [options.ppi]
 * @param {boolean} [options.graded]
 * @returns {Record<string, any>}
 */
export function worked({
  id = 'g4181-1907',
  profile: profileName = 'house',
  degrees = 0,
  ppi = 400,
  graded = true,
} = {}) {
  const surface = capture({ degrees });
  const profile = registry.get(profileName);
  const reading = readCapture(surface);
  const sampling = Sampling.of(ppi);
  const { plan } = planCrop({ reading, profile, capture: surface.bounds, sampling });

  const derived = deriveFromPlan({
    surface,
    plan,
    profile,
    accession: id,
    recipes: derivativeRecipes(profile),
  });

  return {
    accession: id,
    profile,
    surface,
    reading,
    plan,
    files: derived.files,
    steps: derived.steps,
    bytes: derived.bytes,
    quality: graded ? runQc({ surface, reading, profile, sampling, crop: plan.rect }) : undefined,
    warnings: [...plan.warnings],
  };
}

/**
 * The same, packed into a delivery ready to be written.
 *
 * @param {Parameters<typeof worked>[0] & {at?: Date, version?: string}} [options]
 * @returns {import('../../src/manifest/payload.js').Payload}
 */
export function delivery(options = {}) {
  return deliveryFor({
    ...worked(options),
    version: options.version ?? '1.4.0',
    at: options.at ?? new Date('2025-12-18T12:00:00Z'),
  });
}
