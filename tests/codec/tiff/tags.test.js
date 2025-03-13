import { describe, expect, it } from 'vitest';

import {
  COMPRESSION,
  PHOTOMETRIC,
  RESOLUTION_UNIT,
  asBag,
  list,
  looksLikeTiff,
  readIfd,
  readTiffHeader,
  scalar,
  tagName,
} from '../../../src/codec/tiff/tags.js';

/**
 * Build a tiny tiff header and one directory so the reader is measured
 * against the layout rather than against a fixture nobody can check.
 *
 * @param {object} args
 * @param {boolean} [args.little]
 * @param {readonly {tag: number, type: number, count: number, value: number[]|string}[]} args.entries
 * @param {number} [args.trailing] extra bytes for out of line values
 * @returns {Uint8Array}
 */
function makeTiff({ little = true, entries, trailing = 64 }) {
  const bytes = new Uint8Array(8 + 2 + entries.length * 12 + 4 + trailing);
  const view = new DataView(bytes.buffer);
  bytes[0] = little ? 0x49 : 0x4d;
  bytes[1] = little ? 0x49 : 0x4d;
  view.setUint16(2, 42, little);
  view.setUint32(4, 8, little);
  view.setUint16(8, entries.length, little);

  let heap = 8 + 2 + entries.length * 12 + 4;
  entries.forEach((entry, i) => {
    const at = 10 + i * 12;
    view.setUint16(at, entry.tag, little);
    view.setUint16(at + 2, entry.type, little);
    view.setUint32(at + 4, entry.count, little);

    const size = { 1: 1, 2: 1, 3: 2, 4: 4, 5: 8 }[entry.type];
    const total = size * entry.count;
    const target = total <= 4 ? at + 8 : heap;
    if (total > 4) {
      view.setUint32(at + 8, heap, little);
      heap += total;
    }
    if (typeof entry.value === 'string') {
      for (let k = 0; k < entry.count; k += 1) {
        bytes[target + k] = entry.value.charCodeAt(k) || 0;
      }
    } else {
      entry.value.forEach((value, k) => {
        if (entry.type === 3) view.setUint16(target + k * 2, value, little);
        else if (entry.type === 4) view.setUint32(target + k * 4, value, little);
        else if (entry.type === 5) {
          view.setUint32(target + k * 8, value, little);
          view.setUint32(target + k * 8 + 4, 1, little);
        } else bytes[target + k] = value;
      });
    }
  });
  view.setUint32(10 + entries.length * 12, 0, little);
  return bytes;
}

describe('readTiffHeader', () => {
  it('reads a little endian header', () => {
    expect(readTiffHeader(makeTiff({ entries: [] }))).toEqual({
      littleEndian: true,
      firstIfd: 8,
    });
  });

  it('reads a big endian header', () => {
    expect(readTiffHeader(makeTiff({ little: false, entries: [] })).littleEndian).toBe(false);
  });

  it('refuses a wrong byte order mark or version', () => {
    expect(() => readTiffHeader(new Uint8Array([1, 2, 3, 4, 5, 6, 7, 8]))).toThrow(
      /byte order mark is wrong/,
    );
    const wrongVersion = makeTiff({ entries: [] });
    wrongVersion[2] = 43;
    expect(() => readTiffHeader(wrongVersion)).toThrow(/version word is wrong/);
  });

  it('refuses a file that is too short', () => {
    expect(() => readTiffHeader(new Uint8Array([0x49, 0x49]))).toThrow(/too short to be a tiff/);
  });
});

describe('looksLikeTiff', () => {
  it('accepts both byte orders and rejects everything else', () => {
    expect(looksLikeTiff(makeTiff({ entries: [] }))).toBe(true);
    expect(looksLikeTiff(makeTiff({ little: false, entries: [] }))).toBe(true);
    expect(looksLikeTiff(new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10]))).toBe(false);
    expect(looksLikeTiff(new Uint8Array([0x49, 0x49]))).toBe(false);
  });
});

