import { describe, expect, it } from 'vitest';

import { Mask } from '../../src/detect/mask.js';
import {
  checkAgainstClaim,
  countTicks,
  describeBar,
  findScaleBar,
  samplingFromBar,
} from '../../src/detect/scalebar.js';
import { Sampling } from '../../src/measure/resolution.js';
import { Rect } from '../../src/geom/rect.js';

/**
 * @param {readonly Rect[]} blocks
 * @param {number} [width]
 * @param {number} [height]
 * @returns {Mask}
 */
function maskWith(blocks, width = 200, height = 160) {
  const mask = new Mask(width, height);
  for (const rect of blocks) {
    for (let y = rect.top; y < rect.bottom; y += 1) {
      for (let x = rect.left; x < rect.right; x += 1) mask.set(x, y, 1);
    }
  }
  return mask;
}

const BAR = Rect.of(20, 10, 120, 8);
const SHEET = Rect.of(30, 40, 120, 90);

describe('findScaleBar', () => {
  it('finds the long thin block and not the sheet', () => {
    const found = findScaleBar(maskWith([BAR, SHEET]));
    expect(found?.rect.equals(BAR)).toBe(true);
    expect(found?.lengthPixels).toBe(120);
    expect(found?.aspect).toBe(15);
  });

  it('reads a vertical bar as a quarter turn', () => {
    const upright = Rect.of(10, 20, 8, 120);
    const found = findScaleBar(maskWith([upright, SHEET]));
    expect(found?.angle.degrees).toBe(90);
    expect(found?.lengthPixels).toBe(120);
  });

  it('finds nothing when there is no bar', () => {
    expect(findScaleBar(maskWith([SHEET]))).toBeNull();
  });

  it('ignores a block that is not thin enough', () => {
    const stubby = Rect.of(20, 10, 60, 30);
    expect(findScaleBar(maskWith([stubby]))).toBeNull();
  });

  it('ignores a bar that is too short', () => {
    const short = Rect.of(20, 10, 30, 3);
    expect(findScaleBar(maskWith([short]))).toBeNull();
    expect(findScaleBar(maskWith([short]), { minimumLength: 20 })?.lengthPixels).toBe(30);
  });

  it('ignores a diagonal streak, which does not fill its own box', () => {
    const mask = new Mask(200, 160);
    for (let i = 0; i < 120; i += 1) mask.set(20 + i, 10 + i, 1);
    expect(findScaleBar(mask)).toBeNull();
  });

  it('takes the longest bar when there are two', () => {
    const shorter = Rect.of(20, 30, 60, 6);
    const found = findScaleBar(maskWith([BAR, shorter]));
    expect(found?.lengthPixels).toBe(120);
  });

  it('skips components it is told to ignore', () => {
    const mask = maskWith([BAR, SHEET]);
    expect(findScaleBar(mask, { ignore: [1] })).toBeNull();
  });
});

describe('samplingFromBar', () => {
  it('turns a known length into a frequency', () => {
    const bar = { rect: BAR, lengthPixels: 400, aspect: 20, angle: { degrees: 0 } };
    expect(samplingFromBar(/** @type {any} */ (bar), 1, 'in').x).toBe(400);
    expect(samplingFromBar(/** @type {any} */ (bar), 25.4, 'mm').x).toBeCloseTo(400, 6);
  });

  it('refuses a length of zero or a bar with none', () => {
    const bar = { rect: BAR, lengthPixels: 400, aspect: 20, angle: { degrees: 0 } };
    expect(() => samplingFromBar(/** @type {any} */ (bar), 0, 'in')).toThrow(
      /length must be greater/,
    );
    const empty = { ...bar, lengthPixels: 0 };
    expect(() => samplingFromBar(/** @type {any} */ (empty), 1, 'in')).toThrow(/no length/);
  });

  it('refuses a unit it does not know', () => {
    const bar = { rect: BAR, lengthPixels: 400, aspect: 20, angle: { degrees: 0 } };
    expect(() => samplingFromBar(/** @type {any} */ (bar), 1, 'px')).toThrow(/unknown unit px/);
  });
});

describe('checkAgainstClaim', () => {
  it('accepts a small disagreement', () => {
    const out = checkAgainstClaim(Sampling.of(404), Sampling.of(400));
    expect(out.agrees).toBe(true);
    expect(out.error).toBeCloseTo(0.01, 6);
  });

  it('rejects a large one and signs it', () => {
    const low = checkAgainstClaim(Sampling.of(360), Sampling.of(400));
    expect(low.agrees).toBe(false);
    expect(low.error).toBeCloseTo(-0.1, 6);
    expect(low.measured).toBe(360);
    expect(low.claimed).toBe(400);
  });

  it('takes another tolerance', () => {
    expect(checkAgainstClaim(Sampling.of(360), Sampling.of(400), { tolerance: 0.2 }).agrees).toBe(
      true,
    );
  });
});

describe('countTicks', () => {
  it('counts the runs of ink along the bar', () => {
    const mask = maskWith([BAR]);
    const ink = new Mask(200, 160);
    for (let i = 0; i < 6; i += 1) {
      const x = BAR.left + 10 + i * 20;
      for (let y = BAR.top; y < BAR.bottom; y += 1) ink.set(x, y, 1);
    }
    const found = findScaleBar(mask);
    expect(countTicks(mask, /** @type {any} */ (found), ink)).toBe(6);
  });

  it('counts nothing on a blank bar', () => {
    const mask = maskWith([BAR]);
    const found = findScaleBar(mask);
    expect(countTicks(mask, /** @type {any} */ (found), new Mask(200, 160))).toBe(0);
  });

  it('walks a vertical bar the other way', () => {
    const upright = Rect.of(10, 20, 8, 120);
    const mask = maskWith([upright]);
    const ink = new Mask(200, 160);
    for (let i = 0; i < 4; i += 1) {
      const y = upright.top + 10 + i * 25;
      for (let x = upright.left; x < upright.right; x += 1) ink.set(x, y, 1);
    }
    const found = findScaleBar(mask);
    expect(countTicks(mask, /** @type {any} */ (found), ink)).toBe(4);
  });
});

describe('describeBar', () => {
  it('writes one line for the log', () => {
    const bar = { rect: BAR, lengthPixels: 400, aspect: 20, angle: { degrees: 0 } };
    expect(describeBar(/** @type {any} */ (bar), 1, 'in')).toBe('400 px for 1 in, so 400 ppi');
  });
});
