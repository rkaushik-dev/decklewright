import { describe, expect, it } from 'vitest';

import * as codec from '../../src/codec/index.js';
import { Surface } from '../../src/raster/surface.js';
import { makeTiff } from '../helpers/make-tiff.js';

/**
 * @returns {Surface}
 */
function sample() {
  const s = Surface.create({ width: 5, height: 4, layout: 'rgb', depth: 16 });
  for (let y = 0; y < 4; y += 1) {
    for (let x = 0; x < 5; x += 1) s.setPixel(x, y, [x * 1000, y * 2000, 30000]);
  }
  return s;
}

describe('the barrel', () => {
  it('exports the three entry points the stages use', () => {
    for (const name of ['readImage', 'writeImage', 'probeImage', 'inspect', 'triage']) {
      expect(typeof codec[name]).toBe('function');
    }
  });

  it('exports no undefined bindings', () => {
    for (const [name, value] of Object.entries(codec)) {
      expect(value, name).toBeDefined();
    }
  });

  it('still exposes the individual decoders for the odd caller that needs one', () => {
    expect(typeof codec.decodePng).toBe('function');
    expect(typeof codec.readTiff).toBe('function');
    expect(typeof codec.readPnm).toBe('function');
  });
});

describe('a short run through the layer', () => {
  it('writes a master, reads it back and checks its chunk policy', () => {
    const original = sample();
    const written = codec.withMetadata(
      codec.encodePng(original, { ppi: { x: 400, y: 400 }, text: { Title: 'plate 14' } }),
      { time: new Date(Date.UTC(2025, 1, 20, 10, 0, 0)) },
    );

    expect(codec.formatOf(written)).toBe('png');
    expect(codec.checkChunkPolicy(written, codec.MASTER_KEEP).ok).toBe(true);

    const back = codec.readImage(written, { path: 'q17.png' });
    expect(back.toJSON()).toEqual(original.toJSON());
    expect([...back.data]).toEqual([...original.data]);
    expect(codec.readTime(written)?.getUTCFullYear()).toBe(2025);
  });

  it('strips a master down to a service copy without touching the pixels', () => {
    const master = codec.encodePng(sample(), { ppi: { x: 400, y: 400 }, text: { Title: 'x' } });
    const service = codec.keepOnly(master, codec.SERVICE_KEEP);
    expect(codec.chunkTypes(service)).toEqual(['IHDR', 'IDAT', 'IEND']);
    expect([...codec.imageDataOf(service)]).toEqual([...codec.imageDataOf(master)]);
  });

  it('reads a tiff off the flatbed and writes it back out as png', () => {
    const samples = Array.from({ length: 24 }, (_, i) => (i * 9) % 256);
    const tiff = makeTiff({
      width: 4,
      height: 2,
      samples,
      channels: 3,
      photometric: 2,
      compression: 5,
      resolution: 400,
    });
    expect(codec.formatOf(tiff)).toBe('tiff');
    const read = codec.readTiff(tiff);
    expect(read.resolution).toEqual({ x: 400, y: 400 });

    const png = codec.writeImage(read.surface, 'png', { ppi: read.resolution });
    const back = codec.readImage(png);
    expect([...back.data]).toEqual(samples);
  });

  it('triages a folder that holds one of everything', () => {
    const files = [
      { bytes: codec.encodePng(sample()), path: 'a.png' },
      { bytes: codec.writePnm(Surface.create({ width: 2, height: 2 })), path: 'b.ppm' },
      { bytes: makeTiff({ width: 2, height: 1, samples: [1, 2] }), path: 'c.tif' },
      { bytes: new Uint8Array([0xff, 0xd8]), path: 'd.jpg' },
    ];
    const { good, bad } = codec.triage(files);
    expect(good.map((g) => g.format)).toEqual(['png', 'pnm', 'tiff']);
    expect(bad).toHaveLength(1);
  });
});
