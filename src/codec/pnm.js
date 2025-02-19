import { CodecError } from '../errors.js';
import { Surface } from '../raster/surface.js';

/**
 * Netpbm, which is what the copy stand software drops on the watch folder.
 *
 * It is a header of five whitespace separated tokens and then the samples, so
 * there is nothing to get wrong and nothing to depend on. The lab keeps it as
 * the intake format for exactly that reason, and everything downstream is
 * written against a surface rather than against this.
 */

/** @typedef {{magic: string, width: number, height: number, max: number, offset: number}} PnmHeader */

const MAGICS = new Set(['P2', 'P3', 'P5', 'P6']);

/**
 * Header tokens are separated by any run of whitespace, and a comment runs
 * from a hash to the end of its line. A comment may sit between any two
 * tokens, including between the width and the height.
 *
 * @param {Uint8Array} bytes
 * @param {number} start
 * @returns {{token: string, next: number}}
 */
function readToken(bytes, start) {
  let i = start;
  for (;;) {
    while (i < bytes.length && isSpace(bytes[i])) i += 1;
    if (i < bytes.length && bytes[i] === 0x23) {
      while (i < bytes.length && bytes[i] !== 0x0a) i += 1;
      continue;
    }
    break;
  }
  if (i >= bytes.length) throw new CodecError('the header stops early', { at: start });
  let end = i;
  while (end < bytes.length && !isSpace(bytes[end])) end += 1;
  let token = '';
  for (let k = i; k < end; k += 1) token += String.fromCharCode(bytes[k]);
  return { token, next: end };
}

/**
 * @param {number} byte
 * @returns {boolean}
 */
function isSpace(byte) {
  return byte === 0x20 || byte === 0x09 || byte === 0x0a || byte === 0x0d || byte === 0x0b;
}

/**
 * @param {Uint8Array} bytes
 * @returns {PnmHeader}
 */
export function readPnmHeader(bytes) {
  const magic = readToken(bytes, 0);
  if (!MAGICS.has(magic.token)) {
    throw new CodecError(`not a netpbm file, magic was ${magic.token}`, { magic: magic.token });
  }
  const width = readToken(bytes, magic.next);
  const height = readToken(bytes, width.next);
  const max = readToken(bytes, height.next);

  const parsed = {
    magic: magic.token,
    width: Number.parseInt(width.token, 10),
    height: Number.parseInt(height.token, 10),
    max: Number.parseInt(max.token, 10),
    offset: max.next + 1,
  };
  for (const field of ['width', 'height', 'max']) {
    const value = parsed[/** @type {'width'|'height'|'max'} */ (field)];
    if (!Number.isInteger(value) || value <= 0) {
      throw new CodecError(`the ${field} in the header is not a positive whole number`, {
        field,
        value,
      });
    }
  }
  if (parsed.max > 65535) {
    throw new CodecError('a netpbm maximum above 65535 is not supported', { max: parsed.max });
  }
  return parsed;
}

/**
 * @param {PnmHeader} header
 * @returns {{layout: import('../raster/layout.js').LayoutName, depth: import('../raster/layout.js').Depth, ascii: boolean, channels: number}}
 */
function shapeOf(header) {
  const colour = header.magic === 'P3' || header.magic === 'P6';
  return {
    layout: colour ? 'rgb' : 'gray',
    depth: header.max > 255 ? 16 : 8,
    ascii: header.magic === 'P2' || header.magic === 'P3',
    channels: colour ? 3 : 1,
  };
}

/**
 * @param {Uint8Array} bytes
 * @returns {Surface}
 */
export function readPnm(bytes) {
  const header = readPnmHeader(bytes);
  const { layout, depth, ascii, channels } = shapeOf(header);
  const surface = Surface.create({ width: header.width, height: header.height, layout, depth });
  const wanted = header.width * header.height * channels;

  if (ascii) {
    let cursor = header.offset - 1;
    for (let i = 0; i < wanted; i += 1) {
      const { token, next } = readToken(bytes, cursor);
      const value = Number.parseInt(token, 10);
      if (!Number.isInteger(value)) {
        throw new CodecError('a sample is not a whole number', { index: i, token });
      }
      surface.data[i] = value;
      cursor = next;
    }
    return surface;
  }

  const wide = depth === 16;
  const needed = wanted * (wide ? 2 : 1);
  const available = bytes.length - header.offset;
  if (available < needed) {
    throw new CodecError('the file stops before the samples do', {
      needed,
      available,
    });
  }
  if (wide) {
    for (let i = 0; i < wanted; i += 1) {
      const at = header.offset + i * 2;
      surface.data[i] = (bytes[at] << 8) | bytes[at + 1];
    }
  } else {
    surface.data.set(bytes.subarray(header.offset, header.offset + wanted));
  }
  return surface;
}

/**
 * Enough of the header to know the shape without reading the samples, which
 * is what the intake stage uses to decide whether a capture is worth loading.
 *
 * @param {Uint8Array} bytes
 * @returns {{width: number, height: number, layout: string, depth: number}}
 */
export function probePnm(bytes) {
  const header = readPnmHeader(bytes);
  const { layout, depth } = shapeOf(header);
  return { width: header.width, height: header.height, layout, depth };
}

/**
 * @param {Uint8Array} bytes
 * @returns {boolean}
 */
export function looksLikePnm(bytes) {
  if (bytes.length < 2) return false;
  return bytes[0] === 0x50 && MAGICS.has(`P${String.fromCharCode(bytes[1])}`);
}
