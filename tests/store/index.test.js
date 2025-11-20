import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

import { afterEach, describe, expect, it } from 'vitest';

import { deriveOne } from '../../src/derive/oneshot.js';
import { readCapture } from '../../src/detect/capture.js';
import { Rect } from '../../src/geom/rect.js';
import { deliveryFor } from '../../src/manifest/index.js';
import { Sampling } from '../../src/measure/resolution.js';
import { planCrop } from '../../src/plan/crop.js';
import { ProfileRegistry } from '../../src/profile/registry.js';
import * as store from '../../src/store/index.js';
import { bench, paint } from '../helpers/bench.js';

const registry = new ProfileRegistry();
/** @type {string[]} */
const made = [];

afterEach(async () => {
  await Promise.all(made.splice(0).map((path) => rm(path, { recursive: true, force: true })));
});

/** @returns {Promise<store.Workspace>} */
async function workspace() {
  const root = await mkdtemp(join(tmpdir(), 'decklewright-store-'));
  made.push(root);
  return store.Workspace.open(root);
}

/**
 * @param {string} [id]
 * @returns {import('../../src/manifest/payload.js').Payload}
 */
function delivery(id = 'g4181-1907') {
  const surface = bench({
    width: 300,
    height: 240,
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

  const profile = registry.get('house');
  const reading = readCapture(surface);
  const sampling = Sampling.of(400);
  const { plan } = planCrop({ reading, profile, capture: surface.bounds, sampling });
  const derived = deriveOne({ surface, profile, accession: id, sampling });

  return deliveryFor({
    accession: id,
    profile,
    surface,
    reading,
    plan,
    files: derived.files,
    version: '1.4.0',
    at: new Date('2025-11-25T12:00:00Z'),
  });
}

describe('the barrel', () => {
  it('exports the workspace and the two calls a run makes', () => {
    expect(typeof store.Workspace).toBe('function');
    expect(typeof store.deliver).toBe('function');
    expect(typeof store.recordFailure).toBe('function');
  });

  it('exports the atomic writes, for a lab that keeps its own layout', () => {
    expect(typeof store.writeSetAtomic).toBe('function');
    expect(typeof store.sweepTemporary).toBe('function');
  });
});

describe('deliver', () => {
  it('writes the files and records the line', async () => {
    const space = await workspace();
    const out = await store.deliver(space, delivery(), { profile: 'house', stars: 4 });
    expect(out.directory.endsWith('g4181-1907')).toBe(true);
    expect(out.entry.outcome).toBe('delivered');
    expect(out.entry.stars).toBe(4);
  });

  it('records the delivery digest, so a folder can be traced to a line', async () => {
    const space = await workspace();
    const payload = delivery();
    const out = await store.deliver(space, payload);
    expect(out.entry.digest).toBe(payload.digest);
  });

  it('records the size that was actually written', async () => {
    const space = await workspace();
    const out = await store.deliver(space, delivery());
    expect(out.entry.bytes).toBeGreaterThan(0);
  });

  it('records a rejection as a rejection', async () => {
    const space = await workspace();
    const out = await store.deliver(space, delivery(), { folder: 'rejected', stars: 1 });
    expect(out.entry.outcome).toBe('rejected');
    expect(await space.list({ folder: 'rejected' })).toEqual(['g4181-1907']);
  });

  it('writes no line when the files could not be written', async () => {
    const space = await workspace();
    await store.deliver(space, delivery());
    await expect(store.deliver(space, delivery())).rejects.toThrow(/already been delivered/);

    const { entries } = await store.readLedger(space.paths.ledger);
    expect(entries).toHaveLength(1);
  });
});

describe('recordFailure', () => {
  it('records a run that produced nothing', async () => {
    const space = await workspace();
    const entry = await store.recordFailure(space, {
      accession: 'g4182',
      reason: 'no sheet on the bed',
      profile: 'house',
    });
    expect(entry.outcome).toBe('failed');
    expect(entry.reason).toBe('no sheet on the bed');
  });

  it('takes another outcome for a capture that was deliberately passed over', async () => {
    const space = await workspace();
    const entry = await store.recordFailure(space, {
      accession: 'g4183',
      reason: 'already delivered last week',
      outcome: 'skipped',
    });
    expect(entry.outcome).toBe('skipped');
  });
});

describe('shiftReport', () => {
  it('counts what the workspace did', async () => {
    const space = await workspace();
    await store.deliver(space, delivery('a'), { stars: 4 });
    await store.deliver(space, delivery('b'), { stars: 2, folder: 'rejected' });
    await store.recordFailure(space, { accession: 'c', reason: 'nothing on the bed' });

    const report = await store.shiftReport(space);
    expect(report.total).toBe(3);
    expect(report.byOutcome).toEqual({ delivered: 1, rejected: 1, failed: 1 });
    expect(report.averageStars).toBe(3);
  });

  it('reports an empty workspace as empty', async () => {
    const report = await store.shiftReport(await workspace());
    expect(report.total).toBe(0);
    expect(report.unreadable).toBe(0);
  });

  it('says how many lines could not be read', async () => {
    const space = await workspace();
    await store.appendLine(space.paths.ledger, 'this is not json');
    expect((await store.shiftReport(space)).unreadable).toBe(1);
  });
});
