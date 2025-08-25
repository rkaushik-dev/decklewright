import { describe, expect, it } from 'vitest';

import { readCapture } from '../../src/detect/capture.js';
import { Angle } from '../../src/geom/angle.js';
import { Rect } from '../../src/geom/rect.js';
import { Sampling } from '../../src/measure/resolution.js';
import {
  intoStraightFrame,
  planCrop,
  pointsIntoStraightFrame,
  straightFrame,
} from '../../src/plan/crop.js';
import { ProfileRegistry } from '../../src/profile/registry.js';
import { bench, paint } from '../helpers/bench.js';

const registry = new ProfileRegistry();

/**
 * @param {object} [options]
 * @param {number} [options.degrees]
 * @param {boolean} [options.ruled]
 * @returns {import('../../src/raster/surface.js').Surface}
 */
function accession({ degrees = 0, ruled = false } = {}) {
  const surface = bench({
    width: 300,
    height: 240,
    degrees,
    halfWidth: 80,
    halfHeight: 56,
    centreX: 160,
    centreY: 120,
  });
  paint(surface, Rect.of(20, 210, 120, 7), 190);
  if (ruled) {
    paint(surface, Rect.of(95, 78, 130, 2), 30);
    paint(surface, Rect.of(95, 155, 130, 2), 30);
    paint(surface, Rect.of(95, 78, 2, 79), 30);
    paint(surface, Rect.of(223, 78, 2, 79), 30);
  }
  return surface;
}

describe('intoStraightFrame', () => {
  const capture = Rect.of(0, 0, 400, 300);
  const rect = Rect.of(100, 100, 200, 100);

  it('leaves everything alone when there is no skew', () => {
    const out = intoStraightFrame(rect, capture, Angle.zero());
    expect(out.rect).toBe(rect);
    expect(out.capture).toBe(capture);
  });

  it('moves the rect and grows the capture', () => {
    const out = intoStraightFrame(rect, capture, Angle.degrees(10));
    expect(out.capture.width).toBeGreaterThan(400);
    expect(out.capture.left).toBe(0);
    expect(out.rect.equals(rect)).toBe(false);
  });

  it('keeps the rect centred where it was, relative to the capture', () => {
    const middle = Rect.of(150, 100, 100, 100);
    const out = intoStraightFrame(middle, capture, Angle.degrees(8));
    const before = middle.centre;
    const after = out.rect.centre;
    expect(Math.abs(after.x - out.capture.width / 2)).toBeCloseTo(
      Math.abs(before.x - capture.width / 2),
      0,
    );
  });

  it('grows the rect, since an axis aligned box of a turned rect is bigger', () => {
    const out = intoStraightFrame(rect, capture, Angle.degrees(20));
    expect(out.rect.area).toBeGreaterThan(rect.area);
  });

  it('undoes itself when the skew goes the other way', () => {
    const there = intoStraightFrame(rect, capture, Angle.degrees(6));
    const back = intoStraightFrame(there.rect, there.capture, Angle.degrees(-6));
    expect(Math.abs(back.rect.centre.x - there.capture.width / 2)).toBeLessThan(
      there.capture.width,
    );
  });
});

describe('straightFrame', () => {
  const capture = Rect.of(0, 0, 400, 300);

  it('is the identity when there is no skew', () => {
    const frame = straightFrame(capture, Angle.zero());
    expect(frame.point(10, 20)).toEqual({ x: 10, y: 20 });
    expect(frame.capture).toBe(capture);
  });

  it('grows the canvas and puts it at the origin', () => {
    const frame = straightFrame(capture, Angle.degrees(10));
    expect(frame.capture.left).toBe(0);
    expect(frame.capture.top).toBe(0);
    expect(frame.capture.width).toBeGreaterThan(400);
  });

  it('leaves the centre of the capture at the centre of the new canvas', () => {
    const frame = straightFrame(capture, Angle.degrees(12));
    const middle = frame.point(200, 150);
    expect(middle.x).toBeCloseTo(frame.capture.width / 2, 6);
    expect(middle.y).toBeCloseTo(frame.capture.height / 2, 6);
  });
});

