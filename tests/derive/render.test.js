import { describe, expect, it } from 'vitest';

import {
  cutMaster,
  cutOut,
  named,
  renderAll,
  renderDerivative,
  straighten,
  targetSize,
} from '../../src/derive/render.js';
import { Angle } from '../../src/geom/angle.js';
import { straightFrame } from '../../src/plan/crop.js';
import { Rect } from '../../src/geom/rect.js';
import { Size } from '../../src/geom/size.js';
import { Sampling } from '../../src/measure/resolution.js';
import { CropPlan } from '../../src/plan/plan.js';
import { Surface } from '../../src/raster/surface.js';
import { paint } from '../helpers/bench.js';

/**
 * @param {object} [options]
 * @param {Rect} [options.rect]
 * @param {number} [options.degrees]
 * @param {number} [options.ppi]
 * @returns {CropPlan}
 */
function plan({ rect = Rect.of(20, 20, 160, 120), degrees = 0, ppi = 400 } = {}) {
  const skew = Angle.degrees(degrees);
  return CropPlan.start({
    capture: straightFrame(Rect.of(0, 0, 200, 160), skew).capture,
    rect,
    sampling: Sampling.of(ppi),
    deskew: skew,
  });
}

/**
 * @param {object} [options]
 * @returns {Surface}
 */
function capture({ layout = 'rgb', depth = 8 } = {}) {
  const surface = Surface.create({ width: 200, height: 160, layout, depth, fill: 40 });
  paint(surface, Rect.of(20, 20, 160, 120), layout === 'gray' ? 200 : [200, 190, 180]);
  return surface;
}

const RECIPES = [
  { name: 'master', depth: 16, layout: 'rgb', kernel: 'lanczos3' },
  { name: 'service', longestSide: 80, depth: 8, layout: 'rgb', kernel: 'catmullRom' },
  { name: 'thumb', longestSide: 20, depth: 8, layout: 'gray', kernel: 'box' },
];

describe('straighten', () => {
  it('hands back what it was given when there is no turn to take', () => {
    const surface = capture();
    expect(straighten(surface, plan())).toBe(surface);
  });

  it('turns the capture the other way', () => {
    const turned = straighten(capture(), plan({ degrees: 3 }));
    expect(turned.width).toBeGreaterThan(200);
  });
});

describe('cutOut', () => {
  it('cuts the plan out of the capture', () => {
    const { surface: cut } = cutOut(capture(), plan());
    expect(cut.width).toBe(160);
    expect(cut.height).toBe(120);
  });

  it('pads rather than clips a crop that reaches past the edge', () => {
    const { surface: cut, filled } = cutOut(capture(), plan({ rect: Rect.of(150, 120, 100, 80) }), {
      fill: 7,
    });
    expect(Size.of(cut.width, cut.height)).toEqual(Size.of(100, 80));
    expect(filled).toBe(true);
    expect(cut.get(99, 79, 0)).toBe(7);
  });
});

describe('cutMaster', () => {
  it('says what it did', () => {
    const { steps } = cutMaster(capture(), plan());
    expect(steps.join(' ')).toMatch(/cut 160 by 120 out of 200 by 160/);
  });

  it('says nothing about straightening a capture that was square', () => {
    expect(cutMaster(capture(), plan()).steps.join(' ')).not.toMatch(/straightened/);
  });

  it('names the angle it took out', () => {
    const { steps } = cutMaster(capture(), plan({ degrees: 2 }));
    expect(steps[0]).toMatch(/straightened by 2\.000 degrees/);
  });

  it('says when it had to pad', () => {
    const { steps } = cutMaster(capture(), plan({ rect: Rect.of(150, 120, 100, 80) }));
    expect(steps.join(' ')).toMatch(/reaches \d+ px past the capture/);
  });
});

describe('targetSize', () => {
  it('is nothing when the recipe asks for no change', () => {
    expect(targetSize(Size.of(100, 80), { name: 'master' })).toBeNull();
  });

  it('fits the longest side', () => {
    expect(targetSize(Size.of(100, 80), { longestSide: 50 })).toEqual(Size.of(50, 40));
  });

  it('never enlarges to reach a longest side', () => {
    expect(targetSize(Size.of(100, 80), { longestSide: 500 })).toBeNull();
  });

  it('takes a plain scale', () => {
    expect(targetSize(Size.of(100, 80), { scale: 0.25 })).toEqual(Size.of(25, 20));
  });

  it('reads a scale of one as no change', () => {
    expect(targetSize(Size.of(100, 80), { scale: 1 })).toBeNull();
  });
});

