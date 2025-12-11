import { mkdtemp, readdir, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

import { afterEach, describe, expect, it } from 'vitest';

import { parseArgs } from '../../../src/cli/args.js';
import * as batch from '../../../src/cli/commands/batch.js';
import { io } from '../../../src/cli/commands/common.js';
import * as qc from '../../../src/cli/commands/qc.js';
import { encodePng } from '../../../src/codec/png/encode.js';
import { Rect } from '../../../src/geom/rect.js';
import { writeAtomic } from '../../../src/store/atomic.js';
import { Workspace } from '../../../src/store/workspace.js';
import { bench, paint } from '../../helpers/bench.js';

/** @type {string[]} */
const made = [];
const AT = new Date('2025-12-11T08:00:00Z');

afterEach(async () => {
  await Promise.all(made.splice(0).map((path) => rm(path, { recursive: true, force: true })));
});

/** @returns {Promise<string>} */
async function scratch() {
  const root = await mkdtemp(join(tmpdir(), 'decklewright-batch-'));
  made.push(root);
  return root;
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
 * @param {any} command
 * @param {readonly string[]} argv
 */
async function invoke(command, argv) {
  /** @type {string[]} */
  const out = [];
  /** @type {string[]} */
  const err = [];
  const sink = io({
    out: (line) => out.push(line),
    err: (line) => err.push(line),
    now: () => AT,
  });
  const code = await command.run(parseArgs(argv, command.options), sink);
  return { code, out, err };
}

/** @returns {Promise<string>} */
async function workspaceWith(/** @type {Record<string, Uint8Array>} */ files) {
  const root = await scratch();
  const space = await Workspace.open(root);
  for (const [name, bytes] of Object.entries(files)) {
    await writeAtomic(join(space.paths.incoming, name), bytes);
  }
  return root;
}

describe('qc', () => {
  it('grades a capture and exits zero when it passed', async () => {
    const root = await scratch();
    await writeFile(join(root, 'g4181.png'), capture());
    const { code, out } = await invoke(qc, [join(root, 'g4181.png'), '-r', '400']);
    expect(code).toBe(0);
    expect(out[0]).toMatch(/^g4181: /);
  });

  it('exits one when it did not', async () => {
    const root = await scratch();
    await writeFile(join(root, 'g4181.png'), capture({ degrees: 3 }));
    const { code, out } = await invoke(qc, [join(root, 'g4181.png'), '-r', '400']);
    expect(code).toBe(1);
    expect(out.join('\n')).toMatch(/square the sheet/);
  });

  it('measures against another level when it is told one', async () => {
    const root = await scratch();
    await writeFile(join(root, 'g4181.png'), capture());
    expect((await invoke(qc, [join(root, 'g4181.png'), '-r', '400', '-s', '4'])).code).toBe(0);
    expect((await invoke(qc, [join(root, 'g4181.png'), '-r', '100', '-s', '4'])).code).toBe(1);
  });

  it('writes json when it is asked to', async () => {
    const root = await scratch();
    await writeFile(join(root, 'g4181.png'), capture());
    const { out } = await invoke(qc, [join(root, 'g4181.png'), '-r', '400', '--json']);
    const document = JSON.parse(out.join('\n'));
    expect(document.item).toBe('g4181');
    expect(Object.keys(document.measurements)).toHaveLength(8);
  });

  it('grades the crop when it is told to', async () => {
    const root = await scratch();
    await writeFile(join(root, 'g4181.png'), capture());
    expect((await invoke(qc, [join(root, 'g4181.png'), '-r', '400', '--crop'])).code).toBe(0);
  });

  it('asks for a capture when it was given none', async () => {
    const { code, err } = await invoke(qc, []);
    expect(code).toBe(2);
    expect(err[0]).toMatch(/which capture/);
  });
});

describe('batch', () => {
  it('works everything waiting and says so', async () => {
    const root = await workspaceWith({ 'g4181.png': capture(), 'g4182.png': capture() });
    const { code, out } = await invoke(batch, [root, '-r', '400']);

    expect(code).toBe(0);
    expect(out[0]).toBe('2 waiting, profile house');
    expect(await readdir(join(root, 'delivered'))).toEqual(['g4181', 'g4182']);
  });

  it('writes a summary at the end', async () => {
    const root = await workspaceWith({ 'g4181.png': capture() });
    const { out } = await invoke(batch, [root, '-r', '400']);
    expect(out.join('\n')).toMatch(/1 accessions, \d+ bytes/);
    expect(out.join('\n')).toMatch(/1 delivered/);
  });

  it('says so when nothing is waiting', async () => {
    const root = await workspaceWith({});
    const { code, out } = await invoke(batch, [root]);
    expect(code).toBe(0);
    expect(out).toEqual(['nothing is waiting']);
  });

  it('carries on past a capture it could not read and exits one', async () => {
    const root = await workspaceWith({
      'broken.png': new Uint8Array([1, 2, 3]),
      'g4181.png': capture(),
    });
    const { code, err } = await invoke(batch, [root, '-r', '400']);

    expect(code).toBe(1);
    expect(err.join(' ')).toMatch(/1 accessions need somebody/);
    expect(await readdir(join(root, 'delivered'))).toEqual(['g4181']);
  });

  it('stops at the first failure when it is told to', async () => {
    const root = await workspaceWith({
      'broken.png': new Uint8Array([1, 2, 3]),
      'g4181.png': capture(),
    });
    await invoke(batch, [root, '-r', '400', '--stop']);
    expect(await readdir(join(root, 'delivered'))).toEqual([]);
  });

  it('rejects work under the level it was told to deliver at', async () => {
    const root = await workspaceWith({ 'g4181.png': capture({ degrees: 3 }) });
    await invoke(batch, [root, '-r', '400']);
    expect(await readdir(join(root, 'rejected'))).toEqual(['g4181']);
    expect(await readdir(join(root, 'delivered'))).toEqual([]);
  });

  it('writes one line per accession when it is asked to be quiet', async () => {
    const root = await workspaceWith({ 'g4181.png': capture() });
    const { out } = await invoke(batch, [root, '-r', '400', '-q']);
    expect(out.filter((line) => line.startsWith('g4181'))).toHaveLength(1);
  });

  it('records the version it was told into the provenance', async () => {
    const root = await workspaceWith({ 'g4181.png': capture() });
    await invoke(batch, [root, '-r', '400', '--version', '2.1.0']);

    const { readText } = await import('../../../src/store/atomic.js');
    const chain = JSON.parse(await readText(join(root, 'delivered', 'g4181', 'provenance.json')));
    expect(chain[0].agent).toBe('decklewright 2.1.0');
  });

  it('holds a second run over the same accession rather than replacing it', async () => {
    const root = await workspaceWith({ 'g4181.png': capture() });
    await invoke(batch, [root, '-r', '400']);
    const { code, err } = await invoke(batch, [root, '-r', '400']);
    expect(code).toBe(1);
    expect(err.join(' ')).toMatch(/need somebody/);
  });

  it('replaces one when it is told to', async () => {
    const root = await workspaceWith({ 'g4181.png': capture() });
    await invoke(batch, [root, '-r', '400']);
    expect((await invoke(batch, [root, '-r', '400', '-f'])).code).toBe(0);
  });

  it('refuses a directory that is not a workspace', async () => {
    const root = await scratch();
    const { code, err } = await invoke(batch, [root]);
    expect(code).toBe(1);
    expect(err[0]).toMatch(/not a workspace/);
  });

  it('asks for a workspace when it was given none', async () => {
    const { code, err } = await invoke(batch, []);
    expect(code).toBe(2);
    expect(err[0]).toMatch(/which workspace/);
  });
});
