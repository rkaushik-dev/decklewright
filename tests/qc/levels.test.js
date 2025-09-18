import { describe, expect, it } from 'vitest';

import {
  LEVEL_NAMES,
  batchLevel,
  levelName,
  levelOf,
  levelWithRequired,
  meetsLevel,
  shortfall,
  tallyLevels,
} from '../../src/qc/levels.js';

/**
 * @param {string} name
 * @param {number} stars
 * @param {boolean} [ran]
 * @returns {import('../../src/qc/check.js').Finding}
 */
function finding(name, stars, ran = true) {
  return {
    name,
    ran,
    passed: stars >= 3,
    measured: stars,
    stars,
    note: '',
    limit: '3 stars',
  };
}

describe('levelOf', () => {
  it('is the worst check, not the average', () => {
    const out = levelOf([finding('a', 4), finding('b', 4), finding('c', 4), finding('d', 1)]);
    expect(out.stars).toBe(1);
    expect(out.worst).toBe('d');
  });

  it('names the first check at the worst level', () => {
    expect(levelOf([finding('a', 2), finding('b', 2)]).worst).toBe('a');
  });

  it('counts what ran and what did not', () => {
    const out = levelOf([finding('a', 4), finding('b', 0, false)]);
    expect(out.ran).toBe(1);
    expect(out.skipped).toBe(1);
    expect(out.stars).toBe(4);
  });

  it('has no level when nothing ran', () => {
    const out = levelOf([finding('a', 0, false)]);
    expect(out.stars).toBe(0);
    expect(out.worst).toBeNull();
  });

  it('has no level for an empty set', () => {
    expect(levelOf([]).stars).toBe(0);
  });
});

describe('levelWithRequired', () => {
  it('is the plain level when every required check ran', () => {
    const out = levelWithRequired([finding('sampling', 4), finding('skew', 3)], ['sampling']);
    expect(out.stars).toBe(3);
    expect(out.missing).toEqual([]);
  });

  it('drops to nothing when a required check could not run', () => {
    const out = levelWithRequired(
      [finding('sampling', 4), finding('grayBalance', 0, false)],
      ['sampling', 'grayBalance'],
    );
    expect(out.stars).toBe(0);
    expect(out.missing).toEqual(['grayBalance']);
    expect(out.worst).toBe('grayBalance');
  });

  it('drops to nothing when a required check is not there at all', () => {
    expect(levelWithRequired([finding('sampling', 4)], ['noise']).missing).toEqual(['noise']);
  });

  it('ignores a check that did not run and was not required', () => {
    const out = levelWithRequired([finding('a', 4), finding('b', 0, false)], ['a']);
    expect(out.stars).toBe(4);
  });
});

describe('levelName', () => {
  it('names every level', () => {
    expect(levelName(4)).toBe('four stars');
    expect(levelName(1)).toBe('one star');
    expect(levelName(0)).toBe('below the lowest level');
    expect(levelName(9)).toBe('unknown');
    expect(Object.isFrozen(LEVEL_NAMES)).toBe(true);
  });
});

describe('meetsLevel', () => {
  it('compares the worst check against the floor', () => {
    const findings = [finding('a', 4), finding('b', 3)];
    expect(meetsLevel(findings, 3)).toBe(true);
    expect(meetsLevel(findings, 4)).toBe(false);
  });
});

describe('shortfall', () => {
  it('names the checks holding the level down, worst first', () => {
    const out = shortfall([finding('a', 4), finding('b', 1), finding('c', 2)], 3);
    expect(out.map((s) => s.name)).toEqual(['b', 'c']);
    expect(out[0].short).toBe(2);
  });

  it('says nothing when everything reaches the level', () => {
    expect(shortfall([finding('a', 4)], 3)).toEqual([]);
  });

  it('leaves out checks that did not run', () => {
    expect(shortfall([finding('a', 0, false)], 3)).toEqual([]);
  });

  it('breaks a tie by name so a report is stable', () => {
    const out = shortfall([finding('z', 1), finding('a', 1)], 3);
    expect(out.map((s) => s.name)).toEqual(['a', 'z']);
  });
});

describe('tallyLevels', () => {
  it('counts a batch by level', () => {
    expect(tallyLevels([{ stars: 4 }, { stars: 4 }, { stars: 2 }, { stars: 0 }])).toEqual({
      0: 1,
      1: 0,
      2: 1,
      3: 0,
      4: 2,
    });
  });

  it('holds a level outside the range at the nearest end', () => {
    expect(tallyLevels([{ stars: 9 }, { stars: -1 }])).toEqual({ 0: 1, 1: 0, 2: 0, 3: 0, 4: 1 });
  });

  it('counts nothing for an empty batch', () => {
    expect(tallyLevels([])).toEqual({ 0: 0, 1: 0, 2: 0, 3: 0, 4: 0 });
  });
});

describe('batchLevel', () => {
  it('is the worst item in the batch', () => {
    expect(batchLevel([{ stars: 4 }, { stars: 3 }, { stars: 4 }])).toBe(3);
  });

  it('is nothing for an empty batch', () => {
    expect(batchLevel([])).toBe(0);
  });
});
