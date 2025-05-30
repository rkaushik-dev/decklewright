import { describe, expect, it } from 'vitest';

import * as detect from '../../src/detect/index.js';
import { encodePng } from '../../src/codec/png/encode.js';
import { readImage } from '../../src/codec/registry.js';
import { deskew } from '../../src/raster/ops/rotate.js';
import { crop } from '../../src/raster/ops/crop.js';
import { Rect } from '../../src/geom/rect.js';
import { bench, paint } from '../helpers/bench.js';

/**
 * @param {number} degrees
 * @returns {import('../../src/raster/surface.js').Surface}
 */
function accession(degrees) {
  const surface = bench({
    width: 260,
    height: 220,
    degrees,
    halfWidth: 70,
    halfHeight: 50,
    centreX: 140,
    centreY: 110,
    layout: 'rgb',
  });
  for (let i = 0; i < 8; i += 1) {
    const tone = 20 + Math.round((i / 7) * 210);
    paint(surface, Rect.of(14 + i * 10, 12, 10, 14), tone);
  }
  paint(surface, Rect.of(20, 200, 120, 7), 190);
  return surface;
}

describe('the barrel', () => {
  it('exports the one call the planner makes', () => {
    expect(typeof detect.readCapture).toBe('function');
  });

  it('exports every step, so a failure can be taken apart', () => {
    for (const name of [
      'objectMask',
      'open',
      'close',
      'labelComponents',
      'fitQuad',
      'edgeLines',
      'measureSkew',
      'refineCorners',
      'findNeatline',
      'locateTarget',
      'findScaleBar',
    ]) {
      expect(typeof detect[name]).toBe('function');
    }
  });

  it('exports no undefined bindings', () => {
    for (const [name, value] of Object.entries(detect)) {
      expect(value, name).toBeDefined();
    }
  });
});

describe('a capture read from a file', () => {
  it('survives a round trip through png', () => {
    const original = accession(4);
    const back = readImage(encodePng(original));
    const reading = detect.readCapture(back, { sigma: 4 });
    expect(Math.abs(reading.sheet.skew.degrees - 4)).toBeLessThan(1);
    expect(reading.target).not.toBeNull();
    expect(reading.scaleBar).not.toBeNull();
  });
});

describe('detect and straighten', () => {
  it('leaves almost no skew behind', () => {
    const surface = accession(6);
    const reading = detect.readCapture(surface, { sigma: 4 });
    const straight = deskew(surface, reading.sheet.skew, { expand: false, edge: 'clamp' }).surface;
    const after = detect.findSheet(straight, { sigma: 4 });
    expect(Math.abs(after.skew.degrees)).toBeLessThan(1);
  });

  it('leaves the sheet where the quad said it was', () => {
    const surface = accession(0);
    const reading = detect.readCapture(surface, { sigma: 4 });
    const box = reading.sheet.quad.boundingRect.outward();
    const cut = crop(surface, box);
    expect(cut.width).toBeGreaterThan(135);
    expect(cut.width).toBeLessThan(145);
    expect(cut.height).toBeGreaterThan(95);
    expect(cut.height).toBeLessThan(105);
  });
});

describe('the steps agree with the whole', () => {
  it('the mask the finder kept is the largest blob of the mask the estimator made', () => {
    const surface = accession(0);
    const reading = detect.readCapture(surface, { sigma: 4 });
    const { mask } = detect.objectMask(surface, { sigma: 4 });
    const largest = detect.largestComponent(detect.fillHoles(detect.close(mask, 3)));
    expect(largest?.component.area).toBeGreaterThan(reading.sheet.mask.count * 0.9);
  });

  it('the caliper angle and the projection angle agree on the same mask', () => {
    const reading = detect.readCapture(accession(3), { sigma: 4 });
    const calipers = detect.fitQuad(reading.sheet.mask).angle.degrees;
    const projection = detect.measureSkew(reading.sheet.mask).angle.degrees;
    expect(Math.abs(calipers - projection)).toBeLessThan(1);
  });
});
