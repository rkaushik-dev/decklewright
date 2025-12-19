import { ManifestError } from '../errors.js';
import { fixityOf } from './checksum.js';

/**
 * The document that goes beside a delivery.
 *
 * Everything a repository needs to know about an accession that is not in the
 * pixels: what was photographed, at what frequency, where it was cut, how it
 * graded, and what the files hash to. It is json because json is the only
 * format that both the catalogue and the tape robot can read without a
 * library that has to be maintained.
 *
 * Two rules hold this together. The document is written in a fixed key order,
 * so two runs of the same accession produce byte identical sidecars and a
 * diff between them means something. And every number in it is written as a
 * number rather than as a formatted string, because the first thing anybody
 * does with a sidecar is load it into a spreadsheet.
 */

/** Bumped when the shape changes in a way a reader has to know about. */
export const SIDECAR_VERSION = 2;

/** The keys, in the order they are written. */
const ORDER = Object.freeze([
  'version',
  'accession',
  'profile',
  'created',
  'capture',
  'crop',
  'derivatives',
  'quality',
  'warnings',
]);

/**
 * @param {Record<string, any>} document
 * @returns {Record<string, any>} the same fields, in the fixed order
 */
function ordered(document) {
  /** @type {Record<string, any>} */
  const out = {};
  for (const key of ORDER) {
    if (document[key] !== undefined) out[key] = document[key];
  }
  for (const key of Object.keys(document)) {
    if (!(key in out)) out[key] = document[key];
  }
  return out;
}

/**
 * @param {import('../plan/plan.js').CropPlan} plan
 * @returns {Record<string, any>}
 */
function cropOf(plan) {
  return {
    rect: { x: plan.rect.x, y: plan.rect.y, width: plan.rect.width, height: plan.rect.height },
    deskewDegrees: Number(plan.deskew.degrees.toFixed(4)),
    ppi: { x: plan.sampling.x, y: plan.sampling.y },
    rules: plan.steps.map((step) => step.rule),
    overhangs: plan.overhangs,
  };
}

/**
 * @param {import('../detect/capture.js').CaptureReading} reading
 * @param {import('../raster/surface.js').Surface} surface
 * @returns {Record<string, any>}
 */
function captureOf(reading, surface) {
  return {
    pixels: { width: surface.width, height: surface.height },
    layout: surface.layout,
    depth: surface.depth,
    skewDegrees: Number(reading.sheet.skew.degrees.toFixed(4)),
    confidence: Number(reading.sheet.confidence.toFixed(3)),
    spreadDegrees: Number(reading.sheet.spread.toFixed(4)),
    target: Boolean(reading.target),
    neatline: Boolean(reading.neatline),
    scaleBar: Boolean(reading.scaleBar),
  };
}

/**
 * Build the document.
 *
 * The quality section is optional because a lab that is only cutting sheets
 * has no reason to run the checks, and a sidecar with a quality section full
 * of nulls reads as a failed grading rather than as one that was never asked
 * for.
 *
 * @param {object} args
 * @param {string} args.accession
 * @param {Record<string, any>} args.profile
 * @param {import('../raster/surface.js').Surface} args.surface
 * @param {import('../detect/capture.js').CaptureReading} args.reading
 * @param {import('../plan/plan.js').CropPlan} args.plan
 * @param {readonly import('../derive/render.js').DerivedFile[]} args.files
 * @param {Record<string, any>} [args.quality] what runQc gave back
 * @param {readonly string[]} [args.warnings]
 * @param {Date|string} [args.created]
 * @returns {Record<string, any>}
 */
export function sidecarFor({
  accession,
  profile,
  surface,
  reading,
  plan,
  files,
  quality,
  warnings = [],
  created,
}) {
  const when = created instanceof Date ? created.toISOString() : (created ?? null);

  return ordered({
    version: SIDECAR_VERSION,
    accession,
    profile: { name: profile.name, title: profile.title ?? null },
    created: when,
    capture: captureOf(reading, surface),
    crop: cropOf(plan),
    derivatives: files.map((file) => ({
      name: file.name,
      file: file.fileName,
      format: file.format,
      ppi: { x: file.sampling.x, y: file.sampling.y },
      ...fixityOf(file.bytes),
      notes: file.notes,
    })),
    quality: quality ? qualityOf(quality) : null,
    warnings: [...warnings],
  });
}

