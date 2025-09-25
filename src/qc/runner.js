import { qcLimits } from '../profile/settings.js';
import { assertNoDuplicates, runChecks } from './check.js';
import { grayBalanceCheck } from './checks/graybalance.js';
import { illuminationCheck } from './checks/illumination.js';
import { clippingCheck, noiseCheck } from './checks/noise.js';
import { samplingCheck, skewAgreementCheck, skewCheck } from './checks/sampling.js';
import { sharpnessCheck } from './checks/sharpness.js';
import { levelWithRequired } from './levels.js';

/**
 * Running the quality checks against a capture.
 *
 * The set is the same for every collection. What changes is the limits and
 * which checks are required, and both come off the profile. A site that wants
 * another check adds it to the set it passes in rather than editing this file.
 *
 * The context is assembled here, once, so a check never has to know how to get
 * at a reading. That is what makes the checks testable with a plain object.
 *
 * It also carries a list of what is on the bed and is not bed, which the
 * illumination check needs. Without it that check measures the difference
 * between the board and the steel rule and fails every capture in the shop.
 */

/** The checks that run on every capture, in the order a report lists them. */
export function standardChecks() {
  return [
    samplingCheck(),
    skewCheck(),
    skewAgreementCheck(),
    illuminationCheck(),
    grayBalanceCheck(),
    noiseCheck(),
    clippingCheck(),
    sharpnessCheck(),
  ];
}

/** The ones a delivery cannot be graded without. */
export const REQUIRED = Object.freeze(['sampling', 'skew', 'clipping']);

/**
 * Everything a check might ask for, gathered off the reading and the plan.
 *
 * @param {object} args
 * @param {import('../raster/surface.js').Surface} args.surface
 * @param {import('../detect/capture.js').CaptureReading} args.reading
 * @param {Record<string, any>} args.profile
 * @param {import('../measure/resolution.js').Sampling} [args.sampling]
 * @param {import('../geom/rect.js').Rect} [args.crop]
 * @returns {Record<string, any>}
 */
export function contextFor({ surface, reading, profile, sampling, crop }) {
  const sheet = reading.sheet.quad.boundingRect.outward();
  const avoid = [sheet, reading.target?.rect, reading.scaleBar?.rect].filter(Boolean);
  return {
    surface,
    sheet,
    avoid,
    bed: surface.bounds,
    crop,
    target: reading.target,
    sampling: sampling ?? reading.sampling ?? null,
    skew: reading.sheet.skew,
    spread: reading.sheet.spread,
    limits: qcLimits(profile),
  };
}

/**
 * @typedef {object} QcResult
 * @property {import('./check.js').Finding[]} findings
 * @property {number} stars
 * @property {string|null} worst
 * @property {string[]} missing
 * @property {boolean} passed
 * @property {number} wanted
 */

/**
 * @param {object} args
 * @param {import('../raster/surface.js').Surface} args.surface
 * @param {import('../detect/capture.js').CaptureReading} args.reading
 * @param {Record<string, any>} args.profile
 * @param {import('../measure/resolution.js').Sampling} [args.sampling]
 * @param {import('../geom/rect.js').Rect} [args.crop]
 * @param {object} [options]
 * @param {readonly import('./check.js').Check[]} [options.checks]
 * @param {readonly string[]} [options.required]
 * @returns {QcResult}
 */
export function runQc(args, options = {}) {
  const checks = options.checks ?? standardChecks();
  assertNoDuplicates(checks);

  const wanted = args.profile.qc.minimumStars;
  const context = contextFor(args);
  const findings = runChecks(checks, context, { minimumStars: wanted });
  const level = levelWithRequired(findings, options.required ?? REQUIRED);

  return {
    findings,
    stars: level.stars,
    worst: level.worst,
    missing: level.missing,
    passed: level.stars >= wanted,
    wanted,
  };
}

/**
 * Run the same set over a batch and keep the per item results alongside the
 * level the batch as a whole delivers at.
 *
 * @param {readonly Parameters<typeof runQc>[0][]} items
 * @param {object} [options]
 * @param {readonly import('./check.js').Check[]} [options.checks]
 * @param {readonly string[]} [options.required]
 * @returns {{results: QcResult[], stars: number, passed: number, failed: number}}
 */
export function runBatch(items, options) {
  const results = items.map((item) => runQc(item, options));
  const stars = results.reduce((worst, result) => Math.min(worst, result.stars), 4);
  return {
    results,
    stars: results.length === 0 ? 0 : stars,
    passed: results.filter((result) => result.passed).length,
    failed: results.filter((result) => !result.passed).length,
  };
}

/**
 * @param {readonly import('./check.js').Check[]} checks
 * @param {readonly string[]} names
 * @returns {import('./check.js').Check[]}
 */
export function withoutChecks(checks, names) {
  const drop = new Set(names);
  return checks.filter((item) => !drop.has(item.name));
}
