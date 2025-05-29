import { describe, expect, it } from 'vitest';

import { componentIdAt, isUsable, readCapture, summarise } from '../../src/detect/capture.js';
import { objectMask } from '../../src/detect/background.js';
import { findSheet } from '../../src/detect/sheet.js';
import { Rect } from '../../src/geom/rect.js';
import { Sampling } from '../../src/measure/resolution.js';
import { bench, paint } from '../helpers/bench.js';

/**
 * A full bench: a turned sheet, a step wedge above it and a steel rule below.
 *
 * @param {object} [options]
 * @param {number} [options.degrees]
 * @param {boolean} [options.wedge]
 * @param {boolean} [options.rule]
 * @param {boolean} [options.ruled] draw a neatline on the sheet
 * @returns {import('../../src/raster/surface.js').Surface}
 */
function fullBench({ degrees = 0, wedge = true, rule = true, ruled = false } = {}) {
  const surface = bench({
    width: 260,
    height: 220,
    degrees,
    halfWidth: 70,
    halfHeight: 50,
    centreX: 140,
    centreY: 110,
  });

  if (wedge) {
    for (let i = 0; i < 8; i += 1) {
      const tone = 20 + Math.round((i / 7) * 210);
      paint(surface, Rect.of(14 + i * 10, 12, 10, 14), tone);
    }
  }
  if (rule) paint(surface, Rect.of(20, 200, 120, 7), 190);
  if (ruled) {
    paint(surface, Rect.of(85, 75, 110, 2), 30);
    paint(surface, Rect.of(85, 135, 110, 2), 30);
    paint(surface, Rect.of(85, 75, 2, 62), 30);
    paint(surface, Rect.of(193, 75, 2, 62), 30);
  }
  return surface;
}

describe('readCapture', () => {
  it('finds the sheet, the target and the rule', () => {
    const reading = readCapture(fullBench(), { sigma: 4 });
    expect(reading.sheet.confidence).toBeGreaterThan(0.5);
    expect(reading.target).not.toBeNull();
    expect(reading.scaleBar).not.toBeNull();
    expect(reading.warnings).toEqual([]);
  });

  it('does not mistake the sheet for the target or the rule', () => {
    const reading = readCapture(fullBench(), { sigma: 4 });
    const sheetBox = reading.sheet.quad.boundingRect;
    expect(reading.target?.rect.intersects(sheetBox)).toBe(false);
    expect(reading.scaleBar?.rect.intersects(sheetBox)).toBe(false);
  });

  it('warns when the target is missing', () => {
    const reading = readCapture(fullBench({ wedge: false }), { sigma: 4 });
    expect(reading.target).toBeNull();
    expect(reading.warnings).toContain('no colour target on the bed');
  });

  it('warns when the rule is missing', () => {
    const reading = readCapture(fullBench({ rule: false }), { sigma: 4 });
    expect(reading.scaleBar).toBeNull();
    expect(reading.warnings).toContain('no scale bar on the bed');
  });

  it('measures the sampling frequency off the rule', () => {
    const reading = readCapture(fullBench(), { sigma: 4, barLength: 6, barUnit: 'in' });
    expect(reading.sampling?.x).toBeCloseTo(20, 0);
  });

  it('says nothing about frequency when the rule length is unknown', () => {
    expect(readCapture(fullBench(), { sigma: 4 }).sampling).toBeNull();
  });

  it('complains when the rule disagrees with the scanner', () => {
    const reading = readCapture(fullBench(), {
      sigma: 4,
      barLength: 6,
      barUnit: 'in',
      claimed: Sampling.of(400),
    });
    expect(reading.warnings.join(' ')).toMatch(/against a claimed 400/);
  });

  it('accepts a rule that agrees with the scanner', () => {
    const reading = readCapture(fullBench(), {
      sigma: 4,
      barLength: 6,
      barUnit: 'in',
      claimed: Sampling.of(20),
    });
    expect(reading.warnings.join(' ')).not.toMatch(/against a claimed/);
  });

  it('looks for a neatline only when told to', () => {
    expect(readCapture(fullBench({ ruled: true }), { sigma: 4 }).neatline).toBeNull();
    const reading = readCapture(fullBench({ ruled: true }), {
      sigma: 4,
      expectNeatline: true,
    });
    expect(reading.neatline).not.toBeNull();
  });

  it('warns about the sides of a neatline it could not find', () => {
    const reading = readCapture(fullBench(), { sigma: 4, expectNeatline: true });
    expect(reading.warnings.join(' ')).toMatch(/no neatline found/);
  });

  it('reads a turned sheet as turned', () => {
    const reading = readCapture(fullBench({ degrees: 5 }), { sigma: 4 });
    expect(Math.abs(reading.sheet.skew.degrees - 5)).toBeLessThan(1);
  });
});

describe('componentIdAt', () => {
  it('finds the blob the sheet centre sits in', () => {
    const surface = fullBench();
    const sheet = findSheet(surface, { sigma: 4 });
    const { mask } = objectMask(surface, { sigma: 4 });
    expect(componentIdAt(mask, sheet)).toBeGreaterThan(0);
  });

  it('gives nothing back when the centre is not on any blob', () => {
    const surface = fullBench();
    const sheet = findSheet(surface, { sigma: 4 });
    const { mask } = objectMask(surface, { sigma: 4 });
    mask.data.fill(0);
    expect(componentIdAt(mask, sheet)).toBeNull();
  });
});

describe('summarise', () => {
  it('writes a line for each fact it established', () => {
    const lines = summarise(readCapture(fullBench(), { sigma: 4, barLength: 6 }));
    expect(lines[0]).toMatch(/^sheet \d+x\d+ at -?\d+\.\d\d deg$/);
    expect(lines.join('\n')).toMatch(/target over \d+ levels/);
    expect(lines.join('\n')).toMatch(/rule \d+ px long/);
    expect(lines.join('\n')).toMatch(/measured .* ppi/);
  });

  it('writes the warnings out too', () => {
    const lines = summarise(readCapture(fullBench({ rule: false }), { sigma: 4 }));
    expect(lines).toContain('warning: no scale bar on the bed');
  });

  it('reports the collar when a neatline was asked for', () => {
    const lines = summarise(
      readCapture(fullBench({ ruled: true }), { sigma: 4, expectNeatline: true }),
    );
    expect(lines.join('\n')).toMatch(/collar \d+\/\d+\/\d+\/\d+/);
  });
});

describe('isUsable', () => {
  it('accepts a clean capture', () => {
    expect(isUsable(readCapture(fullBench(), { sigma: 4 }))).toBe(true);
  });

  it('rejects a capture whose sheet ran off the bed', () => {
    const reading = readCapture(fullBench(), { sigma: 4 });
    expect(isUsable({ ...reading, sheet: { ...reading.sheet, touchesEdge: true } })).toBe(false);
  });

  it('rejects a capture the detector was unsure about', () => {
    const reading = readCapture(fullBench(), { sigma: 4 });
    expect(isUsable({ ...reading, sheet: { ...reading.sheet, confidence: 0.2 } })).toBe(false);
  });
});
