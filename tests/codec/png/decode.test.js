import { deflateSync } from 'node:zlib';
import { describe, expect, it } from 'vitest';

import { assemble } from '../../../src/codec/png/chunks.js';
import {
  decodePng,
  probePng,
  readHeaderChunk,
  readPhysical,
  readText,
} from '../../../src/codec/png/decode.js';
import { splitChunks } from '../../../src/codec/png/chunks.js';

/**
 * Build a png by hand so the decoder is measured against the format rather
 * than against our own encoder.
 *
 * @param {object} args
 * @param {number} args.width
 * @param {number} args.height
 * @param {number} args.depth
 * @param {number} args.colourType
 * @param {readonly number[]} args.samples
 * @param {readonly {type: string, data: Uint8Array}[]} [args.extra]
 * @param {number} [args.interlace]
 * @returns {Uint8Array}
 */
function makePng({ width, height, depth, colourType, samples, extra = [], interlace = 0 }) {
  const channels = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[colourType];
  const bytesPerSample = depth / 8;
  const rowBytes = width * channels * bytesPerSample;
  const raw = new Uint8Array((rowBytes + 1) * height);
  let at = 0;
  let index = 0;
  for (let y = 0; y < height; y += 1) {
    raw[at] = 0;
    at += 1;
    for (let i = 0; i < width * channels; i += 1) {
      const value = samples[index];
      index += 1;
      if (bytesPerSample === 1) {
        raw[at] = value;
        at += 1;
      } else {
        raw[at] = (value >> 8) & 0xff;
        raw[at + 1] = value & 0xff;
        at += 2;
      }
    }
  }

  const ihdr = new Uint8Array(13);
  const view = new DataView(ihdr.buffer);
  view.setUint32(0, width);
  view.setUint32(4, height);
  ihdr[8] = depth;
  ihdr[9] = colourType;
  ihdr[12] = interlace;

  return assemble([
    { type: 'IHDR', data: ihdr },
    ...extra,
    { type: 'IDAT', data: new Uint8Array(deflateSync(Buffer.from(raw))) },
    { type: 'IEND', data: new Uint8Array() },
  ]);
}

/**
 * @param {string} key
 * @param {string} value
 * @returns {{type: string, data: Uint8Array}}
 */
function textChunk(key, value) {
  const bytes = [...key].map((c) => c.charCodeAt(0));
  bytes.push(0);
  for (const ch of value) bytes.push(ch.charCodeAt(0));
  return { type: 'tEXt', data: new Uint8Array(bytes) };
}

describe('readHeaderChunk', () => {
  it('reads the thirteen bytes', () => {
    const png = makePng({
      width: 2,
      height: 3,
      depth: 8,
      colourType: 0,
      samples: [1, 2, 3, 4, 5, 6],
    });
    const ihdr = splitChunks(png)[0];
    expect(readHeaderChunk(ihdr.data)).toEqual({
      width: 2,
      height: 3,
      depth: 8,
      colourType: 0,
      interlace: 0,
    });
  });

  it('refuses a header of the wrong length', () => {
    expect(() => readHeaderChunk(new Uint8Array(12))).toThrow(/wrong length/);
  });
});

describe('decodePng', () => {
  it('reads an eight bit grayscale image', () => {
    const png = makePng({
      width: 2,
      height: 2,
      depth: 8,
      colourType: 0,
      samples: [0, 64, 128, 255],
    });
    const { surface } = decodePng(png);
    expect(surface.toJSON()).toEqual({ width: 2, height: 2, layout: 'gray', depth: 8 });
    expect([...surface.data]).toEqual([0, 64, 128, 255]);
  });

  it('reads an eight bit colour image', () => {
    const png = makePng({
      width: 2,
      height: 1,
      depth: 8,
      colourType: 2,
      samples: [1, 2, 3, 4, 5, 6],
    });
    const { surface } = decodePng(png);
    expect(surface.layout).toBe('rgb');
    expect(surface.getPixel(1, 0)).toEqual([4, 5, 6]);
  });

  it('reads a sixteen bit image', () => {
    const png = makePng({ width: 2, height: 1, depth: 16, colourType: 0, samples: [1, 65535] });
    const { surface } = decodePng(png);
    expect(surface.depth).toBe(16);
    expect([...surface.data]).toEqual([1, 65535]);
  });

  it('reads the two layouts that carry alpha', () => {
    const graya = makePng({ width: 1, height: 1, depth: 8, colourType: 4, samples: [10, 20] });
    expect(decodePng(graya).surface.getPixel(0, 0)).toEqual([10, 20]);

    const rgba = makePng({ width: 1, height: 1, depth: 8, colourType: 6, samples: [1, 2, 3, 4] });
    expect(decodePng(rgba).surface.getPixel(0, 0)).toEqual([1, 2, 3, 4]);
  });

  it('reads text chunks into a bag', () => {
    const png = makePng({
      width: 1,
      height: 1,
      depth: 8,
      colourType: 0,
      samples: [9],
      extra: [textChunk('Title', 'plate 14'), textChunk('Source', 'stand two')],
    });
    expect(decodePng(png).text).toEqual({ Title: 'plate 14', Source: 'stand two' });
  });

  it('reads the physical chunk when it is there', () => {
    const phys = new Uint8Array(9);
    new DataView(phys.buffer).setUint32(0, 15748);
    new DataView(phys.buffer).setUint32(4, 15748);
    phys[8] = 1;
    const png = makePng({
      width: 1,
      height: 1,
      depth: 8,
      colourType: 0,
      samples: [9],
      extra: [{ type: 'pHYs', data: phys }],
    });
    expect(decodePng(png).physical).toEqual({ x: 15748, y: 15748, unit: 1 });
  });

  it('reports no physical chunk as null', () => {
    const png = makePng({ width: 1, height: 1, depth: 8, colourType: 0, samples: [9] });
    expect(decodePng(png).physical).toBeNull();
  });
});

