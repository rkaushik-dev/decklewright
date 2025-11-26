import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

import { afterEach, describe, expect, it } from 'vitest';

import { encodePng } from '../../src/codec/png/encode.js';
import { Rect } from '../../src/geom/rect.js';
import { Sampling } from '../../src/measure/resolution.js';
import * as pipeline from '../../src/pipeline/index.js';
import { ProfileRegistry } from '../../src/profile/registry.js';
import { Surface } from '../../src/raster/surface.js';
import { writeAtomic } from '../../src/store/atomic.js';
import { Workspace } from '../../src/store/workspace.js';
import { bench, paint } from '../helpers/bench.js';

const registry = new ProfileRegistry();
const AT = new Date('2025-11-27T09:00:00Z');
/** @type {string[]} */
const made = [];

afterEach(async () => {
  await Promise.all(made.splice(0).map((path) => rm(path, { recursive: true, force: true })));
});

/** @returns {Promise<Workspace>} */
async function workspace() {
  const root = await mkdtemp(join(tmpdir(), 'decklewright-run-'));
  made.push(root);
  return Workspace.open(root);
}

/**
 * @param {object} [options]
 * @returns {Uint8Array}
 */
function capture({ degrees = 0 } = {}) {
  const surface = bench({
    width: 300,
    height: 240,
    degrees,
    halfWidth: 80,
    halfHeight: 56,
    centreX: 170,
    centreY: 130,
    layout: 'rgb',
  });
  for (let i = 0; i < 8; i += 1) {
    paint(surface, Rect.of(14 + i * 10, 12, 10, 14), 20 + Math.round((i / 7) * 210));
  }
  paint(surface, Rect.of(20, 220, 120, 7), 190);
  return encodePng(surface);
}

/**
 * @param {object} [options]
 * @returns {Parameters<typeof pipeline.runJob>[0]}
 */
function args({ id = 'g4181-1907', degrees = 0, profile = 'house', ppi = 400 } = {}) {
  return {
    accession: id,
    bytes: capture({ degrees }),
    profile: registry.get(profile),
    sampling: Sampling.of(ppi),
  };
}

describe('standardStages', () => {
  it('is a graph that can run given bytes', () => {
    expect(pipeline.objectionsToGraph(pipeline.standardStages(), ['bytes', 'path'])).toEqual([]);
  });

  it('reads before it plans and packs last', () => {
    const names = pipeline.order(pipeline.standardStages()).map((one) => one.name);
    expect(names[0]).toBe('read');
    expect(names[names.length - 1]).toBe('pack');
  });

  it('describes itself for a lab checking a new profile', () => {
    expect(pipeline.describeRunPlan()[0]).toMatch(/starts with/);
  });
});

