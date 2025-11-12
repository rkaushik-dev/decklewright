import { mkdtemp, readFile, readdir, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

import { afterEach, describe, expect, it } from 'vitest';

import {
  appendLine,
  ensureDirectory,
  listNames,
  readBytes,
  readText,
  sweepTemporary,
  writeAtomic,
  writeSetAtomic,
} from '../../src/store/atomic.js';

/** @type {string[]} */
const made = [];

/** @returns {Promise<string>} */
async function scratch() {
  const path = await mkdtemp(join(tmpdir(), 'decklewright-'));
  made.push(path);
  return path;
}

afterEach(async () => {
  const { rm } = await import('node:fs/promises');
  await Promise.all(made.splice(0).map((path) => rm(path, { recursive: true, force: true })));
});

const BYTES = new Uint8Array([1, 2, 3, 4]);

describe('ensureDirectory', () => {
  it('makes a directory and its parents', async () => {
    const root = await scratch();
    await ensureDirectory(join(root, 'a', 'b', 'c'));
    expect(await readdir(join(root, 'a', 'b'))).toEqual(['c']);
  });

  it('is happy with one that is already there', async () => {
    const root = await scratch();
    await ensureDirectory(root);
    await expect(ensureDirectory(root)).resolves.toBeUndefined();
  });
});

describe('writeAtomic', () => {
  it('writes the bytes', async () => {
    const root = await scratch();
    const out = await writeAtomic(join(root, 'master.png'), BYTES);
    expect(out.bytes).toBe(4);
    expect(new Uint8Array(await readFile(join(root, 'master.png')))).toEqual(BYTES);
  });

  it('makes the folder on the way', async () => {
    const root = await scratch();
    await writeAtomic(join(root, 'deep', 'down', 'master.png'), BYTES);
    expect(await readdir(join(root, 'deep', 'down'))).toEqual(['master.png']);
  });

  it('leaves nothing behind', async () => {
    const root = await scratch();
    await writeAtomic(join(root, 'master.png'), BYTES);
    expect(await readdir(root)).toEqual(['master.png']);
  });

  it('replaces an existing file by default', async () => {
    const root = await scratch();
    await writeAtomic(join(root, 'a.txt'), 'first');
    await writeAtomic(join(root, 'a.txt'), 'second');
    expect(await readFile(join(root, 'a.txt'), 'utf8')).toBe('second');
  });

  it('refuses to replace one when it was told not to', async () => {
    const root = await scratch();
    await writeAtomic(join(root, 'a.txt'), 'first');
    await expect(writeAtomic(join(root, 'a.txt'), 'second', { overwrite: false })).rejects.toThrow(
      /already a file there/,
    );
    expect(await readFile(join(root, 'a.txt'), 'utf8')).toBe('first');
  });
});

describe('writeSetAtomic', () => {
  const entries = [
    { name: 'a_master.png', bytes: BYTES },
    { name: 'a_thumb.png', bytes: new Uint8Array([9]) },
    { name: 'sidecar.json', bytes: new TextEncoder().encode('{}') },
  ];

  it('writes every file', async () => {
    const root = await scratch();
    const out = await writeSetAtomic(join(root, 'a'), entries);
    expect(out.written).toHaveLength(3);
    expect(await readdir(join(root, 'a'))).toEqual(['a_master.png', 'a_thumb.png', 'sidecar.json']);
  });

  it('adds the set up', async () => {
    const root = await scratch();
    expect((await writeSetAtomic(join(root, 'a'), entries)).bytes).toBe(7);
  });

  it('leaves no part files behind', async () => {
    const root = await scratch();
    await writeSetAtomic(join(root, 'a'), entries);
    expect((await readdir(join(root, 'a'))).filter((n) => n.endsWith('.part'))).toEqual([]);
  });

  it('writes nothing at all when one of them cannot be written', async () => {
    const root = await scratch();
    const target = join(root, 'a');
    await ensureDirectory(target);
    await writeFile(join(target, 'a_thumb.png'), 'in the way');

    await expect(writeSetAtomic(target, entries, { overwrite: false })).rejects.toThrow(
      /already a file there/,
    );
    expect(await readdir(target)).toEqual(['a_thumb.png']);
  });

  it('refuses an empty set rather than making an empty folder', async () => {
    const root = await scratch();
    await expect(writeSetAtomic(join(root, 'a'), [])).rejects.toThrow(/nothing to write/);
  });

  it('writes into a subfolder when a name has one', async () => {
    const root = await scratch();
    await writeSetAtomic(join(root, 'a'), [{ name: join('scans', 'x.png'), bytes: BYTES }]);
    expect(await readdir(join(root, 'a', 'scans'))).toEqual(['x.png']);
  });
});

describe('appendLine', () => {
  it('makes the file if it is not there', async () => {
    const root = await scratch();
    await appendLine(join(root, 'ledger.jsonl'), 'one');
    expect(await readFile(join(root, 'ledger.jsonl'), 'utf8')).toBe('one\n');
  });

  it('adds to what is already there', async () => {
    const root = await scratch();
    await appendLine(join(root, 'ledger.jsonl'), 'one');
    await appendLine(join(root, 'ledger.jsonl'), 'two');
    expect(await readFile(join(root, 'ledger.jsonl'), 'utf8')).toBe('one\ntwo\n');
  });

  it('never lets a line become two', async () => {
    const root = await scratch();
    await appendLine(join(root, 'ledger.jsonl'), 'one\nsmuggled');
    expect((await readFile(join(root, 'ledger.jsonl'), 'utf8')).split('\n')).toHaveLength(2);
  });
});

describe('readBytes and readText', () => {
  it('reads back what was written', async () => {
    const root = await scratch();
    await writeAtomic(join(root, 'a.bin'), BYTES);
    expect(await readBytes(join(root, 'a.bin'))).toEqual(BYTES);
  });

  it('reads text back as text', async () => {
    const root = await scratch();
    await writeAtomic(join(root, 'a.txt'), 'plate 12');
    expect(await readText(join(root, 'a.txt'))).toBe('plate 12');
  });

  it('says which file it could not read', async () => {
    const root = await scratch();
    await expect(readBytes(join(root, 'missing.png'))).rejects.toThrow(/could not be read/);
  });
});

describe('listNames', () => {
  it('lists what is there, sorted', async () => {
    const root = await scratch();
    await writeAtomic(join(root, 'b.png'), BYTES);
    await writeAtomic(join(root, 'a.png'), BYTES);
    expect(await listNames(root)).toEqual(['a.png', 'b.png']);
  });

  it('leaves the leftovers of a crashed run out', async () => {
    const root = await scratch();
    await writeAtomic(join(root, 'a.png'), BYTES);
    await writeFile(join(root, 'a.png.1234.part'), 'half');
    expect(await listNames(root)).toEqual(['a.png']);
  });

  it('shows them when it is asked to', async () => {
    const root = await scratch();
    await writeFile(join(root, 'a.png.1234.part'), 'half');
    expect(await listNames(root, { includeTemporary: true })).toEqual(['a.png.1234.part']);
  });

  it('says which directory it could not list', async () => {
    const root = await scratch();
    await expect(listNames(join(root, 'nowhere'))).rejects.toThrow(/could not be listed/);
  });
});

describe('sweepTemporary', () => {
  it('removes what a crashed run left', async () => {
    const root = await scratch();
    await writeAtomic(join(root, 'a.png'), BYTES);
    await writeFile(join(root, 'a.png.1234.part'), 'half');
    expect(await sweepTemporary(root)).toEqual(['a.png.1234.part']);
    expect(await readdir(root)).toEqual(['a.png']);
  });

  it('leaves the finished files alone', async () => {
    const root = await scratch();
    await writeAtomic(join(root, 'a.png'), BYTES);
    expect(await sweepTemporary(root)).toEqual([]);
  });
});
