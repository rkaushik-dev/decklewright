import { describe, expect, it } from 'vitest';

import {
  ALGORITHMS,
  DEFAULT_ALGORITHM,
  digest,
  digestOfSet,
  digests,
  fixityOf,
  fromSumFile,
  sameDigest,
  toSumFile,
  verifyAgainst,
} from '../../src/manifest/checksum.js';

/**
 * @param {string} text
 * @returns {Uint8Array}
 */
function bytes(text) {
  return new TextEncoder().encode(text);
}

const EMPTY_SHA256 = 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855';

describe('digest', () => {
  it('agrees with the published answer for nothing at all', () => {
    expect(digest(new Uint8Array(0))).toBe(EMPTY_SHA256);
  });

  it('agrees with the published answer for abc', () => {
    expect(digest(bytes('abc'))).toBe(
      'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad',
    );
    expect(digest(bytes('abc'), 'md5')).toBe('900150983cd24fb0d6963f7d28e17f72');
  });

  it('gives the same answer twice', () => {
    expect(digest(bytes('plate 12'))).toBe(digest(bytes('plate 12')));
  });

  it('gives a different answer for one byte different', () => {
    expect(digest(bytes('plate 12'))).not.toBe(digest(bytes('plate 13')));
  });

  it('refuses an algorithm no manifest here keeps', () => {
    expect(() => digest(bytes('a'), 'sha1')).toThrow(/no digest is kept/);
  });

  it('defaults to the one anybody would choose now', () => {
    expect(DEFAULT_ALGORITHM).toBe('sha256');
    expect(ALGORITHMS[0]).toBe(DEFAULT_ALGORITHM);
  });
});

describe('digests', () => {
  it('writes every algorithm a manifest carries', () => {
    expect(Object.keys(digests(bytes('a')))).toEqual(['sha256', 'md5']);
  });

  it('writes only what it was asked for', () => {
    expect(Object.keys(digests(bytes('a'), { algorithms: ['md5'] }))).toEqual(['md5']);
  });
});

describe('fixityOf', () => {
  it('keeps the size beside the digests', () => {
    const record = fixityOf(bytes('plate 12'));
    expect(record.bytes).toBe(8);
    expect(record.digests.sha256).toHaveLength(64);
  });
});

describe('verifyAgainst', () => {
  it('passes bytes that have not changed', () => {
    const out = verifyAgainst(bytes('abc'), fixityOf(bytes('abc')));
    expect(out.ok).toBe(true);
    expect(out.checked).toEqual(['sha256', 'md5']);
  });

  it('fails bytes that have', () => {
    const out = verifyAgainst(bytes('abd'), fixityOf(bytes('abc')));
    expect(out.ok).toBe(false);
    expect(out.failed).toEqual(['sha256', 'md5']);
  });

  it('catches a wrong size before it hashes anything', () => {
    const out = verifyAgainst(bytes('abcd'), fixityOf(bytes('abc')));
    expect(out.ok).toBe(false);
    expect(out.checked).toEqual([]);
    expect(out.reason).toMatch(/4 bytes and the record says 3/);
  });

  it('does not pass a record with no digest it can compute', () => {
    const out = verifyAgainst(bytes('abc'), { digests: { sha1: 'whatever' } });
    expect(out.ok).toBe(false);
    expect(out.reason).toMatch(/no digest we can compute/);
  });

  it('does not pass a record with no digests at all', () => {
    expect(verifyAgainst(bytes('abc'), {}).ok).toBe(false);
  });

  it('checks what it can when the record carries one of two', () => {
    const out = verifyAgainst(bytes('abc'), { digests: { md5: digest(bytes('abc'), 'md5') } });
    expect(out.ok).toBe(true);
    expect(out.checked).toEqual(['md5']);
  });

  it('names which algorithm disagreed', () => {
    const record = fixityOf(bytes('abc'));
    record.digests.md5 = 'x'.repeat(32);
    expect(verifyAgainst(bytes('abc'), record).reason).toBe('md5 do not match');
  });
});

describe('sameDigest', () => {
  it('ignores case and surrounding space', () => {
    expect(sameDigest(' ABC123 ', 'abc123')).toBe(true);
  });

  it('is false for anything that is not two strings', () => {
    expect(sameDigest(/** @type {any} */ (undefined), 'abc')).toBe(false);
    expect(sameDigest('abc', /** @type {any} */ (null))).toBe(false);
  });
});

describe('digestOfSet', () => {
  const files = [
    { name: 'a_master.png', bytes: bytes('one') },
    { name: 'a_thumb.png', bytes: bytes('two') },
  ];

  it('gives a folder one number', () => {
    expect(digestOfSet(files)).toHaveLength(64);
  });

  it('does not depend on the order the files came in', () => {
    expect(digestOfSet([...files].reverse())).toBe(digestOfSet(files));
  });

  it('changes when a file changes', () => {
    const other = [files[0], { name: 'a_thumb.png', bytes: bytes('three') }];
    expect(digestOfSet(other)).not.toBe(digestOfSet(files));
  });

  it('changes when a file is renamed', () => {
    const other = [{ name: 'a_service.png', bytes: bytes('one') }, files[1]];
    expect(digestOfSet(other)).not.toBe(digestOfSet(files));
  });

  it('changes when a file is dropped', () => {
    expect(digestOfSet([files[0]])).not.toBe(digestOfSet(files));
  });
});

describe('toSumFile and fromSumFile', () => {
  const entries = [
    { name: 'a_master.png', digests: digests(bytes('one')) },
    { name: 'a_thumb.png', digests: digests(bytes('two')) },
  ];

  it('writes the format sha256sum reads', () => {
    const text = toSumFile(entries);
    expect(text.split('\n')[0]).toBe(`${digest(bytes('one'))}  a_master.png`);
  });

  it('writes the algorithm it was asked for', () => {
    expect(toSumFile(entries, 'md5').split('\n')[0].split('  ')[0]).toHaveLength(32);
  });

  it('leaves out an entry that has no digest under that algorithm', () => {
    const thin = [{ name: 'a.png', digests: { md5: 'x'.repeat(32) } }];
    expect(toSumFile(thin, 'sha256')).toBe('');
  });

  it('reads back what it wrote', () => {
    const read = fromSumFile(toSumFile(entries));
    expect(read.map((r) => r.name)).toEqual(['a_master.png', 'a_thumb.png']);
    expect(read[0].digest).toBe(digest(bytes('one')));
  });

  it('reads the binary star form other tools write', () => {
    const read = fromSumFile(`${digest(bytes('one'))} *a_master.png`);
    expect(read[0].name).toBe('a_master.png');
  });

  it('skips a line nobody can parse rather than guessing', () => {
    expect(fromSumFile('this is not a checksum line\n\n')).toEqual([]);
  });

  it('lowercases what it reads, because half the world writes uppercase', () => {
    const read = fromSumFile(`${digest(bytes('one')).toUpperCase()}  a.png`);
    expect(read[0].digest).toBe(digest(bytes('one')));
  });
});
