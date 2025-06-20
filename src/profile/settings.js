import { Insets } from '../geom/insets.js';
import { Sampling } from '../measure/resolution.js';

/**
 * Turning a profile into the arguments each stage actually takes.
 *
 * The stages below take plain option bags and know nothing about profiles.
 * That is deliberate: it keeps them testable without building a profile, and
 * it keeps the profile vocabulary in one place. This file is that place, and
 * it is the only thing that reads a profile and writes stage options.
 *
 * Lengths become pixels here, so every stage below works in pixels only.
 */

/**
 * @param {Record<string, any>} profile
 * @returns {Sampling}
 */
export function claimedSampling(profile) {
  return Sampling.of(profile.capture.claimedPpi);
}

/**
 * @param {Record<string, any>} profile
 * @returns {{sigma: number, speckle: number, gap: number, expectNeatline: boolean, barLength: number, barUnit: string, claimed: Sampling}}
 */
export function captureOptions(profile) {
  return {
    sigma: profile.capture.sigma,
    speckle: profile.capture.speckle,
    gap: profile.capture.gap,
    expectNeatline: profile.capture.expectNeatline,
    barLength: profile.capture.barLength.value,
    barUnit: profile.capture.barLength.unit,
    claimed: claimedSampling(profile),
  };
}

/**
 * @param {Record<string, any>} edges a bag of four lengths
 * @param {Sampling} sampling
 * @returns {Insets}
 */
export function edgesInPixels(edges, sampling) {
  return new Insets(
    sampling.toPixels(edges.top.value, edges.top.unit, 'y'),
    sampling.toPixels(edges.right.value, edges.right.unit, 'x'),
    sampling.toPixels(edges.bottom.value, edges.bottom.unit, 'y'),
    sampling.toPixels(edges.left.value, edges.left.unit, 'x'),
  );
}

/**
 * @param {Record<string, any>} profile
 * @param {Sampling} sampling what the capture was really taken at
 * @returns {{anchor: string, bleed: Insets, safeArea: Insets, aspect: number|undefined, aspectDirection: string, minimumPpi: number, snapTo: number, snapDirection: string, rounding: string, background: number, deskew: boolean}}
 */
export function cropOptions(profile, sampling) {
  return {
    anchor: profile.crop.anchor,
    bleed: edgesInPixels(profile.crop.bleed, sampling),
    safeArea: edgesInPixels(profile.crop.safeArea, sampling),
    aspect: profile.crop.aspect,
    aspectDirection: profile.crop.aspectDirection,
    minimumPpi: profile.crop.minimumPpi,
    snapTo: profile.crop.snapTo,
    snapDirection: profile.crop.snapDirection,
    rounding: profile.crop.rounding,
    background: profile.crop.background,
    deskew: profile.crop.deskew,
  };
}

/**
 * @param {Record<string, any>} profile
 * @returns {Array<{name: string, format: string, longestSide: number|undefined, scale: number|undefined, depth: 8|16, layout: string, kernel: string, keepMetadata: boolean}>}
 */
export function derivativeRecipes(profile) {
  return profile.derivatives.map((derivative) => ({
    name: derivative.name,
    format: derivative.format,
    longestSide: derivative.longestSide,
    scale: derivative.scale,
    depth: /** @type {8|16} */ (Number(derivative.depth)),
    layout: derivative.layout,
    kernel: derivative.kernel,
    keepMetadata: derivative.keepMetadata,
  }));
}

/**
 * @param {Record<string, any>} profile
 * @returns {{sampling: Sampling, samplingTolerance: import('../measure/tolerance.js').Tolerance, skewTolerance: number, illuminationTolerance: number, grayBalanceTolerance: number, minimumSnr: number, maximumClipping: number, minimumStars: number}}
 */
export function qcLimits(profile) {
  return {
    sampling: Sampling.of(profile.qc.samplingPpi),
    samplingTolerance: profile.qc.samplingTolerance,
    skewTolerance: profile.qc.skewTolerance,
    illuminationTolerance: profile.qc.illuminationTolerance,
    grayBalanceTolerance: profile.qc.grayBalanceTolerance,
    minimumSnr: profile.qc.minimumSnr,
    maximumClipping: profile.qc.maximumClipping,
    minimumStars: profile.qc.minimumStars,
  };
}

/**
 * Fill a naming pattern. An unknown placeholder is left alone rather than
 * blanked, so a typo shows up in the file name instead of quietly producing
 * two files with the same one.
 *
 * @param {Record<string, any>} profile
 * @param {Record<string, string|number>} values
 * @returns {string}
 */
export function fillName(profile, values) {
  const filled = profile.naming.pattern.replace(/\{(\w+)\}/g, (whole, key) =>
    key in values ? String(values[key]) : whole,
  );
  return profile.naming.lowercase ? filled.toLowerCase() : filled;
}

/**
 * Every stage's arguments at once, which is what the pipeline holds for the
 * length of a run.
 *
 * @param {Record<string, any>} profile
 * @param {Sampling} [sampling]
 * @returns {{profile: Record<string, any>, sampling: Sampling, capture: ReturnType<typeof captureOptions>, crop: ReturnType<typeof cropOptions>, derivatives: ReturnType<typeof derivativeRecipes>, qc: ReturnType<typeof qcLimits>}}
 */
export function settingsFor(profile, sampling) {
  const frequency = sampling ?? claimedSampling(profile);
  return {
    profile,
    sampling: frequency,
    capture: captureOptions(profile),
    crop: cropOptions(profile, frequency),
    derivatives: derivativeRecipes(profile),
    qc: qcLimits(profile),
  };
}
