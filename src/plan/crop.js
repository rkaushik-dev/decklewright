import { PlanError } from '../errors.js';
import { Angle } from '../geom/angle.js';
import { Rect } from '../geom/rect.js';
import { rotatedBounds } from '../raster/ops/rotate.js';
import { cropOptions } from '../profile/settings.js';
import { planInDetail } from './engine.js';
import { rulesFor } from './ruleset.js';

/** @typedef {import('../measure/resolution.js').Sampling} Sampling */
/** @typedef {import('./plan.js').CropPlan} CropPlan */

/**
 * From a read capture and a profile to a crop.
 *
 * The one thing this has to get right, and the one thing that has gone wrong
 * twice, is the order of the deskew and the crop. The detector reports the
 * sheet in the coordinates of the raw capture. The render straightens the
 * capture first, which moves every one of those coordinates, and a plan drawn
 * against the raw coordinates is then wrong by the same rotation.
 *
 * So the anchor is turned into the straightened frame here, before any rule
 * sees it, and the plan that comes out is in the frame the render will
 * actually work in.
 */

/**
 * The mapping from raw capture coordinates into the straightened frame, plus
 * the canvas that frame has. The rotation is about the centre of the capture
 * and the canvas grows, so the offset of the new canvas is in the mapping too.
 *
 * @param {Rect} capture
 * @param {import('../geom/angle.js').Angle} skew
 * @returns {{point: (x: number, y: number) => {x: number, y: number}, capture: Rect}}
 */
export function straightFrame(capture, skew) {
  if (skew.isZero) {
    return { point: (x, y) => ({ x, y }), capture };
  }
  const grown = rotatedBounds(capture, skew.negated());
  const radians = -skew.radians;
  const cos = Math.cos(radians);
  const sin = Math.sin(radians);
  const cx = capture.centre.x;
  const cy = capture.centre.y;
  const offsetX = (grown.width - capture.width) / 2;
  const offsetY = (grown.height - capture.height) / 2;

  return {
    point: (x, y) => {
      const dx = x - cx;
      const dy = y - cy;
      return { x: cx + dx * cos - dy * sin + offsetX, y: cy + dx * sin + dy * cos + offsetY };
    },
    capture: grown.translated(-grown.left, -grown.top),
  };
}

/**
 * The box round a set of points once the capture is straightened. Handing the
 * quad's own corners in rather than its bounding box matters: boxing first and
 * turning second grows the crop twice, once for the turn the sheet has and
 * once for the turn being taken out, and the second growth is not real.
 *
 * @param {readonly {x: number, y: number}[]} points
 * @param {Rect} capture
 * @param {import('../geom/angle.js').Angle} skew
 * @returns {{rect: Rect, capture: Rect}}
 */
export function pointsIntoStraightFrame(points, capture, skew) {
  const frame = straightFrame(capture, skew);
  const moved = points.map((p) => frame.point(p.x, p.y));
  const xs = moved.map((p) => p.x);
  const ys = moved.map((p) => p.y);
  return {
    rect: Rect.fromEdges(Math.min(...xs), Math.min(...ys), Math.max(...xs), Math.max(...ys)),
    capture: frame.capture,
  };
}

/**
 * Where a rect ends up once the capture is straightened.
 *
 * @param {Rect} rect
 * @param {Rect} capture
 * @param {import('../geom/angle.js').Angle} skew
 * @returns {{rect: Rect, capture: Rect}}
 */
export function intoStraightFrame(rect, capture, skew) {
  if (skew.isZero) return { rect, capture };
  return pointsIntoStraightFrame(rect.corners(), capture, skew);
}

/**
 * @param {object} args
 * @param {import('../detect/capture.js').CaptureReading} args.reading
 * @param {Record<string, any>} args.profile the resolved profile
 * @param {Rect} args.capture the bounds of the raw capture
 * @param {Sampling} [args.sampling] measured, when the rule gave one
 * @param {object} [options]
 * @param {boolean} [options.fallBackToSheet]
 * @param {string} [options.boundsMode]
 * @param {boolean} [options.strictResolution]
 * @param {number} [options.maxPasses]
 * @returns {{plan: CropPlan, rules: string[], settled: boolean, passes: number}}
 */
export function planCrop({ reading, profile, capture, sampling }, options = {}) {
  const frequency = sampling ?? reading.sampling;
  if (!frequency) {
    throw new PlanError('there is no sampling frequency to plan against', {
      profile: profile.name,
    });
  }

  const crop = cropOptions(profile, frequency);
  const skew = crop.deskew ? reading.sheet.skew : Angle.zero();

  const neatlineRaw = reading.neatline ? reading.neatline.rect : undefined;
  const sheetMoved = pointsIntoStraightFrame(reading.sheet.quad.corners, capture, skew);
  const found = {
    sheet: sheetMoved.rect.outward(),
    neatline: neatlineRaw
      ? intoStraightFrame(neatlineRaw, capture, skew).rect.outward()
      : undefined,
  };

  const rules = rulesFor(crop, found, options);
  const anchor = crop.anchor === 'neatline' && found.neatline ? found.neatline : found.sheet;

  const out = planInDetail(
    rules,
    { capture: sheetMoved.capture, anchor, sampling: frequency, deskew: skew },
    options,
  );

  return { ...out, plan: carryWarnings(out.plan, reading.warnings) };
}

/**
 * @param {CropPlan} plan
 * @param {readonly string[]} warnings
 * @returns {CropPlan}
 */
function carryWarnings(plan, warnings) {
  let out = plan;
  for (const warning of warnings) out = out.warned(warning);
  return out;
}
