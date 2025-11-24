import { describe, expect, it } from 'vitest';

import {
  dependencies,
  describeGraph,
  objectionsToGraph,
  order,
  producers,
  waves,
} from '../../src/pipeline/graph.js';
import { stage } from '../../src/pipeline/stage.js';

/**
 * @param {string} name
 * @param {readonly string[]} needs
 * @param {readonly string[]} produces
 * @returns {import('../../src/pipeline/stage.js').Stage}
 */
function step(name, needs, produces) {
  return stage({ name, needs, produces, run: () => ({}) });
}

const READ = step('read', ['bytes'], ['surface']);
const DETECT = step('detect', ['surface'], ['reading']);
const PLAN = step('plan', ['surface', 'reading'], ['plan']);
const DERIVE = step('derive', ['surface', 'plan'], ['files']);
const GRADE = step('grade', ['surface', 'reading'], ['quality']);
const PACK = step('pack', ['files', 'quality'], ['payload']);

const ALL = [PACK, DERIVE, GRADE, PLAN, DETECT, READ];

describe('producers', () => {
  it('says which stage makes each value', () => {
    const made = producers([READ, DETECT]);
    expect(made.get('surface')).toBe('read');
    expect(made.get('reading')).toBe('detect');
  });

  it('refuses two stages making the same value', () => {
    const other = step('another-read', [], ['surface']);
    expect(() => producers([READ, other])).toThrow(/produce the same value/);
  });

  it('has nothing to say about stages that make nothing', () => {
    expect(producers([step('quiet', ['surface'], [])]).size).toBe(0);
  });
});

describe('dependencies', () => {
  it('says what each stage waits for', () => {
    const { edges } = dependencies(ALL);
    expect([...(edges.get('plan') ?? [])].sort()).toEqual(['detect', 'read']);
  });

  it('names what the run has to supply itself', () => {
    expect(dependencies(ALL).fromOutside).toEqual(['bytes']);
  });

  it('does not make a stage wait for itself', () => {
    const both = step('both', ['x'], ['x']);
    expect([...(dependencies([both]).edges.get('both') ?? [])]).toEqual([]);
  });

  it('has nothing outside when every need is produced', () => {
    expect(dependencies([READ, DETECT]).fromOutside).toEqual(['bytes']);
    expect(dependencies([DETECT, PLAN]).fromOutside).toEqual(['surface']);
  });
});

describe('order', () => {
  it('puts every stage after the ones that feed it', () => {
    const names = order(ALL).map((item) => item.name);
    expect(names.indexOf('read')).toBeLessThan(names.indexOf('detect'));
    expect(names.indexOf('detect')).toBeLessThan(names.indexOf('plan'));
    expect(names.indexOf('derive')).toBeLessThan(names.indexOf('pack'));
    expect(names.indexOf('grade')).toBeLessThan(names.indexOf('pack'));
  });

  it('keeps the order it was given among stages that could go either way', () => {
    const left = step('left', ['surface'], ['a']);
    const right = step('right', ['surface'], ['b']);
    expect(order([READ, left, right]).map((one) => one.name)).toEqual(['read', 'left', 'right']);
    expect(order([READ, right, left]).map((one) => one.name)).toEqual(['read', 'right', 'left']);
  });

  it('gives the same answer whatever order it was handed', () => {
    const one = order(ALL).map((item) => item.name);
    const two = order([READ, DETECT, PLAN, DERIVE, GRADE, PACK]).map((item) => item.name);
    expect(new Set(one)).toEqual(new Set(two));
    expect(one.indexOf('read')).toBe(0);
  });

  it('refuses a circle', () => {
    const a = step('a', ['y'], ['x']);
    const b = step('b', ['x'], ['y']);
    expect(() => order([a, b])).toThrow(/wait on each other in a circle/);
  });

  it('refuses two stages with the same name', () => {
    expect(() => order([READ, READ])).toThrow(/same name/);
  });

  it('is happy with nothing at all', () => {
    expect(order([])).toEqual([]);
  });
});

describe('objectionsToGraph', () => {
  it('has nothing to say about a graph the run can feed', () => {
    expect(objectionsToGraph(ALL, ['bytes'])).toEqual([]);
  });

  it('says what nothing produces and the run does not supply', () => {
    expect(objectionsToGraph(ALL)[0]).toMatch(/nothing produces bytes/);
  });

  it('says so when there is nothing to run', () => {
    expect(objectionsToGraph([])).toEqual(['there are no stages to run']);
  });

  it('reports a circle rather than throwing', () => {
    const a = step('a', ['y'], ['x']);
    const b = step('b', ['x'], ['y']);
    expect(objectionsToGraph([a, b])[0]).toMatch(/circle/);
  });

  it('reports two stages making the same value', () => {
    const other = step('another', [], ['surface']);
    expect(objectionsToGraph([READ, other], ['bytes'])[0]).toMatch(/same value/);
  });
});

describe('describeGraph', () => {
  it('says what the run starts with', () => {
    expect(describeGraph(ALL)[0]).toBe('starts with bytes');
  });

  it('numbers the stages in the order they run', () => {
    const lines = describeGraph(ALL).filter((line) => /^\d+\./.test(line));
    expect(lines[0]).toBe('1. read making surface');
    expect(lines).toHaveLength(6);
  });

  it('says what each one waits for', () => {
    const said = describeGraph(ALL).join('\n');
    expect(said).toMatch(/plan after read, detect making plan/);
  });

  it('says nothing about waiting for a stage that waits for nothing', () => {
    expect(describeGraph([step('alone', [], ['x'])])).toEqual(['1. alone making x']);
  });
});

describe('waves', () => {
  it('groups the ones that could go at the same time', () => {
    const out = waves(ALL);
    expect(out[0]).toEqual(['read']);
    expect(out[1]).toEqual(['detect']);
    expect(out[out.length - 1]).toEqual(['pack']);
  });

  it('puts two independent stages in one wave', () => {
    const out = waves([READ, DETECT, PLAN, GRADE]);
    expect(out[2].sort()).toEqual(['grade', 'plan']);
  });

  it('has one wave for one stage', () => {
    expect(waves([step('alone', [], ['x'])])).toEqual([['alone']]);
  });
});
