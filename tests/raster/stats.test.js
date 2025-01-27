import { describe, expect, it } from 'vitest';

import { Rect } from '../../src/geom/rect.js';
import {
  allChannelStats,
  channelStats,
  difference,
  meanPixel,
  meanProfile,
  signalToNoise,
} from '../../src/raster/stats.js';
import { Surface } from '../../src/raster/surface.js';
import { SurfaceView } from '../../src/raster/view.js';

/**
 * @param {readonly number[][]} rows
 * @returns {Surface}
 */
function gray(rows) {
  const s = Surface.create({ width: rows[0].length, height: rows.length, layout: 'gray' });
  rows.forEach((row, y) => row.forEach((value, x) => s.set(x, y, 0, value)));
  return s;
}

describe('channelStats', () => {
  it('reports the four summaries and the count', () => {
    const stats = channelStats(
      gray([
        [2, 4],
        [4, 6],
      ]),
      0,
    );
    expect(stats.min).toBe(2);
    expect(stats.max).toBe(6);
    expect(stats.mean).toBe(4);
    expect(stats.stdDev).toBeCloseTo(Math.sqrt(2), 12);
    expect(stats.count).toBe(4);
  });

  it('reports zero deviation on a flat patch', () => {
    const flat = Surface.create({ width: 8, height: 8, layout: 'gray', fill: 128 });
    const stats = channelStats(flat, 0);
    expect(stats.stdDev).toBe(0);
    expect(stats.mean).toBe(128);
  });

  it('agrees with a naive computation on a sixteen bit patch', () => {
    const s = Surface.create({ width: 4, height: 4, layout: 'gray', depth: 16 });
    const values = [];
    for (let i = 0; i < 16; i += 1) {
      const value = 40000 + (i % 7);
      values.push(value);
      s.set(i % 4, Math.floor(i / 4), 0, value);
    }
    const naive = values.reduce((a, b) => a + b, 0) / values.length;
    expect(channelStats(s, 0).mean).toBeCloseTo(naive, 9);
  });

  it('measures only what a view covers', () => {
    const s = gray([
      [0, 0, 0],
      [0, 100, 0],
      [0, 0, 0],
    ]);
    const middle = new SurfaceView(s, Rect.of(1, 1, 1, 1));
    expect(channelStats(middle, 0).mean).toBe(100);
  });

  it('refuses a channel outside the layout', () => {
    expect(() => channelStats(gray([[1]]), 3)).toThrow(/outside the layout/);
  });
});

describe('allChannelStats and meanPixel', () => {
  it('gives one entry per channel', () => {
    const s = Surface.create({ width: 2, height: 1, layout: 'rgb' });
    s.setPixel(0, 0, [0, 10, 20]);
    s.setPixel(1, 0, [100, 30, 20]);
    const all = allChannelStats(s);
    expect(all).toHaveLength(3);
    expect(all[0].mean).toBe(50);
    expect(meanPixel(s)).toEqual([50, 20, 20]);
  });
});

describe('signalToNoise', () => {
  it('is infinite on a perfectly flat patch', () => {
    const flat = Surface.create({ width: 4, height: 4, layout: 'gray', fill: 200 });
    expect(signalToNoise(flat)).toBe(Number.POSITIVE_INFINITY);
  });

  it('drops as the noise grows', () => {
    const quiet = gray([[100, 101, 100, 99]]);
    const noisy = gray([[100, 130, 70, 100]]);
    expect(signalToNoise(quiet)).toBeGreaterThan(signalToNoise(noisy));
  });

  it('matches the decibel definition', () => {
    const s = gray([
      [90, 110],
      [90, 110],
    ]);
    expect(signalToNoise(s)).toBeCloseTo(20 * Math.log10(100 / 10), 9);
  });
});

describe('difference', () => {
  it('is zero between a surface and its clone', () => {
    const s = gray([
      [1, 2],
      [3, 4],
    ]);
    expect(difference(s, s.clone())).toEqual({ rms: 0, peak: 0, count: 4 });
  });

  it('reports the root mean square and the worst sample', () => {
    const a = gray([[0, 0]]);
    const b = gray([[3, 4]]);
    const out = difference(a, b);
    expect(out.peak).toBe(4);
    expect(out.rms).toBeCloseTo(Math.sqrt((9 + 16) / 2), 12);
  });

  it('refuses two regions of different shape', () => {
    expect(() => difference(gray([[1, 2]]), gray([[1]]))).toThrow(/not the same shape/);
  });
});

describe('meanProfile', () => {
  const s = gray([
    [0, 10, 20],
    [30, 40, 50],
  ]);

  it('averages along rows by default', () => {
    expect([...meanProfile(s)]).toEqual([10, 40]);
  });

  it('averages down columns when asked', () => {
    expect([...meanProfile(s, { along: 'column' })]).toEqual([15, 25, 35]);
  });

  it('reads the channel it is told to', () => {
    const colour = Surface.create({ width: 2, height: 1, layout: 'rgb' });
    colour.setPixel(0, 0, [0, 60, 0]);
    colour.setPixel(1, 0, [0, 20, 0]);
    expect([...meanProfile(colour, { channel: 1 })]).toEqual([40]);
  });

  it('refuses an axis or channel it does not know', () => {
    expect(() => meanProfile(s, { along: 'diagonal' })).toThrow(/along must be one of/);
    expect(() => meanProfile(s, { channel: 4 })).toThrow(/outside the layout/);
  });

  it('runs over a view in view local coordinates', () => {
    const view = new SurfaceView(s, Rect.of(1, 0, 2, 2));
    expect([...meanProfile(view)]).toEqual([15, 45]);
  });
});
