import { describe, expect, it } from 'vitest';

import {
  describeFinding,
  measurements,
  oneLine,
  remedies,
  report,
  toJSON,
} from '../../src/qc/report.js';

/**
 * @param {string} name
 * @param {number} stars
 * @param {object} [options]
 * @returns {import('../../src/qc/check.js').Finding}
 */
function finding(name, stars, { ran = true, note = 'measured', measured = stars } = {}) {
  return { name, ran, passed: stars >= 3, measured, stars, note, limit: '3 stars' };
}

const good = [
  finding('sampling', 4, { note: '600 ppi against 600' }),
  finding('skew', 4, { note: '0.05 deg off square' }),
  finding('noise', 4, { note: '44 dB' }),
];

const mixed = [
  finding('sampling', 4, { note: '600 ppi against 600' }),
  finding('skew', 2, { note: '0.5 deg off square' }),
  finding('grayBalance', 0, { ran: false, note: 'nothing to measure, no target' }),
];

describe('describeFinding', () => {
  it('shows the stars and the note', () => {
    expect(describeFinding(finding('skew', 3, { note: 'a bit off' }))).toBe('skew: ***. a bit off');
  });

  it('pads so a column of them lines up', () => {
    expect(describeFinding(finding('a', 1, { note: 'x' }))).toBe('a: *... x');
    expect(describeFinding(finding('a', 4, { note: 'x' }))).toBe('a: **** x');
  });

  it('says a check was not measured rather than pretending it failed', () => {
    const out = describeFinding(finding('grayBalance', 0, { ran: false, note: 'no target' }));
    expect(out).toBe('grayBalance: not measured, no target');
  });
});

describe('report', () => {
  it('opens with the level', () => {
    expect(report(good)[0]).toBe('four stars');
  });

  it('names the item when it is given one', () => {
    expect(report(good, { item: 'q17' })[0]).toBe('q17: four stars');
  });

  it('says what is holding the level down', () => {
    expect(report(mixed)).toContain('asked for three stars, held down by skew');
  });

  it('says nothing about the level when it was reached', () => {
    expect(report(good).join('\n')).not.toMatch(/held down/);
  });

  it('counts the checks that could not run', () => {
    expect(report(mixed)).toContain('1 checks could not run');
  });

  it('lists every finding, including the ones that did not run', () => {
    const lines = report(mixed).filter((line) => line.startsWith('  '));
    expect(lines).toHaveLength(3);
    expect(lines[2]).toMatch(/grayBalance: not measured/);
  });

  it('says so when nothing could be measured', () => {
    const none = [finding('a', 0, { ran: false, note: 'no target' })];
    expect(report(none)).toContain('nothing could be measured');
  });
});

describe('oneLine', () => {
  it('writes one line for a batch log', () => {
    expect(oneLine(good, { item: 'q17' })).toBe('q17, four stars');
  });

  it('names the check holding it down', () => {
    expect(oneLine(mixed, { item: 'q18' })).toBe(
      'q18, two stars, held down by skew, 1 not measured',
    );
  });

  it('leaves the item out when there is none', () => {
    expect(oneLine(good)).toBe('four stars');
  });
});

describe('remedies', () => {
  it('says what to change for each check that fell short', () => {
    expect(remedies(mixed)).toEqual(['skew: square the sheet on the bed']);
  });

  it('says nothing when everything reached the level', () => {
    expect(remedies(good)).toEqual([]);
  });

  it('falls back for a check it has no advice for', () => {
    expect(remedies([finding('custom', 1)])).toEqual(['custom: look at the capture']);
  });

  it('takes another level to measure against', () => {
    expect(remedies(good, 4)).toEqual([]);
    expect(remedies([finding('noise', 3)], 4)[0]).toMatch(/lower the gain/);
  });
});

describe('measurements', () => {
  it('reduces the findings to numbers', () => {
    expect(measurements(good).sampling).toEqual({ measured: 4, stars: 4, ran: true });
  });

  it('writes nothing for a measurement that is not a number', () => {
    const out = measurements([finding('a', 0, { ran: false, measured: Number.NaN })]);
    expect(out.a.measured).toBeNull();
    expect(out.a.ran).toBe(false);
  });
});

describe('toJSON', () => {
  it('carries the level and whether it passed', () => {
    const out = toJSON(good, { item: 'q17' });
    expect(out.item).toBe('q17');
    expect(out.stars).toBe(4);
    expect(out.level).toBe('four stars');
    expect(out.passed).toBe(true);
    expect(out.worst).toBe('sampling');
  });

  it('carries the counts, the measurements and the remedies', () => {
    const out = toJSON(mixed);
    expect(out.ran).toBe(2);
    expect(out.skipped).toBe(1);
    expect(out.passed).toBe(false);
    expect(Object.keys(/** @type {any} */ (out.measurements))).toHaveLength(3);
    expect(out.remedies).toEqual(['skew: square the sheet on the bed']);
  });

  it('measures against the level it was told to want', () => {
    expect(toJSON(good, { wanted: 4 }).passed).toBe(true);
    expect(toJSON([finding('a', 3)], { wanted: 4 }).passed).toBe(false);
  });
});
