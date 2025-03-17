import { lzwEncode } from '../../src/codec/tiff/lzw.js';
import { packBits } from '../../src/codec/tiff/packbits.js';

/**
 * Build a baseline tiff by hand.
 *
 * The reader has to be measured against the format, not against a writer we
 * also wrote, so this lays out the header, one directory and the strips
 * directly. It is deliberately literal.
 */

const TYPE_SHORT = 3;
const TYPE_LONG = 4;
const TYPE_RATIONAL = 5;

/**
 * @param {object} args
 * @param {number} args.width
 * @param {number} args.height
 * @param {readonly number[]} args.samples raw sample values, row major
 * @param {number} [args.depth]
 * @param {number} [args.channels]
 * @param {number} [args.photometric]
 * @param {number} [args.compression] 1 for none, 32773 for packbits
 * @param {number} [args.rowsPerStrip]
 * @param {boolean} [args.little]
 * @param {number} [args.resolution]
 * @param {number} [args.resolutionUnit]
 * @param {number} [args.planar]
 * @returns {Uint8Array}
 */
export function makeTiff({
  width,
  height,
  samples,
  depth = 8,
  channels = 1,
  photometric = 1,
  compression = 1,
  rowsPerStrip,
  little = true,
  resolution = 0,
  resolutionUnit = 2,
  planar = 1,
}) {
  const perStrip = rowsPerStrip ?? height;
  const bytesPerRow = width * channels * (depth / 8);

  /** @type {Uint8Array[]} */
  const strips = [];
  for (let row = 0; row < height; row += perStrip) {
    const rows = Math.min(perStrip, height - row);
    const raw = new Uint8Array(bytesPerRow * rows);
    let at = 0;
    for (let r = 0; r < rows; r += 1) {
      for (let i = 0; i < width * channels; i += 1) {
        const value = samples[(row + r) * width * channels + i] ?? 0;
        if (depth === 8) {
          raw[at] = value;
          at += 1;
        } else if (little) {
          raw[at] = value & 0xff;
          raw[at + 1] = (value >> 8) & 0xff;
          at += 2;
        } else {
          raw[at] = (value >> 8) & 0xff;
          raw[at + 1] = value & 0xff;
          at += 2;
        }
      }
    }
    if (compression === 32773) strips.push(packBits(raw));
    else if (compression === 5) strips.push(lzwEncode(raw));
    else strips.push(raw);
  }

  /** @type {{tag: number, type: number, values: number[]}[]} */
  const entries = [
    { tag: 256, type: TYPE_LONG, values: [width] },
    { tag: 257, type: TYPE_LONG, values: [height] },
    { tag: 258, type: TYPE_SHORT, values: new Array(channels).fill(depth) },
    { tag: 259, type: TYPE_SHORT, values: [compression] },
    { tag: 262, type: TYPE_SHORT, values: [photometric] },
    { tag: 273, type: TYPE_LONG, values: strips.map(() => 0) },
    { tag: 277, type: TYPE_SHORT, values: [channels] },
    { tag: 278, type: TYPE_LONG, values: [perStrip] },
    { tag: 279, type: TYPE_LONG, values: strips.map((s) => s.length) },
    { tag: 284, type: TYPE_SHORT, values: [planar] },
  ];
  if (resolution > 0) {
    entries.push({ tag: 282, type: TYPE_RATIONAL, values: [resolution] });
    entries.push({ tag: 283, type: TYPE_RATIONAL, values: [resolution] });
    entries.push({ tag: 296, type: TYPE_SHORT, values: [resolutionUnit] });
  }
  entries.sort((a, b) => a.tag - b.tag);

  const sizeOf = { [TYPE_SHORT]: 2, [TYPE_LONG]: 4, [TYPE_RATIONAL]: 8 };
  const dirSize = 2 + entries.length * 12 + 4;
  let heapSize = 0;
  for (const entry of entries) {
    const total = sizeOf[entry.type] * entry.values.length;
    if (total > 4) heapSize += total;
  }
  let stripBytes = 0;
  for (const strip of strips) stripBytes += strip.length;

  const bytes = new Uint8Array(8 + dirSize + heapSize + stripBytes);
  const view = new DataView(bytes.buffer);
  bytes[0] = little ? 0x49 : 0x4d;
  bytes[1] = little ? 0x49 : 0x4d;
  view.setUint16(2, 42, little);
  view.setUint32(4, 8, little);

  let stripAt = 8 + dirSize + heapSize;
  const offsets = [];
  for (const strip of strips) {
    bytes.set(strip, stripAt);
    offsets.push(stripAt);
    stripAt += strip.length;
  }
  const offsetEntry = entries.find((entry) => entry.tag === 273);
  if (offsetEntry) offsetEntry.values = offsets;

  view.setUint16(8, entries.length, little);
  let heap = 8 + dirSize;
  entries.forEach((entry, i) => {
    const at = 10 + i * 12;
    view.setUint16(at, entry.tag, little);
    view.setUint16(at + 2, entry.type, little);
    view.setUint32(at + 4, entry.values.length, little);
    const size = sizeOf[entry.type];
    const total = size * entry.values.length;
    const target = total <= 4 ? at + 8 : heap;
    if (total > 4) {
      view.setUint32(at + 8, heap, little);
      heap += total;
    }
    entry.values.forEach((value, k) => {
      if (entry.type === TYPE_SHORT) view.setUint16(target + k * 2, value, little);
      else if (entry.type === TYPE_LONG) view.setUint32(target + k * 4, value, little);
      else {
        view.setUint32(target + k * 8, value, little);
        view.setUint32(target + k * 8 + 4, 1, little);
      }
    });
  });
  view.setUint32(10 + entries.length * 12, 0, little);
  return bytes;
}
