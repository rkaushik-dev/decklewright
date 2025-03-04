import { describe, expect, it } from 'vitest';

import { encodePng } from '../../src/codec/png/encode.js';
import { writePnm } from '../../src/codec/pnm.js';
import {
  CODEC_NAMES,
  codecForBytes,
  codecForPath,
  codecNamed,
  extensionFor,
  formatOf,
  probeImage,
  readImage,
  writeImage,
} from '../../src/codec/registry.js';
import { Surface } from '../../src/raster/surface.js';

/**
 * @returns {Surface}
 */
function sample() {
  const s = Surface.create({ width: 4, height: 3, layout: 'rgb' });
  for (let y = 0; y < 3; y += 1) {
    for (let x = 0; x < 4; x += 1) s.setPixel(x, y, [x * 20, y * 30, 100]);
  }
  return s;
}

describe('the codec list', () => {
  it('names the two formats the lab handles', () => {
    expect([...CODEC_NAMES]).toEqual(['png', 'pnm']);
  });

  it('finds a codec by name, case insensitively', () => {
    expect(codecNamed('png').name).toBe('png');
    expect(codecNamed('PNG').name).toBe('png');
  });

  it('lists what it knows when the name is wrong', () => {
    try {
      codecNamed('jpeg');
      throw new Error('should have thrown');
    } catch (err) {
      expect(err.message).toBe('no codec called jpeg');
      expect(err.details.known).toEqual(['png', 'pnm']);
    }
  });
});

describe('codecForPath', () => {
  it('matches on the extension', () => {
    expect(codecForPath('/vault/q17.png')?.name).toBe('png');
    expect(codecForPath('/vault/q17.PGM')?.name).toBe('pnm');
    expect(codecForPath('/vault/q17.ppm')?.name).toBe('pnm');
  });

  it('gives nothing for an unknown or missing extension', () => {
    expect(codecForPath('/vault/q17.tif')).toBeNull();
    expect(codecForPath('/vault/q17')).toBeNull();
  });
});

describe('codecForBytes', () => {
  it('sniffs both formats', () => {
    expect(codecForBytes(encodePng(sample()))?.name).toBe('png');
    expect(codecForBytes(writePnm(sample()))?.name).toBe('pnm');
  });

  it('gives nothing for bytes it does not know', () => {
    expect(codecForBytes(new Uint8Array([0xff, 0xd8, 0xff, 0xe0]))).toBeNull();
    expect(codecForBytes(new Uint8Array())).toBeNull();
  });

  it('reports the format name', () => {
    expect(formatOf(writePnm(sample()))).toBe('pnm');
    expect(formatOf(new Uint8Array([1, 2, 3]))).toBeNull();
  });
});

describe('readImage', () => {
  it('reads either format from its bytes', () => {
    for (const bytes of [encodePng(sample()), writePnm(sample())]) {
      const surface = readImage(bytes);
      expect(surface.toJSON()).toEqual(sample().toJSON());
      expect([...surface.data]).toEqual([...sample().data]);
    }
  });

  it('lets the bytes win over the name', () => {
    const surface = readImage(writePnm(sample()), { path: 'q17.png' });
    expect(surface.width).toBe(4);
  });

  it('refuses bytes it does not recognise and quotes the head', () => {
    try {
      readImage(new Uint8Array([1, 2, 3, 4, 5, 6, 7, 8, 9]), { path: 'q17.jpg' });
      throw new Error('should have thrown');
    } catch (err) {
      expect(err.message).toBe('nothing here recognises those bytes');
      expect(err.details.path).toBe('q17.jpg');
      expect(err.details.head).toEqual([1, 2, 3, 4, 5, 6, 7, 8]);
    }
  });
});

describe('probeImage', () => {
  it('reports the shape without decoding', () => {
    expect(probeImage(encodePng(sample()))).toEqual({
      width: 4,
      height: 3,
      layout: 'rgb',
      depth: 8,
    });
    expect(probeImage(writePnm(sample())).layout).toBe('rgb');
  });

  it('refuses bytes it does not recognise', () => {
    expect(() => probeImage(new Uint8Array([9, 9]))).toThrow(/recognises those bytes/);
  });
});

describe('writeImage', () => {
  it('writes through the named codec', () => {
    expect(formatOf(writeImage(sample(), 'png'))).toBe('png');
    expect(formatOf(writeImage(sample(), 'pnm'))).toBe('pnm');
  });

  it('passes options through to the codec', () => {
    const ascii = writeImage(sample(), 'pnm', { ascii: true });
    expect(String.fromCharCode(ascii[0], ascii[1])).toBe('P3');
  });

  it('round trips through both formats', () => {
    for (const format of CODEC_NAMES) {
      const back = readImage(writeImage(sample(), format));
      expect([...back.data]).toEqual([...sample().data]);
    }
  });

  it('refuses a format it does not know', () => {
    expect(() => writeImage(sample(), 'jpeg')).toThrow(/no codec called jpeg/);
  });
});

describe('extensionFor', () => {
  it('gives the first extension of the codec', () => {
    expect(extensionFor('png')).toBe('.png');
    expect(extensionFor('pnm')).toBe('.pnm');
  });
});
