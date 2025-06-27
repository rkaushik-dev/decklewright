import { describe, expect, it } from 'vitest';

import { Angle } from '../../src/geom/angle.js';
import { Rect } from '../../src/geom/rect.js';
import { Sampling } from '../../src/measure/resolution.js';
import { CropPlan } from '../../src/plan/plan.js';

const capture = Rect.of(0, 0, 1000, 800);

/**
 * @param {Rect} [rect]
 * @returns {CropPlan}
 */
function start(rect = Rect.of(100, 100, 400, 300)) {
  return CropPlan.start({ capture, rect, sampling: Sampling.of(400) });
}

describe('starting a plan', () => {
  it('records the anchor as the first step', () => {
    const plan = start();
    expect(plan.steps).toHaveLength(1);
    expect(plan.steps[0].rule).toBe('start');
    expect(plan.trail).toEqual([]);
  });

  it('is frozen, steps and warnings included', () => {
    const plan = start();
    expect(Object.isFrozen(plan)).toBe(true);
    expect(Object.isFrozen(plan.steps)).toBe(true);
    expect(Object.isFrozen(plan.warnings)).toBe(true);
  });

  it('has no deskew unless one was given', () => {
    expect(start().deskew.isZero).toBe(true);
    const turned = CropPlan.start({
      capture,
      rect: Rect.of(0, 0, 10, 10),
      sampling: Sampling.of(400),
      deskew: Angle.degrees(2),
    });
    expect(turned.deskew.degrees).toBe(2);
  });
});

describe('moved', () => {
  it('records the rule and both rects', () => {
    const plan = start().moved(Rect.of(90, 90, 420, 320), { rule: 'bleed', note: 'added 10 px' });
    expect(plan.trail).toEqual(['bleed']);
    expect(plan.steps[1].before.toJSON()).toEqual({ x: 100, y: 100, width: 400, height: 300 });
    expect(plan.steps[1].after.width).toBe(420);
    expect(plan.rect.width).toBe(420);
  });

  it('records nothing when the rule changed nothing', () => {
    const plan = start();
    const same = plan.moved(plan.rect, { rule: 'bleed', note: 'nothing to add' });
    expect(same).toBe(plan);
    expect(same.trail).toEqual([]);
  });

  it('leaves the plan it came from alone', () => {
    const first = start();
    first.moved(Rect.of(0, 0, 10, 10), { rule: 'snap', note: 'x' });
    expect(first.rect.width).toBe(400);
    expect(first.steps).toHaveLength(1);
  });

  it('keeps the trail in order over several rules', () => {
    const plan = start()
      .moved(Rect.of(90, 90, 420, 320), { rule: 'bleed', note: 'a' })
      .moved(Rect.of(88, 88, 424, 324), { rule: 'snap', note: 'b' })
      .moved(Rect.of(88, 88, 424, 320), { rule: 'aspect', note: 'c' });
    expect(plan.trail).toEqual(['bleed', 'snap', 'aspect']);
    expect(plan.touchedBy('snap')).toBe(true);
    expect(plan.touchedBy('resolution')).toBe(false);
  });
});

describe('warned', () => {
  it('collects warnings', () => {
    const plan = start().warned('the crop reaches off the bed');
    expect(plan.warnings).toEqual(['the crop reaches off the bed']);
  });

  it('says the same thing only once', () => {
    const plan = start().warned('same').warned('same');
    expect(plan.warnings).toHaveLength(1);
  });

  it('keeps the crop and the trail', () => {
    const plan = start()
      .moved(Rect.of(0, 0, 10, 10), { rule: 'bleed', note: 'a' })
      .warned('note');
    expect(plan.rect.width).toBe(10);
    expect(plan.trail).toEqual(['bleed']);
  });
});

describe('withDeskew', () => {
  it('sets the turn without touching anything else', () => {
    const plan = start().warned('x').withDeskew(Angle.degrees(-1.5));
    expect(plan.deskew.degrees).toBe(-1.5);
    expect(plan.warnings).toEqual(['x']);
  });
});

describe('what a plan knows about itself', () => {
  it('reports its size', () => {
    expect(start().size.toJSON()).toEqual({ width: 400, height: 300 });
  });

  it('says when the crop reaches past the capture and by how much', () => {
    expect(start().overhangs).toBe(false);
    expect(start().overhangArea).toBe(0);

    const over = start(Rect.of(900, 700, 400, 300));
    expect(over.overhangs).toBe(true);
    expect(over.overhangArea).toBe(400 * 300 - 100 * 100);
  });

  it('reports the physical size it will come out at', () => {
    const size = start().physical();
    expect(size).toEqual({ width: 1, height: 0.75, unit: 'in' });
    expect(start().physical('mm').width).toBeCloseTo(25.4, 9);
  });
});

describe('serialisation', () => {
  it('writes the rect, the trail and the warnings', () => {
    const json = start()
      .moved(Rect.of(0, 0, 10, 10), { rule: 'bleed', note: 'a' })
      .warned('w')
      .toJSON();
    expect(json.rect).toEqual({ x: 0, y: 0, width: 10, height: 10 });
    expect(json.sampling).toEqual({ x: 400, y: 400 });
    expect(json.deskew).toBe(0);
    expect(json.steps).toHaveLength(2);
    expect(json.steps[1].rule).toBe('bleed');
    expect(json.warnings).toEqual(['w']);
  });

  it('prints the crop and how many rules touched it', () => {
    expect(String(start().moved(Rect.of(0, 0, 10, 10), { rule: 'bleed', note: 'a' }))).toBe(
      '10x10+0+0 after 1 rules',
    );
  });
});
