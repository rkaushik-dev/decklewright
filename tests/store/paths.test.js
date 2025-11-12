import { join, resolve, sep } from 'node:path';

import { describe, expect, it } from 'vitest';

import {
  FOLDERS,
  LEDGER_NAME,
  assertInside,
  deliveryPath,
  filePath,
  isInside,
  isTemporary,
  shortPath,
  temporaryFor,
  workspacePaths,
} from '../../src/store/paths.js';

const ROOT = resolve('/lab/oversize');

describe('workspacePaths', () => {
  it('has the four folders a run uses', () => {
    const paths = workspacePaths(ROOT);
    expect(paths.incoming).toBe(join(ROOT, 'incoming'));
    expect(paths.working).toBe(join(ROOT, 'working'));
    expect(paths.delivered).toBe(join(ROOT, 'delivered'));
    expect(paths.rejected).toBe(join(ROOT, 'rejected'));
  });

  it('puts the ledger at the root', () => {
    expect(workspacePaths(ROOT).ledger).toBe(join(ROOT, LEDGER_NAME));
  });

  it('resolves a relative root', () => {
    expect(workspacePaths('.').root).toBe(resolve('.'));
  });

  it('names the same folders the constant does', () => {
    const paths = workspacePaths(ROOT);
    for (const folder of Object.keys(FOLDERS)) {
      expect(paths[folder].endsWith(FOLDERS[folder])).toBe(true);
    }
  });
});

describe('deliveryPath', () => {
  const workspace = workspacePaths(ROOT);

  it('puts a delivery under delivered by default', () => {
    expect(deliveryPath(workspace, 'g4181-1907')).toBe(join(ROOT, 'delivered', 'g4181-1907'));
  });

  it('takes another folder', () => {
    expect(deliveryPath(workspace, 'g4181', { folder: 'rejected' })).toBe(
      join(ROOT, 'rejected', 'g4181'),
    );
  });

  it('cleans the accession the same way the file names are cleaned', () => {
    expect(deliveryPath(workspace, 'sheet 17 / plate 4')).toBe(
      join(ROOT, 'delivered', 'sheet-17-plate-4'),
    );
  });

  it('refuses a folder a workspace does not have', () => {
    expect(() => deliveryPath(workspace, 'a', { folder: 'archive' })).toThrow(/no folder by that/);
  });

  it('refuses to treat the root as a folder for deliveries', () => {
    expect(() => deliveryPath(workspace, 'a', { folder: 'root' })).toThrow(/no folder by that/);
  });

  it('refuses an accession that cleans away to nothing', () => {
    expect(() => deliveryPath(workspace, '///')).toThrow(/nothing left/);
  });
});

describe('filePath', () => {
  it('joins a name onto a directory', () => {
    expect(filePath('/lab/a', 'master.png')).toBe(join('/lab/a', 'master.png'));
  });

  it('refuses a name that climbs out', () => {
    expect(() => filePath('/lab/a', '../b/master.png')).toThrow(/outside the delivery/);
  });

  it('refuses an absolute name', () => {
    expect(() => filePath('/lab/a', resolve('/etc/passwd'))).toThrow(/outside the delivery/);
  });

  it('allows a name in a subfolder', () => {
    expect(filePath('/lab/a', join('scans', 'master.png'))).toBe(
      join('/lab/a', 'scans', 'master.png'),
    );
  });
});

describe('isInside', () => {
  it('is true for a path under the directory', () => {
    expect(isInside(ROOT, join(ROOT, 'delivered', 'a'))).toBe(true);
  });

  it('is false for a sibling', () => {
    expect(isInside(join(ROOT, 'delivered'), join(ROOT, 'rejected'))).toBe(false);
  });

  it('is false for the directory itself', () => {
    expect(isInside(ROOT, ROOT)).toBe(false);
  });

  it('is false for a path that climbs out and back in by name', () => {
    expect(isInside(join(ROOT, 'delivered'), `${join(ROOT, 'delivered')}-old`)).toBe(false);
  });
});

describe('assertInside', () => {
  it('gives the resolved path back', () => {
    expect(assertInside(ROOT, join(ROOT, 'a'))).toBe(resolve(ROOT, 'a'));
  });

  it('refuses one that is not inside', () => {
    expect(() => assertInside(ROOT, resolve('/elsewhere'))).toThrow(/outside the workspace/);
  });
});

describe('temporaryFor', () => {
  it('keeps the target name in it', () => {
    expect(temporaryFor('/lab/a/master.png', { token: 'x' })).toBe('/lab/a/master.png.x.part');
  });

  it('ends in part, so a sweep can find it', () => {
    expect(isTemporary(temporaryFor('/lab/a/master.png'))).toBe(true);
  });

  it('gives two different names without a token', () => {
    const first = temporaryFor('/lab/a/master.png', { token: 'one' });
    const second = temporaryFor('/lab/a/master.png', { token: 'two' });
    expect(first).not.toBe(second);
  });
});

describe('isTemporary', () => {
  it('is false for a finished file', () => {
    expect(isTemporary('master.png')).toBe(false);
  });

  it('is false for a name that only contains part', () => {
    expect(isTemporary('partial.png')).toBe(false);
  });
});

describe('shortPath', () => {
  it('keeps the last few segments', () => {
    expect(shortPath(join('a', 'b', 'c', 'd', 'e'), 2)).toBe(`...${sep}${join('d', 'e')}`);
  });

  it('leaves a short path alone', () => {
    expect(shortPath(join('a', 'b'))).toBe(join('a', 'b'));
  });
});