describe('runJob', () => {
  it('rejects a sheet that sat well off square, whatever the crop can fix', async () => {
    const out = await pipeline.runJob(args({ degrees: 2 }), { at: AT });
    expect(out.ok).toBe(true);
    expect(out.decision?.decision).toBe('reject');
    expect(out.decision?.reason).toMatch(/held down by skew/);
  });

  it('runs the whole thing and decides to deliver', async () => {
    const out = await pipeline.runJob(args(), { at: AT, version: '1.4.0' });
    expect(out.ok).toBe(true);
    expect(out.decision?.decision).toBe('deliver');
  });

  it('carries a payload that checks out', async () => {
    const out = await pipeline.runJob(args(), { at: AT });
    const payload = out.job.need('payload');
    expect(payload.accession).toBe('g4181-1907');
    expect(payload.entries.length).toBeGreaterThan(4);
  });

  it('records every stage that ran', async () => {
    const out = await pipeline.runJob(args(), { at: AT });
    expect(out.results.map((one) => one.stage)).toEqual([
      'read',
      'detect',
      'plan',
      'grade',
      'derive',
      'pack',
    ]);
  });

  it('stops at the first failure rather than piling errors up', async () => {
    const bad = { ...args(), bytes: new Uint8Array([1, 2, 3]) };
    const out = await pipeline.runJob(bad, { at: AT });
    expect(out.ok).toBe(false);
    expect(out.results).toHaveLength(1);
    expect(out.decision).toBeNull();
  });

  it('says which stage failed', async () => {
    const out = await pipeline.runJob({ ...args(), bytes: new Uint8Array([1]) }, { at: AT });
    expect(out.results[0].stage).toBe('read');
    expect(out.error).not.toBeNull();
  });

  it('rejects work under the level the policy asks for', async () => {
    const out = await pipeline.runJob(args({ degrees: 5 }), {
      at: AT,
      policy: pipeline.policy({ minimumStars: 4, holdOn: [] }),
    });
    expect(out.decision?.decision).toBe('reject');
    expect(out.decision?.folder).toBe('rejected');
  });

  it('holds one that has already been delivered', async () => {
    const out = await pipeline.runJob(args(), { at: AT, alreadyThere: true });
    expect(out.decision?.decision).toBe('hold');
  });

  it('takes a shorter list of stages', async () => {
    const stages = pipeline.without(pipeline.standardStages(), ['grade', 'pack']);
    const out = await pipeline.runJob(args(), { at: AT, stages });
    expect(out.ok).toBe(true);
    expect(out.job.has('files')).toBe(true);
    expect(out.job.has('payload')).toBe(false);
  });

  it('refuses a set of stages the run cannot feed', async () => {
    const stages = pipeline.without(pipeline.standardStages(), ['read']);
    await expect(pipeline.runJob(args(), { stages })).rejects.toThrow(/nothing produces surface/);
  });

  it('takes the profile through to the crop', async () => {
    const out = await pipeline.runJob(args({ profile: 'rolled-drawings', ppi: 300 }), { at: AT });
    expect(out.job.need('files')).toHaveLength(2);
  });

  it('times the run', async () => {
    expect((await pipeline.runJob(args(), { at: AT })).ms).toBeGreaterThanOrEqual(0);
  });
});

describe('runBatch', () => {
  it('runs several and counts what happened', async () => {
    const out = await pipeline.runBatch([args({ id: 'a' }), args({ id: 'b' })], { at: AT });
    expect(out.runs).toHaveLength(2);
    expect(out.delivered).toBe(2);
    expect(out.failed).toBe(0);
  });

  it('carries on past a failure by default', async () => {
    const out = await pipeline.runBatch(
      [args({ id: 'a' }), { ...args({ id: 'b' }), bytes: new Uint8Array([1]) }, args({ id: 'c' })],
      { at: AT },
    );
    expect(out.runs).toHaveLength(3);
    expect(out.failed).toBe(1);
    expect(out.delivered).toBe(2);
  });

  it('stops when the policy says to', async () => {
    const out = await pipeline.runBatch(
      [{ ...args({ id: 'a' }), bytes: new Uint8Array([1]) }, args({ id: 'b' })],
      { at: AT, policy: pipeline.policy({ stopOnFailure: true }) },
    );
    expect(out.stoppedEarly).toBe(true);
    expect(out.runs).toHaveLength(1);
  });

  it('has nothing to report for an empty batch', async () => {
    const out = await pipeline.runBatch([], { at: AT });
    expect(out).toEqual({
      runs: [],
      delivered: 0,
      rejected: 0,
      held: 0,
      failed: 0,
      stoppedEarly: false,
    });
  });
});

describe('describeRun', () => {
  it('opens with the decision', async () => {
    const lines = pipeline.describeRun(await pipeline.runJob(args(), { at: AT }));
    expect(lines[0]).toMatch(/^g4181-1907: deliver, /);
  });

  it('says so when the run failed', async () => {
    const out = await pipeline.runJob({ ...args(), bytes: new Uint8Array([1]) }, { at: AT });
    expect(pipeline.describeRun(out)[0]).toMatch(/failed after 1 stages/);
  });

  it('lists a line per stage', async () => {
    const lines = pipeline.describeRun(await pipeline.runJob(args(), { at: AT }));
    expect(lines.filter((line) => line.startsWith('  '))).toHaveLength(6);
  });
});

