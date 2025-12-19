import { describe, expect, it } from 'vitest';

import {
  EVENT_TYPES,
  agentName,
  breaksIn,
  chainDigest,
  chainFor,
  describeChain,
  digestableForm,
  parseChain,
  recordEvent,
} from '../../src/manifest/provenance.js';
import { worked } from '../helpers/accession.js';

const AT = new Date('2025-11-11T08:00:00Z');

/**
 * @param {object} [options]
 * @returns {Parameters<typeof chainFor>[0]}
 */
function run(options = {}) {
  return { ...worked({ id: 'g4181', ...options }), agent: agentName({ version: '1.4.0' }), at: AT };
}

describe('agentName', () => {
  it('names the software and the version', () => {
    expect(agentName({ version: '1.4.0' })).toBe('decklewright 1.4.0');
  });

  it('names the stand when a lab has several', () => {
    expect(agentName({ version: '1.4.0', host: 'stand-2' })).toBe('decklewright 1.4.0 on stand-2');
  });
});

describe('recordEvent', () => {
  it('appends without touching the chain it was given', () => {
    const first = recordEvent([], { type: 'note', agent: 'a', detail: 'one', at: AT });
    const second = recordEvent(first, { type: 'note', agent: 'a', detail: 'two', at: AT });
    expect(first).toHaveLength(1);
    expect(second).toHaveLength(2);
  });

  it('points each event at the one before it', () => {
    const chain = recordEvent(
      recordEvent([], { type: 'note', agent: 'a', detail: 'one', at: AT }),
      { type: 'note', agent: 'a', detail: 'two', at: AT },
    );
    expect(chain[1].previous).toBe(chainDigest(chain.slice(0, 1)));
  });

  it('copies the values rather than sharing them', () => {
    const values = { degrees: 2 };
    const chain = recordEvent([], { type: 'note', agent: 'a', detail: 'x', values, at: AT });
    values.degrees = 9;
    expect(chain[0].values?.degrees).toBe(2);
  });

  it('leaves the values out when there are none', () => {
    expect(
      recordEvent([], { type: 'note', agent: 'a', detail: 'x', at: AT })[0].values,
    ).toBeUndefined();
  });

  it('takes a time rather than always reading the clock', () => {
    expect(recordEvent([], { type: 'note', agent: 'a', detail: 'x', at: AT })[0].at).toBe(
      '2025-11-11T08:00:00.000Z',
    );
  });

  it('refuses a kind of event nothing records', () => {
    expect(() => recordEvent([], { type: 'reticulated', agent: 'a', detail: 'x', at: AT })).toThrow(
      /nothing records an event of that kind/,
    );
  });
});

describe('digestableForm', () => {
  it('sorts the values, so key order cannot change the digest', () => {
    const one = digestableForm({
      type: 'note',
      at: 'x',
      agent: 'a',
      detail: 'd',
      previous: 'p',
      values: { b: 2, a: 1 },
    });
    const two = digestableForm({
      type: 'note',
      at: 'x',
      agent: 'a',
      detail: 'd',
      previous: 'p',
      values: { a: 1, b: 2 },
    });
    expect(one).toBe(two);
  });

  it('includes the values, so a changed one breaks the chain', () => {
    const base = { type: 'note', at: 'x', agent: 'a', detail: 'd', previous: 'p' };
    expect(digestableForm({ ...base, values: { a: 1 } })).not.toBe(
      digestableForm({ ...base, values: { a: 2 } }),
    );
  });
});

describe('breaksIn', () => {
  const good = chainFor(run());

  it('finds nothing wrong with a chain it built itself', () => {
    expect(breaksIn(good)).toEqual([]);
  });

  it('finds nothing wrong with an empty chain', () => {
    expect(breaksIn([])).toEqual([]);
  });

  it('catches an event edited after the fact', () => {
    const tampered = good.map((event, i) =>
      i === 1 ? { ...event, detail: 'nothing to see' } : event,
    );
    expect(breaksIn(tampered).length).toBeGreaterThan(0);
  });

  it('catches an event dropped out of the middle', () => {
    const shortened = [...good.slice(0, 2), ...good.slice(3)];
    expect(breaksIn(shortened)[0].at).toBe(2);
  });

  it('catches a kind of event nothing records', () => {
    const wrong = [...good];
    wrong[0] = { ...wrong[0], type: 'invented' };
    expect(breaksIn(wrong).some((b) => b.reason.includes('nothing records'))).toBe(true);
  });
});

describe('chainFor', () => {
  it('records the run in the order it happened', () => {
    const types = chainFor(run()).map((event) => event.type);
    expect(types.slice(0, 3)).toEqual(['capture-read', 'sheet-found', 'crop-planned']);
    expect(types[types.length - 1]).toBe('quality-checked');
  });

  it('records one event per file', () => {
    const made = chainFor(run()).filter((event) => event.type === 'derivative-made');
    expect(made).toHaveLength(3);
    expect(made[0].values?.file).toMatch(/_master\.png$/);
  });

  it('records the deskew only when there was one', () => {
    expect(chainFor(run()).some((e) => e.type === 'deskewed')).toBe(false);
    expect(chainFor(run({ degrees: 3 })).some((e) => e.type === 'deskewed')).toBe(true);
  });

  it('leaves the grading out when nothing was graded', () => {
    expect(chainFor(run({ graded: false })).some((e) => e.type === 'quality-checked')).toBe(false);
  });

  it('names the same agent throughout', () => {
    const agents = new Set(chainFor(run()).map((event) => event.agent));
    expect(agents.size).toBe(1);
  });

  it('records every kind it uses as one the reader knows', () => {
    for (const event of chainFor(run({ degrees: 3 }))) {
      expect(EVENT_TYPES).toContain(event.type);
    }
  });
});

describe('describeChain', () => {
  it('writes one line per event', () => {
    const lines = describeChain(chainFor(run()));
    expect(lines).toHaveLength(chainFor(run()).length);
    expect(lines[0]).toMatch(/^2025-11-11T08:00:00\.000Z capture-read: /);
  });
});

describe('parseChain', () => {
  it('reads a chain back through json', () => {
    const chain = chainFor(run());
    expect(parseChain(JSON.parse(JSON.stringify(chain)))).toHaveLength(chain.length);
  });

  it('refuses anything that is not a list', () => {
    expect(() => parseChain({ events: [] })).toThrow(/has to be a list/);
  });

  it('refuses a chain with a break in it', () => {
    const chain = chainFor(run());
    const tampered = [...chain.slice(0, 1), ...chain.slice(2)];
    expect(() => parseChain(tampered)).toThrow(/does not follow/);
  });

  it('reads an empty chain', () => {
    expect(parseChain([])).toEqual([]);
  });
});
