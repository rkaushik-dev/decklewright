import { check, measurement, starsFor } from '../check.js';

/**
 * Sampling frequency and skew: the two checks that read a number somebody else
 * already measured.
 *
 * Neither of them touches a pixel. The frequency comes off the scale bar and
 * the skew off the detector, and both are already in the capture reading. They
 * are checks rather than readings because the profile has an opinion about
 * what an acceptable value is and the detector does not.
 */

/** How far off the stated frequency a capture may be, in percent, per star. */
export const SAMPLING_LADDER = Object.freeze([
  { stars: 4, limit: 0.005 },
  { stars: 3, limit: 0.01 },
  { stars: 2, limit: 0.02 },
  { stars: 1, limit: 0.05 },
]);

/** How far off square a sheet may be, in degrees, per star. */
export const SKEW_LADDER = Object.freeze([
  { stars: 4, limit: 0.1 },
  { stars: 3, limit: 0.3 },
  { stars: 2, limit: 0.6 },
  { stars: 1, limit: 1.5 },
]);

/**
 * @returns {import('../check.js').Check}
 */
export function samplingCheck() {
  return check({
    name: 'sampling',
    needs: ['sampling', 'limits'],
    run(context) {
      const wanted = context.limits.sampling.x;
      const got = context.sampling.x;
      const off = Math.abs(got - wanted) / wanted;

      const tolerance = context.limits.samplingTolerance;
      const withinProfile = tolerance ? tolerance.contains(wanted, got) : off <= 0.01;
      const stars = withinProfile ? starsFor(off, SAMPLING_LADDER) : 0;

      return measurement(
        got,
        stars,
        `${round(got)} ppi against ${round(wanted)}, off by ${(off * 100).toFixed(2)} percent`,
      );
    },
  });
}

/**
 * A capture below the stated frequency is worse than one above it, because
 * detail that was never sampled cannot be got back and detail sampled too
 * finely only costs disk. The ladder is one sided for that reason.
 *
 * @returns {import('../check.js').Check}
 */
export function samplingFloorCheck() {
  return check({
    name: 'samplingFloor',
    needs: ['sampling', 'limits'],
    run(context) {
      const wanted = context.limits.sampling.x;
      const got = context.sampling.x;
      const short = Math.max(0, (wanted - got) / wanted);
      return measurement(
        got,
        starsFor(short, SAMPLING_LADDER),
        short === 0
          ? `${round(got)} ppi, at or above the ${round(wanted)} asked for`
          : `${round(got)} ppi, ${(short * 100).toFixed(2)} percent short of ${round(wanted)}`,
      );
    },
  });
}

/**
 * @returns {import('../check.js').Check}
 */
export function skewCheck() {
  return check({
    name: 'skew',
    needs: ['skew', 'limits'],
    run(context) {
      const degrees = Math.abs(context.skew.degrees);
      const allowed = context.limits.skewTolerance;
      const stars = degrees <= allowed ? starsFor(degrees, SKEW_LADDER) : 0;
      return measurement(
        degrees,
        stars,
        `${degrees.toFixed(3)} deg off square against a limit of ${allowed}`,
      );
    },
  });
}

/**
 * How far apart the three skew measures were. A capture the detector was sure
 * about is worth more than one it guessed at, and this is the only check that
 * grades the measurement rather than the capture.
 *
 * @returns {import('../check.js').Check}
 */
export function skewAgreementCheck() {
  return check({
    name: 'skewAgreement',
    needs: ['spread'],
    run(context) {
      const spread = context.spread;
      const stars = starsFor(spread, [
        { stars: 4, limit: 0.2 },
        { stars: 3, limit: 0.5 },
        { stars: 2, limit: 1 },
        { stars: 1, limit: 2 },
      ]);
      return measurement(
        spread,
        stars,
        `the three skew measures spread over ${spread.toFixed(3)} deg`,
      );
    },
  });
}

/**
 * @param {number} value
 * @returns {number}
 */
function round(value) {
  return Math.round(value * 10) / 10;
}
