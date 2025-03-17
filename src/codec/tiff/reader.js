import { CodecError, UnsupportedError } from '../../errors.js';
import { Surface } from '../../raster/surface.js';
import { lzwDecode } from './lzw.js';
import { unpackBits } from './packbits.js';
import {
  COMPRESSION,
  PHOTOMETRIC,
  RESOLUTION_UNIT,
  asBag,
  list,
  readIfd,
  readTiffHeader,
  scalar,
} from './tags.js';

/** @typedef {import('../../raster/layout.js').LayoutName} LayoutName */

/**
 * Baseline tiff, strips only.
 *
 * The flatbed writes one strip per few rows, uncompressed or packbits, one
 * plane, black is zero. That is what this reads. Tiled tiff, planar layouts
 * and floating point samples are all real parts of the format and none of them
 * has ever come out of this lab, so they are refused by name rather than half
 * supported.
 */

/**
 * @param {readonly number[]} bits
 * @param {number} samples
 * @returns {{layout: LayoutName, depth: import('../../raster/layout.js').Depth}}
 */
function layoutFor(bits, samples) {
  const depth = bits[0];
  if (!bits.every((value) => value === depth)) {
    throw new UnsupportedError('channels of different widths are not read here', {
      bits: [...bits],
    });
  }
  if (depth !== 8 && depth !== 16) {
    throw new UnsupportedError('only eight and sixteen bit tiff is read here', { depth });
  }
  /** @type {Record<number, LayoutName>} */
  const byCount = { 1: 'gray', 2: 'graya', 3: 'rgb', 4: 'rgba' };
  const layout = byCount[samples];
  if (!layout) {
    throw new UnsupportedError(`a tiff with ${samples} samples per pixel is not read here`, {
      samples,
    });
  }
  return { layout, depth };
}

/**
 * @param {Uint8Array} bytes
 * @param {number} compression
 * @param {number} expected
 * @returns {Uint8Array}
 */
function decompress(bytes, compression, expected) {
  switch (compression) {
    case COMPRESSION.NONE:
      if (bytes.length < expected) {
        throw new CodecError('a strip is shorter than its row count needs', {
          expected,
          found: bytes.length,
        });
      }
      return bytes.subarray(0, expected);
    case COMPRESSION.PACKBITS:
      return unpackBits(bytes, expected);
    case COMPRESSION.LZW:
      return lzwDecode(bytes, expected);
    default:
      throw new UnsupportedError(`compression ${compression} is not read here`, { compression });
  }
}

/**
 * @param {readonly import('./tags.js').Entry[]} entries
 * @returns {{x: number, y: number}|null} sampling frequency in pixels per inch
 */
export function resolutionOf(entries) {
  const x = scalar(entries, 'xResolution', 0);
  const y = scalar(entries, 'yResolution', 0);
  if (x <= 0 || y <= 0) return null;
  const unit = scalar(entries, 'resolutionUnit', RESOLUTION_UNIT.INCH);
  if (unit === RESOLUTION_UNIT.NONE) return null;
  const factor = unit === RESOLUTION_UNIT.CENTIMETRE ? 2.54 : 1;
  return { x: x * factor, y: y * factor };
}

/**
 * @param {Uint8Array} bytes
 * @returns {{surface: Surface, tags: Record<string, number[]|string>, resolution: {x: number, y: number}|null, photometric: number}}
 */
export function readTiff(bytes) {
  const { littleEndian, firstIfd } = readTiffHeader(bytes);
  const { entries } = readIfd(bytes, firstIfd, littleEndian);

  const width = scalar(entries, 'imageWidth');
  const height = scalar(entries, 'imageLength');
  const samples = scalar(entries, 'samplesPerPixel', 1);
  const bits = list(entries, 'bitsPerSample');
  const compression = scalar(entries, 'compression', COMPRESSION.NONE);
  const photometric = scalar(entries, 'photometricInterpretation', PHOTOMETRIC.BLACK_IS_ZERO);
  const planar = scalar(entries, 'planarConfiguration', 1);

  if (planar !== 1) {
    throw new UnsupportedError('a planar tiff is not read here', { planar });
  }
  if (photometric === PHOTOMETRIC.PALETTE) {
    throw new UnsupportedError('a palette tiff is not a capture', { photometric });
  }

  const { layout, depth } = layoutFor(bits.length > 0 ? bits : [8], samples);
  const rowsPerStrip = scalar(entries, 'rowsPerStrip', height);
  const offsets = list(entries, 'stripOffsets');
  const counts = list(entries, 'stripByteCounts');
  if (offsets.length === 0) throw new CodecError('the strip offsets tag is missing', {});
  if (offsets.length !== counts.length) {
    throw new CodecError('the strip offsets and byte counts disagree', {
      offsets: offsets.length,
      counts: counts.length,
    });
  }

  const surface = Surface.create({ width, height, layout, depth });
  const bytesPerRow = width * samples * (depth / 8);
  const white = photometric === PHOTOMETRIC.WHITE_IS_ZERO;
  const top = surface.maxValue;

  let row = 0;
  for (let strip = 0; strip < offsets.length; strip += 1) {
    const start = offsets[strip];
    const length = counts[strip];
    if (start + length > bytes.length) {
      throw new CodecError('a strip runs past the end of the file', { strip, start, length });
    }
    const rows = Math.min(rowsPerStrip, height - row);
    if (rows <= 0) break;
    const raw = decompress(bytes.subarray(start, start + length), compression, bytesPerRow * rows);

    for (let r = 0; r < rows; r += 1) {
      const target = surface.row(row + r);
      const base = r * bytesPerRow;
      if (depth === 8) {
        for (let i = 0; i < target.length; i += 1) {
          target[i] = white ? top - raw[base + i] : raw[base + i];
        }
      } else {
        for (let i = 0; i < target.length; i += 1) {
          const at = base + i * 2;
          const value = littleEndian ? raw[at] | (raw[at + 1] << 8) : (raw[at] << 8) | raw[at + 1];
          target[i] = white ? top - value : value;
        }
      }
    }
    row += rows;
  }

  if (row < height) {
    throw new CodecError('the strips do not cover the whole image', { covered: row, height });
  }

  return { surface, tags: asBag(entries), resolution: resolutionOf(entries), photometric };
}

/**
 * @param {Uint8Array} bytes
 * @returns {{width: number, height: number, layout: string, depth: number}}
 */
export function probeTiff(bytes) {
  const { littleEndian, firstIfd } = readTiffHeader(bytes);
  const { entries } = readIfd(bytes, firstIfd, littleEndian);
  const bits = list(entries, 'bitsPerSample');
  const samples = scalar(entries, 'samplesPerPixel', 1);
  const { layout, depth } = layoutFor(bits.length > 0 ? bits : [8], samples);
  return {
    width: scalar(entries, 'imageWidth'),
    height: scalar(entries, 'imageLength'),
    layout,
    depth,
  };
}
