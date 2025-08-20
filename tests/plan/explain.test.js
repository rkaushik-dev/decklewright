import { describe, expect, it } from 'vitest';

import { Angle } from '../../src/geom/angle.js';
import { Insets } from '../../src/geom/insets.js';
import { Rect } from '../../src/geom/rect.js';
import { Sampling } from '../../src/measure/resolution.js';
import { planWith } from '../../src/plan/engine.js';
import {
  biggestMove,
  describeMove,
  explain,
  explainSteps,
  explainWithRules,
  summarise,
} from '../../src/plan/explain.js';
import { CropPlan } from '../../src/plan/plan.js';
import { aspectRule } from '../../src/plan/rules/aspect.js';
import { bleedRule } from '../../src/plan/rules/bleed.js';
import { notEmptyRule } from '../../src/plan/rules/bounds.js';
import { resolutionRule } from '../../src/plan/rules/resolution.js';
import { snapRule } from '../../src/plan/rules/snap.js';

const capture = Rect.of(0, 0, 4000, 3200);
const anchor = Rect.of(400, 400, 2000, 1600);

/**
 * @param {import('../../src/plan/rules/rule.js').Rule[]} rules
 * @returns {CropPlan}
 */
function run(rules) {
  return planWith(rules, { capture, anchor, sampling: Sampling.of(400) });
}

describe('describeMove', () => {
  it('says nothing changed when nothing did', () => {
    expect(describeMove(anchor, anchor)).toBe('no change');
  });

  it('says how much it grew', () => {
    expect(describeMove(Rect.of(0, 0, 10, 10), Rect.of(0, 0, 20, 30))).toBe('grew by 10 by 20');
  });

  it('says how much it shrank', () => {
    expect(describeMove(Rect.of(0, 0, 20, 20), Rect.of(0, 0, 10, 10))).toBe('shrank by 10 by 10');
  });

  it('says how far it moved', () => {
    expect(describeMove(Rect.of(0, 0, 10, 10), Rect.of(5, -3, 10, 10))).toBe('moved by 5 and -3');
  });

  it('says both when both happened', () => {
    expect(describeMove(Rect.of(0, 0, 10, 10), Rect.of(-5, -5, 20, 20))).toBe(
      'grew by 10 by 10, moved by -5 and -5',
    );
  });
});

describe('explainSteps', () => {
  it('leaves out the rules that changed nothing', () => {
    const lines = explainSteps(run([bleedRule(Insets.uniform(20)), notEmptyRule()]));
    expect(lines).toHaveLength(1);
    expect(lines[0]).toMatch(/^bleed: kept 20 all round beyond the anchor \(grew by 40 by 40/);
  });

  it('gives nothing back for a plan nothing touched', () => {
    expect(explainSteps(run([notEmptyRule()]))).toEqual([]);
  });
});

describe('explain', () => {
  it('opens with the crop and its physical size', () => {
    const lines = explain(run([notEmptyRule()]));
    expect(lines[0]).toBe('crop 2000x1600+400+400 at 400 ppi');
    expect(lines[1]).toBe('which is 5.00 in by 4.00 in');
  });

  it('answers in another unit when asked', () => {
    const lines = explain(run([notEmptyRule()]), { unit: 'mm' });
    expect(lines[1]).toMatch(/^which is 127.0 mm by 101.6 mm$/);
  });

  it('mentions the deskew only when there is one', () => {
    expect(explain(run([notEmptyRule()])).join('\n')).not.toMatch(/skew/);
    const turned = CropPlan.start({
      capture,
      rect: anchor,
      sampling: Sampling.of(400),
      deskew: Angle.degrees(1.4),
    });
    expect(explain(turned).join('\n')).toMatch(/after taking out 1.4 deg of skew/);
  });

  it('says how many rules moved it', () => {
    const lines = explain(run([bleedRule(Insets.uniform(20)), snapRule(64), notEmptyRule()]));
    expect(lines.join('\n')).toMatch(/2 rules moved it:/);
  });

  it('says so when none did', () => {
    expect(explain(run([notEmptyRule()]))).toContain('no rule moved it');
  });

  it('prints the warnings at the end', () => {
    const lines = explain(run([resolutionRule(800), notEmptyRule()]));
    expect(lines.at(-1)).toMatch(/^warning: the capture is 400 ppi/);
  });
});

describe('explainWithRules', () => {
  const rules = [bleedRule(Insets.uniform(20)), snapRule(64), resolutionRule(300), notEmptyRule()];

  it('names the rules that ran and changed nothing', () => {
    const lines = explainWithRules(run(rules), rules);
    const quiet = lines.find((line) => line.startsWith('rules that changed nothing:'));
    expect(quiet).toMatch(/resolution/);
    expect(quiet).toMatch(/notEmpty/);
  });

  it('says nothing about quiet rules when every rule moved something', () => {
    const busy = [bleedRule(Insets.uniform(20)), snapRule(64)];
    const lines = explainWithRules(run(busy), busy);
    expect(lines.join('\n')).not.toMatch(/changed nothing/);
  });

  it('notes the pairs that are known to argue', () => {
    const tense = [aspectRule(1.5), snapRule(64), notEmptyRule()];
    const lines = explainWithRules(run(tense), tense);
    expect(lines.join('\n')).toMatch(/note: a forced aspect and a snap pull against each other/);
  });
});

describe('summarise', () => {
  it('writes one line for a batch log', () => {
    const out = summarise(run([bleedRule(Insets.uniform(20)), snapRule(64), notEmptyRule()]));
    expect(out).toMatch(/^\d+x\d+\+-?\d+\+-?\d+ after bleed then snap$/);
  });

  it('says nothing happened when nothing did', () => {
    expect(summarise(run([notEmptyRule()]))).toMatch(/after nothing$/);
  });

  it('counts the warnings', () => {
    expect(summarise(run([resolutionRule(800), notEmptyRule()]))).toMatch(/, 1 warnings$/);
  });
});

describe('biggestMove', () => {
  it('names the rule that changed the most area', () => {
    const out = biggestMove(run([bleedRule(Insets.uniform(100)), snapRule(8), notEmptyRule()]));
    expect(out?.rule).toBe('bleed');
    expect(out?.area).toBeGreaterThan(0);
  });

  it('gives nothing back when nothing moved', () => {
    expect(biggestMove(run([notEmptyRule()]))).toBeNull();
  });
});
