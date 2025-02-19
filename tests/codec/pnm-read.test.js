import { describe, expect, it } from 'vitest';

import { looksLikePnm, probePnm, readPnm, readPnmHeader } from '../../src/codec/pnm.js';

/**
 * @param {string} text
 * @param {readonly number[]} [tail]
 * @returns {Uint8Array}
 */
function bytes(text, tail = []) {
  const head = [...text].map((ch) => ch.charCodeAt(0));
  return new Uint8Array([...head, ...tail]);
}

describe('readPnmHeader', () => {
  it('reads the five tokens', () => {
    const header = readPnmHeader(bytes('P5 4 3 255 '));
    expect(header.magic).toBe('P5');
    expect(header.width).toBe(4);
    expect(header.height).toBe(3);
    expect(header.max).toBe(255);
  });

  it('skips comments wherever they sit', () => {
    const header = readPnmHeader(bytes('P6\n# from the copy stand\n4\n# even here\n3\n255\n'));
    expect(header.width).toBe(4);
    expect(header.height).toBe(3);
  });

  it('tolerates any run of whitespace', () => {
    expect(readPnmHeader(bytes('P5\t\r\n  4\n\n3   255\n')).width).toBe(4);
  });

  it('refuses a magic it does not know', () => {
    expect(() => readPnmHeader(bytes('P7 4 3 255 '))).toThrow(/not a netpbm file, magic was P7/);
    expect(() => readPnmHeader(bytes('JFIF'))).toThrow(/not a netpbm file/);
  });

  it('refuses a header that stops early', () => {
    expect(() => readPnmHeader(bytes('P5 4 3'))).toThrow(/the header stops early/);
  });

  it('refuses a dimension that is not a positive whole number', () => {
    expect(() => readPnmHeader(bytes('P5 0 3 255 '))).toThrow(/width in the header/);
    expect(() => readPnmHeader(bytes('P5 4 x 255 '))).toThrow(/height in the header/);
  });

  it('refuses a maximum above sixteen bits', () => {
    expect(() => readPnmHeader(bytes('P5 4 3 70000 '))).toThrow(/above 65535/);
  });
});

describe('readPnm on binary files', () => {
  it('reads an eight bit grayscale file', () => {
    const s = readPnm(bytes('P5 2 2 255\n', [0, 64, 128, 255]));
    expect(s.toJSON()).toEqual({ width: 2, height: 2, layout: 'gray', depth: 8 });
    expect([...s.data]).toEqual([0, 64, 128, 255]);
  });

  it('reads an eight bit colour file', () => {
    const s = readPnm(bytes('P6 2 1 255\n', [1, 2, 3, 4, 5, 6]));
    expect(s.layout).toBe('rgb');
    expect(s.getPixel(0, 0)).toEqual([1, 2, 3]);
    expect(s.getPixel(1, 0)).toEqual([4, 5, 6]);
  });

  it('reads a sixteen bit file big endian', () => {
    const s = readPnm(bytes('P5 2 1 65535\n', [0x00, 0x01, 0xff, 0xff]));
    expect(s.depth).toBe(16);
    expect([...s.data]).toEqual([1, 65535]);
  });

  it('refuses a file that stops before the samples do', () => {
    expect(() => readPnm(bytes('P5 4 4 255\n', [1, 2, 3]))).toThrow(/stops before the samples/);
  });

  it('ignores trailing bytes after the samples', () => {
    const s = readPnm(bytes('P5 1 1 255\n', [7, 99, 99]));
    expect([...s.data]).toEqual([7]);
  });
});

describe('readPnm on ascii files', () => {
  it('reads a grayscale file', () => {
    const s = readPnm(bytes('P2 2 2 255\n0 64\n128 255\n'));
    expect([...s.data]).toEqual([0, 64, 128, 255]);
  });

  it('reads a colour file', () => {
    const s = readPnm(bytes('P3 2 1 255\n1 2 3 4 5 6\n'));
    expect(s.getPixel(1, 0)).toEqual([4, 5, 6]);
  });

  it('skips comments among the samples', () => {
    const s = readPnm(bytes('P2 2 1 255\n10 # the second one is dark\n20\n'));
    expect([...s.data]).toEqual([10, 20]);
  });

  it('refuses a sample that is not a number', () => {
    expect(() => readPnm(bytes('P2 2 1 255\n10 fish\n'))).toThrow(/not a whole number/);
  });

  it('refuses a file with too few samples', () => {
    expect(() => readPnm(bytes('P2 4 4 255\n1 2 3\n'))).toThrow(/the header stops early/);
  });
});

describe('probePnm', () => {
  it('reports the shape without reading samples', () => {
    expect(probePnm(bytes('P6 800 600 65535\n'))).toEqual({
      width: 800,
      height: 600,
      layout: 'rgb',
      depth: 16,
    });
  });
});

describe('looksLikePnm', () => {
  it('accepts the four magics and nothing else', () => {
    for (const magic of ['P2', 'P3', 'P5', 'P6']) {
      expect(looksLikePnm(bytes(`${magic} 1 1 1 `))).toBe(true);
    }
    expect(looksLikePnm(bytes('P1 1 1 '))).toBe(false);
    expect(looksLikePnm(bytes('PNG'))).toBe(false);
    expect(looksLikePnm(new Uint8Array([0x50]))).toBe(false);
    expect(looksLikePnm(new Uint8Array())).toBe(false);
  });
});
