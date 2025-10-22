import { describe, expect, it } from 'vitest';

import {
  MAX_STEM,
  RESERVED,
  collisions,
  nameFor,
  namesFor,
  objectionsToNames,
  parseName,
  safeStem,
  slug,
} from '../../src/derive/naming.js';
import { ProfileRegistry } from '../../src/profile/registry.js';

const registry = new ProfileRegistry();
const house = registry.get('house');

/**
 * @param {string} pattern
 * @param {boolean} [lowercase]
 * @returns {Record<string, any>}
 */
function withPattern(pattern, lowercase = true) {
  return { ...house, naming: { pattern, lowercase } };
}

describe('slug', () => {
  it('leaves a name that is already safe alone', () => {
    expect(slug('g4181-1907')).toBe('g4181-1907');
  });

  it('turns a run of anything else into one separator', () => {
    expect(slug('sheet   17 / plate 4')).toBe('sheet-17-plate-4');
  });

  it('takes the accents off', () => {
    expect(slug('rivière-des-prairies')).toBe('riviere-des-prairies');
  });

  it('does not start or end with a separator', () => {
    expect(slug('  atlas  ')).toBe('atlas');
    expect(slug('///atlas///')).toBe('atlas');
  });

  it('keeps a dot, because an accession can have one', () => {
    expect(slug('g4181.c2')).toBe('g4181.c2');
  });

  it('takes another separator', () => {
    expect(slug('sheet 17', { separator: '_' })).toBe('sheet_17');
  });
});

describe('safeStem', () => {
  it('is the slug for an ordinary name', () => {
    expect(safeStem('Plate 12')).toBe('Plate-12');
  });

  it('cuts a name that is too long', () => {
    expect(safeStem('a'.repeat(200))).toHaveLength(MAX_STEM);
  });

  it('does not leave a cut name ending in a separator', () => {
    const cut = safeStem(`${'a'.repeat(MAX_STEM - 1)}-bbbb`);
    expect(cut.endsWith('-')).toBe(false);
  });

  it('gets a name past a reserved word rather than refusing it', () => {
    for (const word of RESERVED) {
      expect(safeStem(word)).toBe(`${word}_`);
    }
  });

  it('catches a reserved word whatever the case', () => {
    expect(safeStem('AUX')).toBe('AUX_');
  });

  it('refuses a name with nothing left in it', () => {
    expect(() => safeStem('///')).toThrow(/nothing left/);
  });
});

describe('nameFor', () => {
  it('fills the profile pattern', () => {
    const name = nameFor({
      profile: house,
      accession: 'G4181',
      derivative: 'master',
      extension: '.png',
    });
    expect(name).toBe('g4181_master.png');
  });

  it('keeps the case when the profile says to', () => {
    const name = nameFor({
      profile: withPattern('{accession}_{derivative}', false),
      accession: 'G4181',
      derivative: 'master',
      extension: '.png',
    });
    expect(name).toBe('G4181_master.png');
  });

  it('cleans the accession before it goes into the pattern', () => {
    const name = nameFor({
      profile: house,
      accession: 'sheet 17 / plate 4',
      derivative: 'thumb',
      extension: '.png',
    });
    expect(name).toBe('sheet-17-plate-4_thumb.png');
  });

  it('takes extra placeholders', () => {
    const name = nameFor({
      profile: withPattern('{collection}-{accession}'),
      accession: 'g4181',
      derivative: 'master',
      extension: '.png',
      extra: { collection: 'quads' },
    });
    expect(name).toBe('quads-g4181.png');
  });

  it('leaves a placeholder nobody filled visible in the name', () => {
    const name = nameFor({
      profile: withPattern('{accession}_{shelf}'),
      accession: 'g4181',
      derivative: 'master',
      extension: '.png',
    });
    expect(name).toMatch(/shelf/);
  });

  it('refuses an empty accession', () => {
    expect(() =>
      nameFor({ profile: house, accession: '', derivative: 'master', extension: '.png' }),
    ).toThrow();
  });
});

describe('namesFor', () => {
  const encoded = [
    { name: 'master', extension: '.png' },
    { name: 'service', extension: '.png' },
    { name: 'thumb', extension: '.pnm' },
  ];

  it('names every derivative in the set', () => {
    const names = namesFor({ profile: house, accession: 'g4181', encoded });
    expect(names).toEqual({
      master: 'g4181_master.png',
      service: 'g4181_service.png',
      thumb: 'g4181_thumb.pnm',
    });
  });

  it('gives each one the extension its own format asked for', () => {
    const names = namesFor({ profile: house, accession: 'g4181', encoded });
    expect(names.thumb.endsWith('.pnm')).toBe(true);
  });
});

describe('collisions', () => {
  it('finds two names that differ only in case', () => {
    expect(collisions(['Plate.png', 'plate.png'])).toEqual([['Plate.png', 'plate.png']]);
  });

  it('finds nothing in a set that is genuinely distinct', () => {
    expect(collisions(['a.png', 'b.png'])).toEqual([]);
  });

  it('groups three the same way it groups two', () => {
    expect(collisions(['A', 'a', 'A'])).toEqual([['A', 'A', 'a']]);
  });
});

describe('objectionsToNames', () => {
  it('has nothing to say about a clean set', () => {
    expect(objectionsToNames({ master: 'a_master.png', thumb: 'a_thumb.png' })).toEqual([]);
  });

  it('says so when a set is empty', () => {
    expect(objectionsToNames({})).toEqual(['nothing was named']);
  });

  it('names the pair that would collide', () => {
    const out = objectionsToNames({ a: 'Plate.png', b: 'plate.png' });
    expect(out[0]).toMatch(/differ only in case/);
  });

  it('says so when a name has no extension', () => {
    expect(objectionsToNames({ master: 'a_master' })[0]).toMatch(/no extension/);
  });
});

describe('parseName', () => {
  it('pulls the placeholders back out', () => {
    expect(parseName('g4181_master.png', house)).toEqual({
      accession: 'g4181',
      derivative: 'master',
    });
  });

  it('works without the extension', () => {
    expect(parseName('g4181_master', house)?.derivative).toBe('master');
  });

  it('gives nothing back for a name written under another pattern', () => {
    expect(parseName('g4181-master.png', house)).toBeNull();
  });

  it('splits at the first separator when the accession has one too', () => {
    expect(parseName('g4181_c2_master.png', house)).toEqual({
      accession: 'g4181',
      derivative: 'c2_master',
    });
  });

  it('handles a pattern with a placeholder at the end', () => {
    const profile = withPattern('{collection}-{accession}');
    expect(parseName('quads-g4181.png', profile)).toEqual({
      collection: 'quads',
      accession: 'g4181',
    });
  });

  it('round trips what nameFor wrote', () => {
    const name = nameFor({
      profile: house,
      accession: 'g4181',
      derivative: 'service',
      extension: '.png',
    });
    expect(parseName(name, house)).toEqual({ accession: 'g4181', derivative: 'service' });
  });
});
