import { describe, expect, it } from 'vitest';

import {
  DEPTHS,
  LAYOUTS,
  alphaIndex,
  arrayTypeFor,
  assertDepth,
  assertLayout,
  channelCount,
  channelNames,
  colourCount,
  hasAlpha,
  isColour,
  maxValue,
  rowBytes,
  withAlpha,
  withoutAlpha,
} from '../../src/raster/layout.js';

describe('the tables', () => {
  it('lists the four layouts and two depths', () => {
    expect([...LAYOUTS]).toEqual(['gray', 'graya', 'rgb', 'rgba']);
    expect([...DEPTHS]).toEqual([8, 16]);
    expect(Object.isFrozen(LAYOUTS)).toBe(true);
    expect(Object.isFrozen(DEPTHS)).toBe(true);
  });

  it('accepts every listed name and rejects others', () => {
    for (const name of LAYOUTS) expect(assertLayout(name)).toBe(name);
    expect(() => assertLayout('cmyk')).toThrow(/unknown layout cmyk/);
    expect(() => assertLayout('')).toThrow(/layout must be a non empty string/);
  });

  it('accepts only eight and sixteen bits', () => {
    expect(assertDepth(8)).toBe(8);
    expect(assertDepth(16)).toBe(16);
    expect(() => assertDepth(12)).toThrow(/depth must be one of 8, 16/);
  });
});

describe('channel arithmetic', () => {
  it('counts channels', () => {
    expect(channelCount('gray')).toBe(1);
    expect(channelCount('graya')).toBe(2);
    expect(channelCount('rgb')).toBe(3);
    expect(channelCount('rgba')).toBe(4);
  });

  it('separates coverage from colour', () => {
    expect(colourCount('rgba')).toBe(3);
    expect(colourCount('graya')).toBe(1);
    expect(colourCount('rgb')).toBe(3);
  });

  it('says which layouts carry alpha and where it sits', () => {
    expect(hasAlpha('rgba')).toBe(true);
    expect(hasAlpha('rgb')).toBe(false);
    expect(alphaIndex('rgba')).toBe(3);
    expect(alphaIndex('graya')).toBe(1);
    expect(alphaIndex('gray')).toBe(-1);
  });

  it('says which layouts are colour', () => {
    expect(isColour('rgb')).toBe(true);
    expect(isColour('rgba')).toBe(true);
    expect(isColour('gray')).toBe(false);
  });

  it('names the channels and hands back a copy', () => {
    const names = channelNames('rgba');
    expect(names).toEqual(['red', 'green', 'blue', 'alpha']);
    names.push('extra');
    expect(channelNames('rgba')).toHaveLength(4);
  });
});

describe('adding and removing alpha', () => {
  it('promotes and demotes', () => {
    expect(withAlpha('gray')).toBe('graya');
    expect(withAlpha('rgb')).toBe('rgba');
    expect(withoutAlpha('graya')).toBe('gray');
    expect(withoutAlpha('rgba')).toBe('rgb');
  });

  it('is a no op when the layout is already right', () => {
    expect(withAlpha('rgba')).toBe('rgba');
    expect(withoutAlpha('rgb')).toBe('rgb');
  });

  it('round trips through both directions', () => {
    for (const layout of LAYOUTS) {
      expect(withoutAlpha(withAlpha(layout))).toBe(withoutAlpha(layout));
    }
  });
});

describe('depth helpers', () => {
  it('reports the top of the range', () => {
    expect(maxValue(8)).toBe(255);
    expect(maxValue(16)).toBe(65535);
  });

  it('picks the matching typed array', () => {
    expect(arrayTypeFor(8)).toBe(Uint8Array);
    expect(arrayTypeFor(16)).toBe(Uint16Array);
  });

  it('sizes a row in bytes', () => {
    expect(rowBytes(100, 'gray', 8)).toBe(100);
    expect(rowBytes(100, 'rgb', 8)).toBe(300);
    expect(rowBytes(100, 'rgb', 16)).toBe(600);
    expect(rowBytes(100, 'rgba', 16)).toBe(800);
  });
});
