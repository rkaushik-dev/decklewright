import { PipelineError } from '../errors.js';
import { toDepth, toLayout } from '../raster/convert.js';
import { cropPadded } from '../raster/ops/crop.js';
import { reduceInSteps, resample } from '../raster/ops/resample.js';
import { deskew } from '../raster/ops/rotate.js';

/** @typedef {import('../geom/size.js').Size} Size */
/** @typedef {import('../raster/surface.js').Surface} Surface */
/** @typedef {import('../plan/plan.js').CropPlan} CropPlan */

/**
 * Turning a plan into pixels.
 *
 * The plan says where the sheet is and how far off square it sits. This turns
 * that into the files a repository keeps, and it does the two expensive parts
 * once: the capture is straightened once and cut once, and every derivative
 * comes off that cut rather than off the raw capture. A thumbnail rendered
 * from the raw capture would be straightened and cropped again with different
 * rounding, and a thumbnail that does not line up with its master is worse
 * than no thumbnail.
 *
 * The frequency travels with the pixels. A service copy at half the linear
 * size is at half the frequency, and if that is not carried through then the
 * sidecar claims a resolution the file does not have. That is the one error in
 * this whole tree a reader cannot catch by looking at the file.
 */

/**
 * @typedef {object} Rendered
 * @property {string} name
 * @property {Surface} surface
 * @property {import('../measure/resolution.js').Sampling} sampling
 * @property {string[]} steps what was done, in order
 * @property {number} scale linear size against the cut master
 */

/**
 * Take the turn out of the capture, or hand back what came in when there is no
 * turn to take.
 *
 * @param {Surface} surface
 * @param {CropPlan} plan
 * @param {object} [options]
 * @param {string} [options.sample]
 * @param {number|readonly number[]} [options.fill]
 * @returns {Surface}
 */
export function straighten(surface, plan, { sample = 'bilinear', fill = 0 } = {}) {
  if (plan.deskew.isZero) return surface;
  return deskew(surface, plan.deskew, { sample, background: fill, expand: true }).surface;
}

/**
 * Cut the plan's rect out of a straightened capture.
 *
 * A crop that reaches past the edge is padded rather than clipped. Clipping
 * would hand back a file of the wrong size, and every measurement written
 * beside it is in the units of the plan, so all of them would then be
 * describing a rectangle nobody has.
 *
 * @param {Surface} surface
 * @param {CropPlan} plan
 * @param {object} [options]
 * @param {number|readonly number[]} [options.fill]
 * @returns {{surface: Surface, filled: boolean}}
 */
export function cutOut(surface, plan, { fill = 0 } = {}) {
  const { surface: out, filled } = cropPadded(surface, plan.rect, { fill });
  return { surface: out, filled };
}

/**
 * Straighten and cut in one call, which is what every derivative starts from.
 *
 * The straightened capture is checked against the frame the plan was written
 * in before anything is cut. The two can only disagree if the plan came off a
 * different capture, and a crop taken at plan coordinates out of the wrong
 * frame gives a plausible file of the right size showing the wrong part of the
 * sheet. That is the one failure here nobody would catch by looking.
 *
 * @param {Surface} surface
 * @param {CropPlan} plan
 * @param {object} [options]
 * @param {string} [options.sample]
 * @param {number|readonly number[]} [options.fill]
 * @returns {{surface: Surface, steps: string[], filled: boolean}}
 */
export function cutMaster(surface, plan, options = {}) {
  /** @type {string[]} */
  const steps = [];
  const straightened = straighten(surface, plan, options);
  if (straightened !== surface) {
    steps.push(`straightened by ${plan.deskew.degrees.toFixed(3)} degrees`);
  }

  const frame = straightened.bounds;
  if (frame.width !== plan.capture.width || frame.height !== plan.capture.height) {
    throw new PipelineError('this plan was made against a capture of another size', {
      plan: `${plan.capture.width} by ${plan.capture.height}`,
      capture: `${frame.width} by ${frame.height}`,
    });
  }

  const { surface: cut, filled } = cutOut(straightened, plan, options);
  steps.push(`cut ${cut.width} by ${cut.height} out of ${surface.width} by ${surface.height}`);
  if (filled) {
    steps.push(`padded, the crop reaches ${Math.round(plan.overhangArea)} px past the capture`);
  }
  return { surface: cut, steps, filled };
}

