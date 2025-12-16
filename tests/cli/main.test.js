import { writeFile } from 'node:fs/promises';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { scratch } from '../helpers/scratch.js';

import { io } from '../../src/cli/commands/common.js';
import { COMMANDS, PROGRAM, help, helpFor, main, nearestCommands } from '../../src/cli/main.js';
import { encodePng } from '../../src/codec/png/encode.js';
import { Rect } from '../../src/geom/rect.js';
import { bench, paint } from '../helpers/bench.js';

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
 * @param {readonly string[]} argv
 * @param {string} [version]
 */
async function invoke(argv, version = '1.4.0') {
  /** @type {string[]} */
  const out = [];
  /** @type {string[]} */
  const err = [];
  const sink = io({ out: (line) => out.push(line), err: (line) => err.push(line) });
  const code = await main(argv, { sink, version });
  return { code, out, err };
}

describe('COMMANDS', () => {
  it('has every command the help lists', () => {
    expect(Object.keys(COMMANDS)).toEqual(['plan', 'crop', 'qc', 'batch', 'profile', 'verify']);
  });

  it('gives each one a usage, a description, options and a run', () => {
    for (const [name, command] of Object.entries(COMMANDS)) {
      expect(typeof command.usage, name).toBe('string');
      expect(typeof command.describe, name).toBe('string');
      expect(typeof command.options, name).toBe('object');
      expect(typeof command.run, name).toBe('function');
    }
  });

  it('names itself the same way in every usage line', () => {
    for (const command of Object.values(COMMANDS)) {
      expect(command.usage.startsWith(`${PROGRAM} `)).toBe(true);
    }
  });
});

describe('help', () => {
  it('lists every command with what it does', () => {
    const said = help('1.4.0').join('\n');
    for (const [name, command] of Object.entries(COMMANDS)) {
      expect(said).toContain(name);
      expect(said).toContain(command.describe);
    }
  });

  it('names the version when it has one', () => {
    expect(help('1.4.0')[0]).toMatch(/^deckle 1\.4\.0, /);
    expect(help()[0]).toMatch(/^deckle, /);
  });
});

describe('helpFor', () => {
  it('shows the usage and the options of one command', () => {
    const said = helpFor('crop').join('\n');
    expect(said).toMatch(/deckle crop <capture>/);
    expect(said).toMatch(/--out <string>/);
  });
});

describe('nearestCommands', () => {
  it('finds one that starts the same way', () => {
    expect(nearestCommands('cro')).toEqual(['crop']);
  });

  it('finds one the typed name starts with', () => {
    expect(nearestCommands('batchall')).toEqual(['batch']);
  });

  it('finds one with the same letters in another order', () => {
    expect(nearestCommands('propfile')).toContain('profile');
  });

  it('finds nothing for a word with nothing in common', () => {
    expect(nearestCommands('zzzz')).toEqual([]);
  });
});

describe('main', () => {
  it('shows the help and exits two when it was given nothing', async () => {
    const { code, out } = await invoke([]);
    expect(code).toBe(2);
    expect(out[0]).toMatch(/^deckle 1\.4\.0/);
  });

  it('shows the help and exits zero when it was asked for it', async () => {
    for (const argv of [['--help'], ['-h'], ['help']]) {
      const { code, out } = await invoke(argv);
      expect(code).toBe(0);
      expect(out.join('\n')).toMatch(/usage: deckle <command>/);
    }
  });

  it('says the version', async () => {
    expect((await invoke(['--version'])).out).toEqual(['1.4.0']);
    expect((await invoke(['-V'])).out).toEqual(['1.4.0']);
  });

  it('shows the help for one command', async () => {
    const { code, out } = await invoke(['crop', '--help']);
    expect(code).toBe(0);
    expect(out[0]).toMatch(/^deckle crop/);
  });

  it('runs a command', async () => {
    const root = await scratch();
    await writeFile(join(root, 'g4181.png'), capture());
    const { code, out } = await invoke(['plan', join(root, 'g4181.png')]);
    expect(code).toBe(0);
    expect(out[0]).toBe('g4181, profile house');
  });

  it('gives back the code the command gave', async () => {
    const root = await scratch();
    await writeFile(join(root, 'broken.png'), new Uint8Array([1]));
    expect((await invoke(['plan', join(root, 'broken.png')])).code).toBe(1);
  });

  it('refuses a command nobody has, and suggests one', async () => {
    const { code, err } = await invoke(['crup', 'x.png']);
    expect(code).toBe(2);
    expect(err[0]).toMatch(/no command called crup/);
    expect(err[1]).toMatch(/did you mean crop/);
  });

  it('lists the commands when nothing is close', async () => {
    const { err } = await invoke(['zzzz']);
    expect(err[1]).toMatch(/the commands are plan, crop, qc/);
  });

  it('exits two for an option a command does not have', async () => {
    const { code, err } = await invoke(['crop', 'x.png', '--colour']);
    expect(code).toBe(2);
    expect(err[0]).toMatch(/no such option/);
    expect(err[err.length - 1]).toMatch(/try deckle crop --help/);
  });

  it('gives the version to a command that records one', async () => {
    const root = await scratch();
    const { Workspace } = await import('../../src/store/workspace.js');
    const { writeAtomic, readText } = await import('../../src/store/atomic.js');
    const space = await Workspace.open(root);
    await writeAtomic(join(space.paths.incoming, 'g4181.png'), capture());

    await invoke(['batch', root, '-r', '400'], '3.2.1');
    const chain = JSON.parse(await readText(join(root, 'delivered', 'g4181', 'provenance.json')));
    expect(chain[0].agent).toBe('decklewright 3.2.1');
  });

  it('lets a command say its own version over the program one', async () => {
    const root = await scratch();
    const { Workspace } = await import('../../src/store/workspace.js');
    const { writeAtomic, readText } = await import('../../src/store/atomic.js');
    const space = await Workspace.open(root);
    await writeAtomic(join(space.paths.incoming, 'g4181.png'), capture());

    await invoke(['batch', root, '-r', '400', '--version', '9.9.9'], '3.2.1');
    const chain = JSON.parse(await readText(join(root, 'delivered', 'g4181', 'provenance.json')));
    expect(chain[0].agent).toBe('decklewright 9.9.9');
  });
});
