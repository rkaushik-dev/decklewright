import { describe, expect, it } from 'vitest';

import { Mask } from '../../src/detect/mask.js';
import { boundary, close, dilate, erode, fillHoles, open } from '../../src/detect/morphology.js';

/**
 * @param {readonly string[]} rows using # for set
 * @returns {Mask}
 */
function maskOf(rows) {
  const mask = new Mask(rows[0].length, rows.length);
  rows.forEach((row, y) => {
    [...row].forEach((ch, x) => mask.set(x, y, ch === '#' ? 1 : 0));
  });
  return mask;
}

/**
 * @param {Mask} mask
 * @returns {string[]}
 */
function draw(mask) {
  const out = [];
  for (let y = 0; y < mask.height; y += 1) {
    out.push([...mask.row(y)].map((v) => (v ? '#' : '.')).join(''));
  }
  return out;
}

describe('erode', () => {
  it('keeps only pixels whose whole neighbourhood is set', () => {
    const mask = maskOf(['.....', '.###.', '.###.', '.###.', '.....']);
    expect(draw(erode(mask, 1))).toEqual(['.....', '.....', '..#..', '.....', '.....']);
  });

  it('eats into a block that touches the border', () => {
    const mask = maskOf(['###', '###', '###']);
    expect(erode(mask, 1).count).toBe(1);
  });

  it('is a copy at radius zero', () => {
    const mask = maskOf(['##', '..']);
    const out = erode(mask, 0);
    expect(draw(out)).toEqual(draw(mask));
    expect(out).not.toBe(mask);
  });

  it('removes a single pixel entirely', () => {
    expect(erode(maskOf(['...', '.#.', '...']), 1).count).toBe(0);
  });

  it('refuses a negative radius', () => {
    expect(() => erode(maskOf(['#']), -1)).toThrow(/radius must not be negative/);
  });
});

describe('dilate', () => {
  it('grows a single pixel into a square', () => {
    expect(draw(dilate(maskOf(['.....', '.....', '..#..', '.....', '.....']), 1))).toEqual([
      '.....',
      '.###.',
      '.###.',
      '.###.',
      '.....',
    ]);
  });

  it('clips at the border', () => {
    expect(dilate(maskOf(['#..', '...', '...']), 1).count).toBe(4);
  });

  it('leaves an empty mask empty', () => {
    expect(dilate(new Mask(4, 4), 2).count).toBe(0);
  });

  it('grows by the radius it is given', () => {
    const mask = new Mask(11, 11);
    mask.set(5, 5, 1);
    expect(dilate(mask, 2).count).toBe(25);
    expect(dilate(mask, 3).count).toBe(49);
  });
});

describe('open', () => {
  it('removes speckle and leaves the block', () => {
    const mask = maskOf([
      '.......',
      '.#####.',
      '.#####.',
      '.#####.',
      '.#####.',
      '.......',
      '#......',
    ]);
    const out = open(mask, 1);
    expect(out.get(0, 6)).toBe(0);
    expect(out.get(3, 3)).toBe(1);
  });

  it('never adds a pixel the original did not have nearby', () => {
    const mask = maskOf(['.....', '.###.', '.###.', '.###.', '.....']);
    const out = open(mask, 1);
    expect(out.count).toBeLessThanOrEqual(mask.count);
  });
});

describe('close', () => {
  it('fills a notch on the edge of a block', () => {
    const mask = maskOf([
      '.......',
      '.#####.',
      '.##.##.',
      '.#####.',
      '.#####.',
      '.......',
      '.......',
    ]);
    expect(close(mask, 1).get(3, 2)).toBe(1);
  });

  it('leaves a solid block alone in the middle', () => {
    const mask = maskOf(['.....', '.###.', '.###.', '.###.', '.....']);
    expect(close(mask, 1).get(2, 2)).toBe(1);
  });
});

describe('boundary', () => {
  it('keeps the outline and drops the interior', () => {
    const mask = maskOf(['.....', '.###.', '.###.', '.###.', '.....']);
    expect(draw(boundary(mask))).toEqual(['.....', '.###.', '.#.#.', '.###.', '.....']);
  });

  it('treats a pixel on the mask edge as boundary', () => {
    expect(boundary(maskOf(['##', '##'])).count).toBe(4);
  });

  it('leaves an empty mask empty', () => {
    expect(boundary(new Mask(3, 3)).count).toBe(0);
  });
});

describe('fillHoles', () => {
  it('fills an enclosed gap', () => {
    const mask = maskOf(['.....', '.###.', '.#.#.', '.###.', '.....']);
    expect(draw(fillHoles(mask))).toEqual(['.....', '.###.', '.###.', '.###.', '.....']);
  });

  it('leaves a gap that reaches the border alone', () => {
    const mask = maskOf(['.###.', '.#.#.', '.#.#.', '.#.#.', '.....']);
    expect(fillHoles(mask).get(2, 1)).toBe(0);
  });

  it('leaves a solid mask alone', () => {
    const mask = maskOf(['###', '###', '###']);
    expect(fillHoles(mask).count).toBe(9);
  });

  it('fills a large hole without running out of stack', () => {
    const mask = new Mask(120, 120);
    for (let y = 5; y < 115; y += 1) {
      for (let x = 5; x < 115; x += 1) {
        const onRing = y < 10 || y >= 110 || x < 10 || x >= 110;
        mask.set(x, y, onRing ? 1 : 0);
      }
    }
    expect(fillHoles(mask).count).toBe(110 * 110);
  });
});
