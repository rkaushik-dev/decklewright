import { BadArgumentError } from '../errors.js';
import { assertPositiveInteger } from '../util/assert.js';
import { clamp } from '../util/num.js';

/** @typedef {import('./surface.js').Surface} Surface */
/** @typedef {import('./view.js').SurfaceView} SurfaceView */

/**
 * Sample distributions.
 *
 * Everything the quality checks say about tone comes from one of these, and so
 * does the bed background estimate. Bin count is a parameter because a mask
 * threshold wants two hundred and fifty six bins regardless of depth, while a
 * clipping check on a sixteen bit master wants the full range.
 */
export class Histogram {
  /**
   * @param {Uint32Array} counts
   * @param {number} maxValue the top of the sample range the bins cover
   */
  constructor(counts, maxValue) {
    this.counts = counts;
    this.bins = counts.length;
    this.maxValue = maxValue;
    let total = 0;
    for (const count of counts) total += count;
    this.total = total;
    Object.freeze(this);
  }

  /**
   * The sample value at the centre of a bin, which is what a threshold has to
   * be reported as.
   *
   * @param {number} bin
   * @returns {number}
   */
  valueAt(bin) {
    return ((bin + 0.5) * (this.maxValue + 1)) / this.bins;
  }

  /**
   * @param {number} value
   * @returns {number}
   */
  binFor(value) {
    const bin = Math.floor((value * this.bins) / (this.maxValue + 1));
    return clamp(bin, 0, this.bins - 1);
  }

  /** @returns {number} */
  get mean() {
    if (this.total === 0) return Number.NaN;
    let acc = 0;
    for (let i = 0; i < this.bins; i += 1) acc += this.valueAt(i) * this.counts[i];
    return acc / this.total;
  }

  /** @returns {number} the bin with the most samples, ties going to the lower */
  get modeBin() {
    let best = 0;
    for (let i = 1; i < this.bins; i += 1) {
      if (this.counts[i] > this.counts[best]) best = i;
    }
    return best;
  }

  /** @returns {number} */
  get minBin() {
    for (let i = 0; i < this.bins; i += 1) {
      if (this.counts[i] > 0) return i;
    }
    return -1;
  }

  /** @returns {number} */
  get maxBin() {
    for (let i = this.bins - 1; i >= 0; i -= 1) {
      if (this.counts[i] > 0) return i;
    }
    return -1;
  }

  /**
   * @param {number} fraction between zero and one
   * @returns {number} a sample value
   */
  percentile(fraction) {
    if (this.total === 0) return Number.NaN;
    const wanted = clamp(fraction, 0, 1) * this.total;
    let seen = 0;
    for (let i = 0; i < this.bins; i += 1) {
      seen += this.counts[i];
      if (seen >= wanted) return this.valueAt(i);
    }
    return this.valueAt(this.bins - 1);
  }

  /**
   * The share of samples sitting in the lowest and highest bins, which is what
   * a clipping check reads.
   *
   * @returns {{low: number, high: number}}
   */
  clipping() {
    if (this.total === 0) return { low: 0, high: 0 };
    return {
      low: this.counts[0] / this.total,
      high: this.counts[this.bins - 1] / this.total,
    };
  }

  /** @returns {Uint32Array} */
  cumulative() {
    const out = new Uint32Array(this.bins);
    let running = 0;
    for (let i = 0; i < this.bins; i += 1) {
      running += this.counts[i];
      out[i] = running;
    }
    return out;
  }

  /**
   * Otsu's method. Returns the sample value that best splits the distribution
   * into two groups, which is how the bed background gets told apart from the
   * sheet without anyone typing a number into the profile.
   *
   * @returns {number}
   */
  otsu() {
    if (this.total === 0) return Number.NaN;
    let sum = 0;
    for (let i = 0; i < this.bins; i += 1) sum += i * this.counts[i];

    let weightLow = 0;
    let sumLow = 0;
    let best = 0;
    let bestBin = 0;
    for (let i = 0; i < this.bins; i += 1) {
      weightLow += this.counts[i];
      if (weightLow === 0) continue;
      const weightHigh = this.total - weightLow;
      if (weightHigh === 0) break;
      sumLow += i * this.counts[i];
      const meanLow = sumLow / weightLow;
      const meanHigh = (sum - sumLow) / weightHigh;
      const between = weightLow * weightHigh * (meanLow - meanHigh) ** 2;
      if (between > best) {
        best = between;
        bestBin = i;
      }
    }
    return this.valueAt(bestBin);
  }

  /** @returns {{bins: number, total: number, counts: number[]}} */
  toJSON() {
    return { bins: this.bins, total: this.total, counts: [...this.counts] };
  }
}

/**
 * @param {Surface|SurfaceView} source
 * @param {object} [options]
 * @param {number} [options.channel]
 * @param {number} [options.bins]
 * @returns {Histogram}
 */
export function histogramOf(source, { channel = 0, bins } = {}) {
  if (channel < 0 || channel >= source.channels) {
    throw new BadArgumentError('that channel is outside the layout', {
      channel,
      channels: source.channels,
    });
  }
  const width = source.maxValue + 1;
  const count = bins === undefined ? width : assertPositiveInteger(bins, 'bins');
  const counts = new Uint32Array(count);
  const shift = count / width;
  for (let y = 0; y < source.height; y += 1) {
    const row = source.row(y);
    for (let x = 0; x < source.width; x += 1) {
      const value = row[x * source.channels + channel];
      const bin = Math.min(count - 1, Math.floor(value * shift));
      counts[bin] += 1;
    }
  }
  return new Histogram(counts, source.maxValue);
}

/**
 * One histogram per channel, which the gray balance check needs so it can
 * compare where the three channels sit.
 *
 * @param {Surface|SurfaceView} source
 * @param {object} [options]
 * @param {number} [options.bins]
 * @returns {Histogram[]}
 */
export function histogramsOf(source, options = {}) {
  const out = [];
  for (let c = 0; c < source.channels; c += 1) {
    out.push(histogramOf(source, { ...options, channel: c }));
  }
  return out;
}
