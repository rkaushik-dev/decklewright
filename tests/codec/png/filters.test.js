import { describe, expect, it } from 'vitest';

import {
  FILTER_AVERAGE,
  FILTER_NAMES,
  FILTER_NONE,
  FILTER_PAETH,
  FILTER_SUB,
  FILTER_UP,
  applyFilter,
  chooseFilter,
  filterName,
  paethPredictor,
  undoFilter,
} from '../../../src/codec/png/filters.js';

const ALL = [FILTER_NONE, FILTER_SUB, FILTER_UP, FILTER_AVERAGE, FILTER_PAETH];

describe('paethPredictor', () => {
  it('picks whichever of the three the estimate lands nearest', () => {
    expect(paethPredictor(10, 20, 30)).toBe(10);
    expect(paethPredictor(10, 20, 5)).toBe(20);
    expect(paethPredictor(10, 20, 15)).toBe(15);
    expect(paethPredictor(1, 1, 100)).toBe(1);
  });

  it('breaks a tie between the left and the one above towards the left', () => {
    expect(paethPredictor(10, 10, 10)).toBe(10);
    expect(paethPredictor(10, 10, 4)).toBe(10);
  });
});

describe('every filter undoes itself', () => {
  const row = new Uint8Array([10, 20, 30, 200, 250, 5, 128, 128, 0]);
  const previous = new Uint8Array([5, 25, 35, 190, 255, 1, 120, 130, 9]);

  it.each(ALL)('filter %i on a row with a row above', (filter) => {
    const encoded = applyFilter(filter, row, previous, 3);
    const decoded = undoFilter(filter, encoded.slice(), previous, 3);
    expect([...decoded]).toEqual([...row]);
  });

  it.each(ALL)('filter %i on the first row', (filter) => {
    const encoded = applyFilter(filter, row, null, 3);
    const decoded = undoFilter(filter, encoded.slice(), null, 3);
    expect([...decoded]).toEqual([...row]);
  });

  it.each(ALL)('filter %i on a single byte step', (filter) => {
    const encoded = applyFilter(filter, row, previous, 1);
    const decoded = undoFilter(filter, encoded.slice(), previous, 1);
    expect([...decoded]).toEqual([...row]);
  });
});

describe('the individual filters', () => {
  it('none passes the bytes straight through', () => {
    const row = new Uint8Array([1, 2, 3]);
    expect([...applyFilter(FILTER_NONE, row, null, 1)]).toEqual([1, 2, 3]);
  });

  it('sub takes the left neighbour', () => {
    const row = new Uint8Array([10, 12, 15]);
    expect([...applyFilter(FILTER_SUB, row, null, 1)]).toEqual([10, 2, 3]);
  });

  it('up takes the row above', () => {
    const row = new Uint8Array([10, 12, 15]);
    const previous = new Uint8Array([10, 10, 10]);
    expect([...applyFilter(FILTER_UP, row, previous, 1)]).toEqual([0, 2, 5]);
  });

  it('average halves the pair', () => {
    const row = new Uint8Array([10, 20]);
    const previous = new Uint8Array([10, 10]);
    expect([...applyFilter(FILTER_AVERAGE, row, previous, 1)]).toEqual([5, 10]);
  });

  it('wraps around the byte', () => {
    const row = new Uint8Array([0, 0]);
    const previous = new Uint8Array([1, 1]);
    expect([...applyFilter(FILTER_UP, row, previous, 1)]).toEqual([255, 255]);
  });

  it('writes into a scratch buffer when given one', () => {
    const scratch = new Uint8Array(3);
    const out = applyFilter(FILTER_NONE, new Uint8Array([1, 2, 3]), null, 1, scratch);
    expect(out).toBe(scratch);
  });

  it('refuses a filter number it does not know', () => {
    expect(() => applyFilter(9, new Uint8Array(1), null, 1)).toThrow(/unknown row filter 9/);
    expect(() => undoFilter(9, new Uint8Array(1), null, 1)).toThrow(/unknown row filter 9/);
  });
});

describe('chooseFilter', () => {
  it('picks none for a flat row with nothing above it', () => {
    const row = new Uint8Array(16).fill(0);
    expect(chooseFilter(row, null, 1).filter).toBe(FILTER_NONE);
  });

  it('picks up when the row repeats the one above', () => {
    const row = new Uint8Array(16).fill(200);
    const previous = new Uint8Array(16).fill(200);
    expect(chooseFilter(row, previous, 1).filter).toBe(FILTER_UP);
  });

  it('picks sub on a flat run with no row above', () => {
    const row = new Uint8Array(16).fill(200);
    expect(chooseFilter(row, null, 1).filter).toBe(FILTER_SUB);
  });

  it('returns data that decodes back to the row', () => {
    const row = new Uint8Array([3, 9, 27, 81, 200, 12, 6, 3]);
    const previous = new Uint8Array([1, 8, 30, 79, 210, 10, 6, 4]);
    const { filter, data } = chooseFilter(row, previous, 2);
    expect([...undoFilter(filter, data.slice(), previous, 2)]).toEqual([...row]);
  });

  it('does not hand back the shared scratch buffer', () => {
    const row = new Uint8Array([1, 2, 3, 4]);
    const first = chooseFilter(row, null, 1).data;
    const second = chooseFilter(new Uint8Array([9, 9, 9, 9]), null, 1).data;
    expect(first).not.toBe(second);
    expect([...first]).toEqual([1, 1, 1, 1]);
  });
});

describe('filterName', () => {
  it('names each filter', () => {
    expect(FILTER_NAMES).toHaveLength(5);
    expect(filterName(FILTER_PAETH)).toBe('paeth');
    expect(filterName(FILTER_NONE)).toBe('none');
    expect(() => filterName(7)).toThrow(/unknown row filter 7/);
  });
});
