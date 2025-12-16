import { readdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { scratch } from '../../helpers/scratch.js';

import { parseArgs } from '../../../src/cli/args.js';
import * as crop from '../../../src/cli/commands/crop.js';
import { humanBytes, io, profileNamed, samplingFrom } from '../../../src/cli/commands/common.js';
import * as plan from '../../../src/cli/commands/plan.js';
import { encodePng } from '../../../src/codec/png/encode.js';
import { Rect } from '../../../src/geom/rect.js';
import { bench, paint } from '../../helpers/bench.js';

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
 * @returns {{sink: import('../../../src/cli/commands/common.js').Io, out: string[], err: string[]}}
 */
function collector(cwd = '.') {
  /** @type {string[]} */
  const out = [];
  /** @type {string[]} */
  const err = [];
  return {
    sink: io({ out: (line) => out.push(line), err: (line) => err.push(line), cwd }),
    out,
    err,
  };
}

/**
 * @param {typeof crop} command
 * @param {readonly string[]} argv
 * @param {string} [cwd]
 */
async function invoke(command, argv, cwd = '.') {
  const { sink, out, err } = collector(cwd);
  const parsed = parseArgs(argv, command.options);
  const code = await command.run(parsed, sink);
  return { code, out, err };
}

describe('common', () => {
  it('finds a built in profile by name', async () => {
    expect((await profileNamed('quadrangles')).name).toBe('quadrangles');
  });

  it('refuses a name nothing knows, and says what it does know', async () => {
    await expect(profileNamed('nothing')).rejects.toThrow(/no profile by that name/);
  });

  it('reads a profile off a disk when the name looks like a path', async () => {
    const root = await scratch();
    const path = join(root, 'local.json');
    await writeFile(path, JSON.stringify({ name: 'local', extends: 'house', version: 2 }));
    expect((await profileNamed(path)).name).toBe('local');
  });

  it('says so when a profile file will not read', async () => {
    const root = await scratch();
    const path = join(root, 'broken.json');
    await writeFile(path, 'not json');
    await expect(profileNamed(path)).rejects.toThrow(/will not read as json/);
  });

  it('takes the frequency the caller gave over the one the profile claims', async () => {
    const profile = await profileNamed('quadrangles');
    expect(samplingFrom(400, profile)?.x).toBe(400);
    expect(samplingFrom(undefined, profile)?.x).toBe(600);
  });

  it('writes a size a person reads', () => {
    expect(humanBytes(900)).toBe('900 B');
    expect(humanBytes(2048)).toBe('2.0 kB');
    expect(humanBytes(5 * 1024 * 1024)).toBe('5.0 MB');
  });
});

describe('plan', () => {
  it('says where the crop would be', async () => {
    const root = await scratch();
    await writeFile(join(root, 'g4181.png'), capture());
    const { code, out } = await invoke(plan, [join(root, 'g4181.png')]);
    expect(code).toBe(0);
    expect(out[0]).toBe('g4181, profile house');
    expect(out.join('\n')).toMatch(/crop /);
  });

  it('writes json when it is asked to', async () => {
    const root = await scratch();
    await writeFile(join(root, 'g4181.png'), capture());
    const { out } = await invoke(plan, [join(root, 'g4181.png'), '--json']);
    const document = JSON.parse(out.join('\n'));
    expect(document.accession).toBe('g4181');
    expect(document.rect.width).toBeGreaterThan(100);
    expect(document.settled).toBe(true);
  });

  it('writes nothing to the disk', async () => {
    const root = await scratch();
    await writeFile(join(root, 'g4181.png'), capture());
    await invoke(plan, [join(root, 'g4181.png')]);
    expect(await readdir(root)).toEqual(['g4181.png']);
  });

  it('asks for a capture when it was given none', async () => {
    const { code, err } = await invoke(plan, []);
    expect(code).toBe(2);
    expect(err[0]).toMatch(/which capture/);
  });

  it('reports a capture it could not read rather than throwing', async () => {
    const root = await scratch();
    await writeFile(join(root, 'broken.png'), new Uint8Array([1, 2, 3]));
    const { code, err } = await invoke(plan, [join(root, 'broken.png')]);
    expect(code).toBe(1);
    expect(err.length).toBeGreaterThan(0);
  });

  it('names the profile it was told to use', async () => {
    const root = await scratch();
    await writeFile(join(root, 'g4181.png'), capture());
    const { out } = await invoke(plan, [join(root, 'g4181.png'), '-p', 'rolled-drawings']);
    expect(out[0]).toMatch(/profile rolled-drawings/);
  });
});

describe('crop', () => {
  it('writes the derivatives', async () => {
    const root = await scratch();
    await writeFile(join(root, 'g4181.png'), capture());
    const { code, out } = await invoke(crop, [join(root, 'g4181.png'), '--out', 'cut'], root);

    expect(code).toBe(0);
    expect(await readdir(join(root, 'cut'))).toEqual([
      'g4181_master.png',
      'g4181_service.png',
      'g4181_thumb.png',
    ]);
    expect(out[out.length - 1]).toMatch(/^wrote 3 files/);
  });

  it('writes only the derivative it was asked for', async () => {
    const root = await scratch();
    await writeFile(join(root, 'g4181.png'), capture());
    await invoke(crop, [join(root, 'g4181.png'), '--out', 'cut', '--only', 'thumb'], root);
    expect(await readdir(join(root, 'cut'))).toEqual(['g4181_thumb.png']);
  });

  it('says so when the only it was given is not a derivative', async () => {
    const root = await scratch();
    await writeFile(join(root, 'g4181.png'), capture());
    const { code, err } = await invoke(crop, [join(root, 'g4181.png'), '--only', 'poster'], root);
    expect(code).toBe(1);
    expect(err[0]).toMatch(/this profile makes master, service, thumb/);
  });

  it('takes an accession that is not the file name', async () => {
    const root = await scratch();
    await writeFile(join(root, 'scan001.png'), capture());
    await invoke(crop, [join(root, 'scan001.png'), '--out', 'cut', '-n', 'g4181-1907'], root);
    expect(await readdir(join(root, 'cut'))).toContain('g4181-1907_master.png');
  });

  it('writes nothing on a dry run and says what it would have written', async () => {
    const root = await scratch();
    await writeFile(join(root, 'g4181.png'), capture());
    const { out } = await invoke(crop, [join(root, 'g4181.png'), '--dry-run'], root);
    expect(out[out.length - 1]).toMatch(/^would write 3 files/);
    expect(await readdir(root)).toEqual(['g4181.png']);
  });

  it('refuses to replace files that are there', async () => {
    const root = await scratch();
    await writeFile(join(root, 'g4181.png'), capture());
    await invoke(crop, [join(root, 'g4181.png'), '--out', 'cut'], root);
    const { code, err } = await invoke(crop, [join(root, 'g4181.png'), '--out', 'cut'], root);
    expect(code).toBe(1);
    expect(err.join(' ')).toMatch(/already a file there/);
  });

  it('replaces them when it is told to', async () => {
    const root = await scratch();
    await writeFile(join(root, 'g4181.png'), capture());
    await invoke(crop, [join(root, 'g4181.png'), '--out', 'cut'], root);
    const { code } = await invoke(crop, [join(root, 'g4181.png'), '--out', 'cut', '-f'], root);
    expect(code).toBe(0);
  });

  it('lists what it wrote with the size and the frequency', async () => {
    const root = await scratch();
    await writeFile(join(root, 'g4181.png'), capture());
    const { out } = await invoke(
      crop,
      [join(root, 'g4181.png'), '--out', 'cut', '-r', '600'],
      root,
    );
    expect(out[1]).toMatch(/g4181_master\.png, [\d.]+ (B|kB|MB), 600/);
  });

  it('asks for a capture when it was given none', async () => {
    const { code, err } = await invoke(crop, []);
    expect(code).toBe(2);
    expect(err[0]).toMatch(/which capture/);
  });
});
