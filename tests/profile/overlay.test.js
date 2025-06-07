import { describe, expect, it } from 'vitest';

import { changedPaths, overlay, overlayAll, resolveExtends } from '../../src/profile/overlay.js';

describe('overlay', () => {
  it('takes the patch over the base', () => {
    expect(overlay({ a: 1, b: 2 }, { b: 3 })).toEqual({ a: 1, b: 3 });
  });

  it('merges groups a level down', () => {
    const out = overlay({ crop: { anchor: 'sheet', ppi: 400 } }, { crop: { ppi: 600 } });
    expect(out).toEqual({ crop: { anchor: 'sheet', ppi: 600 } });
  });

  it('merges as deep as the groups go', () => {
    const out = overlay({ a: { b: { c: 1, d: 2 } } }, { a: { b: { d: 3 } } });
    expect(out).toEqual({ a: { b: { c: 1, d: 3 } } });
  });

  it('replaces a list rather than joining it', () => {
    const out = overlay({ derivatives: [{ name: 'a' }, { name: 'b' }] }, { derivatives: [] });
    expect(out.derivatives).toEqual([]);
  });

  it('removes a setting written as null', () => {
    expect(overlay({ a: 1, b: 2 }, { b: null })).toEqual({ a: 1 });
  });

  it('replaces a group with a plain value when the patch says so', () => {
    expect(overlay({ bleed: { top: 1 } }, { bleed: '3mm' })).toEqual({ bleed: '3mm' });
  });

  it('leaves the base untouched', () => {
    const base = { crop: { ppi: 400 } };
    overlay(base, { crop: { ppi: 600 } });
    expect(base.crop.ppi).toBe(400);
  });
});

describe('overlayAll', () => {
  it('folds a list oldest first', () => {
    expect(overlayAll([{ a: 1 }, { a: 2, b: 1 }, { b: 2 }])).toEqual({ a: 2, b: 2 });
  });

  it('gives an empty object for an empty list', () => {
    expect(overlayAll([])).toEqual({});
  });
});

describe('resolveExtends', () => {
  const library = {
    house: { name: 'house', crop: { anchor: 'sheet', minimumPpi: 300 }, qc: { minimumStars: 3 } },
    maps: { name: 'maps', extends: 'house', crop: { anchor: 'neatline' } },
    loops: { name: 'loops', extends: 'alsoLoops' },
    alsoLoops: { name: 'alsoLoops', extends: 'loops' },
  };
  const lookup = (name) => library[name];

  it('leaves a profile with no parent alone', () => {
    const { document, chain } = resolveExtends(library.house, lookup);
    expect(chain).toEqual([]);
    expect(document).toEqual(library.house);
  });

  it('flattens one level', () => {
    const { document, chain } = resolveExtends(library.maps, lookup);
    expect(chain).toEqual(['house']);
    expect(document.name).toBe('maps');
    expect(document.crop).toEqual({ anchor: 'neatline', minimumPpi: 300 });
    expect(document.qc).toEqual({ minimumStars: 3 });
    expect(document.extends).toBeUndefined();
  });

  it('flattens two levels in the right order', () => {
    const deep = { name: 'deep', extends: 'maps', crop: { minimumPpi: 600 } };
    const { document, chain } = resolveExtends(deep, (name) =>
      name === 'maps' ? library.maps : library[name],
    );
    expect(chain).toEqual(['maps', 'house']);
    expect(document.crop).toEqual({ anchor: 'neatline', minimumPpi: 600 });
  });

  it('refuses a parent that is not there and says which', () => {
    const orphan = { name: 'orphan', extends: 'hoose' };
    expect(() => resolveExtends(orphan, lookup)).toThrow(/no profile called hoose to extend/);
  });

  it('refuses a chain that loops', () => {
    expect(() => resolveExtends(library.loops, lookup)).toThrow(/loops back to loops/);
  });

  it('refuses a chain that is too deep', () => {
    const chainOf = (n) => ({ name: `p${n}`, extends: `p${n - 1}` });
    const lookupDeep = (name) => chainOf(Number(name.slice(1)));
    expect(() => resolveExtends(chainOf(20), lookupDeep, { limit: 3 })).toThrow(/too deep/);
  });

  it('keeps the child name rather than the parent one', () => {
    expect(resolveExtends(library.maps, lookup).document.name).toBe('maps');
  });
});

describe('changedPaths', () => {
  it('lists what the child actually set', () => {
    const base = { crop: { anchor: 'sheet', minimumPpi: 300 }, title: 'house' };
    const child = { crop: { anchor: 'neatline', minimumPpi: 300 } };
    expect(changedPaths(base, child)).toEqual(['crop.anchor']);
  });

  it('says nothing when the child changed nothing', () => {
    const base = { crop: { anchor: 'sheet' } };
    expect(changedPaths(base, { crop: { anchor: 'sheet' } })).toEqual([]);
  });

  it('reports a whole list as one path', () => {
    const base = { derivatives: [{ name: 'a' }] };
    const child = { derivatives: [{ name: 'a' }, { name: 'b' }] };
    expect(changedPaths(base, child)).toEqual(['derivatives']);
  });

  it('reports a setting the base did not have', () => {
    expect(changedPaths({}, { crop: { anchor: 'sheet' } })).toEqual(['crop']);
  });
});
