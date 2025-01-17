import { describe, expect, it } from 'vitest';

import {
  KERNEL_NAMES,
  box,
  buildContributions,
  catmullRom,
  haloFor,
  kernelFor,
  lanczos,
  mitchell,
  triangle,
} from '../../src/raster/kernels.js';

describe('the kernels themselves', () => {
  it('all peak at the centre', () => {
    for (const name of KERNEL_NAMES) {
      const k = kernelFor(name);
      expect(k.weight(0)).toBeGreaterThan(0);
      expect(k.weight(k.support + 0.001)).toBe(0);
    }
  });

  it('box covers half a sample either side', () => {
    expect(box.weight(0)).toBe(1);
    expect(box.weight(0.5)).toBe(1);
    expect(box.weight(0.51)).toBe(0);
  });

  it('triangle falls off linearly', () => {
    expect(triangle.weight(0)).toBe(1);
    expect(triangle.weight(0.25)).toBe(0.75);
    expect(triangle.weight(1)).toBe(0);
    expect(triangle.weight(-0.5)).toBe(0.5);
  });

  it('catmull rom passes through the samples and undershoots between them', () => {
    expect(catmullRom.weight(0)).toBeCloseTo(1, 12);
    expect(catmullRom.weight(1)).toBeCloseTo(0, 12);
    expect(catmullRom.weight(2)).toBeCloseTo(0, 12);
    expect(catmullRom.weight(1.5)).toBeLessThan(0);
  });

  it('mitchell never goes negative near the centre', () => {
    expect(mitchell.weight(0)).toBeCloseTo(8 / 9, 9);
    expect(mitchell.weight(0.5)).toBeGreaterThan(0);
  });

  it('lanczos is one at the centre and zero at the whole offsets', () => {
    const k = lanczos(3);
    expect(k.weight(0)).toBe(1);
    expect(k.weight(1)).toBeCloseTo(0, 9);
    expect(k.weight(2)).toBeCloseTo(0, 9);
    expect(k.weight(3)).toBe(0);
    expect(k.name).toBe('lanczos3');
  });

  it('refuses a lobe count that is not a whole number above zero', () => {
    expect(() => lanczos(0)).toThrow(/above zero/);
  });
});

describe('kernelFor', () => {
  it('finds the named kernels', () => {
    expect(kernelFor('box')).toBe(box);
    expect(kernelFor('lanczos3').name).toBe('lanczos3');
  });

  it('passes an object through', () => {
    expect(kernelFor(triangle)).toBe(triangle);
  });

  it('lists what it knows when it cannot find one', () => {
    try {
      kernelFor('sinc');
      throw new Error('should have thrown');
    } catch (err) {
      expect(err.message).toBe('unknown kernel sinc');
      expect(err.details.known).toContain('catmullRom');
    }
  });
});

describe('buildContributions', () => {
  it('produces one entry per output sample', () => {
    const taps = buildContributions({ sourceExtent: 8, targetExtent: 4, kernel: box });
    expect(taps).toHaveLength(4);
  });

  it('normalises every tap list to one', () => {
    for (const name of KERNEL_NAMES) {
      const taps = buildContributions({
        sourceExtent: 9,
        targetExtent: 5,
        kernel: kernelFor(name),
      });
      for (const tap of taps) {
        const total = tap.weights.reduce((a, b) => a + b, 0);
        expect(total).toBeCloseTo(1, 9);
      }
    }
  });

  it('merges pairs of samples when halving with a box', () => {
    const taps = buildContributions({ sourceExtent: 4, targetExtent: 2, kernel: box });
    expect(taps[0].indices).toEqual([0, 1]);
    expect(taps[1].indices).toEqual([2, 3]);
    expect(taps[0].weights).toEqual([0.5, 0.5]);
  });

  it('takes a single sample when the sizes match', () => {
    const taps = buildContributions({ sourceExtent: 4, targetExtent: 4, kernel: box });
    expect(taps.map((t) => t.indices)).toEqual([[0], [1], [2], [3]]);
  });

  it('reaches to two neighbours when doubling with a triangle', () => {
    const taps = buildContributions({ sourceExtent: 2, targetExtent: 4, kernel: triangle });
    expect(taps[0].indices).toEqual([0, 0]);
    expect(taps[1].indices).toEqual([0, 1]);
    expect(taps[1].weights[0]).toBeCloseTo(0.75, 9);
    expect(taps[3].indices).toEqual([1, 1]);
  });

  it('keeps every index inside the source under the clamp policy', () => {
    const taps = buildContributions({ sourceExtent: 3, targetExtent: 9, kernel: catmullRom });
    for (const tap of taps) {
      for (const index of tap.indices) {
        expect(index).toBeGreaterThanOrEqual(0);
        expect(index).toBeLessThan(3);
      }
    }
  });

  it('falls back to one sample when the constant policy drops them all', () => {
    const taps = buildContributions({
      sourceExtent: 1,
      targetExtent: 3,
      kernel: box,
      edge: 'constant',
    });
    for (const tap of taps) {
      expect(tap.indices).toHaveLength(1);
      expect(tap.weights).toEqual([1]);
    }
  });

  it('refuses an extent that is not a whole number above zero', () => {
    expect(() => buildContributions({ sourceExtent: 0, targetExtent: 4, kernel: box })).toThrow(
      /sourceExtent/,
    );
    expect(() => buildContributions({ sourceExtent: 4, targetExtent: 1.5, kernel: box })).toThrow(
      /targetExtent/,
    );
  });
});

describe('haloFor', () => {
  it('is the plain support when enlarging', () => {
    expect(haloFor(catmullRom, 2)).toBe(2);
    expect(haloFor(box, 4)).toBe(1);
  });

  it('grows with the reduction factor', () => {
    expect(haloFor(catmullRom, 0.5)).toBe(4);
    expect(haloFor(catmullRom, 0.25)).toBe(8);
  });

  it('refuses a scale of zero', () => {
    expect(() => haloFor(box, 0)).toThrow(/scale must be greater than zero/);
  });
});
