import { PipelineError } from '../errors.js';

/**
 * What the lab does with a result.
 *
 * The grading says how good a capture is. The policy says what a lab does
 * about it, and those are different questions with different answers in
 * different rooms. A digitisation grant with a deadline delivers three star
 * work and flags it. A programme rephotographing the only surviving copy of a
 * plat book does not, ever.
 *
 * Every decision here is reversible by a person. Nothing is deleted, a
 * rejection is a folder in another place, and the reason is written down in
 * words rather than as a code, because the person who reads it will not have
 * the code list.
 */

/** What can be done with a finished job. */
export const DECISIONS = Object.freeze(['deliver', 'reject', 'hold']);

/**
 * @typedef {object} Policy
 * @property {number} minimumStars below this it does not go out
 * @property {boolean} deliverUngraded whether a run with no grading may deliver
 * @property {boolean} overwrite replace a delivery that is already there
 * @property {number} maximumWarnings above this it is held for a person
 * @property {boolean} stopOnFailure whether one bad sheet stops the batch
 * @property {string[]} holdOn checks that send it to a person whatever the stars
 */

/** The one a lab gets if it says nothing. */
export const HOUSE_POLICY = Object.freeze({
  minimumStars: 3,
  deliverUngraded: false,
  overwrite: false,
  maximumWarnings: 3,
  stopOnFailure: false,
  holdOn: ['skewAgreement'],
});

/**
 * @param {Partial<Policy>} [changes]
 * @returns {Policy}
 */
export function policy(changes = {}) {
  const out = { ...HOUSE_POLICY, ...changes, holdOn: [...(changes.holdOn ?? HOUSE_POLICY.holdOn)] };

  if (!Number.isInteger(out.minimumStars) || out.minimumStars < 0 || out.minimumStars > 4) {
    throw new PipelineError('a star level is a whole number from zero to four', {
      minimumStars: out.minimumStars,
    });
  }
  if (!Number.isInteger(out.maximumWarnings) || out.maximumWarnings < 0) {
    throw new PipelineError('a warning count cannot be negative', {
      maximumWarnings: out.maximumWarnings,
    });
  }
  return Object.freeze(out);
}

/**
 * The policy a profile implies, so a collection that asks for four stars in its
 * quality section does not have to say it twice.
 *
 * @param {Record<string, any>} profile
 * @param {Partial<Policy>} [changes]
 * @returns {Policy}
 */
export function policyFor(profile, changes = {}) {
  return policy({
    minimumStars: profile?.qc?.minimumStars ?? HOUSE_POLICY.minimumStars,
    ...changes,
  });
}

/**
 * @typedef {object} Decision
 * @property {string} decision one of DECISIONS
 * @property {string} reason in words, for the ledger and for a person
 * @property {string} folder where the delivery goes
 */

/**
 * What to do with a finished job.
 *
 * Held is not a third kind of failure. It means the machine will not decide
 * and a person has to look, and the material stays in working until they do.
 * The cases that reach it are the ones where the measurements disagree with
 * each other rather than the ones where they agree that something is bad.
 *
 * @param {object} args
 * @param {Record<string, any>} [args.quality] what the grading gave back
 * @param {readonly string[]} [args.warnings]
 * @param {boolean} [args.alreadyThere]
 * @param {Policy} [args.policy]
 * @returns {Decision}
 */
export function decide({ quality, warnings = [], alreadyThere = false, policy: rules }) {
  const held = rules ?? HOUSE_POLICY;

  if (alreadyThere && !held.overwrite) {
    return {
      decision: 'hold',
      reason: 'this accession has already been delivered',
      folder: 'working',
    };
  }

  if (!quality) {
    return held.deliverUngraded
      ? {
          decision: 'deliver',
          reason: 'nothing was graded and the policy allows it',
          folder: 'delivered',
        }
      : { decision: 'hold', reason: 'nothing was graded', folder: 'working' };
  }

  if (quality.missing && quality.missing.length > 0) {
    return {
      decision: 'hold',
      reason: `${quality.missing.join(' and ')} could not be measured`,
      folder: 'working',
    };
  }

  const disputed = held.holdOn.filter((name) => {
    const finding = (quality.findings ?? []).find((one) => one.name === name);
    return finding && finding.ran && finding.stars < held.minimumStars;
  });
  if (disputed.length > 0) {
    return {
      decision: 'hold',
      reason: `${disputed.join(' and ')} says somebody should look at this`,
      folder: 'working',
    };
  }

  if (quality.stars < held.minimumStars) {
    return {
      decision: 'reject',
      reason: `${quality.stars} of ${held.minimumStars} stars, held down by ${quality.worst ?? 'nothing named'}`,
      folder: 'rejected',
    };
  }

  if (warnings.length > held.maximumWarnings) {
    return {
      decision: 'hold',
      reason: `${warnings.length} warnings, more than the ${held.maximumWarnings} this lab allows`,
      folder: 'working',
    };
  }

  return {
    decision: 'deliver',
    reason: `${quality.stars} of 4 stars`,
    folder: 'delivered',
  };
}

/**
 * Whether a batch carries on after a job failed.
 *
 * @param {Policy} rules
 * @param {number} failedSoFar
 * @returns {boolean}
 */
export function keepGoing(rules, failedSoFar) {
  return !rules.stopOnFailure || failedSoFar === 0;
}

/**
 * @param {Policy} rules
 * @returns {string[]}
 */
export function describePolicy(rules) {
  const lines = [`delivers at ${rules.minimumStars} of 4 stars and above`];
  lines.push(rules.deliverUngraded ? 'delivers ungraded work' : 'holds ungraded work');
  lines.push(rules.overwrite ? 'replaces a delivery that is there' : 'holds rather than replacing');
  lines.push(`holds anything with more than ${rules.maximumWarnings} warnings`);
  if (rules.holdOn.length > 0) lines.push(`holds on ${rules.holdOn.join(', ')}`);
  lines.push(rules.stopOnFailure ? 'stops the batch on a failure' : 'carries on past a failure');
  return lines;
}
