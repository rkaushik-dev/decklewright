import { describe, expect, it } from 'vitest';

import {
  DECISIONS,
  HOUSE_POLICY,
  decide,
  describePolicy,
  keepGoing,
  policy,
  policyFor,
} from '../../src/pipeline/policy.js';
import { ProfileRegistry } from '../../src/profile/registry.js';

const registry = new ProfileRegistry();

/**
 * @param {object} [options]
 * @returns {Record<string, any>}
 */
function grading({ stars = 4, worst = null, missing = [], findings = [] } = {}) {
  return { stars, worst, missing, findings, passed: stars >= 3, wanted: 3 };
}

describe('policy', () => {
  it('is the house one when nothing is said', () => {
    expect(policy()).toEqual(HOUSE_POLICY);
  });

  it('takes a change without touching the rest', () => {
    const strict = policy({ minimumStars: 4 });
    expect(strict.minimumStars).toBe(4);
    expect(strict.overwrite).toBe(HOUSE_POLICY.overwrite);
  });

  it('is frozen, so a stage cannot change the rules mid run', () => {
    expect(Object.isFrozen(policy())).toBe(true);
  });

  it('copies the hold list rather than sharing it', () => {
    const holdOn = ['skew'];
    const rules = policy({ holdOn });
    holdOn.push('noise');
    expect(rules.holdOn).toEqual(['skew']);
  });

  it('refuses a star level outside the ladder', () => {
    expect(() => policy({ minimumStars: 5 })).toThrow(/zero to four/);
    expect(() => policy({ minimumStars: -1 })).toThrow(/zero to four/);
    expect(() => policy({ minimumStars: 2.5 })).toThrow(/whole number/);
  });

  it('refuses a negative warning count', () => {
    expect(() => policy({ maximumWarnings: -1 })).toThrow(/cannot be negative/);
  });

  it('allows a lab that delivers everything', () => {
    expect(policy({ minimumStars: 0 }).minimumStars).toBe(0);
  });
});

describe('policyFor', () => {
  it('takes the star level off the profile', () => {
    expect(policyFor(registry.get('quadrangles')).minimumStars).toBe(4);
    expect(policyFor(registry.get('microfilm')).minimumStars).toBe(2);
  });

  it('lets the caller overrule the profile', () => {
    expect(policyFor(registry.get('quadrangles'), { minimumStars: 2 }).minimumStars).toBe(2);
  });

  it('falls back to the house level for a profile with no quality section', () => {
    expect(policyFor({}).minimumStars).toBe(HOUSE_POLICY.minimumStars);
  });
});

describe('decide', () => {
  it('delivers work that reached the level', () => {
    const out = decide({ quality: grading({ stars: 4 }), policy: policy() });
    expect(out.decision).toBe('deliver');
    expect(out.folder).toBe('delivered');
    expect(out.reason).toBe('4 of 4 stars');
  });

  it('rejects work that did not', () => {
    const out = decide({ quality: grading({ stars: 1, worst: 'skew' }), policy: policy() });
    expect(out.decision).toBe('reject');
    expect(out.folder).toBe('rejected');
    expect(out.reason).toMatch(/held down by skew/);
  });

  it('delivers at exactly the level asked for', () => {
    expect(decide({ quality: grading({ stars: 3 }), policy: policy() }).decision).toBe('deliver');
  });

  it('holds work that was never graded', () => {
    const out = decide({ policy: policy() });
    expect(out.decision).toBe('hold');
    expect(out.reason).toBe('nothing was graded');
  });

  it('delivers ungraded work when the lab says so', () => {
    const out = decide({ policy: policy({ deliverUngraded: true }) });
    expect(out.decision).toBe('deliver');
  });

  it('holds rather than rejecting when a check could not run', () => {
    const out = decide({
      quality: grading({ stars: 0, missing: ['sampling'] }),
      policy: policy(),
    });
    expect(out.decision).toBe('hold');
    expect(out.reason).toMatch(/sampling could not be measured/);
  });

  it('holds when the detectors disagreed, whatever the level', () => {
    const out = decide({
      quality: grading({
        stars: 4,
        findings: [{ name: 'skewAgreement', ran: true, stars: 1 }],
      }),
      policy: policy(),
    });
    expect(out.decision).toBe('hold');
    expect(out.reason).toMatch(/somebody should look at this/);
  });

  it('does not hold on a check that could not run', () => {
    const out = decide({
      quality: grading({
        stars: 4,
        findings: [{ name: 'skewAgreement', ran: false, stars: 0 }],
      }),
      policy: policy(),
    });
    expect(out.decision).toBe('deliver');
  });

  it('holds work with more warnings than the lab allows', () => {
    const out = decide({
      quality: grading({ stars: 4 }),
      warnings: ['a', 'b', 'c', 'd'],
      policy: policy({ maximumWarnings: 3 }),
    });
    expect(out.decision).toBe('hold');
    expect(out.reason).toMatch(/4 warnings/);
  });

  it('delivers work at exactly the warning limit', () => {
    const out = decide({
      quality: grading({ stars: 4 }),
      warnings: ['a', 'b', 'c'],
      policy: policy({ maximumWarnings: 3 }),
    });
    expect(out.decision).toBe('deliver');
  });

  it('holds rather than replacing a delivery that is already there', () => {
    const out = decide({ quality: grading(), alreadyThere: true, policy: policy() });
    expect(out.decision).toBe('hold');
    expect(out.reason).toMatch(/already been delivered/);
  });

  it('replaces one when the lab said it may', () => {
    const out = decide({
      quality: grading(),
      alreadyThere: true,
      policy: policy({ overwrite: true }),
    });
    expect(out.decision).toBe('deliver');
  });

  it('rejects before it counts warnings, because bad work is bad either way', () => {
    const out = decide({
      quality: grading({ stars: 1, worst: 'noise' }),
      warnings: ['a', 'b', 'c', 'd', 'e'],
      policy: policy(),
    });
    expect(out.decision).toBe('reject');
  });

  it('only ever gives back a decision the caller knows', () => {
    for (const args of [
      { quality: grading({ stars: 4 }) },
      { quality: grading({ stars: 0 }) },
      {},
    ]) {
      expect(DECISIONS).toContain(decide({ ...args, policy: policy() }).decision);
    }
  });

  it('works with no policy passed at all', () => {
    expect(decide({ quality: grading({ stars: 4 }) }).decision).toBe('deliver');
  });
});

describe('keepGoing', () => {
  it('carries on past a failure by default', () => {
    expect(keepGoing(policy(), 3)).toBe(true);
  });

  it('stops when the lab asked it to', () => {
    expect(keepGoing(policy({ stopOnFailure: true }), 1)).toBe(false);
    expect(keepGoing(policy({ stopOnFailure: true }), 0)).toBe(true);
  });
});

describe('describePolicy', () => {
  it('says what the lab does, in words', () => {
    const said = describePolicy(policy()).join('\n');
    expect(said).toMatch(/delivers at 3 of 4 stars/);
    expect(said).toMatch(/holds ungraded work/);
    expect(said).toMatch(/carries on past a failure/);
  });

  it('says the other thing when the rules are the other way', () => {
    const said = describePolicy(
      policy({ deliverUngraded: true, overwrite: true, stopOnFailure: true, holdOn: [] }),
    ).join('\n');
    expect(said).toMatch(/delivers ungraded work/);
    expect(said).toMatch(/replaces a delivery/);
    expect(said).toMatch(/stops the batch/);
    expect(said).not.toMatch(/holds on /);
  });
});
