import { readImage } from '../../codec/registry.js';
import { readCapture, summarise as summariseReading } from '../../detect/capture.js';
import { planCrop } from '../../plan/crop.js';
import { explainWithRules } from '../../plan/explain.js';
import { CAPTURE_OPTIONS, captureAt, profileNamed, report, samplingFrom } from './common.js';

/**
 * Say where the crop would be, and write nothing.
 *
 * This is the command a lab runs when it is arguing about a profile. It does
 * every step a real run does up to the point where bytes would be written, so
 * what it reports is what would happen and not an estimate of it.
 */

export const usage = 'deckle plan <capture> [options]';
export const describe = 'work out where the crop would be, without writing anything';

export const options = Object.freeze({
  ...CAPTURE_OPTIONS,
  unit: { short: 'u', type: 'string', fallback: 'in', describe: 'the unit to report lengths in' },
  json: { short: 'j', describe: 'write the plan as json instead of prose' },
  rules: { describe: 'list every rule that was tried, not only the ones that moved it' },
});

/**
 * @param {{options: Record<string, any>, positional: string[]}} args
 * @param {import('./common.js').Io} sink
 * @returns {Promise<number>} the exit code
 */
export async function run({ options: given, positional }, sink) {
  const [path] = positional;
  if (!path) {
    sink.err('which capture? try: deckle plan g4181.png');
    return 2;
  }

  try {
    const profile = await profileNamed(given.profile);
    const { bytes, accession } = await captureAt(path);
    const surface = readImage(bytes, { path });
    const reading = readCapture(surface);
    const sampling = samplingFrom(given.ppi, profile);

    const out = planCrop({ reading, profile, capture: surface.bounds, sampling });

    if (given.json) {
      sink.out(
        JSON.stringify(
          {
            accession,
            profile: profile.name,
            settled: out.settled,
            passes: out.passes,
            rules: out.rules,
            rect: out.plan.rect.toJSON(),
            deskewDegrees: Number(out.plan.deskew.degrees.toFixed(4)),
            ppi: out.plan.sampling.toJSON(),
            warnings: [...out.plan.warnings],
          },
          null,
          2,
        ),
      );
      return 0;
    }

    sink.out(`${accession}, profile ${profile.name}`);
    for (const line of summariseReading(reading)) sink.out(`  ${line}`);
    sink.out('');
    for (const line of explainWithRules(out.plan, given.rules ? out.rules : [], {
      unit: given.unit,
    })) {
      sink.out(line);
    }

    if (!out.settled) {
      sink.err(`the rules did not settle after ${out.passes} passes`);
      return 1;
    }
    for (const warning of out.plan.warnings) sink.err(`warning: ${warning}`);
    return 0;
  } catch (caught) {
    return report(sink, caught);
  }
}
