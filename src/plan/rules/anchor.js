import { PlanError } from '../../errors.js';
import { assertNonEmptyString } from '../../util/assert.js';
import { rule } from './rule.js';

/**
 * What the crop is measured from.
 *
 * Two of the collections crop to the paper edge and two to the neatline, and
 * the choice is the single biggest difference between them. It is a rule
 * rather than a setting the engine reads directly, so a site with a third
 * anchor can add one without touching the engine.
 *
 * It runs first, and it always sets the crop rather than adjusting it, so a
 * second pass lands in the same place as the first.
 */

/**
 * @param {import('../../geom/rect.js').Rect} rect
 * @param {string} name what to call it in the trail
 * @returns {import('./rule.js').Rule}
 */
export function anchorRule(rect, name) {
  assertNonEmptyString(name, 'name');
  if (rect.isEmpty) {
    throw new PlanError(`the ${name} anchor has no area`, { anchor: rect.toJSON(), name });
  }
  return rule({
    name: 'anchor',
    phase: 'anchor',
    apply(plan, context) {
      context.anchor = rect;
      context.anchorName = name;
      return plan.moved(rect, { rule: 'anchor', note: `measured from the ${name}` });
    },
  });
}

/**
 * Pick the anchor a profile asked for out of what the detector found. A
 * profile that wants the neatline and a capture that has none is a fault, not
 * a quiet fall back to the paper edge: the crop would be an inch and a half
 * out and nobody would see it until the plates were bound.
 *
 * @param {string} anchor the name from the profile
 * @param {object} found
 * @param {import('../../geom/rect.js').Rect} found.sheet
 * @param {import('../../geom/rect.js').Rect} [found.neatline]
 * @returns {import('./rule.js').Rule}
 */
export function anchorFor(anchor, found) {
  if (anchor === 'neatline') {
    if (!found.neatline || found.neatline.isEmpty) {
      throw new PlanError('the profile crops to the neatline and none was found', {
        anchor,
      });
    }
    return anchorRule(found.neatline, 'neatline');
  }
  if (anchor === 'sheet') return anchorRule(found.sheet, 'sheet');
  throw new PlanError(`there is no anchor called ${anchor}`, { anchor });
}

/**
 * An anchor that falls back when the one asked for is not there, for the
 * collections that would rather have a crop than a held item. It says loudly
 * what it did.
 *
 * @param {string} anchor
 * @param {object} found
 * @param {import('../../geom/rect.js').Rect} found.sheet
 * @param {import('../../geom/rect.js').Rect} [found.neatline]
 * @returns {import('./rule.js').Rule}
 */
export function anchorOrSheetRule(anchor, found) {
  if (anchor !== 'neatline' || (found.neatline && !found.neatline.isEmpty)) {
    return anchorFor(anchor, found);
  }
  const inner = anchorRule(found.sheet, 'sheet');
  return rule({
    name: 'anchor',
    phase: 'anchor',
    apply(plan, context) {
      return inner
        .apply(plan, context)
        .warned('no neatline was found, so the crop fell back to the paper edge');
    },
  });
}
