import { describe, expect, it } from 'vitest';

import { Angle } from '../../../src/geom/angle.js';
import { Sampling } from '../../../src/measure/resolution.js';
import { Tolerance } from '../../../src/measure/tolerance.js';
import { runCheck } from '../../../src/qc/check.js';
import {
  SAMPLING_LADDER,
  SKEW_LADDER,
  samplingCheck,
  samplingFloorCheck,
  skewAgreementCheck,
  skewCheck,
} from '../../../src/qc/checks/sampling.js';

/**
 * @param {object} [overrides]
 * @returns {Record<string, any>}
 */
function context(overrides = {}) {
  return {
    sampling: Sampling.of(600),
    skew: Angle.degrees(0.05),
    spread: 0.1,
    limits: {
      sampling: Sampling.of(600),
      samplingTolerance: Tolerance.percent(1),
      skewTolerance: 0.3,
    },
    ...overrides,
  };
}

describe('samplingCheck', () => {
  it('gives four stars for an exact match', () => {
    const out = runCheck(samplingCheck(), context());
    expect(out.stars).toBe(4);
    expect(out.passed).toBe(true);
    expect(out.note).toMatch(/600 ppi against 600, off by 0.00 percent/);
  });

  it('drops a star as the capture drifts', () => {
    expect(runCheck(samplingCheck(), context({ sampling: Sampling.of(604) })).stars).toBe(3);
    expect(runCheck(samplingCheck(), context({ sampling: Sampling.of(597) })).stars).toBe(4);
  });

  it('gives no stars once the profile tolerance is broken', () => {
    const out = runCheck(samplingCheck(), context({ sampling: Sampling.of(660) }));
    expect(out.stars).toBe(0);
    expect(out.passed).toBe(false);
  });

  it('is symmetric, since too fine is as wrong as too coarse against a target', () => {
    const high = runCheck(samplingCheck(), context({ sampling: Sampling.of(606) })).stars;
    const low = runCheck(samplingCheck(), context({ sampling: Sampling.of(594) })).stars;
    expect(high).toBe(low);
  });

  it('says it could not run when there is no frequency', () => {
    const out = runCheck(samplingCheck(), context({ sampling: null }));
    expect(out.ran).toBe(false);
    expect(out.note).toMatch(/no sampling/);
  });
});

describe('samplingFloorCheck', () => {
  it('gives full stars for anything at or above the target', () => {
    expect(runCheck(samplingFloorCheck(), context()).stars).toBe(4);
    expect(runCheck(samplingFloorCheck(), context({ sampling: Sampling.of(1200) })).stars).toBe(4);
  });

  it('drops stars only when the capture is short', () => {
    expect(runCheck(samplingFloorCheck(), context({ sampling: Sampling.of(594) })).stars).toBe(3);
    expect(runCheck(samplingFloorCheck(), context({ sampling: Sampling.of(300) })).stars).toBe(0);
  });

  it('says which way it was wrong', () => {
    expect(runCheck(samplingFloorCheck(), context()).note).toMatch(/at or above/);
    expect(runCheck(samplingFloorCheck(), context({ sampling: Sampling.of(500) })).note).toMatch(
      /short of 600/,
    );
  });
});

describe('skewCheck', () => {
  it('gives four stars for a sheet that is square', () => {
    const out = runCheck(skewCheck(), context());
    expect(out.stars).toBe(4);
    expect(out.note).toMatch(/0.050 deg off square against a limit of 0.3/);
  });

  it('reads the size of the turn, not its direction', () => {
    const left = runCheck(skewCheck(), context({ skew: Angle.degrees(-0.2) })).stars;
    const right = runCheck(skewCheck(), context({ skew: Angle.degrees(0.2) })).stars;
    expect(left).toBe(right);
    expect(left).toBe(3);
  });

  it('gives no stars past the profile limit', () => {
    expect(runCheck(skewCheck(), context({ skew: Angle.degrees(0.4) })).stars).toBe(0);
  });

  it('takes a looser limit from a looser profile', () => {
    const loose = context({
      skew: Angle.degrees(0.4),
      limits: { ...context().limits, skewTolerance: 0.6 },
    });
    expect(runCheck(skewCheck(), loose).stars).toBe(2);
  });
});

describe('skewAgreementCheck', () => {
  it('grades how sure the detector was', () => {
    expect(runCheck(skewAgreementCheck(), context({ spread: 0.05 })).stars).toBe(4);
    expect(runCheck(skewAgreementCheck(), context({ spread: 0.4 })).stars).toBe(3);
    expect(runCheck(skewAgreementCheck(), context({ spread: 5 })).stars).toBe(0);
  });

  it('needs only the spread', () => {
    expect(runCheck(skewAgreementCheck(), { spread: 0.1 }).ran).toBe(true);
  });
});

describe('the ladders', () => {
  it('are frozen and run best first', () => {
    for (const ladder of [SAMPLING_LADDER, SKEW_LADDER]) {
      expect(Object.isFrozen(ladder)).toBe(true);
      const stars = ladder.map((rung) => rung.stars);
      expect([...stars].sort((a, b) => b - a)).toEqual(stars);
      const limits = ladder.map((rung) => rung.limit);
      expect([...limits].sort((a, b) => a - b)).toEqual(limits);
    }
  });
});
