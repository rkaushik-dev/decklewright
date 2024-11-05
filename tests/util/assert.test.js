import { describe, expect, it } from 'vitest';

import { ERROR_CODES } from '../../src/errors.js';
import {
  assert,
  assertArrayOf,
  assertFinite,
  assertInRange,
  assertInteger,
  assertNonEmptyString,
  assertNonNegative,
  assertOneOf,
  assertPlainObject,
  assertPositive,
  assertPositiveInteger,
} from '../../src/util/assert.js';

describe('assert', () => {
  it('passes a truthy condition through silently', () => {
    expect(() => assert(1, 'never')).not.toThrow();
  });

  it('throws a bad argument error carrying the details', () => {
    try {
      assert(false, 'bed width is wrong', { width: 0 });
      throw new Error('should have thrown');
    } catch (err) {
      expect(err.code).toBe(ERROR_CODES.BAD_ARGUMENT);
      expect(err.message).toBe('bed width is wrong');
      expect(err.details).toEqual({ width: 0 });
    }
  });
});

describe('assertFinite', () => {
  it('returns the value it was given', () => {
    expect(assertFinite(3.5, 'ppi')).toBe(3.5);
    expect(assertFinite(-0.25, 'skew')).toBe(-0.25);
  });

  it.each([Number.NaN, Number.POSITIVE_INFINITY, Number.NEGATIVE_INFINITY, '4', null, undefined])(
    'rejects %p',
    (value) => {
      expect(() => assertFinite(value, 'ppi')).toThrow(/ppi must be a finite number/);
    },
  );
});

describe('assertInteger', () => {
  it('accepts whole numbers including zero and negatives', () => {
    expect(assertInteger(0, 'x')).toBe(0);
    expect(assertInteger(-12, 'x')).toBe(-12);
  });

  it('rejects a fraction', () => {
    expect(() => assertInteger(2.5, 'rows')).toThrow(/rows must be a whole number/);
  });
});

describe('assertPositive and assertNonNegative', () => {
  it('separates zero from the negatives', () => {
    expect(() => assertPositive(0, 'w')).toThrow(/greater than zero/);
    expect(assertNonNegative(0, 'w')).toBe(0);
    expect(() => assertNonNegative(-1, 'w')).toThrow(/must not be negative/);
  });
});

describe('assertPositiveInteger', () => {
  it('wants both properties', () => {
    expect(assertPositiveInteger(7, 'n')).toBe(7);
    expect(() => assertPositiveInteger(0, 'n')).toThrow(/above zero/);
    expect(() => assertPositiveInteger(1.5, 'n')).toThrow(/whole number/);
  });
});

describe('assertInRange', () => {
  it('is inclusive at both ends', () => {
    expect(assertInRange(0, 0, 1, 't')).toBe(0);
    expect(assertInRange(1, 0, 1, 't')).toBe(1);
  });

  it('names the bounds in the message and the details', () => {
    try {
      assertInRange(4, 0, 1, 't');
      throw new Error('should have thrown');
    } catch (err) {
      expect(err.message).toBe('t must be between 0 and 1');
      expect(err.details).toEqual({ name: 't', value: 4, low: 0, high: 1 });
    }
  });
});

describe('assertOneOf', () => {
  it('returns the matched member', () => {
    expect(assertOneOf('gray', ['gray', 'rgb'], 'layout')).toBe('gray');
  });

  it('lists what was allowed', () => {
    expect(() => assertOneOf('cmyk', ['gray', 'rgb'], 'layout')).toThrow(
      /layout must be one of gray, rgb/,
    );
  });
});

describe('assertNonEmptyString', () => {
  it('rejects the empty string and non strings', () => {
    expect(assertNonEmptyString('q17', 'sheet')).toBe('q17');
    expect(() => assertNonEmptyString('', 'sheet')).toThrow(/non empty string/);
    expect(() => assertNonEmptyString(17, 'sheet')).toThrow(/non empty string/);
  });
});

describe('assertPlainObject', () => {
  it('accepts object literals and null prototype bags', () => {
    expect(assertPlainObject({ a: 1 }, 'profile')).toEqual({ a: 1 });
    expect(assertPlainObject(Object.create(null), 'profile')).toEqual({});
  });

  it.each([[[]], [null], [new Date(0)], ['text']])('rejects %p', (value) => {
    expect(() => assertPlainObject(value, 'profile')).toThrow(/profile must be a plain object/);
  });
});

describe('assertArrayOf', () => {
  it('maps the checker over the members', () => {
    expect(assertArrayOf([1, 2, 3], (v) => assertInteger(v, 'n'), 'list')).toEqual([1, 2, 3]);
  });

  it('lets the member failure surface with its own message', () => {
    expect(() => assertArrayOf([1, 'two'], (v) => assertInteger(v, 'n'), 'list')).toThrow(
      /n must be a finite number/,
    );
  });

  it('rejects a non array outright', () => {
    expect(() => assertArrayOf('abc', (v) => v, 'list')).toThrow(/list must be an array/);
  });
});