describe('renderDerivative', () => {
  const master = cutOut(capture(), plan()).surface;
  const sampling = Sampling.of(400);

  it('leaves a master at the size it was cut', () => {
    const out = renderDerivative(master, RECIPES[0], { sampling });
    expect(out.surface.width).toBe(160);
    expect(out.scale).toBe(1);
  });

  it('carries the frequency down with the pixels', () => {
    const out = renderDerivative(master, RECIPES[1], { sampling });
    expect(out.surface.width).toBe(80);
    expect(out.sampling.x).toBe(200);
  });

  it('leaves the frequency alone when the size is left alone', () => {
    expect(renderDerivative(master, RECIPES[0], { sampling }).sampling.x).toBe(400);
  });

  it('changes the layout the recipe asks for', () => {
    const out = renderDerivative(master, RECIPES[2], { sampling });
    expect(out.surface.layout).toBe('gray');
    expect(out.surface.channels).toBe(1);
  });

  it('changes the depth the recipe asks for', () => {
    expect(renderDerivative(master, RECIPES[0], { sampling }).surface.depth).toBe(16);
    expect(renderDerivative(master, RECIPES[1], { sampling }).surface.depth).toBe(8);
  });

  it('halves its way down a large reduction', () => {
    const out = renderDerivative(master, RECIPES[2], { sampling });
    expect(out.steps.join(' ')).toMatch(/after [1-9] halvings/);
  });

  it('resamples in one pass for a small reduction', () => {
    const out = renderDerivative(master, { name: 'near', longestSide: 120 }, { sampling });
    expect(out.steps.join(' ')).not.toMatch(/halvings/);
    expect(out.surface.width).toBe(120);
  });

  it('says what it did, in the order it did it', () => {
    const deep = cutOut(capture({ depth: 16 }), plan()).surface;
    const out = renderDerivative(deep, RECIPES[2], { sampling });
    expect(out.steps[0]).toBe('to gray');
    expect(out.steps[1]).toMatch(/^to 20 by 15/);
    expect(out.steps[2]).toBe('to 8 bit');
  });

  it('says nothing about a depth it did not have to change', () => {
    const out = renderDerivative(master, RECIPES[2], { sampling });
    expect(out.steps.join(' ')).not.toMatch(/bit/);
  });

  it('refuses a recipe with no name', () => {
    expect(() => renderDerivative(master, { longestSide: 40 }, { sampling })).toThrow(/no name/);
  });
});

describe('renderAll', () => {
  it('renders every recipe off one cut', () => {
    const out = renderAll({ surface: capture(), plan: plan(), recipes: RECIPES });
    expect(out.rendered.map((r) => r.name)).toEqual(['master', 'service', 'thumb']);
    expect(out.master.width).toBe(160);
  });

  it('cuts once, so every derivative agrees on the frame', () => {
    const out = renderAll({ surface: capture(), plan: plan({ degrees: 2 }), recipes: RECIPES });
    const aspects = out.rendered.map((r) => (r.surface.width / r.surface.height).toFixed(1));
    expect(new Set(aspects).size).toBe(1);
  });

  it('carries the cut steps as well as the render steps', () => {
    const out = renderAll({ surface: capture(), plan: plan(), recipes: RECIPES });
    expect(out.cut.join(' ')).toMatch(/cut 160 by 120/);
  });

  it('refuses a profile that asks for nothing', () => {
    expect(() => renderAll({ surface: capture(), plan: plan(), recipes: [] })).toThrow(
      /no derivatives/,
    );
  });

  it('refuses two derivatives under the same name', () => {
    const twice = [RECIPES[0], { ...RECIPES[1], name: 'master' }];
    expect(() => renderAll({ surface: capture(), plan: plan(), recipes: twice })).toThrow(
      /same name/,
    );
  });
});

describe('named', () => {
  it('picks one out by name', () => {
    const out = renderAll({ surface: capture(), plan: plan(), recipes: RECIPES });
    expect(named(out.rendered, 'thumb').surface.layout).toBe('gray');
  });

  it('refuses a name that was never rendered', () => {
    const out = renderAll({ surface: capture(), plan: plan(), recipes: RECIPES });
    expect(() => named(out.rendered, 'poster')).toThrow(/no derivative/);
  });
});
