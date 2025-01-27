import { BadArgumentError } from '../errors.js';
import { assertOneOf } from '../util/assert.js';

/** @typedef {import('./surface.js').Surface} Surface */
/** @typedef {import('./view.js').SurfaceView} SurfaceView */
/** @typedef {Surface|SurfaceView} Readable */
/** @typedef {{min: number, max: number, mean: number, stdDev: number, count: number}} ChannelStats */

/**
 * Sample statistics over a region.
 *
 * The quality checks are built almost entirely out of these. Everything runs
 * in one pass with Welford's update, because a patch on a sixteen bit master
 * is millions of samples and a naive sum of squares loses precision on the
 * flat patches, which are exactly the ones being measured.
 */

/**
 * @param {Readable} source
 * @param {number} channel
 * @returns {ChannelStats}
 */
export function channelStats(source, channel) {
  if (channel < 0 || channel >= source.channels) {
    throw new BadArgumentError('that channel is outside the layout', {
      channel,
      channels: source.channels,
    });
  }
  let count = 0;
  let mean = 0;
  let m2 = 0;
  let min = Number.POSITIVE_INFINITY;
  let max = Number.NEGATIVE_INFINITY;

  for (let y = 0; y < source.height; y += 1) {
    const row = source.row(y);
    for (let x = 0; x < source.width; x += 1) {
      const value = row[x * source.channels + channel];
      count += 1;
      const delta = value - mean;
      mean += delta / count;
      m2 += delta * (value - mean);
      if (value < min) min = value;
      if (value > max) max = value;
    }
  }
  if (count === 0) {
    return { min: Number.NaN, max: Number.NaN, mean: Number.NaN, stdDev: Number.NaN, count: 0 };
  }
  return { min, max, mean, stdDev: Math.sqrt(m2 / count), count };
}

/**
 * @param {Readable} source
 * @returns {ChannelStats[]}
 */
export function allChannelStats(source) {
  const out = [];
  for (let c = 0; c < source.channels; c += 1) out.push(channelStats(source, c));
  return out;
}

/**
 * @param {Readable} source
 * @returns {number[]}
 */
export function meanPixel(source) {
  return allChannelStats(source).map((stats) => stats.mean);
}

/**
 * Signal to noise in decibels, taken as the mean over the deviation of a patch
 * that should be flat. The imaging guidelines quote it this way and an
 * operator reads the number straight off the report.
 *
 * @param {Readable} source
 * @param {number} [channel]
 * @returns {number}
 */
export function signalToNoise(source, channel = 0) {
  const { mean, stdDev } = channelStats(source, channel);
  if (!Number.isFinite(mean)) return Number.NaN;
  if (stdDev === 0) return Number.POSITIVE_INFINITY;
  return 20 * Math.log10(mean / stdDev);
}

/**
 * How far apart two regions of the same shape are, which is what a regression
 * test on a render compares.
 *
 * @param {Readable} a
 * @param {Readable} b
 * @returns {{rms: number, peak: number, count: number}}
 */
export function difference(a, b) {
  if (a.width !== b.width || a.height !== b.height || a.channels !== b.channels) {
    throw new BadArgumentError('those two regions are not the same shape', {
      left: { width: a.width, height: a.height, channels: a.channels },
      right: { width: b.width, height: b.height, channels: b.channels },
    });
  }
  let acc = 0;
  let peak = 0;
  let count = 0;
  for (let y = 0; y < a.height; y += 1) {
    const left = a.row(y);
    const right = b.row(y);
    for (let i = 0; i < left.length; i += 1) {
      const delta = left[i] - right[i];
      acc += delta * delta;
      const magnitude = Math.abs(delta);
      if (magnitude > peak) peak = magnitude;
      count += 1;
    }
  }
  return { rms: count === 0 ? 0 : Math.sqrt(acc / count), peak, count };
}

/**
 * Collapse a region to one number per row or per column, which the projection
 * profiles in the detector are built on.
 *
 * @param {Readable} source
 * @param {object} [options]
 * @param {'row'|'column'} [options.along]
 * @param {number} [options.channel]
 * @returns {Float64Array}
 */
export function meanProfile(source, { along = 'row', channel = 0 } = {}) {
  assertOneOf(along, ['row', 'column'], 'along');
  if (channel < 0 || channel >= source.channels) {
    throw new BadArgumentError('that channel is outside the layout', {
      channel,
      channels: source.channels,
    });
  }
  if (along === 'row') {
    const out = new Float64Array(source.height);
    for (let y = 0; y < source.height; y += 1) {
      const row = source.row(y);
      let acc = 0;
      for (let x = 0; x < source.width; x += 1) acc += row[x * source.channels + channel];
      out[y] = source.width === 0 ? 0 : acc / source.width;
    }
    return out;
  }
  const out = new Float64Array(source.width);
  for (let y = 0; y < source.height; y += 1) {
    const row = source.row(y);
    for (let x = 0; x < source.width; x += 1) out[x] += row[x * source.channels + channel];
  }
  for (let x = 0; x < out.length; x += 1) out[x] /= source.height || 1;
  return out;
}
