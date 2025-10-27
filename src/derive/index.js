import { namesFor, objectionsToNames } from './naming.js';
import { encodeAll, sizeOf } from './recipe.js';
import { renderAll } from './render.js';
import { PipelineError } from '../errors.js';

/**
 * The derivative layer as the rest of the tree sees it.
 *
 * Above this a stage hands in a capture, a plan and a profile and gets named
 * bytes back. Below it there are four steps and they are exported one at a
 * time, because the question an operator asks about a bad derivative is which
 * step went wrong, and answering it means being able to run them apart.
 *
 * Nothing here touches a disk. That is deliberate and it is the reason the
 * whole layer is testable without a temporary directory: a derivative is a
 * pure function of a capture, a plan and a profile, and anything that is not
 * belongs in the store.
 */

export {
  cutMaster,
  cutOut,
  named,
  renderAll,
  renderDerivative,
  straighten,
  targetSize,
} from './render.js';
export {
  DEFAULT_FORMAT,
  FORMAT_LIMITS,
  encodeAll,
  encodeRendered,
  frequencyOf,
  isWritable,
  limitsFor,
  objectionsTo,
  provenanceText,
  sizeOf,
  textOf,
} from './recipe.js';
export {
  MAX_STEM,
  RESERVED,
  collisions,
  nameFor,
  namesFor,
  objectionsToNames,
  parseName,
  safeStem,
  slug,
} from './naming.js';
export { deriveMany, deriveOne, describeDerived } from './oneshot.js';

/**
 * Render, encode and name a set, given a plan somebody else made.
 *
 * This is the half of the one shot path that a stage wants: the reading and
 * the plan have already been done further up, and doing them again here would
 * be both slow and a second opinion nobody asked for.
 *
 * @param {object} args
 * @param {import('../raster/surface.js').Surface} args.surface
 * @param {import('../plan/plan.js').CropPlan} args.plan
 * @param {Record<string, any>} args.profile
 * @param {string} args.accession
 * @param {readonly ReturnType<typeof import('../profile/settings.js').derivativeRecipes>} args.recipes
 * @param {object} [options]
 * @param {number} [options.level]
 * @param {number|readonly number[]} [options.fill]
 * @returns {{files: import('./oneshot.js').DerivedFile[], steps: string[], bytes: number}}
 */
export function deriveFromPlan({ surface, plan, profile, accession, recipes }, options = {}) {
  const rendered = renderAll({ surface, plan, recipes }, { fill: options.fill ?? 0 });
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

  return {
    files: encoded.map((item) => ({
      name: item.name,
      fileName: names[item.name],
      bytes: item.bytes,
      format: item.format,
      sampling: item.rendered.sampling,
      notes: item.notes,
    })),
    steps: [
      ...rendered.cut,
      ...encoded.flatMap((item) => item.rendered.steps.map((step) => `${item.name}: ${step}`)),
    ],
    bytes: sizeOf(encoded).total,
  };
}

/**
 * The file a caller means when it says the master, without having to know that
 * the profile calls it that.
 *
 * @param {readonly import('./oneshot.js').DerivedFile[]} files
 * @returns {import('./oneshot.js').DerivedFile}
 */
export function preservationCopy(files) {
  if (files.length === 0) throw new PipelineError('nothing was derived');
  return files.find((file) => file.name === 'master') ?? files[0];
}
