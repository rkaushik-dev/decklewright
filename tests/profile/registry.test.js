import { describe, expect, it } from 'vitest';

import { ProfileRegistry, registryWith } from '../../src/profile/registry.js';

describe('a fresh registry', () => {
  it('holds the shipped profiles', () => {
    const registry = new ProfileRegistry();
    expect(registry.names()).toContain('house');
    expect(registry.names()).toContain('quadrangles');
    expect(registry.has('microfilm')).toBe(true);
    expect(registry.has('plat-books')).toBe(false);
  });

  it('can be built with nothing in it', () => {
    expect(new ProfileRegistry({ builtIn: false }).names()).toEqual([]);
  });

  it('names what it knows when a lookup misses', () => {
    try {
      new ProfileRegistry().get('plat-books');
      throw new Error('should have thrown');
    } catch (err) {
      expect(err.message).toBe('there is no profile called plat-books');
      expect(err.details.known).toContain('house');
    }
  });
});

describe('get', () => {
  const registry = new ProfileRegistry();

  it('flattens the extends chain', () => {
    const profile = registry.get('quadrangles');
    expect(profile.crop.anchor).toBe('neatline');
    expect(profile.naming.pattern).toBe('{accession}_{derivative}');
    expect(profile.derivatives).toHaveLength(3);
  });

  it('hands back the same object twice', () => {
    expect(registry.get('atlas-plates')).toBe(registry.get('atlas-plates'));
  });

  it('refuses a profile that cannot be used', () => {
    const broken = registryWith([
      { name: 'broken', extends: 'house', crop: { anchor: 'neatline' } },
    ]);
    expect(() => broken.get('broken')).toThrow(/the broken profile cannot be used/);
  });
});

describe('add', () => {
  it('takes a site profile that extends a shipped one', () => {
    const registry = registryWith([
      { name: 'plat-books', extends: 'atlas-plates', crop: { bleed: '5mm' } },
    ]);
    const profile = registry.get('plat-books');
    expect(profile.crop.bleed.top.value).toBe(5);
    expect(profile.crop.minimumPpi).toBe(400);
    expect(profile.qc.samplingPpi).toBe(600);
  });

  it('lets a site replace a shipped profile', () => {
    const registry = registryWith([{ name: 'house', crop: { minimumPpi: 150 } }]);
    expect(registry.get('house').crop.minimumPpi).toBe(150);
    expect(registry.get('atlas-plates').crop.minimumPpi).toBe(400);
  });

  it('clears what it had already worked out', () => {
    const registry = new ProfileRegistry();
    expect(registry.get('house').crop.minimumPpi).toBe(300);
    registry.add({ name: 'house', crop: { minimumPpi: 150 } });
    expect(registry.get('house').crop.minimumPpi).toBe(150);
  });

  it('refuses a document with no name', () => {
    expect(() => new ProfileRegistry().add({ crop: {} })).toThrow(/needs a name/);
  });

  it('returns itself so calls chain', () => {
    const registry = new ProfileRegistry({ builtIn: false });
    expect(registry.add({ name: 'a' }).add({ name: 'b' }).names()).toEqual(['a', 'b']);
  });
});

describe('document', () => {
  it('hands back what was written, not what was worked out', () => {
    const registry = new ProfileRegistry();
    expect(registry.document('quadrangles').extends).toBe('house');
    expect(registry.get('quadrangles').extends).toBeUndefined();
  });
});

describe('inspect', () => {
  it('says nothing about a shipped profile', () => {
    expect(new ProfileRegistry().inspect('quadrangles')).toEqual([]);
  });

  it('reports the problems of a site profile without throwing', () => {
    const registry = registryWith([
      { name: 'odd', extends: 'house', crop: { anchor: 'neatline' } },
    ]);
    const problems = registry.inspect('odd');
    expect(problems).toHaveLength(1);
    expect(problems[0].severity).toBe('fault');
  });
});

describe('explain', () => {
  const registry = new ProfileRegistry();

  it('says what a profile inherited and what it changed', () => {
    const out = registry.explain('atlas-plates');
    expect(out.chain).toEqual(['house']);
    expect(out.changed).toContain('crop.minimumPpi');
    expect(out.changed).toContain('qc.samplingPpi');
    expect(out.changed).not.toContain('naming.pattern');
  });

  it('says nothing was inherited for a root profile', () => {
    const out = registry.explain('house');
    expect(out.chain).toEqual([]);
    expect(out.changed.length).toBeGreaterThan(0);
  });

  it('counts the problems it found', () => {
    const registry2 = registryWith([{ name: 'noisy', extends: 'house', derivatives: [] }]);
    expect(registry2.explain('noisy').problems).toBeGreaterThan(0);
  });
});

describe('inspectAll', () => {
  it('says nothing about a registry of shipped profiles', () => {
    expect(new ProfileRegistry().inspectAll()).toEqual({});
  });

  it('collects the problems of every profile that has any', () => {
    const registry = registryWith([
      { name: 'bad', extends: 'house', crop: { anchor: 'neatline' } },
      { name: 'quiet', extends: 'house' },
    ]);
    const out = registry.inspectAll();
    expect(Object.keys(out)).toEqual(['bad']);
    expect(out.bad[0]).toMatch(/crop.anchor/);
  });

  it('catches a profile whose parent is missing', () => {
    const registry = registryWith([{ name: 'orphan', extends: 'nowhere' }]);
    expect(registry.inspectAll().orphan[0]).toMatch(/no profile called nowhere/);
  });
});