describe('what it refuses', () => {
  it('refuses a palette image rather than expanding it', () => {
    const png = makePng({ width: 1, height: 1, depth: 8, colourType: 3, samples: [0] });
    expect(() => decodePng(png)).toThrow(/palette png is not a capture/);
  });

  it('refuses an interlaced image', () => {
    const png = makePng({
      width: 1,
      height: 1,
      depth: 8,
      colourType: 0,
      samples: [1],
      interlace: 1,
    });
    expect(() => decodePng(png)).toThrow(/interlaced png is not read here/);
  });

  it('refuses a bit depth below eight', () => {
    const png = makePng({ width: 1, height: 1, depth: 8, colourType: 0, samples: [1] });
    const chunks = splitChunks(png);
    chunks[0].data[8] = 4;
    const rebuilt = assemble(chunks.map((c) => ({ type: c.type, data: c.data })));
    expect(() => decodePng(rebuilt)).toThrow(/only eight and sixteen bit/);
  });

  it('refuses a colour type it does not know', () => {
    const png = makePng({ width: 1, height: 1, depth: 8, colourType: 0, samples: [1] });
    const chunks = splitChunks(png);
    chunks[0].data[9] = 7;
    const rebuilt = assemble(chunks.map((c) => ({ type: c.type, data: c.data })));
    expect(() => decodePng(rebuilt)).toThrow(/unknown colour type 7/);
  });

  it('refuses an image with no pixels', () => {
    const png = makePng({ width: 1, height: 1, depth: 8, colourType: 0, samples: [1] });
    const chunks = splitChunks(png);
    new DataView(chunks[0].data.buffer, chunks[0].data.byteOffset).setUint32(0, 0);
    const rebuilt = assemble(chunks.map((c) => ({ type: c.type, data: c.data })));
    expect(() => decodePng(rebuilt)).toThrow(/no pixels/);
  });

  it('refuses a file with no image data', () => {
    const ihdr = new Uint8Array(13);
    const view = new DataView(ihdr.buffer);
    view.setUint32(0, 1);
    view.setUint32(4, 1);
    ihdr[8] = 8;
    const png = assemble([
      { type: 'IHDR', data: ihdr },
      { type: 'IEND', data: new Uint8Array() },
    ]);
    expect(() => decodePng(png)).toThrow(/no IDAT chunk/);
  });

  it('refuses data that will not inflate', () => {
    const ihdr = new Uint8Array(13);
    const view = new DataView(ihdr.buffer);
    view.setUint32(0, 1);
    view.setUint32(4, 1);
    ihdr[8] = 8;
    const png = assemble([
      { type: 'IHDR', data: ihdr },
      { type: 'IDAT', data: new Uint8Array([1, 2, 3, 4]) },
      { type: 'IEND', data: new Uint8Array() },
    ]);
    expect(() => decodePng(png)).toThrow(/will not inflate/);
  });

  it('refuses data that inflates to less than the image', () => {
    const ihdr = new Uint8Array(13);
    const view = new DataView(ihdr.buffer);
    view.setUint32(0, 8);
    view.setUint32(4, 8);
    ihdr[8] = 8;
    const png = assemble([
      { type: 'IHDR', data: ihdr },
      { type: 'IDAT', data: new Uint8Array(deflateSync(Buffer.from(new Uint8Array(4)))) },
      { type: 'IEND', data: new Uint8Array() },
    ]);
    expect(() => decodePng(png)).toThrow(/shorter than the image/);
  });
});

describe('probePng', () => {
  it('reports the shape without inflating', () => {
    const png = makePng({ width: 40, height: 30, depth: 16, colourType: 6, samples: [] });
    expect(probePng(png)).toEqual({ width: 40, height: 30, layout: 'rgba', depth: 16 });
  });
});

describe('the chunk readers on their own', () => {
  it('skip a text chunk with no separator', () => {
    const png = makePng({
      width: 1,
      height: 1,
      depth: 8,
      colourType: 0,
      samples: [1],
      extra: [{ type: 'tEXt', data: new Uint8Array([65, 66, 67]) }],
    });
    expect(readText(splitChunks(png))).toEqual({});
  });

  it('skip a physical chunk of the wrong length', () => {
    const png = makePng({
      width: 1,
      height: 1,
      depth: 8,
      colourType: 0,
      samples: [1],
      extra: [{ type: 'pHYs', data: new Uint8Array(4) }],
    });
    expect(readPhysical(splitChunks(png))).toBeNull();
  });
});
