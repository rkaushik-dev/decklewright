import { writeFile } from 'node:fs/promises';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { scratch } from '../../helpers/scratch.js';

import { parseArgs } from '../../../src/cli/args.js';
import * as batch from '../../../src/cli/commands/batch.js';
import { io } from '../../../src/cli/commands/common.js';
import * as profile from '../../../src/cli/commands/profile.js';
import * as verify from '../../../src/cli/commands/verify.js';
import { encodePng } from '../../../src/codec/png/encode.js';
import { Rect } from '../../../src/geom/rect.js';
import { writeAtomic } from '../../../src/store/atomic.js';
import { Workspace } from '../../../src/store/workspace.js';
import { bench, paint } from '../../helpers/bench.js';

const AT = new Date('2025-12-15T08:00:00Z');

/** @returns {Uint8Array} */
function capture() {
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
async function delivered(count = 1) {
  const root = await scratch();
  const space = await Workspace.open(root);
  for (let i = 0; i < count; i += 1) {
    await writeAtomic(join(space.paths.incoming, `g418${i}.png`), capture());
  }
  await invoke(batch, [root, '-r', '400']);
  return root;
}

describe('profile', () => {
  it('lists what ships', async () => {
    const { code, out } = await invoke(profile, []);
    expect(code).toBe(0);
    expect(out.map((line) => line.split('  ')[0])).toEqual([
      'house',
      'atlas-plates',
      'quadrangles',
      'rolled-drawings',
      'microfilm',
    ]);
  });

  it('shows one resolved, with its parents folded in', async () => {
    const { out } = await invoke(profile, ['atlas-plates']);
    expect(out[0]).toMatch(/^atlas-plates: county atlas plates/);
    expect(out.join('\n')).toMatch(/crops to the/);
  });

  it('writes it as json when it is asked to', async () => {
    const { out } = await invoke(profile, ['quadrangles', '--json']);
    const document = JSON.parse(out.join('\n'));
    expect(document.name).toBe('quadrangles');
    expect(document.crop.anchor).toBe('neatline');
  });

  it('shows one setting on its own', async () => {
    const { code, out } = await invoke(profile, ['quadrangles', '-s', 'qc.minimumStars']);
    expect(code).toBe(0);
    expect(out).toEqual(['4']);
  });

  it('says so when a setting is not there', async () => {
    const { code, err } = await invoke(profile, ['house', '-s', 'qc.colour']);
    expect(code).toBe(1);
    expect(err[0]).toMatch(/no setting called qc.colour/);
  });

  it('lists every setting a profile can carry', async () => {
    const { out } = await invoke(profile, ['--paths']);
    expect(out).toContain('qc.minimumStars');
    expect(out.length).toBeGreaterThan(10);
  });

  it('reports a profile that contradicts itself', async () => {
    const root = await scratch();
    const path = join(root, 'wrong.json');
    await writeFile(
      path,
      JSON.stringify({
        name: 'wrong',
        extends: 'house',
        version: 2,
        crop: { anchor: 'neatline' },
      }),
    );
    const { code, err } = await invoke(profile, [path]);
    expect(code).toBe(1);
    expect(err.join(' ')).toMatch(/never looks for one/);
  });

  it('refuses a name nothing knows', async () => {
    const { code, err } = await invoke(profile, ['nothing']);
    expect(code).toBe(1);
    expect(err[0]).toMatch(/no profile by that name/);
  });
});

describe('verify', () => {
  it('passes a workspace nobody has touched', async () => {
    const root = await delivered(2);
    const { code, out } = await invoke(verify, [root]);
    expect(code).toBe(0);
    expect(out[out.length - 1]).toMatch(/2 accessions, \d+ files, 0 with problems/);
  });

  it('names the file that changed', async () => {
    const root = await delivered();
    await writeFile(join(root, 'delivered', 'g4180', 'g4180_thumb.png'), 'not a png');
    const { code, err } = await invoke(verify, [root]);

    expect(code).toBe(1);
    expect(err.join('\n')).toMatch(/g4180_thumb\.png/);
  });

  it('keeps going past a bad one', async () => {
    const root = await delivered(2);
    await writeFile(join(root, 'delivered', 'g4180', 'g4180_thumb.png'), 'not a png');
    const { out } = await invoke(verify, [root]);
    expect(out[out.length - 1]).toMatch(/2 accessions, \d+ files, 1 with problems/);
  });

  it('checks only the accessions it was named', async () => {
    const root = await delivered(2);
    const { out } = await invoke(verify, [root, 'g4181']);
    expect(out[out.length - 1]).toMatch(/^1 accessions/);
  });

  it('says nothing about the good ones when it is asked to be quiet', async () => {
    const root = await delivered(2);
    const { out } = await invoke(verify, [root, '-q']);
    expect(out).toHaveLength(1);
  });

  it('writes json when it is asked to', async () => {
    const root = await delivered();
    const { out } = await invoke(verify, [root, '--json']);
    const document = JSON.parse(out.join('\n'));
    expect(document.ok).toBe(true);
    expect(document.accessions).toBe(1);
  });

  it('says so when there is nothing in the folder', async () => {
    const root = await scratch();
    await Workspace.open(root);
    const { code, out } = await invoke(verify, [root]);
    expect(code).toBe(0);
    expect(out).toEqual(['nothing in delivered']);
  });

  it('sweeps another folder when it is told to', async () => {
    const root = await delivered();
    const { out } = await invoke(verify, [root, '-d', 'rejected']);
    expect(out).toEqual(['nothing in rejected']);
  });

  it('reports an accession that is not there rather than throwing', async () => {
    const root = await delivered();
    const { code, err } = await invoke(verify, [root, 'nobody']);
    expect(code).toBe(1);
    expect(err.join(' ')).toMatch(/no delivery under that accession/);
  });

  it('refuses a directory that is not a workspace', async () => {
    const root = await scratch();
    const { code, err } = await invoke(verify, [root]);
    expect(code).toBe(1);
    expect(err[0]).toMatch(/not a workspace/);
  });

  it('asks for a workspace when it was given none', async () => {
    const { code, err } = await invoke(verify, []);
    expect(code).toBe(2);
    expect(err[0]).toMatch(/which workspace/);
  });
});
