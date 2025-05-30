/**
 * The detection layer as the rest of the tree sees it.
 *
 * The planner calls readCapture and nothing else. Everything else here is
 * exported because the steps are separately useful when something has gone
 * wrong and somebody is trying to work out which one.
 */

export { componentIdAt, isUsable, readCapture, summarise } from './capture.js';
export { describeFinding, findSheet, objections, spreadOf } from './sheet.js';

export { Mask } from './mask.js';
export {
  borderStrips,
  describeBackground,
  distanceFrom,
  estimateBackground,
  isUniform,
  objectMask,
} from './background.js';

export { boundary, close, dilate, erode, fillHoles, open } from './morphology.js';
export {
  dropSmaller,
  fillRatio,
  keepLargest,
  labelComponents,
  largestComponent,
  maskOfComponent,
} from './components.js';

export {
  SIDES,
  distanceToLine,
  edgeLines,
  edgePoints,
  fitLine,
  fitLineRobust,
  skewFromLines,
} from './edges.js';

export {
  cornerResidual,
  cornerShift,
  cornersFromLines,
  intersect,
  lineConfidence,
  quadConfidence,
  refineCorners,
} from './corners.js';

export { fitQuad, foldToBand, minimumAreaRect, outlineHull, quadSkew } from './quadfit.js';
export { coarseSkew, measureSkew, projectionScore, refineSkew, sampleSetPixels } from './deskew.js';

export {
  collarWidths,
  darkness,
  findNeatline,
  looksLikeQuadrangle,
  strongestLine,
} from './neatline.js';

export { endPatches, locateTarget, patchRange, readPatches, stepsMonotonically } from './target.js';
export {
  checkAgainstClaim,
  countTicks,
  describeBar,
  findScaleBar,
  samplingFromBar,
} from './scalebar.js';
