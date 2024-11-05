import { describe, expect, it } from 'vitest';

import {
  approxEqual,
  ceilToMultiple,
  clamp,
  floorToMultiple,
  gcd,
  isEven,
  lerp,
  mean,
  median,
  percentile,
  roundHalfEven,
  roundTo,
  roundToMultiple,
  stdDev,
  sum,
} from '../../src/util/num.js';

describe('clamp', () => {
  it('holds the value inside the band', () => {
    expect(clamp(5, 0, 10)).toBe(5);
    expect(clamp(-2, 0, 10)).toBe(0);
    expect(clamp(11, 0, 10)).toBe(10);
  });
});

describe('lerp', () => {
  it('hits both ends exactly', () => {
    expect(lerp(2, 8, 0)).toBe(2);
    expect(lerp(2, 8, 1)).toBe(8);
    expect(lerp(2, 8, 0.5)).toBe(5);
  });

  it('extrapolates outside the unit interval', () => {
    expect(lerp(0, 10, 1.5)).toBe(15);
  });
});

describe('approxEqual', () => {
  it('uses the default epsilon and honours an override', () => {
    expect(approxEqual(0.1 + 0.2, 0.3)).toBe(true);
    expect(approxEqual(1, 1.001)).toBe(false);
    expect(approxEqual(1, 1.001, 0.01)).toBe(true);
  });
});

describe('roundHalfEven', () => {
  it('sends exact halves to the even neighbour', () => {
    expect(roundHalfEven(0.5)).toBe(0);
    expect(roundHalfEven(1.5)).toBe(2);
    expect(roundHalfEven(2.5)).toBe(2);
    expect(roundHalfEven(3.5)).toBe(4);
  });

  it('rounds normally away from the halves', () => {
    expect(roundHalfEven(2.4)).toBe(2);
    expect(roundHalfEven(2.6)).toBe(3);
  });

  it('handles negatives through the floor', () => {
    expect(roundHalfEven(-2.5)).toBe(-2);
    expect(roundHalfEven(-2.6)).toBe(-3);
  });
});

describe('the multiple helpers', () => {
  it('rounds to the nearest step', () => {
    expect(roundToMultiple(13, 4)).toBe(12);
    expect(roundToMultiple(14, 4)).toBe(16);
  });

  it('floors and ceils to the step', () => {
    expect(floorToMultiple(13, 4)).toBe(12);
    expect(ceilToMultiple(13, 4)).toBe(16);
    expect(floorToMultiple(12, 4)).toBe(12);
    expect(ceilToMultiple(12, 4)).toBe(12);
  });

  it('refuses a step of zero', () => {
    expect(() => roundToMultiple(1, 0)).toThrow(/step must be greater than zero/);
  });
});

describe('roundTo', () => {
  it('keeps the requested number of places', () => {
    expect(roundTo(3.14159, 2)).toBe(3.14);
    expect(roundTo(2.005, 2)).toBeCloseTo(2.0, 5);
    expect(roundTo(1234.5, -2)).toBe(1200);
  });
});

describe('the summary statistics', () => {
  const values = [4, 8, 15, 16, 23, 42];

  it('sums and averages', () => {
    expect(sum(values)).toBe(108);
    expect(mean(values)).toBe(18);
  });

  it('interpolates a percentile between samples', () => {
    expect(percentile(values, 0)).toBe(4);
    expect(percentile(values, 1)).toBe(42);
    expect(percentile(values, 0.5)).toBe(15.5);
    expect(median(values)).toBe(15.5);
  });

  it('clamps a percentile fraction that runs off the end', () => {
    expect(percentile(values, 2)).toBe(42);
    expect(percentile(values, -1)).toBe(4);
  });

  it('leaves the input untouched when sorting', () => {
    const input = [3, 1, 2];
    percentile(input, 0.5);
    expect(input).toEqual([3, 1, 2]);
  });

  it('reports the population deviation', () => {
    expect(stdDev([2, 4, 4, 4, 5, 5, 7, 9])).toBe(2);
    expect(stdDev([5])).toBe(0);
  });

  it('answers not a number for an empty run', () => {
    expect(mean([])).toBeNaN();
    expect(median([])).toBeNaN();
    expect(stdDev([])).toBeNaN();
    expect(sum([])).toBe(0);
  });
});

describe('gcd and isEven', () => {
  it('reduces a ratio', () => {
    expect(gcd(1200, 800)).toBe(400);
    expect(gcd(0, 5)).toBe(5);
    expect(gcd(-12, 18)).toBe(6);
  });

  it('spots even pixel counts', () => {
    expect(isEven(4)).toBe(true);
    expect(isEven(5)).toBe(false);
    expect(isEven(-2)).toBe(true);
  });
});
