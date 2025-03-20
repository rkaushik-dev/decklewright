import { deflateSync } from 'node:zlib';

import { CodecError } from '../../errors.js';
import { assertInRange } from '../../util/assert.js';
import { latin1Bytes, uint32BE } from '../bytes.js';
import { assemble } from './chunks.js';
import { FILTER_NONE, applyFilter, chooseFilter } from './filters.js';

/** @typedef {import('../../raster/surface.js').Surface} Surface */
/** @typedef {import('../../raster/layout.js').LayoutName} LayoutName */

/**
 * Writing png.
 *
 * Masters go out at sixteen bits with the metre based physical chunk filled
 * in, because the archive reads sampling frequency out of the file rather than
 * out of a sidecar that can get separated from it. Derivatives go out at eight
 * with nothing but the pixels.
 */

/** @type {Record<LayoutName, number>} */
const COLOUR_TYPE = { gray: 0, rgb: 2, graya: 4, rgba: 6 };

const METRES_PER_INCH = 0.0254;

/**
 * @param {Surface} surface
 * @returns {Uint8Array}
 */
function headerChunk(surface) {
  const out = new Uint8Array(13);
  const view = new DataView(out.buffer);
  view.setUint32(0, surface.width);
  view.setUint32(4, surface.height);
  out[8] = surface.depth;
  out[9] = COLOUR_TYPE[surface.layout];
  out[10] = 0;
  out[11] = 0;
  out[12] = 0;
  return out;
}

/**
 * @param {string} key
 * @param {string} value
 * @returns {{type: string, data: Uint8Array}}
 */
export function textChunk(key, value) {
  if (key.length === 0 || key.length > 79) {
    throw new CodecError('a png text key is one to seventy nine characters', { key });
  }
  const keyBytes = latin1Bytes(key, 'a png text key');
  const valueBytes = latin1Bytes(value, 'png text');
  const data = new Uint8Array(keyBytes.length + 1 + valueBytes.length);
  data.set(keyBytes, 0);
  data[keyBytes.length] = 0;
  data.set(valueBytes, keyBytes.length + 1);
  return { type: 'tEXt', data };
}

/**
 * @param {number} ppiX
 * @param {number} ppiY
 * @returns {{type: string, data: Uint8Array}}
 */
export function physicalChunk(ppiX, ppiY) {
  const data = new Uint8Array(9);
  data.set(uint32BE(Math.round(ppiX / METRES_PER_INCH)), 0);
  data.set(uint32BE(Math.round(ppiY / METRES_PER_INCH)), 4);
  data[8] = 1;
  return { type: 'pHYs', data };
}

/**
 * @param {Surface} surface
 * @param {object} [options]
 * @param {Record<string, string>} [options.text]
 * @param {{x: number, y: number}} [options.ppi]
 * @param {'auto'|'none'} [options.filter]
 * @param {number} [options.level] zlib level, zero through nine
 * @returns {Uint8Array}
 */
export function encodePng(surface, { text, ppi, filter = 'auto', level = 6 } = {}) {
  if (!(surface.layout in COLOUR_TYPE)) {
    throw new CodecError('png does not carry that layout', { layout: surface.layout });
  }
  assertInRange(level, 0, 9, 'level');

  const bytesPerSample = surface.depth / 8;
  const step = surface.channels * bytesPerSample;
  const rowBytes = surface.width * step;
  const raw = new Uint8Array((rowBytes + 1) * surface.height);

  /** @type {Uint8Array|null} */
  let previousRaw = null;
  const current = new Uint8Array(rowBytes);
  for (let y = 0; y < surface.height; y += 1) {
    const samples = surface.row(y);
    if (bytesPerSample === 1) {
      current.set(samples);
    } else {
      for (let i = 0; i < samples.length; i += 1) {
        current[i * 2] = (samples[i] >> 8) & 0xff;
        current[i * 2 + 1] = samples[i] & 0xff;
      }
    }
    const at = y * (rowBytes + 1);
    if (filter === 'none') {
      raw[at] = FILTER_NONE;
      raw.set(applyFilter(FILTER_NONE, current, previousRaw, step), at + 1);
    } else {
      const chosen = chooseFilter(current, previousRaw, step);
      raw[at] = chosen.filter;
      raw.set(chosen.data, at + 1);
    }
    previousRaw = current.slice();
  }

  /** @type {{type: string, data: Uint8Array}[]} */
  const chunks = [{ type: 'IHDR', data: headerChunk(surface) }];
  if (ppi) chunks.push(physicalChunk(ppi.x, ppi.y));
  for (const [key, value] of Object.entries(text ?? {})) chunks.push(textChunk(key, value));
  chunks.push({ type: 'IDAT', data: new Uint8Array(deflateSync(Buffer.from(raw), { level })) });
  chunks.push({ type: 'IEND', data: new Uint8Array() });

  return assemble(chunks);
}
