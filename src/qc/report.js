import { levelName, levelOf, shortfall } from './levels.js';

/**
 * The quality report.
 *
 * Two readers and they want different things. An operator wants one line that
 * says whether to go and look at the stand. A funder wants every measurement
 * with the limit beside it, because a report that says four stars without
 * saying what was measured is not evidence of anything.
 *
 * Both come out of the same findings, so they cannot disagree.
 */

/** @typedef {import('./check.js').Finding} Finding */

/**
 * @param {Finding} finding
 * @returns {string}
 */
export function describeFinding(finding) {
  if (!finding.ran) return `${finding.name}: not measured, ${finding.note}`;
  const stars = '*'.repeat(finding.stars).padEnd(4, '.');
  return `${finding.name}: ${stars} ${finding.note}`;
}

/**
 * @param {readonly Finding[]} findings
 * @param {object} [options]
 * @param {number} [options.wanted] the level the profile asked for
 * @param {string} [options.item] what was measured
 * @returns {string[]}
 */
export function report(findings, { wanted = 3, item } = {}) {
  const level = levelOf(findings);
  const head = item ? `${item}: ${levelName(level.stars)}` : levelName(level.stars);
  const lines = [head];

  if (level.ran === 0) {
    lines.push('nothing could be measured');
  } else if (level.stars < wanted) {
    lines.push(`asked for ${levelName(wanted)}, held down by ${level.worst}`);
  }
  if (level.skipped > 0) lines.push(`${level.skipped} checks could not run`);

  lines.push(...findings.map((finding) => `  ${describeFinding(finding)}`));
  return lines;
}

/**
 * @param {readonly Finding[]} findings
 * @param {object} [options]
 * @param {number} [options.wanted]
 * @param {string} [options.item]
 * @returns {string}
 */
export function oneLine(findings, { wanted = 3, item } = {}) {
  const level = levelOf(findings);
  const parts = [item, levelName(level.stars)].filter(Boolean);
  if (level.stars < wanted && level.worst) parts.push(`held down by ${level.worst}`);
  if (level.skipped > 0) parts.push(`${level.skipped} not measured`);
  return parts.join(', ');
}

/**
 * What to do about it. Every entry names a check and says what an operator
 * would actually change, which is the only part of a report anybody acts on.
 */
const REMEDIES = Object.freeze({
  sampling: 'set the scanner to the frequency the profile asks for',
  samplingFloor: 'capture again at a higher frequency',
  skew: 'square the sheet on the bed',
  skewAgreement: 'look at the capture, the detector was not sure what it found',
  illumination: 'move a lamp towards the darker side',
  grayBalance: 'white balance the stand against the target',
  noise: 'lower the gain or lengthen the exposure',
  clipping: 'lower the exposure and capture again',
  sharpness: 'refocus the stand',
});

/**
 * @param {readonly Finding[]} findings
 * @param {number} [wanted]
 * @returns {string[]}
 */
export function remedies(findings, wanted = 3) {
  return shortfall(findings, wanted).map(
    (short) => `${short.name}: ${REMEDIES[short.name] ?? 'look at the capture'}`,
  );
}

/**
 * The measurements on their own, for a machine rather than a person.
 *
 * @param {readonly Finding[]} findings
 * @returns {Record<string, {measured: number|null, stars: number, ran: boolean}>}
 */
export function measurements(findings) {
  /** @type {Record<string, {measured: number|null, stars: number, ran: boolean}>} */
  const out = {};
  for (const finding of findings) {
    out[finding.name] = {
      measured: Number.isFinite(finding.measured) ? finding.measured : null,
      stars: finding.stars,
      ran: finding.ran,
    };
  }
  return out;
}

/**
 * @param {readonly Finding[]} findings
 * @param {object} [options]
 * @param {number} [options.wanted]
 * @param {string} [options.item]
 * @returns {Record<string, unknown>}
 */
export function toJSON(findings, { wanted = 3, item } = {}) {
  const level = levelOf(findings);
  return {
    item: item ?? null,
    stars: level.stars,
    level: levelName(level.stars),
    wanted,
    passed: level.stars >= wanted,
    worst: level.worst,
    ran: level.ran,
    skipped: level.skipped,
    measurements: measurements(findings),
    remedies: remedies(findings, wanted),
  };
}
