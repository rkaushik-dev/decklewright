import { inflateSync } from 'node:zlib';

import { CodecError, UnsupportedError } from '../../errors.js';
import { Surface } from '../../raster/surface.js';
import { chunksOfType, findChunk, splitChunks } from './chunks.js';
import { undoFilter } from './filters.js';

/**
 * Reading png.
 *
 * Only the shapes the lab writes and receives: gray, gray with alpha, rgb and
 * rgba, at eight or sixteen bits, not interlaced. A palette image is refused
 * rather than expanded, because a palette in the intake folder means someone
 * ran a capture through a screenshot tool and that is worth stopping.
 */

/** @typedef {import('../../raster/layout.js').LayoutName} LayoutName */

/** @type {Record<number, {layout: LayoutName, channels: number}>} */
const COLOUR_TYPES = {
  0: { layout: 'gray', channels: 1 },
  2: { layout: 'rgb', channels: 3 },
  4: { layout: 'graya', channels: 2 },
  6: { layout: 'rgba', channels: 4 },
};

/** @typedef {{width: number, height: number, depth: number, colourType: number, interlace: number}} PngHeader */

/**
 * @param {Uint8Array} data
 * @returns {PngHeader}
 */
export function readHeaderChunk(data) {
  if (data.length !== 13) {
    throw new CodecError('the IHDR chunk is the wrong length', { length: data.length });
  }
  const view = new DataView(data.buffer, data.byteOffset, data.byteLength);
  return {
    width: view.getUint32(0),
    height: view.getUint32(4),
    depth: data[8],
    colourType: data[9],
    interlace: data[12],
  };
}

/**
 * @param {PngHeader} header
 * @returns {void}
 */
function assertSupported(header) {
  if (header.width === 0 || header.height === 0) {
    throw new CodecError('a png with no pixels', {
      width: header.width,
      height: header.height,
    });
  }
  if (header.interlace !== 0) {
    throw new UnsupportedError('interlaced png is not read here', { interlace: header.interlace });
  }
  if (header.colourType === 3) {
    throw new UnsupportedError('a palette png is not a capture', { colourType: 3 });
  }
  if (!COLOUR_TYPES[header.colourType]) {
    throw new CodecError(`unknown colour type ${header.colourType}`, {
      colourType: header.colourType,
    });
  }
  if (header.depth !== 8 && header.depth !== 16) {
    throw new UnsupportedError('only eight and sixteen bit png is read here', {
      depth: header.depth,
    });
  }
}

/**
 * @param {readonly {data: Uint8Array}[]} parts
 * @returns {Uint8Array}
 */
function concat(parts) {
  let total = 0;
  for (const part of parts) total += part.data.length;
  const out = new Uint8Array(total);
  let at = 0;
  for (const part of parts) {
    out.set(part.data, at);
    at += part.data.length;
  }
  return out;
}

/**
 * @param {Uint8Array} bytes
 * @returns {{surface: Surface, text: Record<string, string>, physical: {x: number, y: number, unit: number}|null}}
 */
export function decodePng(bytes) {
  const chunks = splitChunks(bytes);
  const ihdr = findChunk(chunks, 'IHDR');
  if (!ihdr) throw new CodecError('no IHDR chunk', {});
  const header = readHeaderChunk(ihdr.data);
  assertSupported(header);

  const idat = chunksOfType(chunks, 'IDAT');
  if (idat.length === 0) throw new CodecError('no IDAT chunk', {});

  let raw;
  try {
    raw = new Uint8Array(inflateSync(Buffer.from(concat(idat))));
  } catch (cause) {
    throw new CodecError('the compressed data will not inflate', { cause });
  }

  const { layout, channels } = COLOUR_TYPES[header.colourType];
  const bytesPerSample = header.depth / 8;
  const step = channels * bytesPerSample;
  const rowBytes = header.width * step;
  const expected = (rowBytes + 1) * header.height;
  if (raw.length < expected) {
    throw new CodecError('the inflated data is shorter than the image', {
      expected,
      found: raw.length,
    });
  }

  const surface = Surface.create({
    width: header.width,
    height: header.height,
    layout,
    depth: /** @type {8|16} */ (header.depth),
  });

  let previous = null;
  for (let y = 0; y < header.height; y += 1) {
    const at = y * (rowBytes + 1);
    const filter = raw[at];
    const row = raw.subarray(at + 1, at + 1 + rowBytes);
    const restored = undoFilter(filter, row, previous, step);
    const target = surface.row(y);
    if (bytesPerSample === 1) {
      target.set(restored);
    } else {
      for (let i = 0; i < target.length; i += 1) {
        target[i] = (restored[i * 2] << 8) | restored[i * 2 + 1];
      }
    }
    previous = restored;
  }

  return {
    surface,
    text: readText(chunks),
    physical: readPhysical(chunks),
  };
}

/**
 * @param {readonly import('./chunks.js').Chunk[]} chunks
 * @returns {Record<string, string>}
 */
export function readText(chunks) {
  /** @type {Record<string, string>} */
  const out = {};
  for (const chunk of chunksOfType(chunks, 'tEXt')) {
    const split = chunk.data.indexOf(0);
    if (split <= 0) continue;
    const key = String.fromCharCode(...chunk.data.subarray(0, split));
    out[key] = String.fromCharCode(...chunk.data.subarray(split + 1));
  }
  return out;
}

/**
 * The pHYs chunk, which is where a capture keeps its sampling frequency. Unit
 * one means pixels per metre and unit zero means the ratio is known but the
 * scale is not, which for our purposes is the same as nothing.
 *
 * @param {readonly import('./chunks.js').Chunk[]} chunks
 * @returns {{x: number, y: number, unit: number}|null}
 */
export function readPhysical(chunks) {
  const chunk = findChunk(chunks, 'pHYs');
  if (!chunk || chunk.data.length !== 9) return null;
  const view = new DataView(chunk.data.buffer, chunk.data.byteOffset, chunk.data.byteLength);
  return { x: view.getUint32(0), y: view.getUint32(4), unit: chunk.data[8] };
}

/**
 * The shape without the samples, which is what the intake stage wants before
 * it decides whether to load a four hundred megabyte master.
 *
 * @param {Uint8Array} bytes
 * @returns {{width: number, height: number, layout: string, depth: number}}
 */
export function probePng(bytes) {
  const chunks = splitChunks(bytes, { checkCrc: false });
  const ihdr = findChunk(chunks, 'IHDR');
  if (!ihdr) throw new CodecError('no IHDR chunk', {});
  const header = readHeaderChunk(ihdr.data);
  assertSupported(header);
  return {
    width: header.width,
    height: header.height,
    layout: COLOUR_TYPES[header.colourType].layout,
    depth: header.depth,
  };
}
