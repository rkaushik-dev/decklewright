import { isUsable, readCapture } from '../detect/capture.js';
import { PipelineError } from '../errors.js';
import { planCrop } from '../plan/crop.js';
import { derivativeRecipes } from '../profile/settings.js';
import { namesFor, objectionsToNames } from './naming.js';
import { encodeAll, sizeOf } from './recipe.js';
import { renderAll } from './render.js';

/**
 * One call from a capture to a set of named files.
 *
 * This is the shape the tool had before there were stages: read the sheet,
 * plan the crop, render, encode, name. It is kept because it is the whole job
 * in forty lines and it is the thing to reach for when somebody is testing a
 * profile against one sheet and does not want a workspace, a ledger and a
 * manifest to go with it.
 *
 * It does no io. Nothing here opens a file, and the bytes come back in memory
 * for the caller to put wherever it likes.
 */

/**
 * @typedef {object} DerivedFile
 * @property {string} name the derivative name, master or thumb
 * @property {string} fileName what it should be written as
 * @property {Uint8Array} bytes
 * @property {string} format
 * @property {import('../measure/resolution.js').Sampling} sampling
 * @property {string[]} notes
 */

/**
 * @typedef {object} OneShot
 * @property {DerivedFile[]} files
 * @property {import('../plan/plan.js').CropPlan} plan
 * @property {import('../detect/capture.js').CaptureReading} reading
 * @property {string[]} steps everything that was done, in order
 * @property {string[]} warnings everything worth a second look
 * @property {number} bytes how large the set came out
 */

/**
 * Read, plan, render, encode and name, in that order.
 *
 * The order is not negotiable and it is worth saying why: the plan is written
 * in the coordinates of the straightened capture, so nothing may be resized
 * before it is cut, and the name depends on the format, so nothing may be
 * named before it is encoded. Every attempt to make this faster by reordering
 * it has produced files whose names and contents disagree.
 *
 * @param {object} args
 * @param {import('../raster/surface.js').Surface} args.surface
 * @param {Record<string, any>} args.profile a resolved profile
 * @param {string} args.accession
 * @param {import('../measure/resolution.js').Sampling} [args.sampling]
 * @param {object} [options]
 * @param {object} [options.capture] passed to the reader
 * @param {object} [options.crop] passed to the planner
 * @param {number} [options.level] zlib level for the formats that have one
 * @param {number|readonly number[]} [options.fill] what padding is filled with
 * @param {boolean} [options.requireUsable] refuse a reading the detector doubts
 * @returns {OneShot}
 */
export function deriveOne({ surface, profile, accession, sampling }, options = {}) {
  const reading = readCapture(surface, options.capture);
  if ((options.requireUsable ?? true) && !isUsable(reading)) {
    throw new PipelineError('the detector is not sure enough of this sheet to crop it', {
      accession,
      confidence: reading.sheet.confidence.toFixed(2),
      touchesEdge: reading.sheet.touchesEdge,
    });
  }

  const planned = planCrop(
    { reading, profile, capture: surface.bounds, sampling },
    options.crop ?? {},
  );
  const recipes = derivativeRecipes(profile);
  const rendered = renderAll({ surface, plan: planned.plan, recipes }, { fill: options.fill ?? 0 });
  const encoded = encodeAll(rendered.rendered, recipes, {
    accession,
    profile: profile.name,
    level: options.level,
  });

  const names = namesFor({ profile, accession, encoded });
  const objections = objectionsToNames(names);
  if (objections.length > 0) {
    throw new PipelineError(objections[0], { accession, profile: profile.name });
  }

  /** @type {DerivedFile[]} */
  const files = encoded.map((item) => ({
    name: item.name,
    fileName: names[item.name],
    bytes: item.bytes,
    format: item.format,
    sampling: item.rendered.sampling,
    notes: item.notes,
  }));

  return {
    files,
    plan: planned.plan,
    reading,
    steps: [
      ...rendered.cut,
      ...encoded.flatMap((item) => item.rendered.steps.map((step) => `${item.name}: ${step}`)),
    ],
    warnings: [
      ...planned.plan.warnings,
      ...(planned.settled ? [] : ['the crop rules did not settle, the last pass was kept']),
      ...encoded.flatMap((item) => item.notes.map((note) => `${item.name}: ${note}`)),
    ],
    bytes: sizeOf(encoded).total,
  };
}

/**
 * The same over a batch, keeping the failures rather than stopping at the
 * first one. A shift produces two hundred captures and one of them is always a
 * blank sheet somebody photographed by mistake.
 *
 * @param {readonly Parameters<typeof deriveOne>[0][]} items
 * @param {Parameters<typeof deriveOne>[1]} [options]
 * @returns {{done: {accession: string, result: OneShot}[], failed: {accession: string, error: Error}[]}}
 */
export function deriveMany(items, options) {
  /** @type {{accession: string, result: OneShot}[]} */
  const done = [];
  /** @type {{accession: string, error: Error}[]} */
  const failed = [];

  for (const item of items) {
    try {
      done.push({ accession: item.accession, result: deriveOne(item, options) });
    } catch (caught) {
      const error = /** @type {Error} */ (caught);
      failed.push({ accession: item.accession, error });
    }
  }
  return { done, failed };
}

/**
 * @param {OneShot} out
 * @returns {string[]} one line per file, for a log
 */
export function describeDerived(out) {
  return out.files.map(
    (file) =>
      `${file.fileName}, ${file.format}, ${file.sampling.x.toFixed(0)} ppi, ${file.bytes.length} bytes`,
  );
}
