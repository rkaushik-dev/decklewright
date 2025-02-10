import { BadArgumentError } from '../errors.js';
import { isWholePixel } from '../geom/round.js';
import { Raster } from './raster.js';
import { Surface } from './surface.js';

/**
 * A window onto part of a surface that shares its samples.
 *
 * The detector runs the same measurements over a dozen candidate regions of a
 * capture and none of them need a copy. A view costs nothing to make and
 * writes through, which is also how the render stage fills one tile of an
 * output without allocating the tile first.
 */
export class SurfaceView extends Raster {
  /**
   * @param {Surface} surface
   * @param {import('../geom/rect.js').Rect} rect in the coordinates of the surface
   */
  constructor(surface, rect) {
    if (!(surface instanceof Surface)) {
      throw new BadArgumentError('a view needs a surface to look at', {});
    }
    super(surface.layout, surface.depth);
    if (!isWholePixel(rect)) {
      throw new BadArgumentError('a view rect must be whole pixels', { rect: rect.toJSON() });
    }
    if (rect.isEmpty) {
      throw new BadArgumentError('a view cannot be empty', { rect: rect.toJSON() });
    }
    if (!surface.bounds.containsRect(rect)) {
      throw new BadArgumentError('that view runs off the surface', {
        rect: rect.toJSON(),
        bounds: surface.bounds.toJSON(),
      });
    }
    this.surface = surface;
    this.rect = rect;
    this.width = rect.width;
    this.height = rect.height;
    Object.freeze(this);
  }

  /**
   * @param {Surface} surface
   * @returns {SurfaceView}
   */
  static whole(surface) {
    return new SurfaceView(surface, surface.bounds);
  }

  /** @returns {string} */
  get regionName() {
    return 'view';
  }

  /**
   * @param {number} x
   * @param {number} y
   * @param {number} [channel]
   * @returns {number}
   */
  get(x, y, channel = 0) {
    this.assertInside(x, y);
    return this.surface.get(x + this.rect.x, y + this.rect.y, channel);
  }

  /**
   * @param {number} x
   * @param {number} y
   * @param {number} channel
   * @param {number} value
   * @returns {void}
   */
  set(x, y, channel, value) {
    this.assertInside(x, y);
    this.surface.set(x + this.rect.x, y + this.rect.y, channel, value);
  }

  /**
   * The part of one parent row this view covers, still live.
   *
   * @param {number} y
   * @returns {Uint8Array|Uint16Array}
   */
  row(y) {
    this.assertInside(0, y);
    const stride = this.surface.rowStride;
    const start = (y + this.rect.y) * stride + this.rect.x * this.channels;
    return this.surface.data.subarray(start, start + this.rowStride);
  }

  /**
   * @param {import('../geom/rect.js').Rect} rect in view local coordinates
   * @returns {SurfaceView}
   */
  subview(rect) {
    if (!this.bounds.containsRect(rect)) {
      throw new BadArgumentError('that subview runs off the parent view', {
        rect: rect.toJSON(),
        bounds: this.bounds.toJSON(),
      });
    }
    return new SurfaceView(this.surface, rect.translated(this.rect.x, this.rect.y));
  }

  /**
   * Copy the window out into a surface of its own, which is what anything
   * leaving the process needs.
   *
   * @returns {Surface}
   */
  toSurface() {
    const out = Surface.create({
      width: this.width,
      height: this.height,
      layout: this.layout,
      depth: this.depth,
    });
    for (let y = 0; y < this.height; y += 1) out.row(y).set(this.row(y));
    return out;
  }

  /** @returns {string} */
  toString() {
    return `view ${this.rect} of ${this.surface}`;
  }
}
