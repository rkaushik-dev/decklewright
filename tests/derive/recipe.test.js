import { describe, expect, it } from 'vitest';

import { readImage } from '../../src/codec/registry.js';
import {
  DEFAULT_FORMAT,
  FORMAT_LIMITS,
  encodeAll,
  encodeRendered,
  frequencyOf,
  isWritable,
  limitsFor,
  objectionsTo,
  provenanceText,
  sizeOf,
  textOf,
} from '../../src/derive/recipe.js';
import { Sampling } from '../../src/measure/resolution.js';
import { Surface } from '../../src/raster/surface.js';

/**
 * @param {object} [options]
 * @returns {import('../../src/derive/render.js').Rendered}
 */
function rendered({ name = 'master', layout = 'rgb', depth = 8, ppi = 400 } = {}) {
  return {
    name,
    surface: Surface.create({ width: 24, height: 16, layout, depth, fill: 120 }),
    sampling: Sampling.of(ppi),
    steps: [],
    scale: 1,
  };
}

describe('limitsFor', () => {
  it('knows what each format holds', () => {
    expect(limitsFor('png').depths).toContain(16);
    expect(limitsFor('pnm').carriesFrequency).toBe(false);
  });

  it('refuses a format nobody here has heard of', () => {
    expect(() => limitsFor('jp2')).toThrow(/nothing here writes/);
  });

  it('is frozen, so a caller cannot widen a format by accident', () => {
    expect(Object.isFrozen(FORMAT_LIMITS)).toBe(true);
  });
});

describe('isWritable', () => {
  it('is true for the formats with an encoder', () => {
    expect(isWritable('png')).toBe(true);
    expect(isWritable('pnm')).toBe(true);
  });

  it('is false for a format that is only read', () => {
    expect(isWritable('tiff')).toBe(false);
  });

  it('is false rather than throwing for a name nobody knows', () => {
    expect(isWritable('jp2')).toBe(false);
  });
});

describe('objectionsTo', () => {
  it('has nothing to say about a file that will go out cleanly', () => {
    expect(objectionsTo(rendered().surface, 'png')).toEqual([]);
  });

  it('says so when the format is only read', () => {
    expect(objectionsTo(rendered().surface, 'tiff')[0]).toMatch(/only reads it/);
  });

  it('says so when the layout will not fit', () => {
    const alpha = rendered({ layout: 'rgba' });
    expect(objectionsTo(alpha.surface, 'pnm')[0]).toMatch(/cannot hold rgba/);
  });

  it('stops at the first objection when the format cannot be written at all', () => {
    expect(objectionsTo(rendered({ layout: 'rgba' }).surface, 'tiff')).toHaveLength(1);
  });
});

describe('provenanceText', () => {
  it('names the software and the source', () => {
    const text = provenanceText({ accession: 'g4181-1907' });
    expect(text.Software).toBe('decklewright');
    expect(text.Source).toBe('g4181-1907');
  });

  it('names the derivative when there is one', () => {
    expect(provenanceText({ accession: 'a', derivative: 'thumb' }).Title).toBe('a thumb');
  });

  it('leaves the profile out when there is none', () => {
    expect(provenanceText({ accession: 'a' }).Comment).toBeUndefined();
  });
});

describe('encodeRendered', () => {
  it('writes a png by default', () => {
    const out = encodeRendered(rendered());
    expect(out.format).toBe(DEFAULT_FORMAT);
    expect(out.extension).toBe('.png');
    expect(readImage(out.bytes).width).toBe(24);
  });

  it('puts the frequency in the file', () => {
    const out = encodeRendered(rendered({ ppi: 300 }));
    expect(frequencyOf(out.bytes)?.x).toBeCloseTo(300, 0);
  });

  it('puts the derivative frequency in, not the master one', () => {
    const half = { ...rendered(), sampling: Sampling.of(200), scale: 0.5 };
    expect(frequencyOf(encodeRendered(half).bytes)?.x).toBeCloseTo(200, 0);
  });

  it('carries the text it was given', () => {
    const out = encodeRendered(rendered(), { text: provenanceText({ accession: 'g4181' }) });
    expect(textOf(out.bytes).Source).toBe('g4181');
  });

  it('leaves the text out when the profile asked for a bare file', () => {
    const out = encodeRendered(rendered(), {
      keepMetadata: false,
      text: provenanceText({ accession: 'g4181' }),
    });
    expect(textOf(out.bytes).Source).toBeUndefined();
    expect(out.notes.join(' ')).toMatch(/metadata was left out/);
  });

  it('still carries the frequency in a bare file', () => {
    const out = encodeRendered(rendered({ ppi: 600 }), { keepMetadata: false });
    expect(frequencyOf(out.bytes)?.x).toBeCloseTo(600, 0);
  });

  it('says so when the format cannot carry the frequency', () => {
    const out = encodeRendered(rendered(), { format: 'pnm' });
    expect(out.notes.join(' ')).toMatch(/only in the sidecar/);
    expect(out.extension).toBe('.pnm');
    expect(frequencyOf(out.bytes)).toBeNull();
  });

  it('refuses a format that cannot hold what it was handed', () => {
    expect(() => encodeRendered(rendered({ layout: 'rgba' }), { format: 'pnm' })).toThrow(
      /cannot hold rgba/,
    );
  });

  it('refuses a format nothing here writes', () => {
    expect(() => encodeRendered(rendered(), { format: 'tiff' })).toThrow(/only reads it/);
  });

  it('takes a compression level through to the codec', () => {
    const loose = encodeRendered(rendered(), { level: 0 });
    const tight = encodeRendered(rendered(), { level: 9 });
    expect(loose.bytes.length).toBeGreaterThan(tight.bytes.length);
  });
});

describe('encodeAll', () => {
  const set = [rendered({ name: 'master', depth: 16 }), rendered({ name: 'thumb' })];
  const recipes = [
    { name: 'master', format: 'png', keepMetadata: true },
    { name: 'thumb', format: 'pnm', keepMetadata: false },
  ];

  it('writes each one in the format its own recipe asked for', () => {
    const out = encodeAll(set, recipes, { accession: 'g4181' });
    expect(out.map((one) => one.format)).toEqual(['png', 'pnm']);
  });

  it('writes the accession into the files that keep metadata', () => {
    const out = encodeAll(set, recipes, { accession: 'g4181', profile: 'atlas-plates' });
    expect(textOf(out[0].bytes).Comment).toBe('profile atlas-plates');
  });

  it('falls back to png for a rendered file with no recipe', () => {
    expect(encodeAll(set, [], {})[0].format).toBe('png');
  });

  it('stops the whole set rather than writing part of it', () => {
    const bad = [{ name: 'master', format: 'tiff' }, recipes[1]];
    expect(() => encodeAll(set, bad, {})).toThrow(/only reads it/);
  });

  it('keeps the rendered file beside its bytes', () => {
    const out = encodeAll(set, recipes, {});
    expect(out[0].rendered.name).toBe('master');
  });
});

describe('sizeOf', () => {
  it('adds the set up', () => {
    const out = sizeOf(encodeAll([rendered({ name: 'a' }), rendered({ name: 'b' })], [], {}));
    expect(out.total).toBe(out.perName.a + out.perName.b);
  });

  it('names the largest', () => {
    const set = [rendered({ name: 'small' }), rendered({ name: 'deep', depth: 16 })];
    expect(sizeOf(encodeAll(set, [], {})).largest).toBe('deep');
  });

  it('has nothing to name for an empty set', () => {
    expect(sizeOf([])).toEqual({ total: 0, largest: null, perName: {} });
  });
});
