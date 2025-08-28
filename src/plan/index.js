/**
 * Crop planning as the rest of the tree sees it.
 *
 * The pipeline calls planCrop. Everything else is exported because a rule set
 * is worth building by hand when an operator is overriding one rule for one
 * item, and because the explain command needs the pieces.
 */

export { CropPlan } from './plan.js';
export { planInDetail, planWith, runRules, withRule, without } from './engine.js';
export { KNOWN_TENSIONS, describeSet, houseRules, rulesFor, tensionsIn } from './ruleset.js';
export { intoStraightFrame, planCrop, pointsIntoStraightFrame, straightFrame } from './crop.js';
export {
  biggestMove,
  describeMove,
  explain,
  explainSteps,
  explainWithRules,
  summarise,
} from './explain.js';

export { PHASES, assertNoDuplicates, inPhase, isRule, ordered, rule } from './rules/rule.js';
export { anchorFor, anchorOrSheetRule, anchorRule } from './rules/anchor.js';
export {
  bleedFor,
  bleedRule,
  bleedWithinSheetRule,
  noBleedRule,
  trimmedBleedRule,
} from './rules/bleed.js';
export { edgeSafeAreaRule, safeAreaFor, safeAreaRule } from './rules/safearea.js';
export { aspectFor, aspectRule, nearestAspectRule, nudgeAspectRule } from './rules/aspect.js';
export {
  achievedPpi,
  minimumSizeRule,
  resolutionFor,
  resolutionRule,
  strictResolutionRule,
} from './rules/resolution.js';
export { evenExtentsRule, gridAlignRule, roundRule, snapFor, snapRule } from './rules/snap.js';
export {
  BOUNDS_MODES,
  boundsFor,
  clampToCaptureRule,
  notEmptyRule,
  nudgeIntoCaptureRule,
  refuseOverhangRule,
  warnOnOverhangRule,
} from './rules/bounds.js';
