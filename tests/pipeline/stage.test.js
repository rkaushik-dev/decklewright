import { describe, expect, it } from 'vitest';

import { Job, missingFrom } from '../../src/pipeline/job.js';
import {
  assertDistinct,
  describeResult,
  insertBefore,
  runStage,
  stage,
  without,
} from '../../src/pipeline/stage.js';

const READ = stage({
  name: 'read',
  produces: ['surface'],
  run: () => ({ surface: { width: 10 } }),
});

const PLAN = stage({
  name: 'plan',
  needs: ['surface'],
  produces: ['plan'],
  run: (job) => ({ plan: { width: job.need('surface').width } }),
});

const GRADE = stage({
  name: 'grade',
  needs: ['surface', 'target'],
  produces: ['stars'],
  optional: true,
  run: () => ({ stars: 4 }),
});

describe('Job', () => {
  it('starts with an accession and nothing else', () => {
    const job = Job.start('g4181');
    expect(job.accession).toBe('g4181');
    expect(job.values).toEqual({});
    expect(job.done).toEqual([]);
  });

  it('refuses a job about nothing', () => {
    expect(() => Job.start('')).toThrow(/has to be about something/);
  });

  it('gives back a new job rather than changing itself', () => {
    const job = Job.start('a');
    const later = job.with({ surface: 1 });
    expect(job.has('surface')).toBe(false);
    expect(later.has('surface')).toBe(true);
  });

  it('is frozen, so a stage cannot reach into it', () => {
    const job = Job.start('a', { surface: 1 });
    expect(Object.isFrozen(job)).toBe(true);
    expect(Object.isFrozen(job.values)).toBe(true);
  });

  it('refuses two stages writing the same value', () => {
    expect(() => Job.start('a', { plan: 1 }).with({ plan: 2 })).toThrow(/already put that/);
  });

  it('allows a deliberate replacement', () => {
    expect(Job.start('a', { plan: 1 }).with({ plan: 2 }, { replace: true }).get('plan')).toBe(2);
  });

  it('says which value a stage asked for and did not get', () => {
    expect(() => Job.start('a').need('surface')).toThrow(/does not carry that yet/);
  });

  it('treats a value set to undefined as absent', () => {
    expect(Job.start('a', { surface: undefined }).has('surface')).toBe(false);
  });

  it('records the stages that ran, in order', () => {
    const job = Job.start('a').finished('read').finished('plan');
    expect(job.done).toEqual(['read', 'plan']);
  });

  it('refuses to run the same stage twice', () => {
    expect(() => Job.start('a').finished('read').finished('read')).toThrow(/already run/);
  });

  it('keeps notes and warnings apart', () => {
    const job = Job.start('a').noted('skipped grading').warned('the sheet touches the edge');
    expect(job.notes).toEqual(['skipped grading']);
    expect(job.warnings).toEqual(['the sheet touches the edge']);
  });

  it('adds a list of warnings at once, and nothing for an empty one', () => {
    const job = Job.start('a').warnedAll(['one', 'two']);
    expect(job.warnings).toEqual(['one', 'two']);
    expect(job.warnedAll([])).toBe(job);
  });

  it('names what it carries without printing it', () => {
    const job = Job.start('a', { surface: { huge: true }, plan: {} }).finished('read');
    expect(String(job)).toBe('a: after 1 stages, carrying plan, surface');
  });
});

describe('missingFrom', () => {
  it('names what a job does not carry', () => {
    expect(missingFrom(Job.start('a', { surface: 1 }), ['surface', 'plan'])).toEqual(['plan']);
  });
});

describe('stage', () => {
  it('freezes what it was given', () => {
    expect(Object.isFrozen(READ)).toBe(true);
    expect(Object.isFrozen(READ.produces)).toBe(true);
  });

  it('refuses one with no name', () => {
    expect(() => stage({ name: '', run: () => ({}) })).toThrow(/has to have a name/);
  });

  it('refuses one that does nothing', () => {
    expect(() => stage({ name: 'x' })).toThrow(/has to do something/);
  });
});

