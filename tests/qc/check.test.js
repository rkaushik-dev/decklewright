import { describe, expect, it } from 'vitest';

import {
  MAX_STARS,
  assertNoDuplicates,
  check,
  measurement,
  missingFrom,
  runCheck,
  runChecks,
  starsFor,
} from '../../src/qc/check.js';

/**
 * @param {number} stars
 * @returns {import('../../src/qc/check.js').Check}
 */
function always(stars) {
  return check({ name: `always${stars}`, run: () => measurement(stars, stars, 'measured') });
}

describe('check', () => {
  it('builds a frozen check', () => {
    const made = always(4);
    expect(Object.isFrozen(made)).toBe(true);
    expect(Object.isFrozen(made.needs)).toBe(true);
    expect(made.name).toBe('always4');
  });

  it('refuses a check with no name or no body', () => {
    expect(() => check({ name: '', run: () => measurement(1, 1, 'x') })).toThrow(/non empty/);
    expect(() => check({ name: 'x', run: null })).toThrow(/nothing to run/);
  });
});

describe('missingFrom', () => {
  it('names what the context does not have', () => {
    expect(missingFrom({ surface: {} }, ['surface', 'target'])).toEqual(['target']);
    expect(missingFrom({ surface: {}, target: null }, ['target'])).toEqual(['target']);
    expect(missingFrom({ surface: {} }, [])).toEqual([]);
  });
});

describe('runCheck', () => {
  it('passes a check that reaches the star floor', () => {
    const out = runCheck(always(4), {}, { minimumStars: 3 });
    expect(out.ran).toBe(true);
    expect(out.passed).toBe(true);
    expect(out.stars).toBe(4);
    expect(out.limit).toBe('3 stars');
  });

  it('fails a check that does not', () => {
    const out = runCheck(always(2), {}, { minimumStars: 3 });
    expect(out.ran).toBe(true);
    expect(out.passed).toBe(false);
  });

  it('separates could not run from failed', () => {
    const needy = check({
      name: 'needy',
      needs: ['target'],
      run: () => measurement(1, 4, 'x'),
    });
    const out = runCheck(needy, {}, {});
    expect(out.ran).toBe(false);
    expect(out.passed).toBe(false);
    expect(out.note).toBe('nothing to measure, no target');
    expect(out.measured).toBeNaN();
  });

  it('runs a check whose needs are met', () => {
    const needy = check({
      name: 'needy',
      needs: ['target'],
      run: (context) => measurement(context.target.value, 4, 'read the target'),
    });
    expect(runCheck(needy, { target: { value: 7 } }).measured).toBe(7);
  });

  it('catches a check that throws rather than stopping the batch', () => {
    const angry = check({
      name: 'angry',
      run: () => {
        throw new Error('the patch is off the capture');
      },
    });
    const out = runCheck(angry, {});
    expect(out.ran).toBe(false);
    expect(out.note).toBe('the patch is off the capture');
  });

  it('uses three stars as the floor by default', () => {
    expect(runCheck(always(3), {}).passed).toBe(true);
    expect(runCheck(always(2), {}).passed).toBe(false);
  });
});

describe('runChecks', () => {
  it('runs each one and keeps the order', () => {
    const out = runChecks([always(4), always(2)], {}, { minimumStars: 3 });
    expect(out.map((f) => f.name)).toEqual(['always4', 'always2']);
    expect(out.map((f) => f.passed)).toEqual([true, false]);
  });

  it('gives an empty list for no checks', () => {
    expect(runChecks([], {})).toEqual([]);
  });
});

describe('starsFor', () => {
  const ladder = [
    { stars: 4, limit: 1 },
    { stars: 3, limit: 2 },
    { stars: 2, limit: 4 },
    { stars: 1, limit: 8 },
  ];

  it('walks down the ladder for a lower is better measure', () => {
    expect(starsFor(0.5, ladder)).toBe(4);
    expect(starsFor(1.5, ladder)).toBe(3);
    expect(starsFor(3, ladder)).toBe(2);
    expect(starsFor(20, ladder)).toBe(0);
  });

  it('takes the limits inclusively', () => {
    expect(starsFor(1, ladder)).toBe(4);
    expect(starsFor(2, ladder)).toBe(3);
  });

  it('walks the other way for a higher is better measure', () => {
    const snr = [
      { stars: 4, limit: 40 },
      { stars: 3, limit: 30 },
      { stars: 2, limit: 20 },
    ];
    expect(starsFor(45, snr, { higherIsBetter: true })).toBe(4);
    expect(starsFor(31, snr, { higherIsBetter: true })).toBe(3);
    expect(starsFor(5, snr, { higherIsBetter: true })).toBe(0);
  });

  it('gives no stars for a measurement that is not a number', () => {
    expect(starsFor(Number.NaN, ladder)).toBe(0);
  });

  it('gives the top rung to an infinite reading that is better when higher', () => {
    const snr = [
      { stars: 4, limit: 40 },
      { stars: 3, limit: 30 },
    ];
    expect(starsFor(Number.POSITIVE_INFINITY, snr, { higherIsBetter: true })).toBe(4);
    expect(starsFor(Number.POSITIVE_INFINITY, ladder)).toBe(0);
  });

  it('never awards more than the maximum', () => {
    expect(MAX_STARS).toBe(4);
    expect(starsFor(0, ladder)).toBeLessThanOrEqual(MAX_STARS);
  });
});

describe('assertNoDuplicates', () => {
  it('catches the same check twice', () => {
    expect(() => assertNoDuplicates([always(4), always(4)])).toThrow(
      /the always4 check is in the set twice/,
    );
    expect(() => assertNoDuplicates([always(4), always(3)])).not.toThrow();
  });
});
