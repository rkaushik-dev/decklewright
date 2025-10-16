import { decodePng } from '../codec/png/decode.js';
import { codecNamed, extensionFor, formatOf, writeImage } from '../codec/registry.js';
import { UnsupportedError } from '../errors.js';
import { Sampling } from '../measure/resolution.js';

/** @typedef {import('./render.js').Rendered} Rendered */

/**
 * Writing a rendered derivative out as bytes.
 *
 * The recipe names a format and this is where that name turns into a file. It
 * is also where the lab finds out that the format it asked for cannot hold
 * what it is being handed, and it finds out here rather than three stages
 * later when a reader opens the file and gets a sixteen bit image read as
 * eight.
 *
 * Two things are carried into the file itself rather than only into the
 * sidecar: the sampling frequency and the provenance text. Everything else a
 * repository knows about a file it can keep beside the file, but a frequency
 * that is not in the file cannot be recovered from the pixels, and a scan of
 * a thirty six inch sheet at an unknown frequency is a picture of a map rather
 * than a measurement of one.
 */

/** What each format can hold, so a recipe can be refused before it is run. */
export const FORMAT_LIMITS = Object.freeze({
  png: { depths: [8, 16], layouts: ['gray', 'graya', 'rgb', 'rgba'], carriesFrequency: true },
  pnm: { depths: [8, 16], layouts: ['gray', 'rgb'], carriesFrequency: false },
  tiff: { depths: [8, 16], layouts: ['gray', 'rgb'], carriesFrequency: true },
});

/** What a derivative is written as when the recipe does not say. */
export const DEFAULT_FORMAT = 'png';

/** png keeps its frequency in pixels per metre, which nothing else here uses. */
const METRES_PER_INCH = 0.0254;

/**
 * Read the frequency back out of written bytes.
 *
 * This is the other half of the reason the frequency goes into the file: a
 * file that carries it can be checked against the sidecar years later by
 * somebody who has both and trusts neither. Formats that cannot carry one give
 * back null, which is a different answer from zero.
 *
 * @param {Uint8Array} bytes
 * @returns {Sampling|null}
 */
export function frequencyOf(bytes) {
  if (formatOf(bytes) !== 'png') return null;
  const physical = decodePng(bytes).physical;
  if (!physical || physical.unit !== 1) return null;
  return new Sampling(physical.x * METRES_PER_INCH, physical.y * METRES_PER_INCH);
}

/**
 * Read the provenance text back out of written bytes.
 *
 * @param {Uint8Array} bytes
 * @returns {Record<string, string>}
 */
export function textOf(bytes) {
  if (formatOf(bytes) !== 'png') return {};
  return decodePng(bytes).text;
}

/**
 * @param {string} format
 * @returns {{depths: number[], layouts: string[], carriesFrequency: boolean}}
 */
export function limitsFor(format) {
  const limits = FORMAT_LIMITS[format];
  if (!limits) throw new UnsupportedError('nothing here writes that format', { format });
  return limits;
}

/**
 * Whether a format can be written at all, as against merely being understood.
 * Reading tiff and writing it are different jobs and only one of them is done
 * here, so a profile that asks for a tiff master has to hear about it.
 *
 * @param {string} format
 * @returns {boolean}
 */
export function isWritable(format) {
  try {
    return codecNamed(format).write !== null;
  } catch {
    return false;
  }
}

/**
 * Everything wrong with writing this surface in this format, in the order a
 * person would want to fix them. An empty list means it will go out cleanly.
 *
 * @param {import('../raster/surface.js').Surface} surface
 * @param {string} format
 * @returns {string[]}
 */
export function objectionsTo(surface, format) {
  /** @type {string[]} */
  const out = [];
  if (!isWritable(format)) {
    out.push(`nothing here writes ${format}, only reads it`);
    return out;
  }
  const limits = limitsFor(format);
  if (!limits.layouts.includes(surface.layout)) {
    out.push(`${format} cannot hold ${surface.layout}`);
  }
  if (!limits.depths.includes(surface.depth)) {
    out.push(`${format} cannot hold ${surface.depth} bit samples`);
  }
  return out;
}

