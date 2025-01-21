import { BadArgumentError } from '../../errors.js';
import { Size } from '../../geom/size.js';
import { assertPositive } from '../../util/assert.js';
import { clamp } from '../../util/num.js';
import { buildContributions, kernelFor } from '../kernels.js';

/** @typedef {import('../kernels.js').Kernel} Kernel */
/** @typedef {import('../surface.js').Surface} Surface */

/**
 * Separable resampling.
 *
 * Horizontal first into a float buffer, then vertical out of it. Doing both
 * passes in one go would need a two dimensional kernel and about forty times
 * the arithmetic for a result nobody could tell apart. The intermediate stays
 * in floats so a sixteen bit master does not lose a bit to the first pass.
 */

/**
 * @param {Surface} surface
 * @param {Size} size
 * @param {object} [options]
 * @param {string|Kernel} [options.kernel]
 * @param {import('../sampler.js').EdgeMode} [options.edge]
 * @returns {Surface}
 */
export function resample(surface, size, { kernel = 'catmullRom', edge = 'clamp' } = {}) {
  const width = Math.round(size.width);
  const height = Math.round(size.height);
  if (width < 1 || height < 1) {
    throw new BadArgumentError('a resample target needs at least one pixel each way', {
      size: size.toJSON(),
    });
  }
  const chosen = kernelFor(kernel);
  const channels = surface.channels;

  if (width === surface.width && height === surface.height) return surface.clone();

  const horizontal = buildContributions({
    sourceExtent: surface.width,
    targetExtent: width,
    kernel: chosen,
    edge,
  });
  const vertical = buildContributions({
    sourceExtent: surface.height,
    targetExtent: height,
    kernel: chosen,
    edge,
  });

  const middle = new Float64Array(width * surface.height * channels);
  for (let y = 0; y < surface.height; y += 1) {
    const source = surface.row(y);
    const base = y * width * channels;
    for (let x = 0; x < width; x += 1) {
      const tap = horizontal[x];
      const out = base + x * channels;
      for (let c = 0; c < channels; c += 1) {
        let acc = 0;
        for (let k = 0; k < tap.indices.length; k += 1) {
          acc += source[tap.indices[k] * channels + c] * tap.weights[k];
        }
        middle[out + c] = acc;
      }
    }
  }

  const out = surface.like({ width, height });
  const top = out.maxValue;
  for (let y = 0; y < height; y += 1) {
    const tap = vertical[y];
    const target = out.row(y);
    for (let x = 0; x < width; x += 1) {
      for (let c = 0; c < channels; c += 1) {
        let acc = 0;
        for (let k = 0; k < tap.indices.length; k += 1) {
          acc += middle[(tap.indices[k] * width + x) * channels + c] * tap.weights[k];
        }
        target[x * channels + c] = clamp(Math.round(acc), 0, top);
      }
    }
  }
  return out;
}

/**
 * @param {Surface} surface
 * @param {number} factor
 * @param {object} [options]
 * @param {string|Kernel} [options.kernel]
 * @param {import('../sampler.js').EdgeMode} [options.edge]
 * @returns {Surface}
 */
export function scaleBy(surface, factor, options) {
  assertPositive(factor, 'factor');
  return resample(
    surface,
    Size.of(
      Math.max(1, Math.round(surface.width * factor)),
      Math.max(1, Math.round(surface.height * factor)),
    ),
    options,
  );
}

/**
 * Reduce so the long side lands on a target, which is how the thumbnail recipe
 * is written. Never enlarges, because a thumbnail larger than its master would
 * be an odd thing to hand out.
 *
 * @param {Surface} surface
 * @param {number} longest
 * @param {object} [options]
 * @param {string|Kernel} [options.kernel]
 * @returns {Surface}
 */
export function fitLongestSide(surface, longest, options) {
  assertPositive(longest, 'longest');
  const current = Math.max(surface.width, surface.height);
  if (current <= longest) return surface.clone();
  return scaleBy(surface, longest / current, options);
}

/**
 * Halve repeatedly until one more halving would overshoot, then resample the
 * rest of the way. A large reduction done in one pass with a wide kernel is
 * slow, and done in one pass with a narrow one aliases.
 *
 * @param {Surface} surface
 * @param {Size} size
 * @param {object} [options]
 * @param {string|Kernel} [options.kernel]
 * @returns {{surface: Surface, halvings: number}}
 */
export function reduceInSteps(surface, size, options = {}) {
  let current = surface;
  let halvings = 0;
  while (current.width >= size.width * 2 && current.height >= size.height * 2) {
    const half = Size.of(
      Math.max(1, Math.floor(current.width / 2)),
      Math.max(1, Math.floor(current.height / 2)),
    );
    if (half.width < size.width || half.height < size.height) break;
    current = resample(current, half, { kernel: 'box' });
    halvings += 1;
  }
  return { surface: resample(current, size, options), halvings };
}
