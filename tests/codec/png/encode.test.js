import { describe, expect, it } from 'vitest';

import { looksLikePng, splitChunks } from '../../../src/codec/png/chunks.js';
import { decodePng, probePng } from '../../../src/codec/png/decode.js';
import { encodePng, physicalChunk, textChunk } from '../../../src/codec/png/encode.js';
import { Surface } from '../../../src/raster/surface.js';

/**
 * @param {readonly number[][]} rows
 * @param {number} [depth]
 * @returns {Surface}
 */
function gray(rows, depth = 8) {
  const s = Surface.create({ width: rows[0].length, height: rows.length, layout: 'gray', depth });
  rows.forEach((row, y) => row.forEach((value, x) => s.set(x, y, 0, value)));
  return s;
}

/**
 * @param {number} width
 * @param {number} height
 * @param {import('../../../src/raster/layout.js').LayoutName} layout
 * @param {import('../../../src/raster/layout.js').Depth} depth
 * @returns {Surface}
 */
function noisy(width, height, layout, depth) {
  const s = Surface.create({ width, height, layout, depth });
  let seed = 7;
  for (let i = 0; i < s.data.length; i += 1) {
    seed = (seed * 1103515245 + 12345) & 0x7fffffff;
    s.data[i] = seed % (s.maxValue + 1);
  }
  return s;
}

describe('encodePng', () => {
  it('writes a file that starts with the signature', () => {
    expect(looksLikePng(encodePng(gray([[1, 2]])))).toBe(true);
  });

  it('writes the chunks in the order the format wants', () => {
    const png = encodePng(gray([[1]]), { ppi: { x: 400, y: 400 }, text: { Title: 'plate' } });
    expect(splitChunks(png).map((c) => c.type)).toEqual(['IHDR', 'pHYs', 'tEXt', 'IDAT', 'IEND']);
  });

  it('refuses a layout png cannot carry', () => {
    const s = Surface.create({ width: 1, height: 1, layout: 'gray' });
    const broken = { ...s, layout: 'cmyk' };
    expect(() => encodePng(/** @type {Surface} */ (broken))).toThrow(/does not carry that layout/);
  });

  it('refuses a compression level outside the range', () => {
    expect(() => encodePng(gray([[1]]), { level: 10 })).toThrow(/level must be between 0 and 9/);
  });
});

describe('round trips through the decoder', () => {
  /**
   * @param {Surface} original
   * @param {object} [options]
   */
  function roundTrip(original, options) {
    const { surface } = decodePng(encodePng(original, options));
    expect(surface.toJSON()).toEqual(original.toJSON());
    expect([...surface.data]).toEqual([...original.data]);
  }

  it('survives every layout at eight bits', () => {
    for (const layout of ['gray', 'graya', 'rgb', 'rgba']) {
      roundTrip(noisy(7, 5, /** @type {any} */ (layout), 8));
    }
  });

  it('survives every layout at sixteen bits', () => {
    for (const layout of ['gray', 'graya', 'rgb', 'rgba']) {
      roundTrip(noisy(7, 5, /** @type {any} */ (layout), 16));
    }
  });

  it('survives a single pixel', () => {
    roundTrip(gray([[42]]));
  });

  it('survives a flat capture at every compression level', () => {
    const flat = Surface.create({ width: 16, height: 16, layout: 'rgb', fill: 200 });
    for (const level of [0, 1, 6, 9]) roundTrip(flat, { level });
  });

  it('survives with the filter heuristic turned off', () => {
    roundTrip(noisy(9, 9, 'rgb', 8), { filter: 'none' });
  });

  it('gives the same pixels either way round the heuristic', () => {
    const s = noisy(12, 9, 'rgba', 16);
    const auto = decodePng(encodePng(s, { filter: 'auto' })).surface;
    const plain = decodePng(encodePng(s, { filter: 'none' })).surface;
    expect([...auto.data]).toEqual([...plain.data]);
  });

  it('is smaller with the heuristic on a gradient', () => {
    const s = Surface.create({ width: 128, height: 128, layout: 'gray' });
    for (let y = 0; y < 128; y += 1) {
      for (let x = 0; x < 128; x += 1) s.set(x, y, 0, (x + y) & 0xff);
    }
    const auto = encodePng(s, { filter: 'auto' }).length;
    const plain = encodePng(s, { filter: 'none' }).length;
    expect(auto).toBeLessThan(plain);
  });
});

describe('metadata', () => {
  it('carries text through', () => {
    const png = encodePng(gray([[1]]), { text: { Title: 'plate 14', Source: 'stand two' } });
    expect(decodePng(png).text).toEqual({ Title: 'plate 14', Source: 'stand two' });
  });

  it('carries sampling frequency through as pixels per metre', () => {
    const png = encodePng(gray([[1]]), { ppi: { x: 400, y: 400 } });
    const physical = decodePng(png).physical;
    expect(physical?.unit).toBe(1);
    expect(physical?.x).toBe(15748);
    expect((physical?.x ?? 0) * 0.0254).toBeCloseTo(400, 0);
  });

  it('writes different frequencies per axis', () => {
    const png = encodePng(gray([[1]]), { ppi: { x: 600, y: 300 } });
    const physical = decodePng(png).physical;
    expect(physical?.x).toBe(23622);
    expect(physical?.y).toBe(11811);
  });
});

describe('the chunk builders on their own', () => {
  it('lays out a text chunk with the separator', () => {
    const chunk = textChunk('Title', 'ab');
    expect([...chunk.data]).toEqual([84, 105, 116, 108, 101, 0, 97, 98]);
  });

  it('refuses a key that is empty or too long', () => {
    expect(() => textChunk('', 'x')).toThrow(/one to seventy nine/);
    expect(() => textChunk('k'.repeat(80), 'x')).toThrow(/one to seventy nine/);
  });

  it('refuses text outside latin one', () => {
    expect(() => textChunk('Title', 'plate — 14')).toThrow(/latin one/);
  });

  it('lays out a physical chunk', () => {
    const chunk = physicalChunk(254, 254);
    expect(chunk.data).toHaveLength(9);
    expect(chunk.data[8]).toBe(1);
  });
});

describe('probe agrees with the encoder', () => {
  it('reports what was written', () => {
    const s = noisy(21, 13, 'rgba', 16);
    expect(probePng(encodePng(s))).toEqual({
      width: 21,
      height: 13,
      layout: 'rgba',
      depth: 16,
    });
  });
});
