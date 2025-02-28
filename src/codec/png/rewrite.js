import { CodecError } from '../../errors.js';
import { assemble, chunkFlags, findChunk, splitChunks } from './chunks.js';
import { textChunk } from './encode.js';

/**
 * Container level edits that never touch a sample.
 *
 * The archival profile says a master carries its capture time and collection
 * identifiers and a service copy carries none of them. Re-encoding to strip a
 * text chunk would cost a generation for nothing, so this works on the chunk
 * list and leaves the image data byte for byte alone.
 */

/** The chunks a master is allowed to keep, everything else being dropped. */
export const MASTER_KEEP = Object.freeze(['IHDR', 'pHYs', 'tEXt', 'tIME', 'sRGB', 'iCCP', 'IEND']);

/** A service copy keeps its geometry and its colour intent and nothing else. */
export const SERVICE_KEEP = Object.freeze(['IHDR', 'sRGB', 'IEND']);

/**
 * @param {Date} when
 * @returns {{type: string, data: Uint8Array}}
 */
export function timeChunk(when) {
  const data = new Uint8Array(7);
  const view = new DataView(data.buffer);
  view.setUint16(0, when.getUTCFullYear());
  data[2] = when.getUTCMonth() + 1;
  data[3] = when.getUTCDate();
  data[4] = when.getUTCHours();
  data[5] = when.getUTCMinutes();
  data[6] = when.getUTCSeconds();
  return { type: 'tIME', data };
}

/**
 * @param {Uint8Array} bytes
 * @returns {Date|null}
 */
export function readTime(bytes) {
  const chunk = findChunk(splitChunks(bytes, { checkCrc: false }), 'tIME');
  if (!chunk || chunk.data.length !== 7) return null;
  const view = new DataView(chunk.data.buffer, chunk.data.byteOffset, chunk.data.byteLength);
  return new Date(
    Date.UTC(
      view.getUint16(0),
      chunk.data[2] - 1,
      chunk.data[3],
      chunk.data[4],
      chunk.data[5],
      chunk.data[6],
    ),
  );
}

/**
 * Keep the listed chunk types and drop the rest. IDAT is always kept because
 * dropping it would leave a file with no image, and IHDR and IEND because
 * without them it is not a png at all.
 *
 * @param {Uint8Array} bytes
 * @param {readonly string[]} keep
 * @returns {Uint8Array}
 */
export function keepOnly(bytes, keep) {
  const allowed = new Set([...keep, 'IHDR', 'IDAT', 'IEND']);
  const chunks = splitChunks(bytes, { checkCrc: false }).filter((chunk) => allowed.has(chunk.type));
  return assemble(chunks.map((chunk) => ({ type: chunk.type, data: chunk.data })));
}

/**
 * Drop every optional chunk, which is what a derivative starts from.
 *
 * @param {Uint8Array} bytes
 * @returns {Uint8Array}
 */
export function stripAncillary(bytes) {
  const chunks = splitChunks(bytes, { checkCrc: false }).filter(
    (chunk) => chunkFlags(chunk.type).critical,
  );
  return assemble(chunks.map((chunk) => ({ type: chunk.type, data: chunk.data })));
}

/**
 * Add or replace metadata before the image data, keeping everything else in
 * the order it was found.
 *
 * @param {Uint8Array} bytes
 * @param {object} [additions]
 * @param {Record<string, string>} [additions.text] replaces a key of the same name
 * @param {Date} [additions.time]
 * @returns {Uint8Array}
 */
export function withMetadata(bytes, { text, time } = {}) {
  const chunks = splitChunks(bytes, { checkCrc: false });
  const keys = new Set(Object.keys(text ?? {}));

  /** @type {{type: string, data: Uint8Array}[]} */
  const head = [];
  /** @type {{type: string, data: Uint8Array}[]} */
  const tail = [];
  let seenData = false;
  for (const chunk of chunks) {
    if (chunk.type === 'IDAT') seenData = true;
    if (chunk.type === 'tIME' && time) continue;
    if (chunk.type === 'tEXt' && keys.size > 0) {
      const split = chunk.data.indexOf(0);
      const key = String.fromCharCode(...chunk.data.subarray(0, Math.max(0, split)));
      if (keys.has(key)) continue;
    }
    (seenData ? tail : head).push({ type: chunk.type, data: chunk.data });
  }

  const inserted = [];
  if (time) inserted.push(timeChunk(time));
  for (const [key, value] of Object.entries(text ?? {})) inserted.push(textChunk(key, value));

  return assemble([...head, ...inserted, ...tail]);
}

/**
 * Say whether a file matches a chunk policy, and which chunks broke it. Used
 * by the verify command rather than by the writers, because a file that came
 * back from a vendor is exactly the one worth checking.
 *
 * @param {Uint8Array} bytes
 * @param {readonly string[]} keep
 * @returns {{ok: boolean, unexpected: string[], missing: string[]}}
 */
export function checkChunkPolicy(bytes, keep) {
  const chunks = splitChunks(bytes, { checkCrc: false });
  const present = new Set(chunks.map((chunk) => chunk.type));
  const allowed = new Set([...keep, 'IDAT']);
  const unexpected = [...present].filter((type) => !allowed.has(type)).sort();
  const missing = ['IHDR', 'IDAT', 'IEND'].filter((type) => !present.has(type));
  return { ok: unexpected.length === 0 && missing.length === 0, unexpected, missing };
}

/**
 * @param {Uint8Array} bytes
 * @returns {string[]}
 */
export function chunkTypes(bytes) {
  return splitChunks(bytes, { checkCrc: false }).map((chunk) => chunk.type);
}

/**
 * @param {Uint8Array} bytes
 * @returns {Uint8Array} the concatenated image data, unchanged
 */
export function imageDataOf(bytes) {
  const parts = splitChunks(bytes, { checkCrc: false }).filter((chunk) => chunk.type === 'IDAT');
  if (parts.length === 0) throw new CodecError('no IDAT chunk', {});
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
