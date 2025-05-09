import { Surface } from '../../src/raster/surface.js';

/**
 * A stand in for a capture off the copy stand.
 *
 * A flat bed with a lighter sheet on it, turned by whatever angle is asked
 * for, with optional bed noise and optional extras: a colour target block and
 * a scale bar. Every detection test builds its input from here so the inputs
 * are comparable across the suite.
 */

/**
 * @param {object} [options]
 * @param {number} [options.width]
 * @param {number} [options.height]
 * @param {number} [options.bed] bed tone
 * @param {number} [options.paper] sheet tone
 * @param {number} [options.degrees] how far the sheet is turned
 * @param {number} [options.halfWidth]
 * @param {number} [options.halfHeight]
 * @param {number} [options.centreX]
 * @param {number} [options.centreY]
 * @param {number} [options.noise] bed noise, plus or minus
 * @param {import('../../src/raster/layout.js').LayoutName} [options.layout]
 * @returns {Surface}
 */
export function bench({
  width = 200,
  height = 160,
  bed = 38,
  paper = 214,
  degrees = 0,
  halfWidth = 62,
  halfHeight = 44,
  centreX,
  centreY,
  noise = 0,
  layout = 'gray',
} = {}) {
  const surface = Surface.create({ width, height, layout, fill: bed });
  const channels = surface.channels;
  const cx = centreX ?? width / 2;
  const cy = centreY ?? height / 2;
  const radians = (degrees * Math.PI) / 180;
  const cos = Math.cos(radians);
  const sin = Math.sin(radians);

  let seed = 20250508;
  const next = () => {
    seed = (seed * 1103515245 + 12345) & 0x7fffffff;
    return seed;
  };

  for (let y = 0; y < height; y += 1) {
    for (let x = 0; x < width; x += 1) {
      const dx = x - cx;
      const dy = y - cy;
      const along = dx * cos + dy * sin;
      const across = -dx * sin + dy * cos;
      const onSheet = Math.abs(along) <= halfWidth && Math.abs(across) <= halfHeight;
      const base = onSheet ? paper : bed;
      const jitter = noise > 0 && !onSheet ? (next() % (noise * 2 + 1)) - noise : 0;
      const value = Math.max(0, Math.min(255, base + jitter));
      for (let c = 0; c < channels; c += 1) surface.set(x, y, c, value);
    }
  }
  return surface;
}

/**
 * Paint a filled block onto a capture, which is how the tests add a colour
 * target or a scale bar to a bench.
 *
 * @param {Surface} surface
 * @param {import('../../src/geom/rect.js').Rect} rect
 * @param {number|readonly number[]} value
 * @returns {Surface}
 */
export function paint(surface, rect, value) {
  const values = typeof value === 'number' ? new Array(surface.channels).fill(value) : [...value];
  for (let y = Math.max(0, rect.top); y < Math.min(surface.height, rect.bottom); y += 1) {
    for (let x = Math.max(0, rect.left); x < Math.min(surface.width, rect.right); x += 1) {
      surface.setPixel(x, y, values);
    }
  }
  return surface;
}
