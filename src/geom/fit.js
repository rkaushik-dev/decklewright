import { assertOneOf } from '../util/assert.js';
import { Point } from './point.js';
import { Rect } from './rect.js';
import { Size } from './size.js';

/**
 * Placing one box inside another.
 *
 * A service copy has a target box and the crop has an aspect that rarely
 * matches it. Which of the four answers is right depends on the collection,
 * so the choice is a named mode and the profile picks one.
 */

/** @typedef {'contain'|'cover'|'stretch'|'none'} FitMode */
/** @typedef {'start'|'centre'|'end'} Align */

export const FIT_MODES = Object.freeze(['contain', 'cover', 'stretch', 'none']);
export const ALIGNMENTS = Object.freeze(['start', 'centre', 'end']);

/**
 * @param {number} extent
 * @param {number} span
 * @param {Align} align
 * @returns {number}
 */
function offsetFor(extent, span, align) {
  switch (align) {
    case 'start':
      return 0;
    case 'end':
      return span - extent;
    default:
      return (span - extent) / 2;
  }
}

/**
 * @param {Size} size
 * @param {Rect} bound
 * @param {object} [options]
 * @param {Align} [options.alignX]
 * @param {Align} [options.alignY]
 * @returns {Rect}
 */
export function alignIn(size, bound, { alignX = 'centre', alignY = 'centre' } = {}) {
  assertOneOf(alignX, ALIGNMENTS, 'alignX');
  assertOneOf(alignY, ALIGNMENTS, 'alignY');
  const x = bound.left + offsetFor(size.width, bound.width, alignX);
  const y = bound.top + offsetFor(size.height, bound.height, alignY);
  return Rect.fromOrigin(new Point(x, y), size);
}

/**
 * Scale a source size to a bound under the named mode, then place it.
 *
 * contain keeps the whole source visible and leaves margin.
 * cover fills the bound and lets the source overhang.
 * stretch ignores the aspect entirely.
 * none leaves the source at its own size and only places it.
 *
 * @param {Size} source
 * @param {Rect} bound
 * @param {object} [options]
 * @param {FitMode} [options.mode]
 * @param {Align} [options.alignX]
 * @param {Align} [options.alignY]
 * @param {boolean} [options.allowUpscale]
 * @returns {Rect}
 */
export function fitIn(source, bound, options = {}) {
  const { mode = 'contain', alignX = 'centre', alignY = 'centre', allowUpscale = true } = options;
  assertOneOf(mode, FIT_MODES, 'mode');
  if (source.isEmpty || bound.isEmpty) return Rect.fromOrigin(bound.origin, Size.empty());

  let sized;
  switch (mode) {
    case 'stretch':
      sized = bound.size;
      break;
    case 'cover':
      sized = source.covering(bound.size);
      break;
    case 'none':
      sized = source;
      break;
    default:
      sized = source.containedIn(bound.size);
      break;
  }

  if (!allowUpscale && mode === 'contain' && !sized.fitsInside(source)) {
    sized = source;
  }

  return alignIn(sized, bound, { alignX, alignY });
}

/**
 * The scale factor a fit would apply, which the render stage needs before it
 * commits to a resample kernel.
 *
 * @param {Size} source
 * @param {Size} bound
 * @param {FitMode} [mode]
 * @returns {number}
 */
export function fitScale(source, bound, mode = 'contain') {
  assertOneOf(mode, FIT_MODES, 'mode');
  if (source.isEmpty || bound.isEmpty) return 0;
  switch (mode) {
    case 'cover':
      return Math.max(bound.width / source.width, bound.height / source.height);
    case 'none':
      return 1;
    case 'stretch':
      return Math.min(bound.width / source.width, bound.height / source.height);
    default:
      return Math.min(bound.width / source.width, bound.height / source.height);
  }
}

/**
 * Grow or shrink a rect to a target aspect while keeping its centre. Growing
 * is the safe direction for a crop that still has image around it, so that is
 * the default.
 *
 * @param {Rect} rect
 * @param {number} aspect width over height
 * @param {object} [options]
 * @param {'grow'|'shrink'} [options.direction]
 * @returns {Rect}
 */
export function forceAspect(rect, aspect, { direction = 'grow' } = {}) {
  assertOneOf(direction, ['grow', 'shrink'], 'direction');
  if (rect.isEmpty) return rect;
  const current = rect.width / rect.height;
  const wider = current > aspect;
  const takeHeight = direction === 'grow' ? wider : !wider;
  const size = takeHeight
    ? new Size(rect.width, rect.width / aspect)
    : new Size(rect.height * aspect, rect.height);
  return Rect.fromCentre(rect.centre, size);
}
