import { describe, expect, it } from 'vitest';

import { probeTiff, readTiff, resolutionOf } from '../../../src/codec/tiff/reader.js';
import { makeTiff } from '../../helpers/make-tiff.js';

const RAMP = [0, 64, 128, 192, 255, 32, 96, 160];

describe('readTiff on a plain grayscale file', () => {
  it('reads the samples', () => {
    const tiff = makeTiff({ width: 4, height: 2, samples: RAMP });
    const { surface } = readTiff(tiff);
    expect(surface.toJSON()).toEqual({ width: 4, height: 2, layout: 'gray', depth: 8 });
    expect([...surface.data]).toEqual(RAMP);
  });

  it('reads the same file in big endian', () => {
    const tiff = makeTiff({ width: 4, height: 2, samples: RAMP, little: false });
    expect([...readTiff(tiff).surface.data]).toEqual(RAMP);
  });

  it('reads a file split into several strips', () => {
    const tiff = makeTiff({ width: 4, height: 2, samples: RAMP, rowsPerStrip: 1 });
    expect([...readTiff(tiff).surface.data]).toEqual(RAMP);
  });

  it('reads a strip count that does not divide the height', () => {
    const samples = Array.from({ length: 20 }, (_, i) => i * 10);
    const tiff = makeTiff({ width: 4, height: 5, samples, rowsPerStrip: 2 });
    expect([...readTiff(tiff).surface.data]).toEqual(samples);
  });
});

describe('readTiff on colour and depth', () => {
  it('reads three channels', () => {
    const samples = [1, 2, 3, 4, 5, 6];
    const tiff = makeTiff({ width: 2, height: 1, samples, channels: 3, photometric: 2 });
    const { surface } = readTiff(tiff);
    expect(surface.layout).toBe('rgb');
    expect(surface.getPixel(1, 0)).toEqual([4, 5, 6]);
  });

  it('reads four channels', () => {
    const tiff = makeTiff({
      width: 1,
      height: 1,
      samples: [1, 2, 3, 4],
      channels: 4,
      photometric: 2,
    });
    expect(readTiff(tiff).surface.layout).toBe('rgba');
  });

  it('reads sixteen bit samples in either byte order', () => {
    for (const little of [true, false]) {
      const tiff = makeTiff({ width: 2, height: 1, samples: [1, 65535], depth: 16, little });
      const { surface } = readTiff(tiff);
      expect(surface.depth).toBe(16);
      expect([...surface.data]).toEqual([1, 65535]);
    }
  });
});

describe('readTiff and compression', () => {
  it('reads packbits strips', () => {
    const samples = Array(64).fill(200);
    samples[10] = 5;
    const tiff = makeTiff({
      width: 8,
      height: 8,
      samples,
      compression: 32773,
      rowsPerStrip: 4,
    });
    expect([...readTiff(tiff).surface.data]).toEqual(samples);
  });

  it('refuses a compression it does not know', () => {
    const tiff = makeTiff({ width: 2, height: 1, samples: [1, 2], compression: 7 });
    expect(() => readTiff(tiff)).toThrow(/compression 7 is not read here/);
  });
});

describe('photometric interpretation', () => {
  it('inverts a white is zero capture', () => {
    const tiff = makeTiff({ width: 2, height: 1, samples: [0, 255], photometric: 0 });
    const { surface, photometric } = readTiff(tiff);
    expect(photometric).toBe(0);
    expect([...surface.data]).toEqual([255, 0]);
  });

  it('inverts sixteen bit as well', () => {
    const tiff = makeTiff({
      width: 2,
      height: 1,
      samples: [0, 65535],
      depth: 16,
      photometric: 0,
    });
    expect([...readTiff(tiff).surface.data]).toEqual([65535, 0]);
  });

  it('refuses a palette capture', () => {
    const tiff = makeTiff({ width: 2, height: 1, samples: [0, 1], photometric: 3 });
    expect(() => readTiff(tiff)).toThrow(/palette tiff is not a capture/);
  });
});

describe('resolution', () => {
  it('reads pixels per inch straight through', () => {
    const tiff = makeTiff({ width: 2, height: 1, samples: [1, 2], resolution: 400 });
    expect(readTiff(tiff).resolution).toEqual({ x: 400, y: 400 });
  });

  it('converts from centimetres', () => {
    const tiff = makeTiff({
      width: 2,
      height: 1,
      samples: [1, 2],
      resolution: 100,
      resolutionUnit: 3,
    });
    expect(readTiff(tiff).resolution?.x).toBeCloseTo(254, 6);
  });

  it('reports nothing when the unit says the scale is unknown', () => {
    const tiff = makeTiff({
      width: 2,
      height: 1,
      samples: [1, 2],
      resolution: 100,
      resolutionUnit: 1,
    });
    expect(readTiff(tiff).resolution).toBeNull();
  });

  it('reports nothing when there is no resolution tag', () => {
    const tiff = makeTiff({ width: 2, height: 1, samples: [1, 2] });
    expect(readTiff(tiff).resolution).toBeNull();
    expect(resolutionOf([])).toBeNull();
  });
});

describe('what it refuses', () => {
  it('refuses a planar layout', () => {
    const tiff = makeTiff({ width: 2, height: 1, samples: [1, 2], planar: 2 });
    expect(() => readTiff(tiff)).toThrow(/planar tiff is not read here/);
  });

  it('refuses a depth it does not carry', () => {
    const tiff = makeTiff({ width: 2, height: 1, samples: [1, 2] });
    const view = new DataView(tiff.buffer);
    view.setUint16(10 + 2 * 12 + 8, 4, true);
    expect(() => readTiff(tiff)).toThrow(/only eight and sixteen bit/);
  });

  it('refuses a strip that runs past the end of the file', () => {
    const tiff = makeTiff({ width: 4, height: 2, samples: RAMP });
    const view = new DataView(tiff.buffer);
    const offsetEntry = 10 + 5 * 12;
    view.setUint32(offsetEntry + 8, 9000, true);
    expect(() => readTiff(tiff)).toThrow(/runs past the end of the file/);
  });

  it('refuses a strip shorter than the rows it covers', () => {
    const tiff = makeTiff({ width: 4, height: 2, samples: RAMP });
    const view = new DataView(tiff.buffer);
    const countEntry = 10 + 8 * 12;
    view.setUint32(countEntry + 8, 3, true);
    expect(() => readTiff(tiff)).toThrow(/shorter than its row count/);
  });
});

describe('probeTiff', () => {
  it('reports the shape without reading strips', () => {
    const tiff = makeTiff({
      width: 40,
      height: 30,
      samples: [],
      channels: 3,
      depth: 16,
      photometric: 2,
    });
    expect(probeTiff(tiff)).toEqual({ width: 40, height: 30, layout: 'rgb', depth: 16 });
  });
});
