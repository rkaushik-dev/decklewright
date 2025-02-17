import { describe, expect, it } from 'vitest';

import { Angle } from '../../src/geom/angle.js';
import { Rect } from '../../src/geom/rect.js';
import { Size } from '../../src/geom/size.js';
import * as raster from '../../src/raster/index.js';

/**
 * A stand in for a capture: a grey bed with a lighter sheet on it, turned by a
 * degree and a half so nothing lines up with the pixel grid.
 *
 * @returns {import('../../src/raster/surface.js').Surface}
 */
function capture() {
  const s = raster.Surface.create({ width: 80, height: 60, layout: 'rgb', fill: 30 });
  for (let y = 10; y < 50; y += 1) {
    for (let x = 12; x < 68; x += 1) s.setPixel(x, y, [220, 215, 205]);
  }
  return raster.rotate(s, Angle.degrees(1.5), { expand: false, edge: 'clamp' }).surface;
}

describe('the barrel', () => {
  it('exports the two region classes and their base', () => {
    expect(typeof raster.Surface).toBe('function');
    expect(typeof raster.SurfaceView).toBe('function');
    expect(typeof raster.Raster).toBe('function');
  });

  it('exports the layout table', () => {
    expect(raster.LAYOUTS).toContain('rgba');
    expect(raster.channelCount('rgb')).toBe(3);
    expect(raster.maxValue(16)).toBe(65535);
  });

  it('exports every operation the stages above use', () => {
    for (const name of [
      'crop',
      'cropPadded',
      'pad',
      'resample',
      'rotate',
      'deskew',
      'toGray',
      'toDepth',
      'histogramOf',
      'integralOf',
      'channelStats',
      'kernelFor',
      'samplerFor',
    ]) {
      expect(typeof raster[name]).toBe('function');
    }
  });

  it('exports no undefined bindings', () => {
    for (const [name, value] of Object.entries(raster)) {
      expect(value, name).toBeDefined();
    }
  });
});

describe('a short run through the layer', () => {
  it('crops, deskews and reduces without leaving the range', () => {
    const raw = capture();
    const cut = raster.cropPadded(raw, Rect.of(8, 6, 64, 48), { fill: 0 });
    expect(cut.surface.size.toJSON()).toEqual({ width: 64, height: 48 });

    const straight = raster.deskew(cut.surface, Angle.degrees(1.5), { expand: false });
    expect(straight.surface.size.toJSON()).toEqual({ width: 64, height: 48 });

    const small = raster.resample(straight.surface, Size.of(16, 12), { kernel: 'catmullRom' });
    expect(small.size.toJSON()).toEqual({ width: 16, height: 12 });
    for (const value of small.data) {
      expect(value).toBeGreaterThanOrEqual(0);
      expect(value).toBeLessThanOrEqual(255);
    }
  });

  it('keeps the sheet brighter than the bed all the way down', () => {
    const raw = capture();
    const gray = raster.toGray(raw);
    const bed = raster.channelStats(new raster.SurfaceView(gray, Rect.of(0, 0, 8, 8)), 0);
    const sheet = raster.channelStats(new raster.SurfaceView(gray, Rect.of(30, 25, 8, 8)), 0);
    expect(sheet.mean).toBeGreaterThan(bed.mean + 100);

    const thumb = raster.fitLongestSide(gray, 20, { kernel: 'box' });
    expect(Math.max(thumb.width, thumb.height)).toBe(20);
    const thumbStats = raster.channelStats(thumb, 0);
    expect(thumbStats.max).toBeGreaterThan(thumbStats.min + 100);
  });

  it('finds a threshold between bed and sheet from the histogram alone', () => {
    const gray = raster.toGray(capture());
    const threshold = raster.histogramOf(gray).otsu();
    expect(threshold).toBeGreaterThan(30);
    expect(threshold).toBeLessThan(220);
  });

  it('promotes to sixteen bits and back without drift', () => {
    const raw = capture();
    const round = raster.toDepth(raster.toDepth(raw, 16), 8);
    expect(raster.difference(raw, round).peak).toBe(0);
  });
});
