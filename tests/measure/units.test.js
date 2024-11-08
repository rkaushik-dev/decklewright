import { describe, expect, it } from 'vitest';

import {
  SHEET_SIZES,
  UNITS,
  assertUnit,
  convert,
  formatLength,
  fromInches,
  isUnit,
  parseLength,
  sheetSize,
  toInches,
} from '../../src/measure/units.js';

describe('the unit table', () => {
  it('is frozen and has no duplicates', () => {
    expect(Object.isFrozen(UNITS)).toBe(true);
    expect(new Set(UNITS).size).toBe(UNITS.length);
  });

  it('recognises every listed unit and nothing else', () => {
    for (const unit of UNITS) expect(isUnit(unit)).toBe(true);
    expect(isUnit('px')).toBe(false);
    expect(isUnit('MM')).toBe(false);
    expect(isUnit(null)).toBe(false);
  });

  it('names what it knows when it rejects a unit', () => {
    try {
      assertUnit('furlong');
      throw new Error('should have thrown');
    } catch (err) {
      expect(err.message).toBe('unknown unit furlong');
      expect(err.details.known).toEqual([...UNITS]);
    }
  });
});

describe('toInches and fromInches', () => {
  it('uses the exact inch definitions', () => {
    expect(toInches(25.4, 'mm')).toBeCloseTo(1, 12);
    expect(toInches(2.54, 'cm')).toBeCloseTo(1, 12);
    expect(toInches(72, 'pt')).toBeCloseTo(1, 12);
    expect(toInches(6, 'pc')).toBeCloseTo(1, 12);
    expect(toInches(1, 'm')).toBeCloseTo(39.3700787, 6);
  });

  it('round trips through the inch', () => {
    for (const unit of UNITS) {
      expect(fromInches(toInches(17, unit), unit)).toBeCloseTo(17, 10);
    }
  });
});

describe('convert', () => {
  it('short circuits when the units match', () => {
    expect(convert(3.25, 'mm', 'mm')).toBe(3.25);
  });

  it('walks a plate width from inches to millimetres', () => {
    expect(convert(17, 'in', 'mm')).toBeCloseTo(431.8, 9);
    expect(convert(431.8, 'mm', 'in')).toBeCloseTo(17, 9);
  });

  it('goes between the printer units', () => {
    expect(convert(1, 'pc', 'pt')).toBeCloseTo(12, 9);
  });

  it('refuses a value that is not a number', () => {
    expect(() => convert('17', 'in', 'mm')).toThrow(/value must be a finite number/);
  });
});

describe('parseLength', () => {
  it('reads what an operator types', () => {
    expect(parseLength('12.5mm')).toEqual({ value: 12.5, unit: 'mm' });
    expect(parseLength(' 12.5 mm ')).toEqual({ value: 12.5, unit: 'mm' });
    expect(parseLength('.5in')).toEqual({ value: 0.5, unit: 'in' });
    expect(parseLength('-3pt')).toEqual({ value: -3, unit: 'pt' });
    expect(parseLength('17IN')).toEqual({ value: 17, unit: 'in' });
  });

  it('refuses a bare number rather than assuming a unit', () => {
    expect(() => parseLength('12')).toThrow(/cannot read 12 as a length/);
  });

  it('refuses junk and unknown units', () => {
    expect(() => parseLength('mm')).toThrow(/cannot read/);
    expect(() => parseLength('12 furlong')).toThrow(/unknown unit furlong/);
    expect(() => parseLength('12mm extra')).toThrow(/cannot read/);
  });
});

describe('formatLength', () => {
  it('uses the report precision for the unit', () => {
    expect(formatLength(431.8004, 'mm')).toBe('431.80 mm');
    expect(formatLength(17.00004, 'in')).toBe('17.0000 in');
  });

  it('honours an explicit place count', () => {
    expect(formatLength(431.8004, 'mm', 0)).toBe('432 mm');
  });
});

describe('sheetSize', () => {
  it('has a frozen table of the nominal sizes', () => {
    expect(Object.isFrozen(SHEET_SIZES)).toBe(true);
    expect(Object.keys(SHEET_SIZES)).toContain('quad75');
  });

  it('answers in inches by default', () => {
    expect(sheetSize('archD')).toEqual({ width: 24, height: 36, unit: 'in' });
  });

  it('converts on the way out', () => {
    const size = sheetSize('atlasPlate', 'mm');
    expect(size.width).toBeCloseTo(431.8, 6);
    expect(size.height).toBeCloseTo(355.6, 6);
    expect(size.unit).toBe('mm');
  });

  it('lists the known names when it cannot find one', () => {
    try {
      sheetSize('tabloid');
      throw new Error('should have thrown');
    } catch (err) {
      expect(err.message).toBe('no nominal size called tabloid');
      expect(err.details.known).toContain('archE');
    }
  });
});
