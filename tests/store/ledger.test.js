import { appendFile } from 'node:fs/promises';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { scratch } from '../helpers/scratch.js';

import {
  OUTCOMES,
  appendEntry,
  describeSummary,
  entryFor,
  latestByAccession,
  readLedger,
  select,
  summarise,
} from '../../src/store/ledger.js';

/** @returns {Promise<string>} */
async function ledgerPath() {
  return join(await scratch('ledger'), 'ledger.jsonl');
}

const ENTRIES = [
  entryFor({
    accession: 'a',
    outcome: 'delivered',
    profile: 'house',
    stars: 4,
    bytes: 100,
    at: '2025-11-20T09:00:00.000Z',
  }),
  entryFor({
    accession: 'b',
    outcome: 'rejected',
    profile: 'house',
    stars: 1,
    bytes: 80,
    at: '2025-11-20T09:05:00.000Z',
  }),
  entryFor({
    accession: 'c',
    outcome: 'failed',
    profile: 'quadrangles',
    reason: 'no neatline',
    at: '2025-11-21T09:00:00.000Z',
  }),
];

describe('entryFor', () => {
  it('always carries a time, an accession and an outcome', () => {
    const entry = entryFor({ accession: 'a', outcome: 'delivered' });
    expect(entry.accession).toBe('a');
    expect(entry.outcome).toBe('delivered');
    expect(entry.at).toMatch(/^\d{4}-\d{2}-\d{2}T/);
  });

  it('leaves out what it was not given', () => {
    expect(Object.keys(entryFor({ accession: 'a', outcome: 'skipped' }))).toEqual([
      'at',
      'accession',
      'outcome',
    ]);
  });

  it('keeps a zero star grading, which is not the same as none', () => {
    expect(entryFor({ accession: 'a', outcome: 'rejected', stars: 0 }).stars).toBe(0);
  });

  it('leaves an empty warning list out', () => {
    expect(
      entryFor({ accession: 'a', outcome: 'delivered', warnings: [] }).warnings,
    ).toBeUndefined();
  });

  it('copies the warnings rather than sharing them', () => {
    const warnings = ['one'];
    const entry = entryFor({ accession: 'a', outcome: 'delivered', warnings });
    warnings.push('two');
    expect(entry.warnings).toEqual(['one']);
  });

  it('refuses an outcome a run cannot have', () => {
    expect(() => entryFor({ accession: 'a', outcome: 'mislaid' })).toThrow(/does not end that way/);
  });

  it('names every outcome the reader knows', () => {
    for (const outcome of OUTCOMES) {
      expect(entryFor({ accession: 'a', outcome }).outcome).toBe(outcome);
    }
  });
});

describe('appendEntry and readLedger', () => {
  it('reads back nothing from a ledger that is not there yet', async () => {
    expect(await readLedger(await ledgerPath())).toEqual({ entries: [], unreadable: 0 });
  });

  it('reads back what it appended, in order', async () => {
    const path = await ledgerPath();
    for (const entry of ENTRIES) await appendEntry(path, entry);
    const { entries } = await readLedger(path);
    expect(entries.map((one) => one.accession)).toEqual(['a', 'b', 'c']);
  });

  it('keeps every field through the round trip', async () => {
    const path = await ledgerPath();
    await appendEntry(path, ENTRIES[2]);
    expect((await readLedger(path)).entries[0]).toEqual(ENTRIES[2]);
  });

  it('counts a torn line rather than losing the file behind it', async () => {
    const path = await ledgerPath();
    await appendEntry(path, ENTRIES[0]);
    await appendFile(path, '{"accession": "b", "outc\n');
    await appendEntry(path, ENTRIES[1]);

    const out = await readLedger(path);
    expect(out.entries.map((one) => one.accession)).toEqual(['a', 'b']);
    expect(out.unreadable).toBe(1);
  });

  it('counts a line that parses but is not an entry', async () => {
    const path = await ledgerPath();
    await appendFile(path, '{"note": "somebody was here"}\n');
    expect((await readLedger(path)).unreadable).toBe(1);
  });

  it('ignores blank lines', async () => {
    const path = await ledgerPath();
    await appendFile(path, '\n\n');
    expect(await readLedger(path)).toEqual({ entries: [], unreadable: 0 });
  });
});

describe('latestByAccession', () => {
  it('keeps the most recent for each', () => {
    const twice = [
      entryFor({ accession: 'a', outcome: 'failed', at: '2025-11-20T09:00:00.000Z' }),
      entryFor({ accession: 'a', outcome: 'delivered', at: '2025-11-21T09:00:00.000Z' }),
    ];
    expect(latestByAccession(twice).get('a')?.outcome).toBe('delivered');
  });

  it('does not depend on the order they were read in', () => {
    const twice = [
      entryFor({ accession: 'a', outcome: 'delivered', at: '2025-11-21T09:00:00.000Z' }),
      entryFor({ accession: 'a', outcome: 'failed', at: '2025-11-20T09:00:00.000Z' }),
    ];
    expect(latestByAccession(twice).get('a')?.outcome).toBe('delivered');
  });

  it('has one entry per accession', () => {
    expect(latestByAccession(ENTRIES).size).toBe(3);
  });
});

describe('select', () => {
  it('picks by outcome', () => {
    expect(select(ENTRIES, { outcome: 'failed' }).map((e) => e.accession)).toEqual(['c']);
  });

  it('picks by profile', () => {
    expect(select(ENTRIES, { profile: 'house' })).toHaveLength(2);
  });

  it('picks by day, taking since as inclusive', () => {
    expect(select(ENTRIES, { since: '2025-11-21T00:00:00.000Z' })).toHaveLength(1);
    expect(select(ENTRIES, { since: '2025-11-20T09:00:00.000Z' })).toHaveLength(3);
  });

  it('takes until as exclusive', () => {
    expect(select(ENTRIES, { until: '2025-11-20T09:05:00.000Z' })).toHaveLength(1);
  });

  it('takes several at once', () => {
    expect(select(ENTRIES, { profile: 'house', outcome: 'delivered' })).toHaveLength(1);
  });

  it('gives everything back for no filter', () => {
    expect(select(ENTRIES)).toHaveLength(3);
  });
});

describe('summarise', () => {
  it('counts each outcome', () => {
    expect(summarise(ENTRIES).byOutcome).toEqual({ delivered: 1, rejected: 1, failed: 1 });
  });

  it('adds the bytes up', () => {
    expect(summarise(ENTRIES).bytes).toBe(180);
  });

  it('averages over the graded ones and says how many those were', () => {
    const out = summarise(ENTRIES);
    expect(out.graded).toBe(2);
    expect(out.averageStars).toBe(2.5);
  });

  it('has no average when nothing was graded', () => {
    expect(summarise([ENTRIES[2]]).averageStars).toBeNull();
  });

  it('counts an empty ledger as empty', () => {
    expect(summarise([])).toEqual({
      total: 0,
      byOutcome: {},
      bytes: 0,
      graded: 0,
      averageStars: null,
    });
  });
});

describe('describeSummary', () => {
  it('opens with the totals', () => {
    expect(describeSummary(summarise(ENTRIES))[0]).toBe('3 accessions, 180 bytes');
  });

  it('lists the outcomes in a fixed order', () => {
    const lines = describeSummary(summarise(ENTRIES));
    expect(lines[1]).toBe('  1 delivered');
    expect(lines[2]).toBe('  1 rejected');
  });

  it('says nothing about stars when nothing was graded', () => {
    expect(describeSummary(summarise([ENTRIES[2]])).join(' ')).not.toMatch(/stars/);
  });
});