/**
 * @param {Record<string, any>} result what runQc gave back
 * @returns {Record<string, any>}
 */
export function qualityOf(result) {
  return {
    stars: result.stars,
    wanted: result.wanted,
    passed: result.passed,
    worst: result.worst ?? null,
    missing: [...(result.missing ?? [])],
    checks: (result.findings ?? []).map((finding) => ({
      name: finding.name,
      ran: finding.ran,
      stars: finding.stars,
      measured: Number.isFinite(finding.measured) ? finding.measured : null,
      note: finding.note,
    })),
  };
}

/**
 * Write the document out. Two spaces because somebody will read it in a
 * terminal, and a trailing newline because somebody will `cat` it.
 *
 * @param {Record<string, any>} document
 * @returns {string}
 */
export function writeSidecar(document) {
  return `${JSON.stringify(ordered(document), null, 2)}\n`;
}

/**
 * Read one back, refusing anything this reader would misunderstand.
 *
 * A version from the future is refused rather than read on a hope. A sidecar
 * is the only record of how a file was made, and half reading one is how a
 * repository ends up with a crop rect measured in the wrong units.
 *
 * @param {string} text
 * @returns {Record<string, any>}
 */
export function parseSidecar(text) {
  /** @type {any} */
  let document;
  try {
    document = JSON.parse(text);
  } catch (cause) {
    throw new ManifestError('that is not a sidecar, it is not even json', { cause });
  }
  if (!document || typeof document !== 'object' || Array.isArray(document)) {
    throw new ManifestError('a sidecar has to be an object', {});
  }
  if (typeof document.version !== 'number') {
    throw new ManifestError('this sidecar does not say what version it is', {});
  }
  if (document.version > SIDECAR_VERSION) {
    throw new ManifestError('this sidecar was written by a later version', {
      found: document.version,
      understood: SIDECAR_VERSION,
    });
  }
  if (typeof document.accession !== 'string' || document.accession === '') {
    throw new ManifestError('this sidecar names no accession', {});
  }
  return document;
}

/**
 * Everything wrong with a document, without refusing it. The cli uses this to
 * say what is missing from a hand written sidecar rather than stopping at the
 * first fault.
 *
 * @param {Record<string, any>} document
 * @returns {string[]}
 */
export function objectionsToSidecar(document) {
  /** @type {string[]} */
  const out = [];
  if (!Array.isArray(document.derivatives)) {
    out.push('there is no list of derivatives');
  } else {
    if (document.derivatives.length === 0) out.push('the list of derivatives is empty');
    for (const item of document.derivatives) {
      if (!item.file) out.push(`a derivative called ${item.name ?? 'nothing'} names no file`);
      if (!item.digests?.sha256) out.push(`${item.file ?? item.name} carries no sha256`);
    }
  }
  if (!document.crop?.rect) out.push('there is no crop rect');
  if (!document.capture?.pixels) out.push('there is no capture size');
  return out;
}

/**
 * One line per file, for a person who wants to know what is in a delivery
 * without opening the json.
 *
 * @param {Record<string, any>} document
 * @returns {string[]}
 */
export function describeSidecar(document) {
  const head = `${document.accession}, profile ${document.profile?.name ?? 'unknown'}`;
  const quality = document.quality
    ? `${document.quality.stars} of 4 stars, ${document.quality.passed ? 'passed' : 'held back'}`
    : 'not graded';

  return [
    head,
    quality,
    ...(document.derivatives ?? []).map(
      (item) => `  ${item.file}, ${item.format}, ${item.bytes} bytes, ${item.ppi?.x ?? '?'} ppi`,
    ),
    ...(document.warnings ?? []).map((warning) => `  warning: ${warning}`),
  ];
}