describe('readIfd', () => {
  const entries = [
    { tag: 256, type: 3, count: 1, value: [640] },
    { tag: 257, type: 3, count: 1, value: [480] },
    { tag: 258, type: 3, count: 3, value: [8, 8, 8] },
    { tag: 259, type: 3, count: 1, value: [COMPRESSION.NONE] },
    { tag: 262, type: 3, count: 1, value: [PHOTOMETRIC.RGB] },
    { tag: 282, type: 5, count: 1, value: [400] },
    { tag: 296, type: 3, count: 1, value: [RESOLUTION_UNIT.INCH] },
    { tag: 305, type: 2, count: 8, value: 'benchtop' },
    { tag: 61000, type: 3, count: 1, value: [7] },
  ];

  it('reads every entry with its name', () => {
    const tiff = makeTiff({ entries });
    const { entries: read, next } = readIfd(tiff, 8, true);
    expect(read).toHaveLength(entries.length);
    expect(next).toBe(0);
    expect(read[0].name).toBe('imageWidth');
    expect(read[0].value).toEqual([640]);
  });

  it('reads a value that lives out of line', () => {
    const { entries: read } = readIfd(makeTiff({ entries }), 8, true);
    expect(read.find((e) => e.name === 'bitsPerSample')?.value).toEqual([8, 8, 8]);
    expect(read.find((e) => e.name === 'software')?.value).toBe('benchtop');
  });

  it('divides a rational', () => {
    const { entries: read } = readIfd(makeTiff({ entries }), 8, true);
    expect(read.find((e) => e.name === 'xResolution')?.value).toEqual([400]);
  });

  it('keeps a tag it does not know under a made up name', () => {
    const { entries: read } = readIfd(makeTiff({ entries }), 8, true);
    expect(read.find((e) => e.tag === 61000)?.name).toBe('tag61000');
  });

  it('reads the same values in big endian', () => {
    const tiff = makeTiff({ little: false, entries });
    const { entries: read } = readIfd(tiff, 8, false);
    expect(read[0].value).toEqual([640]);
    expect(read.find((e) => e.name === 'software')?.value).toBe('benchtop');
  });

  it('refuses a directory offset past the end', () => {
    expect(() => readIfd(makeTiff({ entries }), 9000, true)).toThrow(/points past the end/);
  });

  it('refuses an entry that points past the end', () => {
    const tiff = makeTiff({ entries });
    const view = new DataView(tiff.buffer);
    view.setUint32(10 + 2 * 12 + 8, 9000, true);
    expect(() => readIfd(tiff, 8, true)).toThrow(/points past the end/);
  });

  it('refuses a field type it does not know', () => {
    const tiff = makeTiff({ entries: [{ tag: 256, type: 3, count: 1, value: [1] }] });
    new DataView(tiff.buffer).setUint16(12, 99, true);
    expect(() => readIfd(tiff, 8, true)).toThrow(/unknown tiff field type 99/);
  });
});

describe('the lookup helpers', () => {
  const entries = [
    { tag: 256, name: 'imageWidth', type: 3, count: 1, value: [640] },
    { tag: 258, name: 'bitsPerSample', type: 3, count: 3, value: [8, 8, 8] },
    { tag: 305, name: 'software', type: 2, count: 3, value: 'abc' },
  ];

  it('folds the entries into a bag', () => {
    expect(asBag(entries)).toEqual({
      imageWidth: [640],
      bitsPerSample: [8, 8, 8],
      software: 'abc',
    });
  });

  it('reads a scalar with a fallback', () => {
    expect(scalar(entries, 'imageWidth')).toBe(640);
    expect(scalar(entries, 'orientation', 1)).toBe(1);
    expect(() => scalar(entries, 'orientation')).toThrow(/the orientation tag is missing/);
    expect(scalar(entries, 'software', 5)).toBe(5);
  });

  it('reads a list and gives an empty one when the tag is absent', () => {
    expect(list(entries, 'bitsPerSample')).toEqual([8, 8, 8]);
    expect(list(entries, 'stripOffsets')).toEqual([]);
    expect(list(entries, 'software')).toEqual([]);
  });
});

describe('the constant tables', () => {
  it('names a tag it knows and one it does not', () => {
    expect(tagName(256)).toBe('imageWidth');
    expect(tagName(61000)).toBe('tag61000');
  });

  it('is frozen', () => {
    expect(Object.isFrozen(COMPRESSION)).toBe(true);
    expect(Object.isFrozen(PHOTOMETRIC)).toBe(true);
    expect(Object.isFrozen(RESOLUTION_UNIT)).toBe(true);
  });
});