/**
 * The size a recipe asks for, or null when it asks for the size it was given.
 *
 * @param {Size} from
 * @param {{longestSide?: number|null, scale?: number|null}} recipe
 * @returns {Size|null}
 */
export function targetSize(from, recipe) {
  if (recipe.longestSide) {
    const longest = Math.max(from.width, from.height);
    if (longest <= recipe.longestSide) return null;
    return from.scaled(recipe.longestSide / longest).rounded(Math.round);
  }
  if (recipe.scale && recipe.scale !== 1) {
    return from.scaled(recipe.scale).rounded(Math.round);
  }
  return null;
}

/**
 * Render one derivative off the cut master.
 *
 * The order is layout, then size, then depth. Layout first because dropping
 * two channels before resampling is a third of the work. Depth last because
 * reducing to eight bits and then resampling rounds twice, and the rounding
 * shows up as banding in exactly the flat margins an oversize sheet is mostly
 * made of.
 *
 * @param {Surface} master
 * @param {ReturnType<typeof import('../profile/settings.js').derivativeRecipes>[number]} recipe
 * @param {object} args
 * @param {import('../measure/resolution.js').Sampling} args.sampling
 * @returns {Rendered}
 */
export function renderDerivative(master, recipe, { sampling }) {
  if (!recipe.name) throw new PipelineError('a derivative recipe has no name');

  /** @type {string[]} */
  const steps = [];
  let surface = master;

  if (recipe.layout && recipe.layout !== surface.layout) {
    surface = toLayout(surface, recipe.layout);
    steps.push(`to ${recipe.layout}`);
  }

  const wanted = targetSize(surface.size, recipe);
  let scale = 1;
  if (wanted) {
    scale = wanted.width / surface.width;
    const kernel = recipe.kernel ?? 'catmullRom';
    if (scale < 0.5) {
      const reduced = reduceInSteps(surface, wanted, { kernel });
      surface = reduced.surface;
      steps.push(
        `to ${wanted.width} by ${wanted.height} with ${kernel} after ${reduced.halvings} halvings`,
      );
    } else {
      surface = resample(surface, wanted, { kernel });
      steps.push(`to ${wanted.width} by ${wanted.height} with ${kernel}`);
    }
  }

  if (recipe.depth && recipe.depth !== surface.depth) {
    surface = toDepth(surface, recipe.depth);
    steps.push(`to ${recipe.depth} bit`);
  }

  return { name: recipe.name, surface, sampling: sampling.scaled(scale), steps, scale };
}

/**
 * Render every derivative a profile asks for, off one cut.
 *
 * @param {object} args
 * @param {Surface} args.surface the raw capture
 * @param {CropPlan} args.plan
 * @param {readonly ReturnType<typeof import('../profile/settings.js').derivativeRecipes>} args.recipes
 * @param {object} [options]
 * @param {number|readonly number[]} [options.fill]
 * @param {string} [options.sample] for the straightening, not the reducing
 * @returns {{master: Surface, cut: string[], filled: boolean, rendered: Rendered[]}}
 */
export function renderAll({ surface, plan, recipes }, options = {}) {
  if (recipes.length === 0) throw new PipelineError('the profile asks for no derivatives');

  const seen = new Set();
  for (const recipe of recipes) {
    if (seen.has(recipe.name)) {
      throw new PipelineError('two derivatives want the same name', { name: recipe.name });
    }
    seen.add(recipe.name);
  }

  const { surface: master, steps, filled } = cutMaster(surface, plan, options);
  return {
    master,
    cut: steps,
    filled,
    rendered: recipes.map((recipe) =>
      renderDerivative(master, recipe, { sampling: plan.sampling }),
    ),
  };
}

/**
 * @param {readonly Rendered[]} rendered
 * @param {string} name
 * @returns {Rendered}
 */
export function named(rendered, name) {
  const found = rendered.find((item) => item.name === name);
  if (!found) throw new PipelineError('no derivative was rendered under that name', { name });
  return found;
}
