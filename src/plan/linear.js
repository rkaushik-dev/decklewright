import { PlanError } from '../errors.js';
import { forceAspect } from '../geom/fit.js';
import { roundRect, snapRect } from '../geom/round.js';
import { achievedSampling } from '../measure/resolution.js';
import { CropPlan } from './plan.js';

/** @typedef {import('../geom/rect.js').Rect} Rect */
/** @typedef {import('../measure/resolution.js').Sampling} Sampling */

/**
 * The planner, written out in order.
 *
 * Anchor, bleed, safe area, aspect, resolution, snap, round, bounds. Each step
 * takes the crop the last one produced. It is easy to read and it is easy to
 * follow when a crop comes out wrong, which is why it is the first version.
 *
 * It has one flaw that this shape cannot fix: the order is fixed. Snapping
 * after forcing an aspect breaks the aspect, and forcing the aspect after
 * snapping breaks the snap, and both collections exist.
 */

/**
 * @param {object} args
 * @param {Rect} args.capture the whole capture
 * @param {Rect} args.anchor what the crop is measured from
 * @param {Sampling} args.sampling
 * @param {import('../geom/angle.js').Angle} [args.deskew]
 * @param {object} options the crop settings, already in pixels
 * @param {import('../geom/insets.js').Insets} options.bleed
 * @param {import('../geom/insets.js').Insets} options.safeArea
 * @param {number} [options.aspect]
 * @param {string} [options.aspectDirection]
 * @param {number} options.minimumPpi
 * @param {number} options.snapTo
 * @param {string} options.snapDirection
 * @param {string} options.rounding
 * @returns {CropPlan}
 */
export function planLinear({ capture, anchor, sampling, deskew }, options) {
  if (anchor.isEmpty) {
    throw new PlanError('there is nothing to crop, the anchor has no area', {
      anchor: anchor.toJSON(),
    });
  }

  let plan = CropPlan.start({ capture, rect: anchor, sampling, deskew });

  plan = plan.moved(options.bleed.expandFrom(plan.rect), {
    rule: 'bleed',
    note: `kept ${options.bleed} beyond the anchor`,
  });

  plan = plan.moved(options.safeArea.applyTo(plan.rect), {
    rule: 'safeArea',
    note: `pulled in by ${options.safeArea}`,
  });

  if (options.aspect !== undefined) {
    const direction = options.aspectDirection === 'shrink' ? 'shrink' : 'grow';
    plan = plan.moved(forceAspect(plan.rect, options.aspect, { direction }), {
      rule: 'aspect',
      note: `forced to ${options.aspect} by ${direction}ing`,
    });
  }

  const achieved = achievedSampling(
    plan.rect.width,
    sampling.toLength(plan.rect.width, 'in'),
    'in',
  );
  if (achieved.x < options.minimumPpi) {
    plan = plan.warned(
      `the crop only reaches ${Math.round(achieved.x)} ppi against a floor of ${options.minimumPpi}`,
    );
  }

  plan = plan.moved(
    snapRect(plan.rect, options.snapTo, {
      direction: /** @type {any} */ (options.snapDirection),
    }),
    { rule: 'snap', note: `snapped to ${options.snapTo} px ${options.snapDirection}` },
  );

  plan = plan.moved(roundRect(plan.rect, { policy: /** @type {any} */ (options.rounding) }), {
    rule: 'round',
    note: `rounded ${options.rounding}`,
  });

  if (plan.overhangs) {
    plan = plan.warned(`${plan.overhangArea} px of the crop falls off the capture`);
  }
  if (plan.rect.isEmpty) {
    throw new PlanError('the rules between them left no crop at all', {
      trail: plan.trail,
    });
  }

  return plan;
}

/**
 * The sampling frequency a crop of this size would come out at, given what the
 * anchor is known to measure. Used by the resolution warning above and by the
 * quality checks later, which have to agree.
 *
 * @param {Rect} rect
 * @param {number} physicalWidth
 * @param {import('../measure/units.js').Unit} unit
 * @returns {Sampling}
 */
export function croppedSampling(rect, physicalWidth, unit) {
  return achievedSampling(rect.width, physicalWidth, unit);
}
