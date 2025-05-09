import { DetectionError } from '../errors.js';
import { assertInRange } from '../util/assert.js';
import { objectMask } from './background.js';
import { largestComponent } from './components.js';
import { edgeLines, skewFromLines } from './edges.js';
import { quadConfidence, refineCorners } from './corners.js';
import { measureSkew } from './deskew.js';
import { close, fillHoles, open } from './morphology.js';
import { fitQuad } from './quadfit.js';

/** @typedef {import('../raster/surface.js').Surface} Surface */
/** @typedef {import('./mask.js').Mask} Mask */
/** @typedef {import('../geom/quad.js').Quad} Quad */
/** @typedef {import('../geom/angle.js').Angle} Angle */

/**
 * Finding the sheet, from a capture to a quad.
 *
 * Everything above this calls this one function, and everything below it is a
 * step this one arranges. The steps are all separately testable and the order
 * matters: clean before labelling, label before fitting, and fit before asking
 * any of the three skew measures what they think.
 *
 * The three measures are the point. A single number with no way to tell
 * whether it is right is worse than no number at all, because the operator
 * cannot see a wrong crop until the derivatives are already made.
 */

/**
 * @typedef {object} SheetFinding
 * @property {Quad} quad
 * @property {Angle} skew
 * @property {Mask} mask
 * @property {number} confidence between zero and one
 * @property {number} spread how far apart the skew measures were, in degrees
 * @property {boolean} touchesEdge the sheet runs off the bed
 * @property {number} coverage share of the capture the sheet takes
 * @property {{calipers: Angle, projection: Angle, sides: Angle}} readings
 */

/**
 * @param {Surface} surface
 * @param {object} [options]
 * @param {number} [options.sigma] how far from the bed counts as object
 * @param {number} [options.fraction] how much of each edge is read as bed
 * @param {number} [options.speckle] radius of the opening that removes dust
 * @param {number} [options.gap] radius of the closing that mends a torn edge
 * @param {number} [options.agreement] degrees the measures may differ by
 * @param {number} [options.minimumCoverage] share of the capture a sheet must fill
 * @returns {SheetFinding}
 */
export function findSheet(surface, options = {}) {
  const {
    sigma = 6,
    speckle = 2,
    gap = 3,
    agreement = 1,
    minimumCoverage = 0.02,
    fraction,
  } = options;
  assertInRange(minimumCoverage, 0, 1, 'minimumCoverage');

  const { mask: raw } = objectMask(surface, fraction ? { sigma, fraction } : { sigma });
  if (raw.count === 0) {
    throw new DetectionError('nothing on the bed stands out from it', {
      size: surface.size.toJSON(),
    });
  }

  const cleaned = fillHoles(close(open(raw, speckle), gap));
  const found = largestComponent(cleaned);
  if (!found) {
    throw new DetectionError('nothing survived the cleanup', { before: raw.count });
  }

  const coverage = found.component.area / (surface.width * surface.height);
  if (coverage < minimumCoverage) {
    throw new DetectionError('what is on the bed is too small to be a sheet', {
      coverage,
      minimumCoverage,
    });
  }

  const caliper = fitQuad(found.mask);
  const lines = edgeLines(found.mask);
  const readings = {
    calipers: caliper.angle,
    projection: measureSkew(found.mask).angle,
    sides: skewFromLines(lines),
  };
  const spread = spreadOf(readings);
  const refined = refineCorners(caliper.quad, lines);

  const agreementScore = spread <= agreement ? 1 : agreement / spread;
  const confidence = Math.min(quadConfidence(lines), caliper.fill, agreementScore);

  return {
    quad: refined.quad,
    skew: readings.calipers,
    mask: found.mask,
    confidence,
    spread,
    touchesEdge: found.component.touchesEdge,
    coverage,
    readings,
  };
}

/**
 * @param {{calipers: Angle, projection: Angle, sides: Angle}} readings
 * @returns {number} degrees between the furthest apart pair
 */
export function spreadOf(readings) {
  const values = [readings.calipers.degrees, readings.projection.degrees, readings.sides.degrees];
  return Math.max(...values) - Math.min(...values);
}

/**
 * A one line summary for the run log. Operators read these by the hundred and
 * the only thing they want is whether to go and look.
 *
 * @param {SheetFinding} finding
 * @returns {string}
 */
export function describeFinding(finding) {
  const size = finding.quad.boundingRect;
  const parts = [
    `${Math.round(size.width)}x${Math.round(size.height)}`,
    `skew ${finding.skew.degrees.toFixed(2)} deg`,
    `spread ${finding.spread.toFixed(2)}`,
    `confidence ${finding.confidence.toFixed(2)}`,
  ];
  if (finding.touchesEdge) parts.push('runs off the bed');
  return parts.join(', ');
}

/**
 * @param {SheetFinding} finding
 * @param {object} [options]
 * @param {number} [options.minimumConfidence]
 * @returns {string[]} the reasons this finding should not be used unchecked
 */
export function objections(finding, { minimumConfidence = 0.5 } = {}) {
  const out = [];
  if (finding.confidence < minimumConfidence) out.push('the sides do not fit well');
  if (finding.touchesEdge) out.push('the sheet runs off the bed');
  if (!finding.quad.isConvex) out.push('the corners fold back on themselves');
  if (finding.coverage > 0.95) out.push('the sheet fills the whole bed, so there is no margin');
  return out;
}
