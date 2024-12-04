import { BadArgumentError } from '../errors.js';
import { assertNonEmptyString, assertOneOf } from '../util/assert.js';

/**
 * Channel layouts and sample depths.
 *
 * The lab captures three channel colour at sixteen bits for masters and hands
 * out eight bit derivatives. Grayscale turns up in the mask and profile work
 * and in the odd microfilm scan. Alpha only exists so a rotated master can
 * mark the corners that fell outside the original capture.
 */

/** @typedef {'gray'|'graya'|'rgb'|'rgba'} LayoutName */
/** @typedef {8|16} Depth */

/** @type {Record<LayoutName, {channels: number, alpha: number, names: string[]}>} */
const TABLE = {
  gray: { channels: 1, alpha: -1, names: ['gray'] },
  graya: { channels: 2, alpha: 1, names: ['gray', 'alpha'] },
  rgb: { channels: 3, alpha: -1, names: ['red', 'green', 'blue'] },
  rgba: { channels: 4, alpha: 3, names: ['red', 'green', 'blue', 'alpha'] },
};

export const LAYOUTS = Object.freeze(/** @type {LayoutName[]} */ (Object.keys(TABLE)));
export const DEPTHS = Object.freeze(/** @type {Depth[]} */ ([8, 16]));

/**
 * @param {unknown} name
 * @returns {LayoutName}
 */
export function assertLayout(name) {
  assertNonEmptyString(name, 'layout');
  if (!(name in TABLE)) {
    throw new BadArgumentError(`unknown layout ${name}`, { layout: name, known: [...LAYOUTS] });
  }
  return /** @type {LayoutName} */ (name);
}

/**
 * @param {unknown} depth
 * @returns {Depth}
 */
export function assertDepth(depth) {
  return /** @type {Depth} */ (assertOneOf(depth, DEPTHS, 'depth'));
}

/**
 * @param {LayoutName} layout
 * @returns {number}
 */
export function channelCount(layout) {
  return TABLE[assertLayout(layout)].channels;
}

/**
 * @param {LayoutName} layout
 * @returns {boolean}
 */
export function hasAlpha(layout) {
  return TABLE[assertLayout(layout)].alpha >= 0;
}

/**
 * @param {LayoutName} layout
 * @returns {number} the alpha channel index, or minus one when there is none
 */
export function alphaIndex(layout) {
  return TABLE[assertLayout(layout)].alpha;
}

/**
 * @param {LayoutName} layout
 * @returns {string[]}
 */
export function channelNames(layout) {
  return [...TABLE[assertLayout(layout)].names];
}

/**
 * @param {LayoutName} layout
 * @returns {number} how many channels carry image rather than coverage
 */
export function colourCount(layout) {
  return channelCount(layout) - (hasAlpha(layout) ? 1 : 0);
}

/**
 * @param {LayoutName} layout
 * @returns {boolean}
 */
export function isColour(layout) {
  return colourCount(layout) === 3;
}

/**
 * The largest value a sample can hold at this depth.
 *
 * @param {Depth} depth
 * @returns {number}
 */
export function maxValue(depth) {
  return assertDepth(depth) === 16 ? 65535 : 255;
}

/**
 * @param {Depth} depth
 * @returns {Uint8ArrayConstructor|Uint16ArrayConstructor}
 */
export function arrayTypeFor(depth) {
  return assertDepth(depth) === 16 ? Uint16Array : Uint8Array;
}

/**
 * The layout that carries the same colour channels with alpha added, or the
 * layout itself when it already has one.
 *
 * @param {LayoutName} layout
 * @returns {LayoutName}
 */
export function withAlpha(layout) {
  switch (assertLayout(layout)) {
    case 'gray':
      return 'graya';
    case 'rgb':
      return 'rgba';
    default:
      return layout;
  }
}

/**
 * @param {LayoutName} layout
 * @returns {LayoutName}
 */
export function withoutAlpha(layout) {
  switch (assertLayout(layout)) {
    case 'graya':
      return 'gray';
    case 'rgba':
      return 'rgb';
    default:
      return layout;
  }
}

/**
 * How many bytes one row of samples occupies.
 *
 * @param {number} width
 * @param {LayoutName} layout
 * @param {Depth} depth
 * @returns {number}
 */
export function rowBytes(width, layout, depth) {
  return width * channelCount(layout) * (assertDepth(depth) / 8);
}
