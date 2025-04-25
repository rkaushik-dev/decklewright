import { describe, expect, it } from 'vitest';

import { Mask } from '../../src/detect/mask.js';
import {
  gradient,
  project,
  projectedExtent,
  rampWidths,
  smooth,
  span,
} from '../../src/detect/profile.js';

/**
 * @param {readonly string[]} rows
 * @returns {Mask}
 */
function maskOf(rows) {
  const mask = new Mask(rows[0].length, rows.length);
  rows.forEach((row, y) => {
    [...row].forEach((ch, x) => mask.set(x, y, ch === '#' ? 1 : 0));
  });
  return mask;
}

const block = maskOf(['.....', '.###.', '.###.', '.###.', '.....']);

describe('project', () => {
  it('sums down columns for the x axis', () => {
    expect([...project(block, 'x')]).toEqual([0, 3, 3, 3, 0]);
  });

  it('sums across rows for the y axis', () => {
    expect([...project(block, 'y')]).toEqual([0, 3, 3, 3, 0]);
  });

  it('is all zero for an empty mask', () => {
    expect([...project(new Mask(3, 2), 'x')]).toEqual([0, 0, 0]);
  });

  it('refuses an axis it does not know', () => {
    expect(() => project(block, 'z')).toThrow(/axis must be one of x, y/);
  });
});

describe('smooth', () => {
  it('is a copy at a window of one', () => {
    const profile = Float64Array.from([1, 2, 3]);
    const out = smooth(profile, 1);
    expect([...out]).toEqual([1, 2, 3]);
    expect(out).not.toBe(profile);
  });

  it('averages the neighbourhood', () => {
    expect([...smooth(Float64Array.from([0, 3, 0]), 3)]).toEqual([1.5, 1, 1.5]);
  });

  it('shortens the window at the ends rather than padding with zero', () => {
    expect([...smooth(Float64Array.from([4, 4, 4]), 3)]).toEqual([4, 4, 4]);
  });

  it('refuses a window that is not a whole number above zero', () => {
    expect(() => smooth(Float64Array.from([1]), 0)).toThrow(/above zero/);
  });
});

describe('gradient', () => {
  it('differences neighbours and opens with zero', () => {
    expect([...gradient(Float64Array.from([0, 0, 5, 5, 0]))]).toEqual([0, 0, 5, 0, -5]);
  });
});

describe('span', () => {
  it('finds where the profile is above half its peak', () => {
    const out = span(Float64Array.from([0, 0, 4, 4, 4, 0, 0]));
    expect(out.start).toBe(2);
    expect(out.end).toBe(5);
    expect(out.peak).toBe(4);
    expect(out.threshold).toBe(2);
  });

  it('takes another fraction', () => {
    const out = span(Float64Array.from([0, 1, 4, 1, 0]), { fraction: 0.2 });
    expect(out.start).toBe(1);
    expect(out.end).toBe(4);
  });

  it('spans a profile with a gap in the middle from first to last', () => {
    const out = span(Float64Array.from([4, 0, 4]));
    expect(out.start).toBe(0);
    expect(out.end).toBe(3);
  });

  it('refuses a flat zero profile', () => {
    expect(() => span(Float64Array.from([0, 0, 0]))).toThrow(/flat at zero/);
  });
});

describe('projectedExtent', () => {
  it('boxes a square block exactly', () => {
    expect(projectedExtent(block).toJSON()).toEqual({ x: 1, y: 1, width: 3, height: 3 });
  });

  it('boxes an off centre block', () => {
    const mask = maskOf(['......', '..####', '..####', '......']);
    expect(projectedExtent(mask).toJSON()).toEqual({ x: 2, y: 1, width: 4, height: 2 });
  });

  it('grows the box for a skewed block, since the box is axis aligned', () => {
    const mask = maskOf([
      '........',
      '..##....',
      '.####...',
      '..####..',
      '...####.',
      '....##..',
      '........',
    ]);
    const extent = projectedExtent(mask, { fraction: 0.25 });
    expect(extent.width).toBeGreaterThan(4);
  });

  it('takes a smoothing window', () => {
    const mask = maskOf(['.....', '.###.', '.#.#.', '.###.', '.....']);
    expect(projectedExtent(mask, { smoothing: 3 }).width).toBeGreaterThan(0);
  });
});

describe('rampWidths', () => {
  it('is near zero for a square edged profile', () => {
    const out = rampWidths(Float64Array.from([0, 0, 10, 10, 10, 0, 0]));
    expect(out.leading).toBeLessThanOrEqual(1);
    expect(out.trailing).toBeLessThanOrEqual(1);
  });

  it('grows with the width of the ramp', () => {
    const sloped = Float64Array.from([0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 10, 10]);
    expect(rampWidths(sloped).leading).toBeGreaterThan(5);
  });

  it('measures the two ends separately', () => {
    const lopsided = Float64Array.from([0, 1, 2, 3, 4, 5, 10, 10, 10, 0, 0]);
    const out = rampWidths(lopsided);
    expect(out.leading).toBeGreaterThan(out.trailing);
  });

  it('reports zero when the profile never reaches the high mark cleanly', () => {
    const out = rampWidths(Float64Array.from([10, 10, 10]));
    expect(out.leading).toBe(0);
    expect(out.trailing).toBe(0);
  });
});
