import { QcError } from '../errors.js';
import { assertNonEmptyString } from '../util/assert.js';

/**
 * What a quality check is.
 *
 * A check reads a capture and a set of limits and says whether it passed, what
 * it measured, and at what star level. The star level matters: the funder
 * grades a collection by the worst check across it, so a check that only says
 * yes or no cannot answer the question anybody actually asks.
 *
 * A check may also say it could not run, which is not the same as failing. A
 * gray balance check on a capture with no colour target has nothing to measure
 * and saying it failed would be a lie.
 */

/** @typedef {{measured: number, stars: number, note: string}} Measurement */
/** @typedef {{name: string, ran: boolean, passed: boolean, measured: number, stars: number, note: string, limit: string}} Finding */
/** @typedef {{name: string, needs: string[], run: (context: Record<string, any>) => Measurement}} Check */

/** The highest star level any check awards. */
export const MAX_STARS = 4;

/**
 * @param {object} args
 * @param {string} args.name
 * @param {readonly string[]} [args.needs] what has to be in the context
 * @param {(context: Record<string, any>) => Measurement} args.run
 * @returns {Check}
 */
export function check({ name, needs = [], run }) {
  assertNonEmptyString(name, 'name');
  if (typeof run !== 'function') {
    throw new QcError(`the ${name} check has nothing to run`, { name });
  }
  return Object.freeze({ name, needs: Object.freeze([...needs]), run });
}

/**
 * @param {Record<string, any>} context
 * @param {readonly string[]} needs
 * @returns {string[]} what was missing
 */
export function missingFrom(context, needs) {
  return needs.filter((key) => context[key] === undefined || context[key] === null);
}

/**
 * Run one check, catching the two ways it can decline: something it needed was
 * not there, or it threw. Either way the run carries on, because a batch that
 * stops on the first unmeasurable capture is a batch nobody finishes.
 *
 * @param {Check} item
 * @param {Record<string, any>} context
 * @param {object} [options]
 * @param {number} [options.minimumStars]
 * @returns {Finding}
 */
export function runCheck(item, context, { minimumStars = 3 } = {}) {
  const missing = missingFrom(context, item.needs);
  if (missing.length > 0) {
    return {
      name: item.name,
      ran: false,
      passed: false,
      measured: Number.NaN,
      stars: 0,
      note: `nothing to measure, no ${missing.join(' or ')}`,
      limit: '',
    };
  }

  try {
    const out = item.run(context);
    return {
      name: item.name,
      ran: true,
      passed: out.stars >= minimumStars,
      measured: out.measured,
      stars: out.stars,
      note: out.note,
      limit: `${minimumStars} stars`,
    };
  } catch (error) {
    return {
      name: item.name,
      ran: false,
      passed: false,
      measured: Number.NaN,
      stars: 0,
      note: error instanceof Error ? error.message : String(error),
      limit: '',
    };
  }
}

/**
 * @param {readonly Check[]} checks
 * @param {Record<string, any>} context
 * @param {object} [options]
 * @param {number} [options.minimumStars]
 * @returns {Finding[]}
 */
export function runChecks(checks, context, options) {
  return checks.map((item) => runCheck(item, context, options));
}

/**
 * @param {number} measured
 * @param {number} stars
 * @param {string} note
 * @returns {Measurement}
 */
export function measurement(measured, stars, note) {
  return { measured, stars, note };
}

/**
 * Turn a measurement into stars against a ladder of limits, best first. The
 * ladder is written the way the guidelines write it, so a reader can check one
 * against the other.
 *
 * @param {number} measured
 * @param {readonly {stars: number, limit: number}[]} ladder best first
 * @param {object} [options]
 * @param {boolean} [options.higherIsBetter]
 * @returns {number}
 */
export function starsFor(measured, ladder, { higherIsBetter = false } = {}) {
  if (!Number.isFinite(measured)) return 0;
  for (const rung of ladder) {
    const met = higherIsBetter ? measured >= rung.limit : measured <= rung.limit;
    if (met) return rung.stars;
  }
  return 0;
}

/**
 * @param {readonly Check[]} checks
 * @returns {void}
 */
export function assertNoDuplicates(checks) {
  const seen = new Set();
  for (const item of checks) {
    if (seen.has(item.name)) {
      throw new QcError(`the ${item.name} check is in the set twice`, { name: item.name });
    }
    seen.add(item.name);
  }
}