describe('process', () => {
  it('writes the delivery and the ledger line', async () => {
    const space = await workspace();
    const out = await pipeline.process(space, args(), { at: AT, version: '1.4.0' });
    expect(out.directory).not.toBeNull();
    expect(out.entry.outcome).toBe('delivered');
    expect(await space.list()).toEqual(['g4181-1907']);
  });

  it('writes a delivery that verifies off the disk', async () => {
    const space = await workspace();
    await pipeline.process(space, args(), { at: AT });
    expect((await space.verify('g4181-1907')).ok).toBe(true);
  });

  it('records a failure without writing anything', async () => {
    const space = await workspace();
    const out = await pipeline.process(
      space,
      { ...args(), bytes: new Uint8Array([1]) },
      { at: AT },
    );
    expect(out.directory).toBeNull();
    expect(out.entry.outcome).toBe('failed');
    expect(await space.list()).toEqual([]);
  });

  it('records a hold as skipped and writes nothing', async () => {
    const space = await workspace();
    await pipeline.process(space, args(), { at: AT });
    const again = await pipeline.process(space, args(), { at: AT });
    expect(again.entry.outcome).toBe('skipped');
    expect(again.entry.reason).toMatch(/already been delivered/);
  });

  it('puts a rejection in the rejected folder', async () => {
    const space = await workspace();
    const out = await pipeline.process(space, args({ degrees: 5 }), {
      at: AT,
      policy: pipeline.policy({ minimumStars: 4, holdOn: [] }),
    });
    expect(out.entry.outcome).toBe('rejected');
    expect(await space.list({ folder: 'rejected' })).toEqual(['g4181-1907']);
  });
});

describe('processIncoming', () => {
  it('works everything waiting', async () => {
    const space = await workspace();
    await writeAtomic(join(space.paths.incoming, 'g4181.png'), capture());
    await writeAtomic(join(space.paths.incoming, 'g4182.png'), capture());

    const out = await pipeline.processIncoming(
      space,
      { profile: registry.get('house'), sampling: Sampling.of(400) },
      { at: AT },
    );
    expect(out.done).toHaveLength(2);
    expect(out.counts.delivered).toBe(2);
  });

  it('leaves the spreadsheet alone', async () => {
    const space = await workspace();
    await writeAtomic(join(space.paths.incoming, 'notes.txt'), 'nothing to report');
    const out = await pipeline.processIncoming(
      space,
      { profile: registry.get('house') },
      { at: AT },
    );
    expect(out.done).toEqual([]);
  });

  it('records a capture it could not read and carries on', async () => {
    const space = await workspace();
    await writeAtomic(join(space.paths.incoming, 'broken.png'), new Uint8Array([1, 2, 3]));
    await writeAtomic(join(space.paths.incoming, 'g4181.png'), capture());

    const out = await pipeline.processIncoming(
      space,
      { profile: registry.get('house'), sampling: Sampling.of(400) },
      { at: AT },
    );
    expect(out.counts).toEqual({ failed: 1, delivered: 1 });
  });

  it('does not read a capture it cannot grade into the ledger twice', async () => {
    const space = await workspace();
    const surface = Surface.create({ width: 60, height: 40, layout: 'rgb', fill: 40 });
    await writeAtomic(join(space.paths.incoming, 'blank.png'), encodePng(surface));

    const out = await pipeline.processIncoming(
      space,
      { profile: registry.get('house'), sampling: Sampling.of(400) },
      { at: AT },
    );
    expect(out.done).toHaveLength(1);
    expect(out.done[0].entry.outcome).toBe('failed');
  });
});
