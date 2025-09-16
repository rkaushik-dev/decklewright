import { Rect } from '../../geom/rect.js';
import { meanProfile } from '../../raster/stats.js';
import { SurfaceView } from '../../raster/view.js';
import { check, measurement, starsFor } from '../check.js';

/**
 * Sharpness, measured across the sheet edge.
 *
 * A proper slanted edge measurement wants a target with a machined edge at a
 * known angle, and the lab does not have one on every stand. What it does have
 * on every capture is the edge of the sheet against the bed, which is a real
 * step of known direction, and the width of the transition across it is a
 * usable stand in.
 *
 * It is reported in pixels of rise, which is what an operator can act on: two
 * pixels means the stand needs refocusing and six means somebody knocked it.
 */

/** Rise distance in pixels from a tenth to nine tenths, per star. */
export const SHARPNESS_LADDER = Object.freeze([
  { stars: 4, limit: 1.5 },
  { stars: 3, limit: 2.5 },
  { stars: 2, limit: 4 },
  { stars: 1, limit: 7 },
]);

/**
 * The width of the transition in a profile that runs from one flat level to
 * another. Measured between a tenth and nine tenths of the step, which is the
 * usual convention and is what the ladder above is written against.
 *
 * @param {Float64Array|readonly number[]} profile
 * @returns {number} in samples, or not a number when there is no step
 */
export function riseDistance(profile) {
  const values = Array.from(profile);
  if (values.length < 3) return Number.NaN;

  const first = values[0];
  const last = values[values.length - 1];
  const step = last - first;
  if (Math.abs(step) < 1e-9) return Number.NaN;

  const low = first + step * 0.1;
  const high = first + step * 0.9;
  const rising = step > 0;

  const crossing = (level) => {
    for (let i = 1; i < values.length; i += 1) {
      const before = values[i - 1];
      const now = values[i];
      const passed = rising ? before < level && now >= level : before > level && now <= level;
      if (!passed) continue;
      const span = now - before;
      return span === 0 ? i : i - 1 + (level - before) / span;
    }
    return Number.NaN;
  };

  const from = crossing(low);
  const to = crossing(high);
  if (!Number.isFinite(from) || !Number.isFinite(to)) return Number.NaN;
  return Math.abs(to - from);
}

/**
 * A strip of capture running across an edge, averaged along it so the noise
 * comes out and the step stays.
 *
 * @param {import('../../raster/surface.js').Surface} surface
 * @param {import('../../geom/rect.js').Rect} strip
 * @param {'x'|'y'} across
 * @returns {Float64Array|null}
 */
export function edgeProfile(surface, strip, across) {
  const held = strip.intersect(surface.bounds);
  if (held.isEmpty || held.width < 3 || held.height < 3) return null;
  return meanProfile(new SurfaceView(surface, held), {
    along: across === 'x' ? 'column' : 'row',
  });
}

/**
 * Four strips, one across each side of a rect, reaching a little either way.
 *
 * @param {import('../../geom/rect.js').Rect} rect
 * @param {object} [options]
 * @param {number} [options.reach] how far either side of the edge to look
 * @param {number} [options.along] how much of the edge to average over
 * @returns {{strip: import('../../geom/rect.js').Rect, across: 'x'|'y'}[]}
 */
export function edgeStrips(rect, { reach = 8, along = 0.4 } = {}) {
  const width = Math.max(3, Math.round(rect.width * along));
  const height = Math.max(3, Math.round(rect.height * along));
  const midX = Math.round(rect.centre.x - width / 2);
  const midY = Math.round(rect.centre.y - height / 2);
  return [
    { strip: Rect.of(midX, rect.top - reach, width, reach * 2), across: 'y' },
    { strip: Rect.of(midX, rect.bottom - reach, width, reach * 2), across: 'y' },
    { strip: Rect.of(rect.left - reach, midY, reach * 2, height), across: 'x' },
    { strip: Rect.of(rect.right - reach, midY, reach * 2, height), across: 'x' },
  ];
}

/**
 * @param {object} [options]
 * @param {number} [options.reach]
 * @returns {import('../check.js').Check}
 */
export function sharpnessCheck({ reach = 8 } = {}) {
  return check({
    name: 'sharpness',
    needs: ['surface', 'sheet'],
    run(context) {
      /** @type {number[]} */
      const rises = [];
      for (const { strip, across } of edgeStrips(context.sheet, { reach })) {
        const profile = edgeProfile(context.surface, strip, across);
        if (!profile) continue;
        const rise = riseDistance(profile);
        if (Number.isFinite(rise)) rises.push(rise);
      }

      if (rises.length === 0) {
        return measurement(Number.NaN, 0, 'no edge of the sheet gave a usable step');
      }

      const worst = Math.max(...rises);
      return measurement(
        worst,
        starsFor(worst, SHARPNESS_LADDER),
        `the softest edge rises over ${worst.toFixed(2)} px across ${rises.length} edges`,
      );
    },
  });
}
