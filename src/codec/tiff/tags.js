import { CodecError } from '../../errors.js';

/**
 * Tiff tags and directory entries.
 *
 * The flatbed writes baseline tiff and the vendor's driver is not going to
 * change, so this reads what that driver produces and nothing more. Tags it
 * does not know are kept as raw values rather than dropped, because the
 * provenance record quotes them back and a tag we did not recognise is still
 * a fact about the capture.
 */

export const TAGS = Object.freeze({
  256: 'imageWidth',
  257: 'imageLength',
  258: 'bitsPerSample',
  259: 'compression',
  262: 'photometricInterpretation',
  270: 'imageDescription',
  271: 'make',
  272: 'model',
  273: 'stripOffsets',
  274: 'orientation',
  277: 'samplesPerPixel',
  278: 'rowsPerStrip',
  279: 'stripByteCounts',
  282: 'xResolution',
  283: 'yResolution',
  284: 'planarConfiguration',
  296: 'resolutionUnit',
  305: 'software',
  306: 'dateTime',
  339: 'sampleFormat',
});

export const COMPRESSION = Object.freeze({
  NONE: 1,
  LZW: 5,
  PACKBITS: 32773,
});

export const PHOTOMETRIC = Object.freeze({
  WHITE_IS_ZERO: 0,
  BLACK_IS_ZERO: 1,
  RGB: 2,
  PALETTE: 3,
});

export const RESOLUTION_UNIT = Object.freeze({
  NONE: 1,
  INCH: 2,
  CENTIMETRE: 3,
});

/** Byte width of each tiff field type, indexed by the type code. */
const TYPE_SIZE = Object.freeze({
  1: 1,
  2: 1,
  3: 2,
  4: 4,
  5: 8,
  6: 1,
  7: 1,
  8: 2,
  9: 4,
  10: 8,
  11: 4,
  12: 8,
});

/** @typedef {{tag: number, name: string, type: number, count: number, value: number[]|string}} Entry */

/**
 * @param {number} tag
 * @returns {string}
 */
export function tagName(tag) {
  return TAGS[/** @type {keyof typeof TAGS} */ (tag)] ?? `tag${tag}`;
}

/**
 * @param {Uint8Array} bytes
 * @returns {{littleEndian: boolean, firstIfd: number}}
 */
export function readTiffHeader(bytes) {
  if (bytes.length < 8) throw new CodecError('the file is too short to be a tiff', {});
  const little = bytes[0] === 0x49 && bytes[1] === 0x49;
  const big = bytes[0] === 0x4d && bytes[1] === 0x4d;
  if (!little && !big) {
    throw new CodecError('that is not a tiff, the byte order mark is wrong', {
      head: [bytes[0], bytes[1]],
    });
  }
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  const magic = view.getUint16(2, little);
  if (magic !== 42) {
    throw new CodecError('that is not a tiff, the version word is wrong', { magic });
  }
  return { littleEndian: little, firstIfd: view.getUint32(4, little) };
}

/**
 * @param {Uint8Array} bytes
 * @returns {boolean}
 */
export function looksLikeTiff(bytes) {
  if (bytes.length < 8) return false;
  const little = bytes[0] === 0x49 && bytes[1] === 0x49;
  const big = bytes[0] === 0x4d && bytes[1] === 0x4d;
  if (!little && !big) return false;
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  return view.getUint16(2, little) === 42;
}

/**
 * @param {DataView} view
 * @param {number} at
 * @param {number} type
 * @param {boolean} little
 * @returns {number}
 */
function readOne(view, at, type, little) {
  switch (type) {
    case 1:
    case 2:
    case 7:
      return view.getUint8(at);
    case 3:
      return view.getUint16(at, little);
    case 4:
      return view.getUint32(at, little);
    case 6:
      return view.getInt8(at);
    case 8:
      return view.getInt16(at, little);
    case 9:
      return view.getInt32(at, little);
    case 5:
      return view.getUint32(at, little) / (view.getUint32(at + 4, little) || 1);
    case 10:
      return view.getInt32(at, little) / (view.getInt32(at + 4, little) || 1);
    case 11:
      return view.getFloat32(at, little);
    case 12:
      return view.getFloat64(at, little);
    default:
      throw new CodecError(`unknown tiff field type ${type}`, { type });
  }
}

/**
 * Read one directory. Values of four bytes or fewer live in the entry itself
 * and anything longer lives at an offset, which is the one part of the format
 * that catches people out.
 *
 * @param {Uint8Array} bytes
 * @param {number} offset
 * @param {boolean} little
 * @returns {{entries: Entry[], next: number}}
 */
export function readIfd(bytes, offset, little) {
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  if (offset + 2 > bytes.length) {
    throw new CodecError('the directory offset points past the end of the file', { offset });
  }
  const count = view.getUint16(offset, little);
  /** @type {Entry[]} */
  const entries = [];
  for (let i = 0; i < count; i += 1) {
    const at = offset + 2 + i * 12;
    if (at + 12 > bytes.length) {
      throw new CodecError('the file stops inside a directory entry', { index: i });
    }
    const tag = view.getUint16(at, little);
    const type = view.getUint16(at + 2, little);
    const length = view.getUint32(at + 4, little);
    const size = TYPE_SIZE[/** @type {keyof typeof TYPE_SIZE} */ (type)];
    if (!size) throw new CodecError(`unknown tiff field type ${type}`, { type, tag });

    const total = size * length;
    const start = total <= 4 ? at + 8 : view.getUint32(at + 8, little);
    if (start + total > bytes.length) {
      throw new CodecError('a directory entry points past the end of the file', { tag, start });
    }

    /** @type {number[]|string} */
    let value;
    if (type === 2) {
      let text = '';
      for (let k = 0; k < length; k += 1) {
        const code = bytes[start + k];
        if (code === 0) break;
        text += String.fromCharCode(code);
      }
      value = text;
    } else {
      const list = new Array(length);
      for (let k = 0; k < length; k += 1) list[k] = readOne(view, start + k * size, type, little);
      value = list;
    }
    entries.push({ tag, name: tagName(tag), type, count: length, value });
  }
  const nextAt = offset + 2 + count * 12;
  const next = nextAt + 4 <= bytes.length ? view.getUint32(nextAt, little) : 0;
  return { entries, next };
}

/**
 * @param {readonly Entry[]} entries
 * @returns {Record<string, number[]|string>}
 */
export function asBag(entries) {
  /** @type {Record<string, number[]|string>} */
  const out = {};
  for (const entry of entries) out[entry.name] = entry.value;
  return out;
}

/**
 * @param {readonly Entry[]} entries
 * @param {string} name
 * @param {number} [fallback]
 * @returns {number}
 */
export function scalar(entries, name, fallback) {
  const entry = entries.find((item) => item.name === name);
  if (!entry || typeof entry.value === 'string' || entry.value.length === 0) {
    if (fallback === undefined) throw new CodecError(`the ${name} tag is missing`, { name });
    return fallback;
  }
  return entry.value[0];
}

/**
 * @param {readonly Entry[]} entries
 * @param {string} name
 * @returns {number[]}
 */
export function list(entries, name) {
  const entry = entries.find((item) => item.name === name);
  if (!entry || typeof entry.value === 'string') return [];
  return entry.value;
}