describe('runStage', () => {
  it('runs a stage and puts what it made on the job', async () => {
    const out = await runStage(READ, Job.start('a'));
    expect(out.ok).toBe(true);
    expect(out.ran).toBe(true);
    expect(out.job.get('surface')).toEqual({ width: 10 });
  });

  it('records that the stage ran', async () => {
    const out = await runStage(READ, Job.start('a'));
    expect(out.job.done).toEqual(['read']);
  });

  it('gives a stage what earlier ones produced', async () => {
    const first = await runStage(READ, Job.start('a'));
    const second = await runStage(PLAN, first.job);
    expect(second.job.get('plan')).toEqual({ width: 10 });
  });

  it('fails a required stage that was reached without what it needs', async () => {
    const out = await runStage(PLAN, Job.start('a'));
    expect(out.ok).toBe(false);
    expect(out.missing).toEqual(['surface']);
    expect(out.error?.message).toMatch(/without what it needs/);
  });

  it('skips an optional one and says so', async () => {
    const first = await runStage(READ, Job.start('a'));
    const out = await runStage(GRADE, first.job);
    expect(out.ok).toBe(true);
    expect(out.ran).toBe(false);
    expect(out.missing).toEqual(['target']);
    expect(out.job.notes[0]).toMatch(/grade was skipped, no target/);
  });

  it('leaves the job alone when a stage fails', async () => {
    const job = Job.start('a');
    const out = await runStage(PLAN, job);
    expect(out.job).toBe(job);
  });

  it('catches what a stage threw and says which stage it was', async () => {
    const broken = stage({
      name: 'broken',
      run: () => {
        throw new Error('the lamp went out');
      },
    });
    const out = await runStage(broken, Job.start('a'));
    expect(out.ok).toBe(false);
    expect(out.error?.message).toMatch(/the lamp went out/);
  });

  it('fails a stage that did not produce what it promised', async () => {
    const liar = stage({ name: 'liar', produces: ['plan'], run: () => ({}) });
    const out = await runStage(liar, Job.start('a'));
    expect(out.error?.message).toMatch(/did not produce what it said/);
  });

  it('allows a stage that promises nothing to return nothing', async () => {
    const quiet = stage({ name: 'quiet', run: () => undefined });
    expect((await runStage(quiet, Job.start('a'))).ok).toBe(true);
  });

  it('waits for a stage that takes its time', async () => {
    const slow = stage({
      name: 'slow',
      produces: ['x'],
      run: async () => {
        await Promise.resolve();
        return { x: 1 };
      },
    });
    expect((await runStage(slow, Job.start('a'))).job.get('x')).toBe(1);
  });

  it('passes the context to every stage', async () => {
    const reader = stage({
      name: 'reader',
      produces: ['seen'],
      run: (job, context) => ({ seen: context.profile }),
    });
    const out = await runStage(reader, Job.start('a'), { profile: 'house' });
    expect(out.job.get('seen')).toBe('house');
  });

  it('times what it ran', async () => {
    expect((await runStage(READ, Job.start('a'))).ms).toBeGreaterThanOrEqual(0);
  });
});

describe('assertDistinct', () => {
  it('is happy with distinct names', () => {
    expect(() => assertDistinct([READ, PLAN])).not.toThrow();
  });

  it('refuses the same name twice', () => {
    expect(() => assertDistinct([READ, READ])).toThrow(/same name/);
  });
});

describe('without', () => {
  it('drops the ones it was told to', () => {
    expect(without([READ, PLAN, GRADE], ['grade']).map((s) => s.name)).toEqual(['read', 'plan']);
  });

  it('ignores a name that is not there', () => {
    expect(without([READ], ['nothing'])).toHaveLength(1);
  });
});

describe('insertBefore', () => {
  it('puts a stage in front of another', () => {
    const extra = stage({ name: 'extra', run: () => ({}) });
    expect(insertBefore([READ, PLAN], 'plan', extra).map((s) => s.name)).toEqual([
      'read',
      'extra',
      'plan',
    ]);
  });

  it('refuses a name that is not in the list', () => {
    const extra = stage({ name: 'extra', run: () => ({}) });
    expect(() => insertBefore([READ], 'nowhere', extra)).toThrow(/no stage by that name/);
  });
});

describe('describeResult', () => {
  it('says a stage ran and how long it took', async () => {
    expect(describeResult(await runStage(READ, Job.start('a')))).toMatch(/^read ran in \d+ ms$/);
  });

  it('says why one was skipped', async () => {
    const first = await runStage(READ, Job.start('a'));
    expect(describeResult(await runStage(GRADE, first.job))).toBe('grade was skipped, no target');
  });

  it('says why one failed', async () => {
    expect(describeResult(await runStage(PLAN, Job.start('a')))).toMatch(/^plan failed: /);
  });
});
