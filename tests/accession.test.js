import { readdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { scratch } from './helpers/scratch.js';

import * as deckle from '../src/index.js';
import { encodePng } from '../src/codec/png/encode.js';
import { capture } from './helpers/accession.js';

/**
 * One sheet, all the way through, using nothing but the public surface.
 *
 * Every other test in the tree reaches into a layer. This one is the only test
 * that is a customer: it opens a workspace, drops a capture in it, runs the
 * shift, and then checks the folder the way somebody would in five years with
 * none of this code to hand. If the layers are all right and this is wrong,
 * the thing that is wrong is the shape of the library.
 */

const AT = new Date('2025-12-19T09:00:00Z');

describe('the whole job', () => {
  it('takes a capture off a bed and leaves a delivery on a disk', async () => {
    const root = await scratch('accession');
    const workspace = await deckle.Workspace.open(root);

    await writeFile(join(workspace.paths.incoming, 'g4181-1907.png'), encodePng(capture()));

    const out = await deckle.processIncoming(
      workspace,
      { profile: new deckle.ProfileRegistry().get('house'), sampling: deckle.Sampling.of(400) },
      { version: '1.4.0', host: 'stand-2', at: AT },
    );

    expect(out.counts).toEqual({ delivered: 1 });

    const written = await readdir(join(root, 'delivered', 'g4181-1907'));
    expect(written).toEqual([
      'g4181-1907_master.png',
      'g4181-1907_service.png',
      'g4181-1907_thumb.png',
      'provenance.json',
      'sha256sums.txt',
      'sidecar.json',
    ]);
  });

  it('leaves a master that reads back as the sheet, cut and straightened', async () => {
    const root = await scratch('accession');
    const workspace = await deckle.Workspace.open(root);
    await writeFile(
      join(workspace.paths.incoming, 'g4181.png'),
      encodePng(capture({ degrees: 1.5 })),
    );

    await deckle.processIncoming(
      workspace,
      { profile: new deckle.ProfileRegistry().get('house'), sampling: deckle.Sampling.of(400) },
      { at: AT, policy: deckle.policy({ minimumStars: 0, holdOn: [] }) },
    );

    const delivered = await workspace.get('g4181');
    const master = delivered.entries.find((one) => one.name.endsWith('_master.png'));
    const surface = deckle.readImage(/** @type {Uint8Array} */ (master?.bytes));

    expect(surface.depth).toBe(16);
    expect(surface.width).toBeGreaterThan(140);
    expect(surface.width).toBeLessThan(200);
    expect(deckle.frequencyOf(/** @type {Uint8Array} */ (master?.bytes))?.x).toBeCloseTo(400, 1);
  });

  it('leaves a sidecar that says what was done, in numbers', async () => {
    const root = await scratch('accession');
    const workspace = await deckle.Workspace.open(root);
    await writeFile(join(workspace.paths.incoming, 'g4181.png'), encodePng(capture()));

    await deckle.processIncoming(
      workspace,
      { profile: new deckle.ProfileRegistry().get('house'), sampling: deckle.Sampling.of(400) },
      { version: '1.4.0', at: AT },
    );

    const delivered = await workspace.get('g4181');
    const bytes = delivered.entries.find((one) => one.name === 'sidecar.json')?.bytes;
    const sidecar = deckle.parseSidecar(new TextDecoder().decode(bytes));

    expect(sidecar.accession).toBe('g4181');
    expect(sidecar.profile.name).toBe('house');
    expect(sidecar.created).toBe(AT.toISOString());
    expect(sidecar.capture.pixels).toEqual({ width: 300, height: 240 });
    expect(sidecar.derivatives).toHaveLength(3);
    expect(sidecar.quality.stars).toBeGreaterThanOrEqual(3);
  });

  it('leaves a delivery that verifies years later with nothing else to hand', async () => {
    const root = await scratch('accession');
    const workspace = await deckle.Workspace.open(root);
    await writeFile(join(workspace.paths.incoming, 'g4181.png'), encodePng(capture()));
    await deckle.processIncoming(
      workspace,
      { profile: new deckle.ProfileRegistry().get('house'), sampling: deckle.Sampling.of(400) },
      { at: AT },
    );

    const later = deckle.Workspace.at(root);
    expect(await later.verify('g4181')).toEqual({
      accession: 'g4181',
      ok: true,
      checked: 3,
      problems: [],
    });
  });

  it('catches a master somebody edited on the disk', async () => {
    const root = await scratch('accession');
    const workspace = await deckle.Workspace.open(root);
    await writeFile(join(workspace.paths.incoming, 'g4181.png'), encodePng(capture()));
    await deckle.processIncoming(
      workspace,
      { profile: new deckle.ProfileRegistry().get('house'), sampling: deckle.Sampling.of(400) },
      { at: AT },
    );

    await writeFile(join(root, 'delivered', 'g4181', 'g4181_master.png'), 'improved');
    const checked = await workspace.verify('g4181');
    expect(checked.ok).toBe(false);
    expect(checked.problems.join(' ')).toMatch(/g4181_master\.png/);
  });

  it('records the shift in the ledger, delivered and not', async () => {
    const root = await scratch('accession');
    const workspace = await deckle.Workspace.open(root);
    await writeFile(join(workspace.paths.incoming, 'g4181.png'), encodePng(capture()));
    await writeFile(join(workspace.paths.incoming, 'lenscap.png'), new Uint8Array([1, 2, 3]));

    await deckle.processIncoming(
      workspace,
      { profile: new deckle.ProfileRegistry().get('house'), sampling: deckle.Sampling.of(400) },
      { at: AT },
    );

    const report = await deckle.shiftReport(workspace);
    expect(report.total).toBe(2);
    expect(report.byOutcome).toEqual({ delivered: 1, failed: 1 });
    expect(report.unreadable).toBe(0);
  });

  it('runs the same job through the command line and gets the same folder', async () => {
    const one = await scratch('accession');
    const two = await scratch('accession');
    for (const root of [one, two]) {
      const workspace = await deckle.Workspace.open(root);
      await writeFile(join(workspace.paths.incoming, 'g4181.png'), encodePng(capture()));
    }

    await deckle.processIncoming(
      deckle.Workspace.at(one),
      { profile: new deckle.ProfileRegistry().get('house'), sampling: deckle.Sampling.of(400) },
      { version: '1.4.0', at: AT },
    );
    await deckle.cli(['batch', two, '-r', '400'], {
      version: '1.4.0',
      sink: {
        out: () => {},
        err: () => {},
        cwd: two,
        now: () => AT,
      },
    });

    expect(await readdir(join(one, 'delivered', 'g4181'))).toEqual(
      await readdir(join(two, 'delivered', 'g4181')),
    );
  });
});

describe('the public surface', () => {
  it('exports exactly the names it says it does', () => {
    expect(Object.keys(deckle).sort()).toEqual([...deckle.PUBLIC_NAMES].sort());
  });

  it('says which version of the surface this is', () => {
    expect(deckle.API_VERSION).toBe(2);
  });

  it('exports the one call that does the whole job', () => {
    expect(typeof deckle.process).toBe('function');
    expect(typeof deckle.processIncoming).toBe('function');
  });

  it('exports every error a caller has to catch', () => {
    for (const name of [
      'BadArgumentError',
      'CodecError',
      'DetectionError',
      'GeometryError',
      'ManifestError',
      'PipelineError',
      'PlanError',
      'ProfileError',
      'QcError',
      'StoreError',
      'UnsupportedError',
    ]) {
      expect(typeof deckle[name], name).toBe('function');
      expect(new deckle[name]('x') instanceof deckle.DeckleError, name).toBe(true);
    }
  });

  it('keeps the list frozen, so it cannot be widened at runtime', () => {
    expect(Object.isFrozen(deckle.PUBLIC_NAMES)).toBe(true);
  });
});
