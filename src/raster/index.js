/**
 * The raster layer as the rest of the tree sees it.
 *
 * Anything above this line imports from here rather than reaching into the
 * files, so the internal split between a surface, a view and their base can
 * move again without a hundred import edits.
 */

export { Raster } from './raster.js';
export { Surface } from './surface.js';
export { SurfaceView } from './view.js';

export {
  DEPTHS,
  LAYOUTS,
  alphaIndex,
  arrayTypeFor,
  assertDepth,
  assertLayout,
  channelCount,
  channelNames,
  colourCount,
  hasAlpha,
  isColour,
  maxValue,
  rowBytes,
  withAlpha,
  withoutAlpha,
} from './layout.js';

export { EDGE_MODES, SAMPLE_MODES, Sampler, resolveIndex, samplerFor } from './sampler.js';

export {
  KERNEL_NAMES,
  box,
  buildContributions,
  catmullRom,
  haloFor,
  kernelFor,
  lanczos,
  mitchell,
  triangle,
} from './kernels.js';

export { Histogram, histogramOf, histogramsOf } from './histogram.js';
export { IntegralImage, integralOf } from './integral.js';

export {
  allChannelStats,
  channelStats,
  difference,
  meanPixel,
  meanProfile,
  signalToNoise,
} from './stats.js';

export {
  LUMA_601,
  LUMA_709,
  addAlpha,
  byteSize,
  dropAlpha,
  scaleSample,
  toColour,
  toDepth,
  toGray,
  toLayout,
} from './convert.js';

export { crop, cropClamped, cropPadded, cropView } from './ops/crop.js';
export { PAD_MODES, pad, padTo, padUniform } from './ops/pad.js';
export {
  applyExifOrientation,
  flipHorizontal,
  flipVertical,
  rotateQuarters,
  transpose,
} from './ops/orient.js';
export { fitLongestSide, reduceInSteps, resample, scaleBy } from './ops/resample.js';
export { deskew, rotate, rotatedBounds } from './ops/rotate.js';
