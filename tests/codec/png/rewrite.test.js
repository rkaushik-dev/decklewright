import { describe, expect, it } from 'vitest';

import { decodePng } from '../../../src/codec/png/decode.js';
import { encodePng } from '../../../src/codec/png/encode.js';
import {
  MASTER_KEEP,
  SERVICE_KEEP,
  checkChunkPolicy,
  chunkTypes,
  imageDataOf,
  keepOnly,
  readTime,
  stripAncillary,
  timeChunk,
  withMetadata,
} from '../../../src/codec/png/rewrite.js';
import { Surface } from '../../../src/raster/surface.js';

/**
 * @returns {Uint8Array}
 */
function master() {
  const s = Surface.create({ width: 8, height: 6, layout: 'rgb', depth: 16, fill: 30000 });
  return withMetadata(
    encodePng(s, { ppi: { x: 400, y: 400 }, text: { Title: 'plate 14', Rights: 'public' } }),
    { time: new Date(Date.UTC(2025, 2, 14, 9, 30, 15)) },
  );
}

describe('timeChunk and readTime', () => {
  it('round trips a capture time to the second', () => {
    const when = new Date(Date.UTC(2025, 4, 2, 13, 45, 7));
    const file = withMetadata(encodePng(Surface.create({ width: 1, height: 1 })), { time: when });
    expect(readTime(file)?.toISOString()).toBe(when.toISOString());
  });

  it('lays out the seven bytes', () => {
    const chunk = timeChunk(new Date(Date.UTC(2025, 0, 1, 0, 0, 0)));
    expect([...chunk.data]).toEqual([7, 233, 1, 1, 0, 0, 0]);
  });

  it('reports no time chunk as null', () => {
    expect(readTime(encodePng(Surface.create({ width: 1, height: 1 })))).toBeNull();
  });
});

describe('withMetadata', () => {
  it('puts the new chunks before the image data', () => {
    const types = chunkTypes(master());
    expect(types.indexOf('tIME')).toBeLessThan(types.indexOf('IDAT'));
    expect(types[0]).toBe('IHDR');
    expect(types[types.length - 1]).toBe('IEND');
  });

  it('replaces a text key rather than adding a second one', () => {
    const once = master();
    const twice = withMetadata(once, { text: { Title: 'plate 15' } });
    expect(chunkTypes(twice).filter((t) => t === 'tEXt')).toHaveLength(2);
    expect(decodePng(twice).text.Title).toBe('plate 15');
    expect(decodePng(twice).text.Rights).toBe('public');
  });

  it('replaces the time rather than adding a second one', () => {
    const twice = withMetadata(master(), { time: new Date(Date.UTC(2026, 0, 1)) });
    expect(chunkTypes(twice).filter((t) => t === 'tIME')).toHaveLength(1);
    expect(readTime(twice)?.getUTCFullYear()).toBe(2026);
  });

  it('leaves the samples untouched', () => {
    const before = decodePng(master()).surface;
    const after = decodePng(withMetadata(master(), { text: { Note: 'rescanned' } })).surface;
    expect([...after.data]).toEqual([...before.data]);
  });

  it('does not touch the compressed data itself', () => {
    expect([...imageDataOf(withMetadata(master(), { text: { Note: 'x' } }))]).toEqual([
      ...imageDataOf(master()),
    ]);
  });
});

describe('keepOnly', () => {
  it('drops what is not on the list', () => {
    const cut = keepOnly(master(), SERVICE_KEEP);
    expect(chunkTypes(cut)).toEqual(['IHDR', 'IDAT', 'IEND']);
  });

  it('keeps the master list intact', () => {
    const kept = chunkTypes(keepOnly(master(), MASTER_KEEP));
    expect(kept).toContain('pHYs');
    expect(kept).toContain('tIME');
    expect(kept.filter((t) => t === 'tEXt')).toHaveLength(2);
  });

  it('never drops the three chunks a png needs', () => {
    const cut = keepOnly(master(), []);
    expect(chunkTypes(cut)).toEqual(['IHDR', 'IDAT', 'IEND']);
  });

  it('leaves a decodable file behind', () => {
    const before = decodePng(master()).surface;
    const after = decodePng(keepOnly(master(), SERVICE_KEEP)).surface;
    expect(after.toJSON()).toEqual(before.toJSON());
    expect([...after.data]).toEqual([...before.data]);
  });
});

describe('stripAncillary', () => {
  it('keeps only the critical chunks', () => {
    expect(chunkTypes(stripAncillary(master()))).toEqual(['IHDR', 'IDAT', 'IEND']);
  });

  it('makes the file smaller', () => {
    expect(stripAncillary(master()).length).toBeLessThan(master().length);
  });
});

describe('checkChunkPolicy', () => {
  it('passes a file that matches', () => {
    expect(checkChunkPolicy(keepOnly(master(), SERVICE_KEEP), SERVICE_KEEP)).toEqual({
      ok: true,
      unexpected: [],
      missing: [],
    });
  });

  it('names the chunks that broke the policy', () => {
    const result = checkChunkPolicy(master(), SERVICE_KEEP);
    expect(result.ok).toBe(false);
    expect(result.unexpected).toEqual(['pHYs', 'tEXt', 'tIME']);
    expect(result.missing).toEqual([]);
  });

  it('accepts a master against the master policy', () => {
    expect(checkChunkPolicy(master(), MASTER_KEEP).ok).toBe(true);
  });
});

describe('imageDataOf', () => {
  it('hands back the compressed bytes', () => {
    expect(imageDataOf(master()).length).toBeGreaterThan(0);
  });
});
