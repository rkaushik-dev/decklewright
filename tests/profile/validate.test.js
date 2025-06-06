import { describe, expect, it } from 'vitest';

import { parseProfile } from '../../src/profile/fields.js';
import {
  assertUsable,
  describeProblems,
  fault,
  faultsOnly,
  inspectDerivatives,
  inspectProfile,
  note,
} from '../../src/profile/validate.js';

/**
 * @param {Record<string, unknown>} written
 * @returns {Record<string, any>}
 */
function profile(written) {
  return parseProfile({
    name: 'atlas',
    derivatives: [{ name: 'master' }, { name: 'service', longestSide: 3000 }],
    ...written,
  });
}

/**
 * @param {readonly {path: string}[]} problems
 * @returns {string[]}
 */
function paths(problems) {
  return problems.map((problem) => problem.path);
}

describe('inspectProfile', () => {
  it('has nothing to say about a sensible profile', () => {
    expect(inspectProfile(profile({}))).toEqual([]);
  });

  it('catches a neatline crop the capture never looks for', () => {
    const problems = inspectProfile(profile({ crop: { anchor: 'neatline' } }));
    expect(faultsOnly(problems)).toHaveLength(1);
    expect(paths(problems)).toContain('crop.anchor');
  });

  it('accepts the pair when both are set', () => {
    const problems = inspectProfile(
      profile({ crop: { anchor: 'neatline' }, capture: { expectNeatline: true } }),
    );
    expect(problems).toEqual([]);
  });

  it('notes a neatline the crop then ignores', () => {
    const problems = inspectProfile(profile({ capture: { expectNeatline: true } }));
    expect(faultsOnly(problems)).toHaveLength(0);
    expect(paths(problems)).toContain('capture.expectNeatline');
  });

  it('catches a crop that demands more than the capture has', () => {
    const problems = inspectProfile(
      profile({ crop: { minimumPpi: 800 }, qc: { samplingPpi: 400 } }),
    );
    expect(faultsOnly(problems)).toHaveLength(1);
    expect(problems[0].message).toMatch(/demands 800 ppi from a capture taken at 400/);
  });

  it('catches a negative bleed or safe area', () => {
    const problems = inspectProfile(
      profile({ crop: { bleed: { top: '-3mm' }, safeArea: { left: '-1mm' } } }),
    );
    expect(paths(faultsOnly(problems))).toEqual(['crop.bleed.top', 'crop.safeArea.left']);
  });

  it('notes a bleed and a safe area set on the same edge', () => {
    const problems = inspectProfile(
      profile({ crop: { bleed: { top: '3mm' }, safeArea: { top: '1mm' } } }),
    );
    expect(faultsOnly(problems)).toHaveLength(0);
    expect(paths(problems)).toContain('crop.safeArea.top');
  });

  it('notes a profile that writes nothing', () => {
    const problems = inspectProfile(parseProfile({ name: 'atlas' }));
    expect(paths(problems)).toContain('derivatives');
    expect(faultsOnly(problems)).toHaveLength(0);
  });

  it('notes a star level the clipping limit cannot reach', () => {
    const problems = inspectProfile(profile({ qc: { minimumStars: 4, maximumClipping: 0.01 } }));
    expect(paths(problems)).toContain('qc.maximumClipping');
  });
});

describe('inspectDerivatives', () => {
  it('catches a repeated name', () => {
    const problems = inspectDerivatives(
      parseProfile({
        name: 'a',
        derivatives: [{ name: 'service', longestSide: 100 }, { name: 'service' }],
      }).derivatives,
    );
    expect(faultsOnly(problems)).toHaveLength(1);
    expect(problems[0].message).toMatch(/repeats the name service/);
  });

  it('catches a derivative that sets both a side and a scale', () => {
    const problems = inspectDerivatives(
      parseProfile({
        name: 'a',
        derivatives: [{ name: 'service', longestSide: 100, scale: 0.5 }],
      }).derivatives,
    );
    expect(faultsOnly(problems)).toHaveLength(1);
    expect(problems[0].path).toBe('derivatives[0]');
  });

  it('notes two derivatives at full size', () => {
    const problems = inspectDerivatives(
      parseProfile({ name: 'a', derivatives: [{ name: 'a' }, { name: 'b' }] }).derivatives,
    );
    expect(paths(problems)).toContain('derivatives');
  });

  it('notes metadata asked for on a format that cannot carry it', () => {
    const problems = inspectDerivatives(
      parseProfile({
        name: 'a',
        derivatives: [{ name: 'a', format: 'pnm', keepMetadata: true }],
      }).derivatives,
    );
    expect(paths(problems)).toContain('derivatives[0].keepMetadata');
  });

  it('notes a very large netpbm', () => {
    const problems = inspectDerivatives(
      parseProfile({
        name: 'a',
        derivatives: [{ name: 'a', format: 'pnm', depth: '16', layout: 'rgb' }],
      }).derivatives,
    );
    expect(paths(problems)).toContain('derivatives[0].format');
  });

  it('has nothing to say about a clean list', () => {
    expect(
      inspectDerivatives(
        parseProfile({
          name: 'a',
          derivatives: [{ name: 'master' }, { name: 'thumb', longestSide: 400 }],
        }).derivatives,
      ),
    ).toEqual([]);
  });
});

describe('assertUsable', () => {
  it('passes a sensible profile through', () => {
    const good = profile({});
    expect(assertUsable(good)).toBe(good);
  });

  it('throws on the first fault and lists them all', () => {
    try {
      assertUsable(profile({ crop: { anchor: 'neatline', minimumPpi: 900 } }));
      throw new Error('should have thrown');
    } catch (err) {
      expect(err.message).toMatch(/^the atlas profile cannot be used:/);
      expect(err.details.faults).toHaveLength(2);
      expect(err.details.name).toBe('atlas');
    }
  });

  it('lets a profile with only notes through', () => {
    const noted = profile({ capture: { expectNeatline: true } });
    expect(assertUsable(noted)).toBe(noted);
  });
});

describe('the helpers', () => {
  it('builds and filters problems', () => {
    const problems = [fault('a', 'one'), note('b', 'two')];
    expect(faultsOnly(problems)).toHaveLength(1);
    expect(describeProblems(problems)).toEqual(['fault: a: one', 'note: b: two']);
  });
});
