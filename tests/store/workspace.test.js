import { readdir, rm, writeFile } from 'node:fs/promises';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { scratch } from '../helpers/scratch.js';

import { deriveOne } from '../../src/derive/oneshot.js';
import { readCapture } from '../../src/detect/capture.js';
import { Rect } from '../../src/geom/rect.js';
import { deliveryFor } from '../../src/manifest/index.js';
import { SIDECAR_NAME } from '../../src/manifest/payload.js';
import { Sampling } from '../../src/measure/resolution.js';
import { planCrop } from '../../src/plan/crop.js';
import { ProfileRegistry } from '../../src/profile/registry.js';
import { writeAtomic } from '../../src/store/atomic.js';
import { Workspace } from '../../src/store/workspace.js';
import { bench, paint } from '../helpers/bench.js';

const registry = new ProfileRegistry();
/** @returns {Promise<Workspace>} */
async function workspace() {
  return Workspace.open(await scratch('ws'));
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

describe('open', () => {
  it('makes the four folders', async () => {
    const store = await workspace();
    expect(await readdir(store.root)).toEqual(['delivered', 'incoming', 'rejected', 'working']);
  });

  it('is happy with a workspace that is already there', async () => {
    const store = await workspace();
    await expect(Workspace.open(store.root)).resolves.toBeInstanceOf(Workspace);
  });
});

describe('at', () => {
  it('opens one that exists', async () => {
    const store = await workspace();
    expect(Workspace.at(store.root).root).toBe(store.root);
  });

  it('refuses a directory that is not a workspace', async () => {
    const plain = await scratch('plain');
    expect(() => Workspace.at(plain)).toThrow(/not a workspace/);
  });
});

describe('incoming', () => {
  it('lists the captures waiting', async () => {
    const store = await workspace();
    await writeAtomic(join(store.paths.incoming, 'g4181.png'), new Uint8Array([1, 2]));
    const waiting = await store.incoming();
    expect(waiting).toHaveLength(1);
    expect(waiting[0].accession).toBe('g4181');
    expect(waiting[0].bytes).toBe(2);
  });

  it('leaves the spreadsheet and the notes out', async () => {
    const store = await workspace();
    await writeAtomic(join(store.paths.incoming, 'g4181.png'), new Uint8Array([1]));
    await writeAtomic(join(store.paths.incoming, 'shift notes.txt'), 'nothing to report');
    expect((await store.incoming()).map((one) => one.name)).toEqual(['g4181.png']);
  });

  it('has nothing to say about an empty folder', async () => {
    expect(await (await workspace()).incoming()).toEqual([]);
  });
});

describe('put', () => {
  it('writes every file of the delivery', async () => {
    const store = await workspace();
    const out = await store.put(delivery());
    expect(out.written.length).toBeGreaterThan(4);
    expect(await readdir(out.directory)).toContain(SIDECAR_NAME);
  });

  it('puts it under the accession', async () => {
    const store = await workspace();
    await store.put(delivery());
    expect(await store.list()).toEqual(['g4181-1907']);
  });

  it('refuses to deliver the same accession twice', async () => {
    const store = await workspace();
    await store.put(delivery());
    await expect(store.put(delivery())).rejects.toThrow(/already been delivered/);
  });

  it('replaces one when it is told to', async () => {
    const store = await workspace();
    await store.put(delivery());
    await expect(store.put(delivery(), { overwrite: true })).resolves.toBeTruthy();
  });

  it('takes another folder', async () => {
    const store = await workspace();
    await store.put(delivery(), { folder: 'rejected' });
    expect(await store.list({ folder: 'rejected' })).toEqual(['g4181-1907']);
    expect(await store.list()).toEqual([]);
  });

  it('refuses a delivery that does not agree with itself', async () => {
    const store = await workspace();
    const bad = delivery();
    bad.entries = bad.entries.filter((entry) => entry.name !== SIDECAR_NAME);
    await expect(store.put(bad)).rejects.toThrow(/does not agree with itself/);
  });
});

describe('has', () => {
  it('is false before and true after', async () => {
    const store = await workspace();
    expect(store.has('g4181-1907')).toBe(false);
    await store.put(delivery());
    expect(store.has('g4181-1907')).toBe(true);
  });
});

describe('get', () => {
  it('reads back what put wrote', async () => {
    const store = await workspace();
    const written = delivery();
    await store.put(written);
    const read = await store.get('g4181-1907');
    expect(read.digest).toBe(written.digest);
  });

  it('says so when there is nothing under that accession', async () => {
    const store = await workspace();
    await expect(store.get('nobody')).rejects.toThrow(/no delivery under that accession/);
  });
});

describe('verify', () => {
  it('passes a delivery that has not been touched', async () => {
    const store = await workspace();
    await store.put(delivery());
    const out = await store.verify('g4181-1907');
    expect(out.ok).toBe(true);
    expect(out.checked).toBe(3);
  });

  it('catches a file edited on the disk', async () => {
    const store = await workspace();
    const out = await store.put(delivery());
    await writeFile(join(out.directory, 'g4181-1907_thumb.png'), 'not a png any more');
    const checked = await store.verify('g4181-1907');
    expect(checked.ok).toBe(false);
    expect(checked.problems.join(' ')).toMatch(/g4181-1907_thumb\.png/);
  });

  it('catches a file removed from the folder', async () => {
    const store = await workspace();
    const out = await store.put(delivery());
    await rm(join(out.directory, 'g4181-1907_thumb.png'));
    expect((await store.verify('g4181-1907')).problems.join(' ')).toMatch(/is not here/);
  });

  it('reports rather than throwing for an accession that is not there', async () => {
    const out = await (await workspace()).verify('nobody');
    expect(out.ok).toBe(false);
    expect(out.problems[0]).toMatch(/no delivery under that accession/);
  });

  it('says each problem once', async () => {
    const store = await workspace();
    const out = await store.put(delivery());
    await writeFile(join(out.directory, 'g4181-1907_thumb.png'), 'x');
    const checked = await store.verify('g4181-1907');
    expect(new Set(checked.problems).size).toBe(checked.problems.length);
  });
});

describe('move', () => {
  it('moves a delivery between folders', async () => {
    const store = await workspace();
    await store.put(delivery());
    await store.move('g4181-1907', { from: 'delivered', to: 'rejected' });
    expect(await store.list()).toEqual([]);
    expect(await store.list({ folder: 'rejected' })).toEqual(['g4181-1907']);
  });

  it('refuses to move one that is not there', async () => {
    const store = await workspace();
    await expect(store.move('nobody', { from: 'delivered', to: 'rejected' })).rejects.toThrow(
      /nothing to move/,
    );
  });

  it('refuses to move one on top of another', async () => {
    const store = await workspace();
    await store.put(delivery());
    await store.put(delivery(), { folder: 'rejected' });
    await expect(store.move('g4181-1907', { from: 'delivered', to: 'rejected' })).rejects.toThrow(
      /already one there/,
    );
  });
});

describe('tidy', () => {
  it('empties the working folder', async () => {
    const store = await workspace();
    await writeAtomic(join(store.paths.working, 'half.png'), new Uint8Array([1]));
    await store.tidy();
    expect(await store.list({ folder: 'working' })).toEqual([]);
  });

  it('sweeps up what a crashed run left', async () => {
    const store = await workspace();
    await writeFile(join(store.paths.working, 'a.png.999.part'), 'half');
    expect((await store.tidy()).swept).toEqual(['a.png.999.part']);
  });

  it('leaves the delivered folder alone', async () => {
    const store = await workspace();
    await store.put(delivery());
    await store.tidy();
    expect(await store.list()).toEqual(['g4181-1907']);
  });
});