describe('pointsIntoStraightFrame', () => {
  const capture = Rect.of(0, 0, 400, 300);

  it('keeps a turned quad the size it really is', () => {
    const radians = (10 * Math.PI) / 180;
    const cos = Math.cos(radians);
    const sin = Math.sin(radians);
    const corners = [
      [-100, -50],
      [100, -50],
      [100, 50],
      [-100, 50],
    ].map(([x, y]) => ({ x: 200 + x * cos - y * sin, y: 150 + x * sin + y * cos }));

    const out = pointsIntoStraightFrame(corners, capture, Angle.degrees(10));
    expect(out.rect.width).toBeCloseTo(200, 6);
    expect(out.rect.height).toBeCloseTo(100, 6);
  });

  it('is bigger when the box is turned rather than the corners', () => {
    const radians = (10 * Math.PI) / 180;
    const cos = Math.cos(radians);
    const sin = Math.sin(radians);
    const corners = [
      [-100, -50],
      [100, -50],
      [100, 50],
      [-100, 50],
    ].map(([x, y]) => ({ x: 200 + x * cos - y * sin, y: 150 + x * sin + y * cos }));
    const boxed = Rect.bounding(corners.map((p) => ({ x: p.x, y: p.y, equals: () => false })));

    const tight = pointsIntoStraightFrame(corners, capture, Angle.degrees(10)).rect;
    const loose = intoStraightFrame(boxed, capture, Angle.degrees(10)).rect;
    expect(loose.width).toBeGreaterThan(tight.width);
  });
});

describe('planCrop', () => {
  /**
   * @param {string} name
   * @param {object} [options]
   * @returns {ReturnType<typeof planCrop>}
   */
  function plan(name, { degrees = 0, ruled = false, ...rest } = {}) {
    const surface = accession({ degrees, ruled });
    const profile = registry.get(name);
    const reading = readCapture(surface, {
      sigma: 4,
      expectNeatline: profile.capture.expectNeatline,
    });
    return planCrop(
      { reading, profile, capture: surface.bounds, sampling: Sampling.of(400) },
      rest,
    );
  }

  it('plans a straight sheet to the paper edge', () => {
    const out = plan('house');
    expect(out.settled).toBe(true);
    expect(out.plan.rect.width).toBeGreaterThan(150);
    expect(out.plan.rect.width).toBeLessThan(175);
    expect(out.plan.deskew.isZero).toBe(true);
  });

  it('keeps a bleed the profile asked for', () => {
    const plain = plan('house').plan.rect;
    const bled = plan('atlas-plates').plan.rect;
    expect(bled.width).toBeGreaterThan(plain.width);
  });

  it('records the skew it will take out', () => {
    const out = plan('house', { degrees: 5 });
    expect(Math.abs(out.plan.deskew.degrees - 5)).toBeLessThan(1);
  });

  it('plans against the straightened frame, not the raw one', () => {
    const straight = plan('house').plan.rect;
    const turned = plan('house', { degrees: 6 }).plan.rect;
    // the anchor is an axis aligned box round a turned sheet, so it is a little
    // larger, but it is the same crop and not one rotated away from it
    expect(Math.abs(turned.width - straight.width)).toBeLessThan(30);
    expect(Math.abs(turned.height - straight.height)).toBeLessThan(30);
  });

  it('carries the detector warnings onto the plan', () => {
    const out = plan('house');
    expect(out.plan.warnings.join(' ')).toMatch(/no colour target on the bed/);
  });

  it('names the rules it ran', () => {
    expect(plan('house').rules).toContain('notEmpty');
  });

  it('crops a quadrangle to the neatline', () => {
    const out = plan('quadrangles', { ruled: true, fallBackToSheet: true });
    expect(out.plan.rect.width).toBeGreaterThan(0);
    expect(out.rules).toContain('anchor');
  });

  it('refuses when there is no frequency to plan against', () => {
    const surface = accession();
    const reading = readCapture(surface, { sigma: 4 });
    expect(() =>
      planCrop({ reading, profile: registry.get('house'), capture: surface.bounds }),
    ).toThrow(/no sampling frequency to plan against/);
  });

  it('takes a measured frequency over the one the reading carried', () => {
    const surface = accession();
    const reading = readCapture(surface, { sigma: 4, barLength: 6 });
    const out = planCrop({
      reading,
      profile: registry.get('house'),
      capture: surface.bounds,
      sampling: Sampling.of(800),
    });
    expect(out.plan.sampling.x).toBe(800);
  });
});
