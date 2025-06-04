import { describe, expect, it } from 'vitest';

import { Length } from '../../src/measure/length.js';
import {
  choice,
  edges,
  edgesToInsets,
  fieldError,
  flag,
  group,
  length,
  list,
  number,
  readGroup,
  text,
  tolerance,
} from '../../src/profile/schema.js';

describe('fieldError', () => {
  it('puts the path in front of the message and in the details', () => {
    const err = fieldError('crop.bleed', 'expected a length', { got: 3 });
    expect(err.message).toBe('crop.bleed: expected a length');
    expect(err.details).toEqual({ path: 'crop.bleed', got: 3 });
  });
});

describe('text', () => {
  it('accepts a non empty string', () => {
    expect(text().read('quadrangle', 'name')).toBe('quadrangle');
  });

  it('refuses anything else', () => {
    expect(() => text().read('', 'name')).toThrow(/name: expected a non empty string/);
    expect(() => text().read(7, 'name')).toThrow(/expected a non empty string/);
  });
});

describe('number', () => {
  it('accepts a number and enforces the bounds', () => {
    expect(number().read(3.5, 'ppi')).toBe(3.5);
    expect(() => number({ min: 100 }).read(50, 'ppi')).toThrow(/ppi: must be at least 100/);
    expect(() => number({ max: 10 }).read(50, 'ppi')).toThrow(/must be at most 10/);
  });

  it('can demand a whole number', () => {
    expect(number({ integer: true }).read(8, 'n')).toBe(8);
    expect(() => number({ integer: true }).read(8.5, 'n')).toThrow(/whole number/);
  });

  it('refuses anything that is not a finite number', () => {
    expect(() => number().read('400', 'ppi')).toThrow(/expected a number/);
    expect(() => number().read(Number.NaN, 'ppi')).toThrow(/expected a number/);
  });
});

describe('flag and choice', () => {
  it('accepts only booleans', () => {
    expect(flag().read(true, 'on')).toBe(true);
    expect(() => flag().read('yes', 'on')).toThrow(/expected true or false/);
  });

  it('accepts only listed choices and names them', () => {
    const anchor = choice(['sheet', 'neatline']);
    expect(anchor.read('sheet', 'crop.anchor')).toBe('sheet');
    expect(() => anchor.read('collar', 'crop.anchor')).toThrow(
      /crop.anchor: expected one of sheet, neatline/,
    );
  });
});

describe('length and tolerance', () => {
  it('reads the shapes a profile writes', () => {
    expect(length().read('3mm', 'bleed')).toBeInstanceOf(Length);
    expect(length().read({ value: 3, unit: 'mm' }, 'bleed').value).toBe(3);
  });

  it('names the field when a length will not read', () => {
    expect(() => length().read(3, 'crop.bleed')).toThrow(/crop.bleed: expected a length/);
  });

  it('reads a tolerance', () => {
    expect(tolerance().read('+2/-1', 'skew').plus).toBe(2);
    expect(() => tolerance().read({}, 'skew')).toThrow(/skew: expected a tolerance/);
  });
});

describe('edges', () => {
  it('spreads a single length over all four', () => {
    const bag = edges().read('3mm', 'bleed');
    expect(Object.keys(bag)).toEqual(['top', 'right', 'bottom', 'left']);
    expect(bag.top.value).toBe(3);
    expect(bag.left.value).toBe(3);
  });

  it('reads a bag and defaults the edges left out', () => {
    const bag = edges().read({ bottom: '10mm' }, 'bleed');
    expect(bag.bottom.value).toBe(10);
    expect(bag.top.isZero).toBe(true);
  });

  it('refuses an edge name it does not know', () => {
    expect(() => edges().read({ middle: '1mm' }, 'bleed')).toThrow(/bleed.middle: is not an edge/);
  });

  it('refuses a list', () => {
    expect(() => edges().read([1, 2], 'bleed')).toThrow(/expected a length or a bag of four/);
  });

  it('turns into insets in pixels', () => {
    const insets = edgesToInsets(edges().read({ top: '1mm', bottom: '2mm' }, 'bleed'));
    expect(insets.top).toBe(1);
    expect(insets.bottom).toBe(2);
    expect(insets.left).toBe(0);
  });
});

describe('list', () => {
  it('reads each member and paths them by index', () => {
    expect(list(number()).read([1, 2], 'sizes')).toEqual([1, 2]);
    expect(() => list(number()).read([1, 'two'], 'sizes')).toThrow(/sizes\[1\]: expected a number/);
  });

  it('refuses a value that is not a list', () => {
    expect(() => list(number()).read(3, 'sizes')).toThrow(/sizes: expected a list/);
  });
});

describe('readGroup', () => {
  const fields = {
    name: text({ required: true }),
    ppi: number({ min: 100, fallback: 400 }),
    anchor: choice(['sheet', 'neatline'], { fallback: 'sheet' }),
    nested: group({ bleed: length({ fallback: '0mm' }) }, { fallback: {} }),
  };

  it('reads the fields and fills the fallbacks', () => {
    const out = readGroup(fields, { name: 'atlas' });
    expect(out.name).toBe('atlas');
    expect(out.ppi).toBe(400);
    expect(out.anchor).toBe('sheet');
    expect(out.nested).toEqual({ bleed: expect.any(Length) });
  });

  it('takes what is given over the fallback', () => {
    const out = readGroup(fields, { name: 'atlas', ppi: 600, anchor: 'neatline' });
    expect(out.ppi).toBe(600);
    expect(out.anchor).toBe('neatline');
  });

  it('demands the required fields', () => {
    expect(() => readGroup(fields, {})).toThrow(/name: is required/);
  });

  it('refuses a key it does not know and lists what it does', () => {
    try {
      readGroup(fields, { name: 'atlas', bleeed: '3mm' });
      throw new Error('should have thrown');
    } catch (err) {
      expect(err.message).toBe('bleeed: is not a setting this profile understands');
      expect(err.details.known).toContain('ppi');
    }
  });

  it('paths a nested failure', () => {
    expect(() => readGroup(fields, { name: 'a', nested: { bleed: 3 } })).toThrow(
      /nested.bleed: expected a length/,
    );
  });

  it('refuses a value that is not a group', () => {
    expect(() => readGroup(fields, 'atlas')).toThrow(/\(root\): expected a group of settings/);
    expect(() => readGroup(fields, [])).toThrow(/expected a group of settings/);
  });

  it('treats an explicit undefined as absent', () => {
    expect(readGroup(fields, { name: 'atlas', ppi: undefined }).ppi).toBe(400);
  });
});
