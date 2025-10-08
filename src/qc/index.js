import { darkestCorner } from './checks/illumination.js';
import { castDirection } from './checks/graybalance.js';
import { levelName } from './levels.js';
import { report as reportLines, remedies, toJSON } from './report.js';
import { runQc } from './runner.js';

/**
 * Quality control as the rest of the tree sees it.
 *
 * One call grades a capture and one call turns the grading into something a
 * person or a machine reads. Everything else is exported because a lab that
 * disagrees with a limit needs to be able to run one check on its own and see
 * the number, and because the ladders are the documentation.
 *
 * Nothing here writes a file or decides what happens next. A capture that
 * grades at two stars is still a capture, and whether two stars is good enough
 * to deliver is the pipeline's decision, not this layer's.
 */

export { runQc, runBatch, contextFor, standardChecks, withoutChecks, REQUIRED } from './runner.js';
export {
  MAX_STARS,
  assertNoDuplicates,
  check,
  measurement,
  missingFrom,
  runCheck,
  runChecks,
  starsFor,
} from './check.js';
export {
  LEVEL_NAMES,
  batchLevel,
  levelName,
  levelOf,
  levelWithRequired,
  meetsLevel,
  shortfall,
  tallyLevels,
} from './levels.js';
export { describeFinding, measurements, oneLine, remedies, report, toJSON } from './report.js';

export {
  PATCH_MARGIN,
  channelMeans,
  gridOver,
  lightestFlatPatch,
  patchGrid,
  patchMeans,
  patchView,
} from './sample.js';

export {
  SAMPLING_LADDER,
  SKEW_LADDER,
  samplingCheck,
  samplingFloorCheck,
  skewAgreementCheck,
  skewCheck,
} from './checks/sampling.js';
export { ILLUMINATION_LADDER, darkestCorner, illuminationCheck } from './checks/illumination.js';
export {
  GRAY_BALANCE_LADDER,
  castDirection,
  grayBalanceCheck,
  neutralError,
} from './checks/graybalance.js';
export { CLIPPING_LADDER, NOISE_LADDER, clippingCheck, noiseCheck } from './checks/noise.js';
export {
  SHARPNESS_LADDER,
  edgeProfile,
  edgeStrips,
  riseDistance,
  sharpnessCheck,
} from './checks/sharpness.js';

/**
 * Grade a capture and say what to do about it in the same call.
 *
 * The advice is the part an operator acts on and it is worth more than the
 * level, because a capture that failed on illumination and a capture that
 * failed on focus both read two stars and want opposite things done to the
 * stand. Where a check can say which way the fault goes, it does.
 *
 * @param {Parameters<typeof runQc>[0]} args
 * @param {Parameters<typeof runQc>[1]} [options]
 * @returns {ReturnType<typeof runQc> & {advice: string[]}}
 */
export function gradeCapture(args, options) {
  const out = runQc(args, options);
  const advice = remedies(out.findings, out.wanted);

  const shortOn = new Set(advice.map((line) => line.slice(0, line.indexOf(':'))));
  if (shortOn.has('illumination')) {
    const corner = darkestCorner(args.surface, args.reading.sheet.quad.boundingRect.outward());
    if (Number.isFinite(corner.mean)) advice.push(`the ${corner.corner} is the dark side`);
  }
  if (shortOn.has('grayBalance') && args.reading.target) {
    const cast = castDirection(args.surface, args.reading.target.patches);
    if (cast) {
      advice.push(`the cast is ${cast.amount > 0 ? 'towards' : 'away from'} ${cast.channel}`);
    }
  }

  return { ...out, advice };
}

/**
 * The same grading written out for a person.
 *
 * @param {Parameters<typeof runQc>[0]} args
 * @param {object} [options]
 * @param {string} [options.item] what was measured, for the first line
 * @param {Parameters<typeof runQc>[1]} [options.qc]
 * @returns {string[]}
 */
export function reportOn(args, { item, qc } = {}) {
  const out = gradeCapture(args, qc);
  return [
    ...reportLines(out.findings, { wanted: out.wanted, item }),
    ...out.advice.map((a) => `  ${a}`),
  ];
}

/**
 * The same grading written out for a machine, with the advice folded in so a
 * sidecar carries everything the report showed.
 *
 * The level comes off the runner rather than off the findings. They disagree
 * on exactly one case and it is the case that matters: a capture whose
 * required checks could not be run has no level at all, and a sidecar that
 * says four stars because the four checks that did run were fine would be
 * evidence of something that was never measured.
 *
 * @param {Parameters<typeof runQc>[0]} args
 * @param {object} [options]
 * @param {string} [options.item]
 * @param {Parameters<typeof runQc>[1]} [options.qc]
 * @returns {Record<string, unknown>}
 */
export function gradingToJSON(args, { item, qc } = {}) {
  const out = gradeCapture(args, qc);
  return {
    ...toJSON(out.findings, { wanted: out.wanted, item }),
    stars: out.stars,
    level: levelName(out.stars),
    passed: out.passed,
    missing: out.missing,
    advice: out.advice,
  };
}
