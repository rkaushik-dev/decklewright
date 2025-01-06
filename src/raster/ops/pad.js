import { BadArgumentError } from '../../errors.js';
import { Insets } from '../../geom/insets.js';
import { assertOneOf } from '../../util/assert.js';
import { clamp } from '../../util/num.js';
import { Surface } from '../surface.js';

/**
 * Adding border to a capture.
 *
 * Two callers need this and they want different borders. A master that has to
 * reach the size the plan asked for wants a flat fill, because a made up edge
 * would be a made up record. A convolution that runs off the edge wants the
 * edge repeated, because a flat fill would look like a black line to it.
 */

/** @typedef {'constant'|'edge'|'mirror'} PadMode */

export const PAD_MODES = Object.freeze(['constant', 'edge', 'mirror']);

/**
 * @param {number} value
 * @param {number} extent
 * @param {PadMode} mode
 * @returns {number}
 */
function sourceIndex(value, extent, mode) {
  if (value >= 0 && value < extent) return value;
  if (mode === 'edge') return clamp(value, 0, extent - 1);
  if (mode === 'mirror') {
    if (extent === 1) return 0;
    const period = 2 * extent - 2;
    let wrapped = ((value % period) + period) % period;
    if (wrapped >= extent) wrapped = period - wrapped;
    return wrapped;
  }
  return -1;
}

/**
 * @param {number|readonly number[]} fill
 * @param {number} channels
 * @returns {number[]}
 */
function normaliseFill(fill, channels) {
  if (typeof fill === 'number') return new Array(channels).fill(fill);
  if (fill.length !== channels) {
    throw new BadArgumentError('the fill colour has the wrong number of channels', {
      given: fill.length,
      expected: channels,
    });
  }
  return [...fill];
}

/**
 * @param {Surface} surface
 * @param {Insets} insets whole pixels, and never negative
 * @param {object} [options]
 * @param {PadMode} [options.mode]
 * @param {number|readonly number[]} [options.fill]
 * @returns {Surface}
 */
export function pad(surface, insets, { mode = 'constant', fill = 0 } = {}) {
  assertOneOf(mode, PAD_MODES, 'mode');
  const edges = [insets.top, insets.right, insets.bottom, insets.left];
  if (edges.some((edge) => edge < 0 || !Number.isInteger(edge))) {
    throw new BadArgumentError('padding must be whole pixels and never negative', {
      insets: insets.toJSON(),
    });
  }

  const width = surface.width + insets.horizontal;
  const height = surface.height + insets.vertical;
  const out = Surface.create({
    width,
    height,
    layout: surface.layout,
    depth: surface.depth,
  });
  const values = normaliseFill(fill, surface.channels);

  for (let y = 0; y < height; y += 1) {
    const sy = sourceIndex(y - insets.top, surface.height, mode);
    for (let x = 0; x < width; x += 1) {
      const sx = sourceIndex(x - insets.left, surface.width, mode);
      if (sx < 0 || sy < 0) out.setPixel(x, y, values);
      else out.setPixel(x, y, surface.getPixel(sx, sy));
    }
  }
  return out;
}

/**
 * Grow a capture to a target size, splitting the extra border between the two
 * sides. An odd remainder goes on the trailing edge, which keeps the result
 * stable when the same call runs twice.
 *
 * @param {Surface} surface
 * @param {import('../../geom/size.js').Size} size
 * @param {object} [options]
 * @param {PadMode} [options.mode]
 * @param {number|readonly number[]} [options.fill]
 * @returns {Surface}
 */
export function padTo(surface, size, options = {}) {
  const extraX = size.width - surface.width;
  const extraY = size.height - surface.height;
  if (extraX < 0 || extraY < 0) {
    throw new BadArgumentError('padTo cannot shrink a capture', {
      from: surface.size.toJSON(),
      to: size.toJSON(),
    });
  }
  const left = Math.floor(extraX / 2);
  const top = Math.floor(extraY / 2);
  return pad(surface, new Insets(top, extraX - left, extraY - top, left), options);
}

/**
 * Pad by the same amount on every edge, which is what a filter that needs a
 * halo asks for.
 *
 * @param {Surface} surface
 * @param {number} amount
 * @param {object} [options]
 * @param {PadMode} [options.mode]
 * @param {number|readonly number[]} [options.fill]
 * @returns {Surface}
 */
export function padUniform(surface, amount, options = {}) {
  return pad(surface, Insets.uniform(amount), options);
}
