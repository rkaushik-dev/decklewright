import { assertOneOf, assertPositiveInteger } from '../util/assert.js';
import { Point } from '../geom/point.js';
import { Rect } from '../geom/rect.js';
import { Mask } from './mask.js';

/**
 * Connected components.
 *
 * The mask usually holds the sheet, the colour target, the scale bar and a
 * handful of dust. Telling them apart by size and position is the cheapest
 * reliable way through, and every later stage wants the pieces separately
 * anyway.
 *
 * Labels start at one. Zero means background, which lets the label buffer
 * double as a mask.
 */

/** @typedef {{id: number, area: number, bounds: Rect, centroid: Point, touchesEdge: boolean}} Component */

/**
 * @param {Mask} mask
 * @param {object} [options]
 * @param {4|8} [options.connectivity]
 * @returns {{labels: Int32Array, components: Component[]}}
 */
export function labelComponents(mask, { connectivity = 8 } = {}) {
  assertOneOf(connectivity, [4, 8], 'connectivity');
  const labels = new Int32Array(mask.width * mask.height);
  /** @type {Component[]} */
  const components = [];

  /** @type {number[]} */
  const stack = [];
  let nextId = 0;

  for (let startY = 0; startY < mask.height; startY += 1) {
    for (let startX = 0; startX < mask.width; startX += 1) {
      const at = startY * mask.width + startX;
      if (!mask.data[at] || labels[at] !== 0) continue;

      nextId += 1;
      labels[at] = nextId;
      stack.push(startX, startY);

      let area = 0;
      let left = startX;
      let right = startX;
      let top = startY;
      let bottom = startY;
      let sumX = 0;
      let sumY = 0;
      let touchesEdge = false;

      while (stack.length > 0) {
        const y = /** @type {number} */ (stack.pop());
        const x = /** @type {number} */ (stack.pop());
        area += 1;
        sumX += x;
        sumY += y;
        if (x < left) left = x;
        if (x > right) right = x;
        if (y < top) top = y;
        if (y > bottom) bottom = y;
        if (x === 0 || y === 0 || x === mask.width - 1 || y === mask.height - 1) {
          touchesEdge = true;
        }

        for (let dy = -1; dy <= 1; dy += 1) {
          for (let dx = -1; dx <= 1; dx += 1) {
            if (dx === 0 && dy === 0) continue;
            if (connectivity === 4 && dx !== 0 && dy !== 0) continue;
            const nx = x + dx;
            const ny = y + dy;
            if (nx < 0 || ny < 0 || nx >= mask.width || ny >= mask.height) continue;
            const index = ny * mask.width + nx;
            if (!mask.data[index] || labels[index] !== 0) continue;
            labels[index] = nextId;
            stack.push(nx, ny);
          }
        }
      }

      components.push({
        id: nextId,
        area,
        bounds: Rect.fromEdges(left, top, right + 1, bottom + 1),
        centroid: new Point(sumX / area, sumY / area),
        touchesEdge,
      });
    }
  }

  return { labels, components };
}

/**
 * @param {Mask} mask
 * @param {Int32Array} labels
 * @param {number} id
 * @returns {Mask}
 */
export function maskOfComponent(mask, labels, id) {
  const out = new Mask(mask.width, mask.height);
  for (let i = 0; i < labels.length; i += 1) out.data[i] = labels[i] === id ? 1 : 0;
  return out;
}

/**
 * @param {Mask} mask
 * @param {object} [options]
 * @param {4|8} [options.connectivity]
 * @returns {{mask: Mask, component: Component}|null}
 */
export function largestComponent(mask, options) {
  const { labels, components } = labelComponents(mask, options);
  if (components.length === 0) return null;
  const biggest = components.reduce((best, item) => (item.area > best.area ? item : best));
  return { mask: maskOfComponent(mask, labels, biggest.id), component: biggest };
}

/**
 * Keep the largest few and drop the rest, which is how the dust comes off
 * without an area threshold anybody has to maintain.
 *
 * @param {Mask} mask
 * @param {number} count
 * @param {object} [options]
 * @param {4|8} [options.connectivity]
 * @returns {Mask}
 */
export function keepLargest(mask, count, options) {
  assertPositiveInteger(count, 'count');
  const { labels, components } = labelComponents(mask, options);
  const keep = new Set(
    [...components]
      .sort((a, b) => b.area - a.area)
      .slice(0, count)
      .map((item) => item.id),
  );
  const out = new Mask(mask.width, mask.height);
  for (let i = 0; i < labels.length; i += 1) out.data[i] = keep.has(labels[i]) ? 1 : 0;
  return out;
}

/**
 * @param {Mask} mask
 * @param {number} minimumArea
 * @param {object} [options]
 * @param {4|8} [options.connectivity]
 * @returns {Mask}
 */
export function dropSmaller(mask, minimumArea, options) {
  const { labels, components } = labelComponents(mask, options);
  const keep = new Set(components.filter((item) => item.area >= minimumArea).map((i) => i.id));
  const out = new Mask(mask.width, mask.height);
  for (let i = 0; i < labels.length; i += 1) out.data[i] = keep.has(labels[i]) ? 1 : 0;
  return out;
}

/**
 * How well a component fills its own bounding box. A sheet fills nearly all of
 * it even when skewed. A scale bar does not, and neither does a shadow.
 *
 * @param {Component} component
 * @returns {number}
 */
export function fillRatio(component) {
  const area = component.bounds.area;
  return area === 0 ? 0 : component.area / area;
}
