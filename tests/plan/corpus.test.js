import { describe, expect, it } from 'vitest';

import { CASES, describeResults, runCase, runCorpus, tally } from '../../src/plan/corpus.js';
import { ProfileRegistry, registryWith } from '../../src/profile/registry.js';

const registry = new ProfileRegistry();

describe('the corpus itself', () => {
  it('covers every shipped profile', () => {
    const covered = new Set(CASES.map((item) => item.profile));
    for (const name of registry.names()) {
      if (name === 'house') continue;
      expect(covered.has(name), name).toBe(true);
    }
  });

  it('is frozen and every case has a name', () => {
    expect(Object.isFrozen(CASES)).toBe(true);
    expect(new Set(CASES.map((c) => c.name)).size).toBe(CASES.length);
  });

  it('holds a case for each thing that has gone wrong', () => {
    const names = CASES.map((c) => c.name).join(' ');
    expect(names).toMatch(/neatline/);
    expect(names).toMatch(/edge of the bed/);
    expect(names).toMatch(/under the floor/);
  });
});

describe('runCorpus', () => {
  it('reproduces every recorded answer', () => {
    const results = runCorpus(registry);
    const changed = results.filter((r) => !r.matched);
    expect(describeResults(changed)).toEqual([]);
  });

  it('tallies what it found', () => {
    expect(tally(runCorpus(registry))).toEqual({
      total: CASES.length,
      matched: CASES.length,
      changed: 0,
      failed: 0,
    });
  });

  it('notices when a profile changes underneath it', () => {
    const changed = registryWith([{ name: 'house', crop: { bleed: '9mm' } }]);
    const results = runCorpus(changed);
    expect(tally(results).changed).toBeGreaterThan(0);
  });

  it('reports a case that could not be planned rather than throwing', () => {
    const broken = registryWith([
      { name: 'microfilm', crop: { anchor: 'neatline' }, capture: { expectNeatline: true } },
    ]);
    const results = runCorpus(broken);
    const film = results.find((r) => r.name.includes('microfilm'));
    expect(film?.error).toMatch(/none was found/);
    expect(tally(results).failed).toBe(1);
  });

  it('takes a set of cases of its own', () => {
    const results = runCorpus(registry, [CASES[0]]);
    expect(results).toHaveLength(1);
    expect(results[0].matched).toBe(true);
  });
});

describe('runCase', () => {
  it('gives back a plan with a trail', () => {
    const plan = runCase(CASES[1], registry);
    expect(plan.trail).toContain('bleed');
    expect(plan.rect.width).toBe(CASES[1].expected.width);
  });

  it('warns on the case that was recorded as warning', () => {
    const edge = CASES.find((c) => c.name.includes('edge of the bed'));
    expect(runCase(/** @type {any} */ (edge), registry).warnings).toHaveLength(1);
  });
});

describe('describeResults', () => {
  it('says a case is as recorded when it is', () => {
    const lines = describeResults(runCorpus(registry, [CASES[0]]));
    expect(lines[0]).toMatch(/as recorded$/);
  });

  it('shows both crops when a case changed', () => {
    const changed = registryWith([{ name: 'house', crop: { bleed: '9mm' } }]);
    const lines = describeResults(runCorpus(changed, [CASES[0]]));
    expect(lines[0]).toMatch(/was 6800x5600\+700\+600, now /);
  });

  it('says what went wrong when a case failed', () => {
    const broken = registryWith([
      { name: 'house', crop: { anchor: 'neatline' }, capture: { expectNeatline: true } },
    ]);
    expect(describeResults(runCorpus(broken, [CASES[0]]))[0]).toMatch(/failed, /);
  });
});
