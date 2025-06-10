/**
 * The house styles and the collections that ship with the tool.
 *
 * These are the profiles the lab actually runs. They are here rather than in a
 * data directory because they are part of what the tool promises: a run that
 * names one of these gets the same crop it got last year, and a change to one
 * of them is a change to the tool that has to go through review.
 *
 * A site with its own collections writes its own files and registers them.
 */

/** The base every other profile extends. Nothing here is collection specific. */
export const HOUSE = Object.freeze({
  name: 'house',
  title: 'house style',
  version: 2,
  capture: {
    sigma: 6,
    speckle: 2,
    gap: 3,
    expectNeatline: false,
    barLength: '6in',
    claimedPpi: 400,
  },
  crop: {
    anchor: 'sheet',
    bleed: '0mm',
    safeArea: '0mm',
    minimumPpi: 300,
    snapTo: 2,
    snapDirection: 'up',
    rounding: 'outward',
    background: 0,
    deskew: true,
  },
  derivatives: [
    { name: 'master', depth: '16', layout: 'rgb', kernel: 'lanczos3', keepMetadata: true },
    { name: 'service', longestSide: 3000, depth: '8', layout: 'rgb', kernel: 'catmullRom' },
    { name: 'thumb', longestSide: 400, depth: '8', layout: 'rgb', kernel: 'box' },
  ],
  qc: {
    samplingPpi: 400,
    samplingTolerance: '1%',
    skewTolerance: 0.3,
    illuminationTolerance: 0.1,
    grayBalanceTolerance: 6,
    minimumSnr: 30,
    maximumClipping: 0.005,
    minimumStars: 3,
  },
  naming: { pattern: '{accession}_{derivative}', lowercase: true },
});

/**
 * Bound volumes photographed on the copy stand. The paper edge is the crop,
 * because a plate that has been trimmed by a binder is still the whole plate.
 */
export const ATLAS_PLATES = Object.freeze({
  name: 'atlas-plates',
  title: 'county atlas plates',
  extends: 'house',
  crop: {
    bleed: '2mm',
    minimumPpi: 400,
  },
  qc: {
    samplingPpi: 600,
    skewTolerance: 0.2,
  },
});

/**
 * Quadrangle sheets. The crop is the neatline plus a strip of collar, so the
 * legend at the bottom survives and the grid ticks on the other three sides do
 * not.
 */
export const QUADRANGLES = Object.freeze({
  name: 'quadrangles',
  title: 'usgs quadrangle sheets',
  extends: 'house',
  capture: {
    expectNeatline: true,
    claimedPpi: 600,
  },
  crop: {
    anchor: 'neatline',
    bleed: { top: '3mm', right: '3mm', bottom: '38mm', left: '3mm' },
    minimumPpi: 500,
    snapTo: 8,
  },
  qc: {
    samplingPpi: 600,
    skewTolerance: 0.15,
    minimumStars: 4,
    maximumClipping: 0.001,
  },
});

/**
 * Rolled drawings on the large flatbed. They relax slowly and the edges are
 * rarely straight, so the tolerances are wider and the crop leaves a margin.
 */
export const ROLLED_DRAWINGS = Object.freeze({
  name: 'rolled-drawings',
  title: 'oversize rolled drawings',
  extends: 'house',
  capture: {
    sigma: 4,
    gap: 6,
    barLength: '12in',
    claimedPpi: 300,
  },
  crop: {
    bleed: '6mm',
    minimumPpi: 250,
    rounding: 'outward',
  },
  derivatives: [
    { name: 'master', depth: '16', layout: 'rgb', kernel: 'lanczos3', keepMetadata: true },
    { name: 'service', longestSide: 4000, depth: '8', layout: 'rgb', kernel: 'catmullRom' },
  ],
  qc: {
    samplingPpi: 300,
    skewTolerance: 0.6,
    minimumSnr: 24,
    minimumStars: 2,
  },
});

/**
 * Microfilm frames, which arrive as grayscale and never carry a colour target.
 */
export const MICROFILM = Object.freeze({
  name: 'microfilm',
  title: 'microfilm frames',
  extends: 'house',
  capture: { sigma: 8, speckle: 1, gap: 1, claimedPpi: 300 },
  crop: { bleed: '0mm', minimumPpi: 200, snapTo: 2 },
  derivatives: [
    { name: 'master', depth: '8', layout: 'gray', kernel: 'lanczos3', keepMetadata: true },
    { name: 'service', longestSide: 2000, depth: '8', layout: 'gray', kernel: 'catmullRom' },
  ],
  qc: {
    samplingPpi: 300,
    grayBalanceTolerance: 255,
    minimumSnr: 20,
    minimumStars: 2,
  },
});

/** Every profile that ships, in the order a listing shows them. */
export const BUILT_IN = Object.freeze([
  HOUSE,
  ATLAS_PLATES,
  QUADRANGLES,
  ROLLED_DRAWINGS,
  MICROFILM,
]);

/**
 * @param {string} name
 * @returns {Record<string, unknown>|undefined}
 */
export function builtInNamed(name) {
  return BUILT_IN.find((profile) => profile.name === name);
}

/** @returns {string[]} */
export function builtInNames() {
  return BUILT_IN.map((profile) => profile.name);
}
