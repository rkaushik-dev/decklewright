import { BadArgumentError, UnsupportedError } from '../errors.js';
import { clamp } from '../util/num.js';
import {
  alphaIndex,
  assertDepth,
  assertLayout,
  channelCount,
  colourCount,
  hasAlpha,
  isColour,
} from './layout.js';

/** @typedef {import('./surface.js').Surface} Surface */
/** @typedef {import('./layout.js').LayoutName} LayoutName */
/** @typedef {import('./layout.js').Depth} Depth */

/**
 * Depth and layout conversion.
 *
 * Eight to sixteen bits multiplies by two hundred and fifty seven rather than
 * shifting left by eight, so full scale stays full scale. Shifting leaves
 * white at sixty five two hundred and eighty, which a quality check then
 * reports as a capture that never reaches white.
 */

/** Rec 709 luma weights, which is what the derivative recipes assume. */
export const LUMA_709 = Object.freeze([0.2126, 0.7152, 0.0722]);

/** Rec 601 luma, kept because the older service copies were built with it. */
export const LUMA_601 = Object.freeze([0.299, 0.587, 0.114]);

/**
 * @param {number} value
 * @param {Depth} from
 * @param {Depth} to
 * @returns {number}
 */
export function scaleSample(value, from, to) {
  if (from === to) return value;
  if (from === 8) return value * 257;
  return Math.round(value / 257);
}

/**
 * @param {Surface} surface
 * @param {Depth} depth
 * @returns {Surface}
 */
export function toDepth(surface, depth) {
  assertDepth(depth);
  if (surface.depth === depth) return surface.clone();
  const out = surface.like({ depth });
  const from = surface.depth;
  for (let y = 0; y < surface.height; y += 1) {
    const source = surface.row(y);
    const target = out.row(y);
    for (let i = 0; i < source.length; i += 1) target[i] = scaleSample(source[i], from, depth);
  }
  return out;
}

/**
 * @param {Surface} surface
 * @param {object} [options]
 * @param {readonly number[]} [options.weights]
 * @returns {Surface}
 */
export function toGray(surface, { weights = LUMA_709 } = {}) {
  if (!isColour(surface.layout)) return surface.clone();
  if (weights.length !== 3) {
    throw new BadArgumentError('luma needs exactly three weights', { given: weights.length });
  }
  const keepAlpha = hasAlpha(surface.layout);
  const out = surface.like({ layout: keepAlpha ? 'graya' : 'gray' });
  const alpha = alphaIndex(surface.layout);
  const top = surface.maxValue;
  for (let y = 0; y < surface.height; y += 1) {
    const source = surface.row(y);
    const target = out.row(y);
    for (let x = 0; x < surface.width; x += 1) {
      const base = x * surface.channels;
      const luma =
        source[base] * weights[0] + source[base + 1] * weights[1] + source[base + 2] * weights[2];
      const to = x * out.channels;
      target[to] = clamp(Math.round(luma), 0, top);
      if (keepAlpha) target[to + 1] = source[base + alpha];
    }
  }
  return out;
}

/**
 * @param {Surface} surface
 * @returns {Surface}
 */
export function toColour(surface) {
  if (isColour(surface.layout)) return surface.clone();
  const keepAlpha = hasAlpha(surface.layout);
  const out = surface.like({ layout: keepAlpha ? 'rgba' : 'rgb' });
  for (let y = 0; y < surface.height; y += 1) {
    const source = surface.row(y);
    const target = out.row(y);
    for (let x = 0; x < surface.width; x += 1) {
      const value = source[x * surface.channels];
      const to = x * out.channels;
      target[to] = value;
      target[to + 1] = value;
      target[to + 2] = value;
      if (keepAlpha) target[to + 3] = source[x * surface.channels + 1];
    }
  }
  return out;
}

/**
 * @param {Surface} surface
 * @param {object} [options]
 * @param {number} [options.opaque] the value a fully covered sample gets
 * @returns {Surface}
 */
export function addAlpha(surface, { opaque } = {}) {
  if (hasAlpha(surface.layout)) return surface.clone();
  const layout = /** @type {LayoutName} */ (surface.layout === 'gray' ? 'graya' : 'rgba');
  const out = surface.like({ layout });
  const value = opaque ?? surface.maxValue;
  const colours = colourCount(surface.layout);
  for (let y = 0; y < surface.height; y += 1) {
    const source = surface.row(y);
    const target = out.row(y);
    for (let x = 0; x < surface.width; x += 1) {
      const from = x * surface.channels;
      const to = x * out.channels;
      for (let c = 0; c < colours; c += 1) target[to + c] = source[from + c];
      target[to + colours] = value;
    }
  }
  return out;
}

/**
 * Composite over a flat background and drop the coverage channel. A master
 * that was rotated has transparent corners and the archive will not accept a
 * file with an alpha channel, so this is where those corners become paper
 * white or bed black depending on the profile.
 *
 * @param {Surface} surface
 * @param {object} [options]
 * @param {number|readonly number[]} [options.background]
 * @returns {Surface}
 */
export function dropAlpha(surface, { background = 0 } = {}) {
  if (!hasAlpha(surface.layout)) return surface.clone();
  const layout = /** @type {LayoutName} */ (surface.layout === 'graya' ? 'gray' : 'rgb');
  const out = surface.like({ layout });
  const colours = colourCount(surface.layout);
  const values =
    typeof background === 'number' ? new Array(colours).fill(background) : [...background];
  if (values.length !== colours) {
    throw new BadArgumentError('the background has the wrong number of channels', {
      given: values.length,
      expected: colours,
    });
  }
  const top = surface.maxValue;
  for (let y = 0; y < surface.height; y += 1) {
    const source = surface.row(y);
    const target = out.row(y);
    for (let x = 0; x < surface.width; x += 1) {
      const from = x * surface.channels;
      const to = x * out.channels;
      const coverage = source[from + colours] / top;
      for (let c = 0; c < colours; c += 1) {
        const mixed = source[from + c] * coverage + values[c] * (1 - coverage);
        target[to + c] = clamp(Math.round(mixed), 0, top);
      }
    }
  }
  return out;
}

/**
 * @param {Surface} surface
 * @param {LayoutName} layout
 * @param {object} [options]
 * @param {number|readonly number[]} [options.background]
 * @returns {Surface}
 */
export function toLayout(surface, layout, options = {}) {
  assertLayout(layout);
  if (surface.layout === layout) return surface.clone();

  let current = surface;
  if (isColour(layout) && !isColour(current.layout)) current = toColour(current);
  if (!isColour(layout) && isColour(current.layout)) current = toGray(current);
  if (hasAlpha(layout) && !hasAlpha(current.layout)) current = addAlpha(current);
  if (!hasAlpha(layout) && hasAlpha(current.layout)) current = dropAlpha(current, options);

  if (current.layout !== layout) {
    throw new UnsupportedError('no route from that layout to this one', {
      from: surface.layout,
      to: layout,
    });
  }
  return current === surface ? surface.clone() : current;
}

/**
 * How many bytes a surface of this shape would take, which the pipeline uses
 * to decide whether a job fits alongside the others already running.
 *
 * @param {number} width
 * @param {number} height
 * @param {LayoutName} layout
 * @param {Depth} depth
 * @returns {number}
 */
export function byteSize(width, height, layout, depth) {
  return width * height * channelCount(layout) * (assertDepth(depth) / 8);
}
