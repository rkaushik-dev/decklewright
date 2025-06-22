import { formatLength } from '../measure/units.js';
import { Sampling } from '../measure/resolution.js';
import { settingsFor } from './settings.js';
import { describeProblems, inspectProfile } from './validate.js';

/**
 * Saying in plain words what a profile will do.
 *
 * A curator writes a profile and cannot run a batch to see what it means. This
 * prints the answer: what gets cropped to, how much paper is kept, what comes
 * out, and where the quality bar sits. It reads off the resolved profile
 * rather than the file, so an inherited setting shows up the same as a written
 * one.
 */

/**
 * @param {Record<string, any>} edges
 * @returns {string}
 */
function describeEdges(edges) {
  const sides = ['top', 'right', 'bottom', 'left'];
  const values = sides.map((side) => edges[side]);
  const same = values.every((value) => value.equals(values[0]));
  if (same) return formatLength(values[0].value, values[0].unit);
  return sides
    .map((side, i) => `${side} ${formatLength(values[i].value, values[i].unit)}`)
    .join(', ');
}

/**
 * @param {Record<string, any>} derivative
 * @returns {string}
 */
export function describeDerivative(derivative) {
  const size =
    derivative.longestSide !== undefined
      ? `longest side ${derivative.longestSide} px`
      : derivative.scale !== undefined
        ? `scaled to ${derivative.scale}`
        : 'full size';
  const bits = `${derivative.layout} at ${derivative.depth} bit`;
  const extra = derivative.keepMetadata ? ', keeping metadata' : '';
  return `${derivative.name}: ${size}, ${bits}, ${derivative.format} by ${derivative.kernel}${extra}`;
}

/**
 * @param {Record<string, any>} profile
 * @returns {string[]}
 */
export function describeProfile(profile) {
  const lines = [`${profile.name}: ${profile.title} (version ${profile.version})`];

  lines.push(
    `crops to the ${profile.crop.anchor}${profile.crop.deskew ? ', straightened first' : ''}`,
  );
  if (!isZeroEdges(profile.crop.bleed)) {
    lines.push(`keeps ${describeEdges(profile.crop.bleed)} beyond it`);
  }
  if (!isZeroEdges(profile.crop.safeArea)) {
    lines.push(`holds a safe area of ${describeEdges(profile.crop.safeArea)}`);
  }
  if (profile.crop.aspect !== undefined) {
    lines.push(`forces an aspect of ${profile.crop.aspect}, ${profile.crop.aspectDirection}ing`);
  }
  lines.push(
    `rounds ${profile.crop.rounding} and snaps to ${profile.crop.snapTo} px ${profile.crop.snapDirection}`,
  );
  lines.push(`refuses anything under ${profile.crop.minimumPpi} ppi`);

  lines.push(`capture claimed at ${profile.capture.claimedPpi} ppi`);
  if (profile.capture.expectNeatline) lines.push('looks for a neatline');

  for (const derivative of profile.derivatives) lines.push(describeDerivative(derivative));
  if (profile.derivatives.length === 0) lines.push('writes nothing');

  lines.push(
    `quality: ${profile.qc.samplingPpi} ppi ${profile.qc.samplingTolerance}, skew under ${profile.qc.skewTolerance} deg, ${profile.qc.minimumStars} stars`,
  );
  return lines;
}

/**
 * @param {Record<string, any>} edges
 * @returns {boolean}
 */
function isZeroEdges(edges) {
  return ['top', 'right', 'bottom', 'left'].every((side) => edges[side].isZero);
}

/**
 * What the crop settings come to in pixels for a given capture. A curator
 * writes millimetres and this is what the planner will actually use, which is
 * the number they want when a crop looks wrong by a hair.
 *
 * @param {Record<string, any>} profile
 * @param {Sampling|number} [sampling]
 * @returns {string[]}
 */
export function describeInPixels(profile, sampling) {
  const frequency = sampling === undefined ? undefined : Sampling.of(Number(sampling));
  const settings = settingsFor(profile, frequency);
  const bleed = settings.crop.bleed.rounded();
  const safe = settings.crop.safeArea.rounded();
  return [
    `at ${settings.sampling}`,
    `bleed ${bleed.top}/${bleed.right}/${bleed.bottom}/${bleed.left} px`,
    `safe area ${safe.top}/${safe.right}/${safe.bottom}/${safe.left} px`,
    `snap to ${settings.crop.snapTo} px ${settings.crop.snapDirection}`,
  ];
}

/**
 * Everything about one profile, ready to print.
 *
 * @param {Record<string, any>} profile
 * @param {object} [options]
 * @param {Sampling|number} [options.sampling]
 * @returns {string[]}
 */
export function report(profile, { sampling } = {}) {
  const problems = inspectProfile(profile);
  return [
    ...describeProfile(profile),
    ...describeInPixels(profile, sampling),
    ...describeProblems(problems),
  ];
}
