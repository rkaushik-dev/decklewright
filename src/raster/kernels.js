import { BadArgumentError } from '../errors.js';
import { assertNonEmptyString, assertPositive, assertPositiveInteger } from '../util/assert.js';
import { resolveIndex } from './sampler.js';

/**
 * Resampling kernels and the tap lists built from them.
 *
 * The lab uses three of these in anger. Box for the thumbnail, because the
 * reduction is large and anything else just costs time. Catmull Rom for the
 * service copy, because it holds an edge without ringing badly. Lanczos for the
 * rare case where a master has to be resized at all, which the policy says
 * should not happen but occasionally does.
 */

/** @typedef {{name: string, support: number, weight: (t: number) => number}} Kernel */

/** @type {Kernel} */
export const box = {
  name: 'box',
  support: 0.5,
  weight: (t) => (Math.abs(t) <= 0.5 ? 1 : 0),
};

/** @type {Kernel} */
export const triangle = {
  name: 'triangle',
  support: 1,
  weight: (t) => {
    const a = Math.abs(t);
    return a < 1 ? 1 - a : 0;
  },
};

/**
 * @param {number} b
 * @param {number} c
 * @param {string} name
 * @returns {Kernel}
 */
function cubicFamily(b, c, name) {
  return {
    name,
    support: 2,
    weight: (t) => {
      const a = Math.abs(t);
      const a2 = a * a;
      const a3 = a2 * a;
      if (a < 1) {
        return ((12 - 9 * b - 6 * c) * a3 + (-18 + 12 * b + 6 * c) * a2 + (6 - 2 * b)) / 6;
      }
      if (a < 2) {
        return (
          ((-b - 6 * c) * a3 + (6 * b + 30 * c) * a2 + (-12 * b - 48 * c) * a + (8 * b + 24 * c)) /
          6
        );
      }
      return 0;
    },
  };
}

/** @type {Kernel} */
export const catmullRom = cubicFamily(0, 0.5, 'catmullRom');

/** @type {Kernel} */
export const mitchell = cubicFamily(1 / 3, 1 / 3, 'mitchell');

/**
 * @param {number} lobes
 * @returns {Kernel}
 */
export function lanczos(lobes) {
  assertPositiveInteger(lobes, 'lobes');
  return {
    name: `lanczos${lobes}`,
    support: lobes,
    weight: (t) => {
      const a = Math.abs(t);
      if (a < 1e-12) return 1;
      if (a >= lobes) return 0;
      const pi = Math.PI * a;
      return (lobes * Math.sin(pi) * Math.sin(pi / lobes)) / (pi * pi);
    },
  };
}

/** @type {Record<string, Kernel>} */
const REGISTRY = {
  box,
  triangle,
  catmullRom,
  mitchell,
  lanczos2: lanczos(2),
  lanczos3: lanczos(3),
};

export const KERNEL_NAMES = Object.freeze(Object.keys(REGISTRY));

/**
 * @param {string|Kernel} name
 * @returns {Kernel}
 */
export function kernelFor(name) {
  if (typeof name === 'object' && name !== null) return name;
  assertNonEmptyString(name, 'kernel');
  const found = REGISTRY[name];
  if (!found) {
    throw new BadArgumentError(`unknown kernel ${name}`, {
      kernel: name,
      known: [...KERNEL_NAMES],
    });
  }
  return found;
}

/** @typedef {{start: number, indices: number[], weights: number[]}} Contribution */

/**
 * Build the taps that make one output row or column.
 *
 * When the image is being reduced the kernel has to be stretched to cover the
 * samples that are being merged, or the result aliases. When it is being
 * enlarged the kernel keeps its own width. That asymmetry is the whole reason
 * this is not a two line loop.
 *
 * @param {object} args
 * @param {number} args.sourceExtent
 * @param {number} args.targetExtent
 * @param {Kernel} args.kernel
 * @param {import('./sampler.js').EdgeMode} [args.edge]
 * @returns {Contribution[]}
 */
export function buildContributions({ sourceExtent, targetExtent, kernel, edge = 'clamp' }) {
  assertPositiveInteger(sourceExtent, 'sourceExtent');
  assertPositiveInteger(targetExtent, 'targetExtent');
  const scale = targetExtent / sourceExtent;
  const stretch = scale < 1 ? 1 / scale : 1;
  const support = kernel.support * stretch;

  /** @type {Contribution[]} */
  const out = [];
  for (let i = 0; i < targetExtent; i += 1) {
    const centre = (i + 0.5) / scale - 0.5;
    const first = Math.ceil(centre - support);
    const last = Math.floor(centre + support);

    /** @type {number[]} */
    const indices = [];
    /** @type {number[]} */
    const weights = [];
    let total = 0;
    for (let j = first; j <= last; j += 1) {
      const weight = kernel.weight((j - centre) / stretch);
      if (weight === 0) continue;
      const index = resolveIndex(j, sourceExtent, edge);
      if (index < 0) continue;
      indices.push(index);
      weights.push(weight);
      total += weight;
    }
    if (indices.length === 0) {
      const fallback = resolveIndex(Math.round(centre), sourceExtent, 'clamp');
      indices.push(fallback);
      weights.push(1);
      total = 1;
    }
    for (let k = 0; k < weights.length; k += 1) weights[k] /= total;
    out.push({ start: first, indices, weights });
  }
  return out;
}

/**
 * How wide a halo a kernel needs when it is used at a given scale, which the
 * tiled renderer has to know before it reads anything.
 *
 * @param {Kernel} kernel
 * @param {number} scale
 * @returns {number}
 */
export function haloFor(kernel, scale) {
  assertPositive(scale, 'scale');
  const stretch = scale < 1 ? 1 / scale : 1;
  return Math.ceil(kernel.support * stretch);
}
