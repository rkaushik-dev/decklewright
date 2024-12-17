import { BadArgumentError } from '../../errors.js';
import { Rect } from '../../geom/rect.js';
import { isWholePixel, roundRect } from '../../geom/round.js';
import { Surface } from '../surface.js';
import { SurfaceView } from '../view.js';

/**
 * Cutting a rectangle out of a capture.
 *
 * A planned crop is real valued and often runs a little past the edge of the
 * bed, because bleed is measured outward from the sheet and the sheet was
 * placed near the edge. Three answers are possible and the caller has to say
 * which one it wants rather than getting a silent clamp.
 */

/**
 * @param {Rect} rect
 * @param {import('../../geom/round.js').RoundPolicy} policy
 * @returns {Rect}
 */
function toPixelRect(rect, policy) {
  return isWholePixel(rect) ? rect : roundRect(rect, { policy });
}

/**
 * Cut out a rect that must already lie on the capture.
 *
 * @param {Surface} surface
 * @param {Rect} rect
 * @param {object} [options]
 * @param {import('../../geom/round.js').RoundPolicy} [options.round]
 * @returns {Surface}
 */
export function crop(surface, rect, { round = 'outward' } = {}) {
  const pixels = toPixelRect(rect, round);
  if (pixels.isEmpty) {
    throw new BadArgumentError('that crop has no area', { rect: rect.toJSON() });
  }
  if (!surface.bounds.containsRect(pixels)) {
    throw new BadArgumentError('that crop runs off the capture', {
      rect: pixels.toJSON(),
      bounds: surface.bounds.toJSON(),
    });
  }
  return new SurfaceView(surface, pixels).toSurface();
}

/**
 * The same cut with no copy, for measurement rather than output.
 *
 * @param {Surface} surface
 * @param {Rect} rect
 * @param {object} [options]
 * @param {import('../../geom/round.js').RoundPolicy} [options.round]
 * @returns {SurfaceView}
 */
export function cropView(surface, rect, { round = 'outward' } = {}) {
  return new SurfaceView(surface, toPixelRect(rect, round));
}

/**
 * Cut what overlaps and drop the rest, which is what a preview wants.
 *
 * @param {Surface} surface
 * @param {Rect} rect
 * @param {object} [options]
 * @param {import('../../geom/round.js').RoundPolicy} [options.round]
 * @returns {Surface}
 */
export function cropClamped(surface, rect, { round = 'outward' } = {}) {
  const pixels = toPixelRect(rect, round).intersect(surface.bounds);
  if (pixels.isEmpty) {
    throw new BadArgumentError('that crop does not touch the capture', {
      rect: rect.toJSON(),
      bounds: surface.bounds.toJSON(),
    });
  }
  return new SurfaceView(surface, pixels).toSurface();
}

/**
 * Cut the rect as asked and fill whatever fell outside the capture. This is
 * the one a master uses, because the output has to be the size the plan said
 * even when the sheet was placed too near the edge of the bed.
 *
 * @param {Surface} surface
 * @param {Rect} rect
 * @param {object} [options]
 * @param {number|readonly number[]} [options.fill]
 * @param {import('../../geom/round.js').RoundPolicy} [options.round]
 * @returns {{surface: Surface, filled: boolean, covered: Rect}}
 */
export function cropPadded(surface, rect, { fill = 0, round = 'outward' } = {}) {
  const pixels = toPixelRect(rect, round);
  if (pixels.isEmpty) {
    throw new BadArgumentError('that crop has no area', { rect: rect.toJSON() });
  }
  const out = Surface.create({
    width: pixels.width,
    height: pixels.height,
    layout: surface.layout,
    depth: surface.depth,
  });
  const values = normaliseFill(fill, surface.channels);
  if (values.some((v) => v !== 0)) {
    for (let y = 0; y < out.height; y += 1) {
      for (let x = 0; x < out.width; x += 1) out.setPixel(x, y, values);
    }
  }

  const overlap = pixels.intersect(surface.bounds);
  if (overlap.isEmpty) return { surface: out, filled: true, covered: Rect.empty() };

  const source = new SurfaceView(surface, overlap);
  const target = new SurfaceView(out, overlap.translated(-pixels.x, -pixels.y));
  for (let y = 0; y < source.height; y += 1) target.row(y).set(source.row(y));

  return { surface: out, filled: overlap.area < pixels.area, covered: overlap };
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
