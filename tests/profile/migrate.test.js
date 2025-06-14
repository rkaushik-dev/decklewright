import { describe, expect, it } from 'vitest';

import { parseProfile } from '../../src/profile/fields.js';
import {
  CURRENT_VERSION,
  MIGRATIONS,
  migrate,
  migrateAll,
  migrationNotes,
  needsMigration,
  versionOf,
} from '../../src/profile/migrate.js';

/**
 * A profile as it was written before the renames.
 *
 * @returns {Record<string, any>}
 */
function oldProfile() {
  return {
    name: 'plat-books',
    crop: { margin: '3mm', roundTo: 8, straighten: true, anchor: 'sheet' },
    qc: { dpi: 600, dpiTolerance: '1%' },
    derivatives: [
      { name: 'master', depth: 16 },
      { name: 'service', maxSide: 3000, depth: 8 },
    ],
  };
}

describe('versionOf', () => {
  it('reads a written version', () => {
    expect(versionOf({ version: 2 })).toBe(2);
  });

  it('treats a document with no version as version one', () => {
    expect(versionOf({ name: 'a' })).toBe(1);
  });

  it('refuses a version that is not a whole number from one', () => {
    expect(() => versionOf({ version: 0 })).toThrow(/whole number from one/);
    expect(() => versionOf({ version: 1.5 })).toThrow(/whole number from one/);
    expect(() => versionOf({ version: 'two' })).toThrow(/whole number from one/);
  });
});

describe('needsMigration', () => {
  it('is true for an old document and false for a current one', () => {
    expect(needsMigration(oldProfile())).toBe(true);
    expect(needsMigration({ name: 'a', version: CURRENT_VERSION })).toBe(false);
  });
});

describe('migrate', () => {
  it('renames the crop settings', () => {
    const { document } = migrate(oldProfile());
    expect(document.crop.bleed).toBe('3mm');
    expect(document.crop.snapTo).toBe(8);
    expect(document.crop.deskew).toBe(true);
    expect(document.crop.margin).toBeUndefined();
    expect(document.crop.roundTo).toBeUndefined();
    expect(document.crop.straighten).toBeUndefined();
  });

  it('renames the quality settings', () => {
    const { document } = migrate(oldProfile());
    expect(document.qc.samplingPpi).toBe(600);
    expect(document.qc.samplingTolerance).toBe('1%');
    expect(document.qc.dpi).toBeUndefined();
  });

  it('renames and retypes the derivative settings', () => {
    const { document } = migrate(oldProfile());
    expect(document.derivatives[0].depth).toBe('16');
    expect(document.derivatives[1].longestSide).toBe(3000);
    expect(document.derivatives[1].maxSide).toBeUndefined();
  });

  it('stamps the new version and says what it applied', () => {
    const { document, applied } = migrate(oldProfile());
    expect(document.version).toBe(CURRENT_VERSION);
    expect(applied).toEqual([1]);
  });

  it('leaves a current document alone', () => {
    const current = { name: 'a', version: CURRENT_VERSION, crop: { bleed: '1mm' } };
    const { document, applied } = migrate(current);
    expect(applied).toEqual([]);
    expect(document).toBe(current);
  });

  it('does not touch the document it was given', () => {
    const before = oldProfile();
    migrate(before);
    expect(before.crop.margin).toBe('3mm');
    expect(before.version).toBeUndefined();
  });

  it('produces something the schema will read', () => {
    const { document } = migrate(oldProfile());
    const profile = parseProfile(document);
    expect(profile.crop.bleed.top.value).toBe(3);
    expect(profile.derivatives[1].longestSide).toBe(3000);
    expect(profile.qc.samplingPpi).toBe(600);
  });

  it('refuses a document from the future', () => {
    expect(() => migrate({ name: 'a', version: 99 })).toThrow(
      /written for version 99 and this build understands 2/,
    );
  });

  it('leaves a document that only has some of the old names alone otherwise', () => {
    const { document } = migrate({ name: 'a', crop: { anchor: 'sheet' } });
    expect(document.crop).toEqual({ anchor: 'sheet' });
  });

  it('copes with a document that has no groups at all', () => {
    expect(migrate({ name: 'a' }).document.version).toBe(CURRENT_VERSION);
  });
});

describe('migrateAll', () => {
  it('brings a whole folder forward', () => {
    const out = migrateAll([oldProfile(), { name: 'b', version: CURRENT_VERSION }]);
    expect(out).toHaveLength(2);
    expect(out[0].version).toBe(CURRENT_VERSION);
    expect(out[1].name).toBe('b');
  });
});

describe('migrationNotes', () => {
  it('says nothing about a current document', () => {
    expect(migrationNotes({ name: 'a', version: CURRENT_VERSION })).toEqual([]);
  });

  it('names each rename it had to make', () => {
    const notes = migrationNotes(oldProfile());
    expect(notes[0]).toMatch(/brought forward from version 1 through 1/);
    expect(notes).toContain('crop.margin is now crop.bleed');
    expect(notes).toContain('qc.dpi is now qc.samplingPpi');
    expect(notes).toContain('derivatives[].maxSide is now longestSide');
    expect(notes).toContain('derivatives[].depth is written as text now');
  });

  it('mentions only what the document actually used', () => {
    const notes = migrationNotes({ name: 'a', qc: { dpi: 400 } });
    expect(notes).toContain('qc.dpi is now qc.samplingPpi');
    expect(notes.join(' ')).not.toMatch(/crop.margin/);
  });
});

describe('the migration table', () => {
  it('has a step off every version below the current one', () => {
    for (let version = 1; version < CURRENT_VERSION; version += 1) {
      expect(typeof MIGRATIONS[version]).toBe('function');
    }
  });

  it('is frozen', () => {
    expect(Object.isFrozen(MIGRATIONS)).toBe(true);
  });
});