/**
 * The text a file carries about where it came from.
 *
 * It is deliberately short. A repository that wants the full record reads the
 * sidecar, and a text block long enough to hold the full record is a text
 * block somebody will eventually strip to save space, taking the short version
 * with it.
 *
 * @param {object} args
 * @param {string} args.accession
 * @param {string} [args.derivative]
 * @param {string} [args.profile]
 * @param {string} [args.software]
 * @returns {Record<string, string>}
 */
export function provenanceText({ accession, derivative, profile, software = 'decklewright' }) {
  /** @type {Record<string, string>} */
  const text = { Software: software, Source: accession };
  if (derivative) text.Title = `${accession} ${derivative}`;
  if (profile) text.Comment = `profile ${profile}`;
  return text;
}

/**
 * Write one rendered derivative out.
 *
 * @param {Rendered} rendered
 * @param {object} args
 * @param {string} [args.format]
 * @param {boolean} [args.keepMetadata] false strips the text, not the frequency
 * @param {Record<string, string>} [args.text]
 * @param {number} [args.level] zlib level where the format has one
 * @returns {{bytes: Uint8Array, format: string, extension: string, notes: string[]}}
 */
export function encodeRendered(rendered, { format, keepMetadata = true, text, level } = {}) {
  const chosen = format ?? DEFAULT_FORMAT;
  const objections = objectionsTo(rendered.surface, chosen);
  if (objections.length > 0) {
    throw new UnsupportedError(objections[0], {
      format: chosen,
      derivative: rendered.name,
      layout: rendered.surface.layout,
      depth: rendered.surface.depth,
    });
  }

  /** @type {string[]} */
  const notes = [];
  const limits = limitsFor(chosen);

  /** @type {Record<string, any>} */
  const options = {};
  if (limits.carriesFrequency) {
    options.ppi = { x: rendered.sampling.x, y: rendered.sampling.y };
  } else {
    notes.push(`${chosen} cannot carry the frequency, so it is only in the sidecar`);
  }
  if (keepMetadata && text && Object.keys(text).length > 0) {
    options.text = text;
  } else if (!keepMetadata) {
    notes.push('metadata was left out, the profile asked for a bare file');
  }
  if (level !== undefined) options.level = level;

  return {
    bytes: writeImage(rendered.surface, chosen, options),
    format: chosen,
    extension: extensionFor(chosen),
    notes,
  };
}

/**
 * @typedef {object} Encoded
 * @property {string} name
 * @property {Uint8Array} bytes
 * @property {string} format
 * @property {string} extension
 * @property {string[]} notes
 * @property {Rendered} rendered
 */

/**
 * Write every rendered derivative out, each in the format its own recipe
 * asked for.
 *
 * A recipe that names a format nothing can write stops the whole set rather
 * than only its own file. A partial set is the worst outcome available here:
 * the folder looks finished, the manifest lists what is there, and the missing
 * master is not noticed until somebody goes looking for it years later.
 *
 * @param {readonly Rendered[]} rendered
 * @param {readonly {name: string, format?: string, keepMetadata?: boolean}[]} recipes
 * @param {object} [args]
 * @param {string} [args.accession]
 * @param {string} [args.profile]
 * @param {number} [args.level]
 * @returns {Encoded[]}
 */
export function encodeAll(rendered, recipes, { accession, profile, level } = {}) {
  return rendered.map((item) => {
    const recipe = recipes.find((one) => one.name === item.name);
    const text = accession
      ? provenanceText({ accession, derivative: item.name, profile })
      : undefined;
    const out = encodeRendered(item, {
      format: recipe?.format,
      keepMetadata: recipe?.keepMetadata ?? true,
      text,
      level,
    });
    return { name: item.name, rendered: item, ...out };
  });
}

/**
 * How large the set came out, which is the number a lab plans storage on.
 *
 * @param {readonly Encoded[]} encoded
 * @returns {{total: number, largest: string|null, perName: Record<string, number>}}
 */
export function sizeOf(encoded) {
  /** @type {Record<string, number>} */
  const perName = {};
  let total = 0;
  let largest = null;
  let most = -1;
  for (const item of encoded) {
    perName[item.name] = item.bytes.length;
    total += item.bytes.length;
    if (item.bytes.length > most) {
      most = item.bytes.length;
      largest = item.name;
    }
  }
  return { total, largest, perName };
}
