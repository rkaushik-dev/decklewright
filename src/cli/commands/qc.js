import { readImage } from '../../codec/registry.js';
import { readCapture } from '../../detect/capture.js';
import { planCrop } from '../../plan/crop.js';
import { gradingToJSON, reportOn } from '../../qc/index.js';
import { CAPTURE_OPTIONS, captureAt, profileNamed, report, samplingFrom } from './common.js';

/**
 * Grade a capture and say what to change on the stand.
 *
 * The exit code is the point. An operator runs this over a shift's captures
 * from a shell loop and wants a non zero code for the ones to look at, so the
 * code is zero when the capture reached the level the profile asks for and one
 * when it did not, whatever else was printed.
 */

export const usage = 'deckle qc <capture> [options]';
export const describe = 'grade a capture against the profile and say what to fix';

export const options = Object.freeze({
  ...CAPTURE_OPTIONS,
  json: { short: 'j', describe: 'write the grading as json' },
  stars: { short: 's', type: 'number', describe: 'the level to measure against' },
  crop: { describe: 'grade the crop rather than the whole capture' },
});

/**
 * @param {{options: Record<string, any>, positional: string[]}} args
 * @param {import('./common.js').Io} sink
 * @returns {Promise<number>}
 */
export async function run({ options: given, positional }, sink) {
  const [path] = positional;
  if (!path) {
    sink.err('which capture? try: deckle qc g4181.png');
    return 2;
  }

  try {
    const base = await profileNamed(given.profile);
    const profile =
      given.stars === undefined ? base : { ...base, qc: { ...base.qc, minimumStars: given.stars } };

    const { bytes, accession } = await captureAt(path);
    const surface = readImage(bytes, { path });
    const reading = readCapture(surface);
    const sampling = samplingFrom(given.ppi, profile);

    /** @type {import('../../geom/rect.js').Rect|undefined} */
    let crop;
    if (given.crop) {
      crop = planCrop({ reading, profile, capture: surface.bounds, sampling }).plan.rect;
    }

    const args = { surface, reading, profile, sampling, crop };
    if (given.json) {
      sink.out(JSON.stringify(gradingToJSON(args, { item: accession }), null, 2));
    } else {
      for (const line of reportOn(args, { item: accession })) sink.out(line);
    }

    const graded = gradingToJSON(args, { item: accession });
    return graded.passed ? 0 : 1;
  } catch (caught) {
    return report(sink, caught);
  }
}
