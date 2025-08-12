import { describe, expect, it } from 'vitest';

import { Rect } from '../../../src/geom/rect.js';
import { Sampling } from '../../../src/measure/resolution.js';
import { CropPlan } from '../../../src/plan/plan.js';
import {
  BOUNDS_MODES,
  boundsFor,
  clampToCaptureRule,
  notEmptyRule,
  nudgeIntoCaptureRule,
  refuseOverhangRule,
  warnOnOverhangRule,
} from '../../../src/plan/rules/bounds.js';

const capture = Rect.of(0, 0, 1000, 800);

/**
 * @param {Rect} rect
 * @returns {CropPlan}
 */
function start(rect) {
  return CropPlan.start({ capture, rect, sampling: Sampling.of(400) });
}

const inside = start(Rect.of(100, 100, 400, 300));
const over = start(Rect.of(900, 700, 400, 300));

describe('warnOnOverhangRule', () => {
  it('says nothing about a crop that is inside', () => {
    expect(warnOnOverhangRule().apply(inside, {})).toBe(inside);
  });

  it('warns and changes nothing', () => {
    const out = warnOnOverhangRule().apply(over, {});
    expect(out.rect.equals(over.rect)).toBe(true);
    expect(out.warnings[0]).toBe('110000 px of the crop falls off the capture');
  });

  it('warns once when run twice', () => {
    const once = warnOnOverhangRule().apply(over, {});
    expect(warnOnOverhangRule().apply(once, {}).warnings).toHaveLength(1);
  });
});

describe('clampToCaptureRule', () => {
  it('leaves a crop that is inside alone', () => {
    expect(clampToCaptureRule().apply(inside, {})).toBe(inside);
  });

  it('cuts the crop back to the capture and says so', () => {
    const out = clampToCaptureRule().apply(over, {});
    expect(out.rect.toJSON()).toEqual({ x: 900, y: 700, width: 100, height: 100 });
    expect(out.warnings[0]).toMatch(/trimmed at the edge of the capture/);
    expect(out.trail).toEqual(['bounds']);
  });

  it('refuses a crop that misses the capture entirely', () => {
    const away = start(Rect.of(2000, 2000, 100, 100));
    expect(() => clampToCaptureRule().apply(away, {})).toThrow(/does not touch the capture/);
  });

  it('lands in the same place when run twice', () => {
    const once = clampToCaptureRule().apply(over, {});
    expect(clampToCaptureRule().apply(once, {})).toBe(once);
  });
});

describe('nudgeIntoCaptureRule', () => {
  it('leaves a crop that is inside alone', () => {
    expect(nudgeIntoCaptureRule().apply(inside, {})).toBe(inside);
  });

  it('slides the crop back on and keeps its size', () => {
    const out = nudgeIntoCaptureRule().apply(over, {});
    expect(out.rect.size.toJSON()).toEqual({ width: 400, height: 300 });
    expect(capture.containsRect(out.rect)).toBe(true);
    expect(out.warnings).toEqual([]);
  });

  it('centres a crop larger than the capture and says so', () => {
    const huge = start(Rect.of(0, 0, 1400, 1000));
    const out = nudgeIntoCaptureRule().apply(huge, {});
    expect(out.rect.centre.equals(capture.centre)).toBe(true);
    expect(out.warnings[0]).toMatch(/larger than the capture and was centred/);
  });

  it('lands in the same place when run twice', () => {
    const once = nudgeIntoCaptureRule().apply(over, {});
    expect(nudgeIntoCaptureRule().apply(once, {})).toBe(once);
  });
});

describe('refuseOverhangRule', () => {
  it('lets a crop that is inside through', () => {
    expect(refuseOverhangRule().apply(inside, {})).toBe(inside);
  });

  it('stops on a crop that is not', () => {
    expect(() => refuseOverhangRule().apply(over, {})).toThrow(/will not pad/);
  });

  it('carries the overhang in the error', () => {
    try {
      refuseOverhangRule().apply(over, {});
      throw new Error('should have thrown');
    } catch (err) {
      expect(err.details.overhang).toBe(110000);
    }
  });
});

describe('boundsFor', () => {
  it('gives every named mode', () => {
    expect(BOUNDS_MODES).toHaveLength(4);
    for (const mode of BOUNDS_MODES) {
      expect(boundsFor(mode).name).toBe('bounds');
    }
  });

  it('behaves differently under each one', () => {
    expect(boundsFor('warn').apply(over, {}).rect.width).toBe(400);
    expect(boundsFor('clamp').apply(over, {}).rect.width).toBe(100);
    expect(boundsFor('nudge').apply(over, {}).rect.width).toBe(400);
    expect(() => boundsFor('refuse').apply(over, {})).toThrow();
  });

  it('warns by default', () => {
    expect(boundsFor().apply(over, {}).warnings).toHaveLength(1);
  });

  it('refuses a mode it does not know', () => {
    expect(() => boundsFor('shrug')).toThrow(/mode must be one of/);
  });
});

describe('notEmptyRule', () => {
  it('lets a crop with area through', () => {
    expect(notEmptyRule().apply(inside, {})).toBe(inside);
  });

  it('stops on a crop with none, and names the rules that got there', () => {
    const empty = start(Rect.of(10, 10, 400, 300)).moved(Rect.of(10, 10, 0, 300), {
      rule: 'safeArea',
      note: 'x',
    });
    try {
      notEmptyRule().apply(empty, {});
      throw new Error('should have thrown');
    } catch (err) {
      expect(err.message).toMatch(/left no crop at all/);
      expect(err.details.trail).toEqual(['safeArea']);
    }
  });
});
