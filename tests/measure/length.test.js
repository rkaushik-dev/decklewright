import { describe, expect, it } from 'vitest';

import { Length, assertLength, maxLength, minLength, toLength } from '../../src/measure/length.js';

describe('building a Length', () => {
  it('keeps the unit it was written in', () => {
    const l = Length.of(3, 'mm');
    expect(l.value).toBe(3);
    expect(l.unit).toBe('mm');
    expect(Object.isFrozen(l)).toBe(true);
  });

  it('parses the string an operator types', () => {
    expect(Length.parse('0.25in').toJSON()).toEqual({ value: 0.25, unit: 'in' });
  });

  it('has a zero that still carries a unit', () => {
    expect(Length.zero().unit).toBe('mm');
    expect(Length.zero('pt').isZero).toBe(true);
  });

  it('refuses a unit it does not know', () => {
    expect(() => Length.of(3, 'px')).toThrow(/unknown unit px/);
  });
});

describe('conversion', () => {
  it('returns itself when the unit already matches', () => {
    const l = Length.of(3, 'mm');
    expect(l.to('mm')).toBe(l);
  });

  it('converts and reports inches', () => {
    expect(Length.of(25.4, 'mm').to('in').value).toBeCloseTo(1, 12);
    expect(Length.of(1, 'in').inches).toBe(1);
    expect(Length.of(72, 'pt').inches).toBeCloseTo(1, 12);
  });
});

describe('arithmetic', () => {
  it('adds in the unit of the left operand', () => {
    const sum = Length.of(1, 'in').plus(Length.of(25.4, 'mm'));
    expect(sum.unit).toBe('in');
    expect(sum.value).toBeCloseTo(2, 9);
  });

  it('subtracts and can go negative', () => {
    const diff = Length.of(3, 'mm').minus(Length.of(5, 'mm'));
    expect(diff.value).toBe(-2);
    expect(diff.isNegative).toBe(true);
    expect(diff.abs().value).toBe(2);
    expect(diff.negated().value).toBe(2);
  });

  it('leaves a positive length alone when taking the absolute value', () => {
    const l = Length.of(3, 'mm');
    expect(l.abs()).toBe(l);
  });

  it('scales by a plain factor', () => {
    expect(Length.of(3, 'mm').times(2).value).toBe(6);
    expect(Length.of(3, 'mm').times(0).isZero).toBe(true);
  });

  it('divides one length by another across units', () => {
    expect(Length.of(1, 'in').over(Length.of(25.4, 'mm'))).toBeCloseTo(1, 9);
    expect(Length.of(12, 'in').over(Length.of(1, 'in'))).toBe(12);
  });

  it('refuses to divide by zero', () => {
    expect(() => Length.of(1, 'in').over(Length.zero())).toThrow(/divide a length by zero/);
  });

  it('refuses an operand that is not a Length', () => {
    expect(() => Length.of(1, 'in').plus(3)).toThrow(/other must be a Length/);
    expect(() => Length.of(1, 'in').minus('3mm')).toThrow(/other must be a Length/);
  });
});

describe('comparison', () => {
  it('orders across units', () => {
    expect(Length.of(1, 'in').compare(Length.of(25.4, 'mm'))).toBe(0);
    expect(Length.of(1, 'in').compare(Length.of(30, 'mm'))).toBe(-1);
    expect(Length.of(2, 'in').compare(Length.of(30, 'mm'))).toBe(1);
  });

  it('treats equality as exact unless a tolerance is given', () => {
    const a = Length.of(3, 'mm');
    const b = Length.of(3.05, 'mm');
    expect(a.equals(b)).toBe(false);
    expect(a.equals(b, Length.of(0.1, 'mm'))).toBe(true);
    expect(a.equals(b, Length.of(-0.1, 'mm'))).toBe(true);
  });

  it('is not equal to a bare number', () => {
    expect(Length.of(3, 'mm').equals(3)).toBe(false);
  });
});

describe('printing', () => {
  it('uses the report precision of its own unit', () => {
    expect(String(Length.of(3.14159, 'mm'))).toBe('3.14 mm');
    expect(String(Length.of(0.25, 'in'))).toBe('0.2500 in');
  });
});

describe('toLength', () => {
  it('takes the three shapes a profile may hold', () => {
    const l = Length.of(3, 'mm');
    expect(toLength(l)).toBe(l);
    expect(toLength('3mm').equals(l)).toBe(true);
    expect(toLength({ value: 3, unit: 'mm' }).equals(l)).toBe(true);
  });

  it('rejects a bad unit inside a pair', () => {
    expect(() => toLength({ value: 3, unit: 'px' })).toThrow(/unit must be one of/);
  });

  it('rejects anything else', () => {
    expect(() => toLength(3)).toThrow(/cannot read a length from that/);
    expect(() => toLength(null)).toThrow(/cannot read a length from that/);
  });
});

describe('assertLength', () => {
  it('names the argument it was checking', () => {
    expect(() => assertLength('3mm', 'bleed')).toThrow(/bleed must be a Length/);
  });
});

describe('maxLength and minLength', () => {
  const lengths = [Length.of(3, 'mm'), Length.of(0.25, 'in'), Length.of(12, 'pt')];

  it('picks across units', () => {
    expect(maxLength(lengths).unit).toBe('in');
    expect(minLength(lengths).unit).toBe('mm');
  });

  it('refuses an empty list', () => {
    expect(() => maxLength([])).toThrow(/no lengths to compare/);
    expect(() => minLength([])).toThrow(/no lengths to compare/);
  });
});
