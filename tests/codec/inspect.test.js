import { describe, expect, it } from 'vitest';

import {
  classify,
  describe as describeFile,
  inspect,
  inspectDeep,
  isReadable,
  triage,
} from '../../src/codec/inspect.js';
import { encodePng } from '../../src/codec/png/encode.js';
import { writePnm } from '../../src/codec/pnm.js';
import { UnsupportedError } from '../../src/errors.js';
import { Surface } from '../../src/raster/surface.js';
import { makeTiff } from '../helpers/make-tiff.js';

/**
 * @returns {Surface}
 */
function sample() {
  return Surface.create({ width: 6, height: 4, layout: 'rgb', fill: 90 });
}

describe('inspect', () => {
  it('reports a good png', () => {
    const out = inspect(encodePng(sample()), { path: 'q17.png' });
    expect(out.ok).toBe(true);
    expect(out.format).toBe('png');
    expect(out.shape).toEqual({ width: 6, height: 4, layout: 'rgb', depth: 8 });
    expect(out.problems).toEqual([]);
  });

  it('reports a good tiff', () => {
    const tiff = makeTiff({ width: 4, height: 2, samples: [1, 2, 3, 4, 5, 6, 7, 8] });
    const out = inspect(tiff, { path: 'q17.tif' });
    expect(out.ok).toBe(true);
    expect(out.format).toBe('tiff');
    expect(out.shape?.layout).toBe('gray');
  });

  it('notes a wrong extension without failing the file', () => {
    const out = inspect(writePnm(sample()), { path: 'q17.png' });
    expect(out.ok).toBe(true);
    expect(out.format).toBe('pnm');
    expect(out.problems).toEqual([
      { kind: 'wrongExtension', detail: 'named png but the bytes say pnm' },
    ]);
  });

  it('says nothing about the extension when there is no path', () => {
    expect(inspect(writePnm(sample())).problems).toEqual([]);
  });

  it('reports an empty file', () => {
    const out = inspect(new Uint8Array(), { path: 'q17.png' });
    expect(out.ok).toBe(false);
    expect(out.problems[0].kind).toBe('empty');
  });

  it('reports bytes nothing recognises', () => {
    const out = inspect(new Uint8Array([1, 2, 3, 4, 5, 6, 7, 8, 9]));
    expect(out.ok).toBe(false);
    expect(out.format).toBeNull();
    expect(out.problems[0].kind).toBe('unreadable');
  });

  it('reports a truncated png as truncated', () => {
    const png = encodePng(sample());
    const out = inspect(png.subarray(0, 20), { path: 'q17.png' });
    expect(out.ok).toBe(false);
    expect(out.problems[0].kind).toBe('truncated');
  });

  it('reports a palette png as unsupported', () => {
    const png = encodePng(sample());
    const cut = png.slice();
    cut[8 + 8 + 9] = 3;
    const out = inspect(cut);
    expect(out.problems[0].kind).toBe('unsupported');
  });
});

describe('inspectDeep', () => {
  it('agrees with the shallow look on a good file', () => {
    const out = inspectDeep(encodePng(sample()), { path: 'q17.png' });
    expect(out.ok).toBe(true);
  });

  it('catches a strip that decodes short', () => {
    const tiff = makeTiff({ width: 4, height: 2, samples: [1, 2, 3, 4, 5, 6, 7, 8] });
    const view = new DataView(tiff.buffer);
    view.setUint32(10 + 8 * 12 + 8, 3, true);
    expect(inspect(tiff).ok).toBe(true);
    const deep = inspectDeep(tiff);
    expect(deep.ok).toBe(false);
    expect(deep.problems[0].kind).toBe('truncated');
  });

  it('does not read a file the shallow look already rejected', () => {
    expect(inspectDeep(new Uint8Array([9, 9])).ok).toBe(false);
  });
});

describe('classify', () => {
  it('separates the three kinds', () => {
    expect(classify(new UnsupportedError('no')).kind).toBe('unsupported');
    expect(classify(new Error('the file stops early')).kind).toBe('truncated');
    expect(classify(new Error('something else entirely')).kind).toBe('unreadable');
    expect(classify('a bare string').detail).toBe('a bare string');
  });
});

describe('describe', () => {
  it('writes one line for a good file', () => {
    const out = describeFile(inspect(encodePng(sample()), { path: 'q17.png' }));
    expect(out).toBe('q17.png: png 6x4 rgb8');
  });

  it('mentions a note on an otherwise good file', () => {
    const out = describeFile(inspect(writePnm(sample()), { path: 'q17.png' }));
    expect(out).toContain('named png but the bytes say pnm');
  });

  it('writes the problem for a bad one', () => {
    expect(describeFile(inspect(new Uint8Array(), { path: 'q17.png' }))).toBe(
      'q17.png: empty, the file holds no bytes',
    );
  });

  it('copes with no path', () => {
    expect(describeFile(inspect(new Uint8Array([1, 2])))).toContain('(no name)');
  });
});

describe('triage', () => {
  it('splits a folder into what can be worked and what cannot', () => {
    const files = [
      { bytes: encodePng(sample()), path: 'a.png' },
      { bytes: new Uint8Array(), path: 'b.png' },
      { bytes: writePnm(sample()), path: 'c.ppm' },
      { bytes: new Uint8Array([1, 2, 3]), path: 'd.png' },
    ];
    const { good, bad } = triage(files);
    expect(good.map((g) => g.path)).toEqual(['a.png', 'c.ppm']);
    expect(bad.map((b) => b.path)).toEqual(['b.png', 'd.png']);
  });
});

describe('isReadable', () => {
  it('is a quick yes or no', () => {
    expect(isReadable(encodePng(sample()))).toBe(true);
    expect(isReadable(new Uint8Array([1, 2, 3]))).toBe(false);
  });
});
