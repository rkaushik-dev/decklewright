import { assertInteger } from '../../util/assert.js';

/**
 * The lossless reorientations.
 *
 * A sheet fed into the flatbed the short way comes out on its side, and the
 * operator marks the work order with a quarter turn. None of this touches a
 * sample value, which is why it happens before any resampling and why the
 * quality checks can be run either side of it and agree.
 */

/**
 * @param {import('../surface.js').Surface} surface
 * @returns {import('../surface.js').Surface}
 */
export function flipHorizontal(surface) {
  const out = surface.like();
  const channels = surface.channels;
  for (let y = 0; y < surface.height; y += 1) {
    const source = surface.row(y);
    const target = out.row(y);
    for (let x = 0; x < surface.width; x += 1) {
      const from = x * channels;
      const to = (surface.width - 1 - x) * channels;
      for (let c = 0; c < channels; c += 1) target[to + c] = source[from + c];
    }
  }
  return out;
}

/**
 * @param {import('../surface.js').Surface} surface
 * @returns {import('../surface.js').Surface}
 */
export function flipVertical(surface) {
  const out = surface.like();
  for (let y = 0; y < surface.height; y += 1) {
    out.row(surface.height - 1 - y).set(surface.row(y));
  }
  return out;
}

/**
 * Reflect across the main diagonal, which is the building block both quarter
 * turns are made of.
 *
 * @param {import('../surface.js').Surface} surface
 * @returns {import('../surface.js').Surface}
 */
export function transpose(surface) {
  const out = surface.like({ width: surface.height, height: surface.width });
  for (let y = 0; y < surface.height; y += 1) {
    for (let x = 0; x < surface.width; x += 1) {
      out.setPixel(y, x, surface.getPixel(x, y));
    }
  }
  return out;
}

/**
 * Turn clockwise by whole quarters. Negative and out of range counts are
 * folded, so minus one is the same as three.
 *
 * @param {import('../surface.js').Surface} surface
 * @param {number} turns
 * @returns {import('../surface.js').Surface}
 */
export function rotateQuarters(surface, turns) {
  assertInteger(turns, 'turns');
  const folded = ((turns % 4) + 4) % 4;
  switch (folded) {
    case 1:
      return flipHorizontal(transpose(surface));
    case 2:
      return flipVertical(flipHorizontal(surface));
    case 3:
      return flipVertical(transpose(surface));
    default:
      return surface.clone();
  }
}

/**
 * The eight orientations a scanner can hand back, named the way the exif tag
 * numbers them so a work order and a file agree.
 *
 * @param {import('../surface.js').Surface} surface
 * @param {number} orientation one through eight
 * @returns {import('../surface.js').Surface}
 */
export function applyExifOrientation(surface, orientation) {
  assertInteger(orientation, 'orientation');
  switch (orientation) {
    case 2:
      return flipHorizontal(surface);
    case 3:
      return rotateQuarters(surface, 2);
    case 4:
      return flipVertical(surface);
    case 5:
      return transpose(surface);
    case 6:
      return rotateQuarters(surface, 1);
    case 7:
      return flipHorizontal(flipVertical(transpose(surface)));
    case 8:
      return rotateQuarters(surface, 3);
    default:
      return surface.clone();
  }
}
