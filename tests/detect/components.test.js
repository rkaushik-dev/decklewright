import { describe, expect, it } from 'vitest';

import {
  dropSmaller,
  fillRatio,
  keepLargest,
  labelComponents,
  largestComponent,
  maskOfComponent,
} from '../../src/detect/components.js';
import { Mask } from '../../src/detect/mask.js';

/**
 * @param {readonly string[]} rows
 * @returns {Mask}
 */
function maskOf(rows) {
  const mask = new Mask(rows[0].length, rows.length);
  rows.forEach((row, y) => {
    [...row].forEach((ch, x) => mask.set(x, y, ch === '#' ? 1 : 0));
  });
  return mask;
}

const twoBlobs = maskOf(['##...', '##...', '.....', '...#.', '...##']);

describe('labelComponents', () => {
  it('finds each blob and numbers them from one', () => {
    const { components } = labelComponents(twoBlobs);
    expect(components.map((c) => c.id)).toEqual([1, 2]);
    expect(components.map((c) => c.area)).toEqual([4, 3]);
  });

  it('bounds each blob', () => {
    const { components } = labelComponents(twoBlobs);
    expect(components[0].bounds.toJSON()).toEqual({ x: 0, y: 0, width: 2, height: 2 });
    expect(components[1].bounds.toJSON()).toEqual({ x: 3, y: 3, width: 2, height: 2 });
  });

  it('centres each blob', () => {
    const { components } = labelComponents(twoBlobs);
    expect(components[0].centroid.toJSON()).toEqual({ x: 0.5, y: 0.5 });
  });

  it('says which blobs touch the edge of the capture', () => {
    const { components } = labelComponents(twoBlobs);
    expect(components[0].touchesEdge).toBe(true);
    expect(components[1].touchesEdge).toBe(true);

    const inner = maskOf(['.....', '.###.', '.###.', '.###.', '.....']);
    expect(labelComponents(inner).components[0].touchesEdge).toBe(false);
  });

  it('labels the background as zero', () => {
    const { labels } = labelComponents(twoBlobs);
    expect(labels[2]).toBe(0);
    expect(labels[0]).toBe(1);
  });

  it('finds nothing in an empty mask', () => {
    expect(labelComponents(new Mask(4, 4)).components).toEqual([]);
  });

  it('joins diagonal neighbours under eight connectivity and not under four', () => {
    const diagonal = maskOf(['#..', '.#.', '..#']);
    expect(labelComponents(diagonal).components).toHaveLength(1);
    expect(labelComponents(diagonal, { connectivity: 4 }).components).toHaveLength(3);
  });

  it('refuses a connectivity it does not know', () => {
    expect(() => labelComponents(twoBlobs, { connectivity: 6 })).toThrow(
      /connectivity must be one of 4, 8/,
    );
  });

  it('walks a long snake without running out of stack', () => {
    const mask = new Mask(200, 200);
    for (let y = 0; y < 200; y += 2) {
      for (let x = 0; x < 200; x += 1) mask.set(x, y, 1);
      const joinAt = (y / 2) % 2 === 0 ? 199 : 0;
      mask.set(joinAt, y + 1, 1);
    }
    const { components } = labelComponents(mask);
    expect(components).toHaveLength(1);
  });
});

describe('maskOfComponent', () => {
  it('keeps only the labelled pixels', () => {
    const { labels } = labelComponents(twoBlobs);
    const first = maskOfComponent(twoBlobs, labels, 1);
    expect(first.count).toBe(4);
    expect(first.get(3, 3)).toBe(0);
  });

  it('gives an empty mask for a label nothing carries', () => {
    const { labels } = labelComponents(twoBlobs);
    expect(maskOfComponent(twoBlobs, labels, 99).count).toBe(0);
  });
});

describe('largestComponent', () => {
  it('picks the biggest blob and reports it', () => {
    const found = largestComponent(twoBlobs);
    expect(found?.component.area).toBe(4);
    expect(found?.mask.count).toBe(4);
  });

  it('gives nothing back for an empty mask', () => {
    expect(largestComponent(new Mask(3, 3))).toBeNull();
  });
});

describe('keepLargest', () => {
  it('keeps the count it is given', () => {
    const three = maskOf(['##...', '##..#', '.....', '###..', '###..']);
    expect(keepLargest(three, 1).count).toBe(6);
    expect(keepLargest(three, 2).count).toBe(10);
    expect(keepLargest(three, 9).count).toBe(three.count);
  });

  it('refuses a count that is not a whole number above zero', () => {
    expect(() => keepLargest(twoBlobs, 0)).toThrow(/above zero/);
  });
});

describe('dropSmaller', () => {
  it('removes speckle below the area', () => {
    const speckled = maskOf(['####.', '####.', '.....', '....#', '.....']);
    expect(dropSmaller(speckled, 4).count).toBe(8);
    expect(dropSmaller(speckled, 1).count).toBe(9);
  });

  it('can remove everything', () => {
    expect(dropSmaller(twoBlobs, 100).count).toBe(0);
  });
});

describe('fillRatio', () => {
  it('is one for a solid rectangle', () => {
    const { components } = labelComponents(maskOf(['.....', '.###.', '.###.', '.....']));
    expect(fillRatio(components[0])).toBe(1);
  });

  it('is low for a thin diagonal', () => {
    const { components } = labelComponents(maskOf(['#...', '.#..', '..#.', '...#']));
    expect(fillRatio(components[0])).toBe(0.25);
  });
});
