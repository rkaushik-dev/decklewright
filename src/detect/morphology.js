import { assertNonNegative } from '../util/assert.js';
import { Mask } from './mask.js';

/**
 * Morphology on a mask.
 *
 * A square structuring element, done through a running sum, so the cost does
 * not grow with the radius. The shapes here are sheets of paper and the
 * difference between a square element and a disc is not measurable on one, but
 * the difference between a linear pass and a quadratic one is very measurable
 * on a three hundred megapixel capture.
 *
 * Outside the mask counts as background by default, so an erode eats into the
 * border. A closing has to say otherwise. It is meant to mend a gap and put
 * everything else back, and with the default it quietly shaves three pixels
 * off every edge instead, which loses exactly the evidence that a sheet ran
 * off the bed.
 */

/**
 * @param {Mask} mask
 * @returns {Int32Array} one row and column larger than the mask
 */
function prefixSum(mask) {
  const stride = mask.width + 1;
  const sums = new Int32Array(stride * (mask.height + 1));
  for (let y = 0; y < mask.height; y += 1) {
    const row = mask.row(y);
    let running = 0;
    const here = (y + 1) * stride;
    const above = y * stride;
    for (let x = 0; x < mask.width; x += 1) {
      running += row[x];
      sums[here + x + 1] = sums[above + x + 1] + running;
    }
  }
  return sums;
}

/**
 * @param {Mask} mask
 * @param {number} radius
 * @param {(count: number, area: number) => boolean} decide
 * @param {boolean} [outsideIsSet]
 * @returns {Mask}
 */
function windowPass(mask, radius, decide, outsideIsSet = false) {
  assertNonNegative(radius, 'radius');
  const r = Math.round(radius);
  if (r === 0) return mask.clone();

  const sums = prefixSum(mask);
  const stride = mask.width + 1;
  const out = new Mask(mask.width, mask.height);
  const side = 2 * r + 1;
  const fullArea = side * side;

  for (let y = 0; y < mask.height; y += 1) {
    const top = Math.max(0, y - r);
    const bottom = Math.min(mask.height, y + r + 1);
    for (let x = 0; x < mask.width; x += 1) {
      const left = Math.max(0, x - r);
      const right = Math.min(mask.width, x + r + 1);
      let count =
        sums[bottom * stride + right] -
        sums[bottom * stride + left] -
        sums[top * stride + right] +
        sums[top * stride + left];
      if (outsideIsSet) count += fullArea - (right - left) * (bottom - top);
      out.data[y * mask.width + x] = decide(count, fullArea) ? 1 : 0;
    }
  }
  return out;
}

/**
 * @param {Mask} mask
 * @param {number} radius
 * @param {object} [options]
 * @param {boolean} [options.outsideIsSet] treat beyond the border as object
 * @returns {Mask}
 */
export function erode(mask, radius, { outsideIsSet = false } = {}) {
  return windowPass(mask, radius, (count, area) => count === area, outsideIsSet);
}

/**
 * @param {Mask} mask
 * @param {number} radius
 * @returns {Mask}
 */
export function dilate(mask, radius) {
  return windowPass(mask, radius, (count) => count > 0);
}

/**
 * Erode then dilate, which removes speckle without shrinking what survives.
 *
 * @param {Mask} mask
 * @param {number} radius
 * @returns {Mask}
 */
export function open(mask, radius) {
  return dilate(erode(mask, radius), radius);
}

/**
 * Dilate then erode, which closes the gap a fold or a tear leaves in the
 * outline of a sheet.
 *
 * @param {Mask} mask
 * @param {number} radius
 * @returns {Mask}
 */
export function close(mask, radius) {
  return erode(dilate(mask, radius), radius, { outsideIsSet: true });
}

/**
 * The set pixels that have an unset neighbour, which is the outline the quad
 * fitter walks.
 *
 * @param {Mask} mask
 * @returns {Mask}
 */
export function boundary(mask) {
  const out = new Mask(mask.width, mask.height);
  for (let y = 0; y < mask.height; y += 1) {
    for (let x = 0; x < mask.width; x += 1) {
      if (!mask.get(x, y)) continue;
      const surrounded =
        mask.get(x - 1, y) && mask.get(x + 1, y) && mask.get(x, y - 1) && mask.get(x, y + 1);
      out.data[y * mask.width + x] = surrounded ? 0 : 1;
    }
  }
  return out;
}

/**
 * Fill anything unset that cannot reach the border. A sheet with a light
 * patch in the middle comes out of the threshold with a hole in it, and the
 * hole is not a hole in the paper.
 *
 * @param {Mask} mask
 * @returns {Mask}
 */
export function fillHoles(mask) {
  const reachable = new Mask(mask.width, mask.height);
  /** @type {number[]} */
  const stack = [];

  const push = (x, y) => {
    if (x < 0 || y < 0 || x >= mask.width || y >= mask.height) return;
    if (mask.get(x, y) || reachable.get(x, y)) return;
    reachable.set(x, y, 1);
    stack.push(x, y);
  };

  for (let x = 0; x < mask.width; x += 1) {
    push(x, 0);
    push(x, mask.height - 1);
  }
  for (let y = 0; y < mask.height; y += 1) {
    push(0, y);
    push(mask.width - 1, y);
  }

  while (stack.length > 0) {
    const y = /** @type {number} */ (stack.pop());
    const x = /** @type {number} */ (stack.pop());
    push(x - 1, y);
    push(x + 1, y);
    push(x, y - 1);
    push(x, y + 1);
  }

  const out = new Mask(mask.width, mask.height);
  for (let i = 0; i < mask.data.length; i += 1) {
    out.data[i] = mask.data[i] || !reachable.data[i] ? 1 : 0;
  }
  return out;
}
