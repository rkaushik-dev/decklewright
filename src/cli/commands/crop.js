import { join } from 'node:path';

import { deriveOne } from '../../derive/oneshot.js';
import { readImage } from '../../codec/registry.js';
import { summarise as summarisePlan } from '../../plan/explain.js';
import { writeSetAtomic } from '../../store/atomic.js';
import {
  CAPTURE_OPTIONS,
  captureAt,
  humanBytes,
  profileNamed,
  report,
  samplingFrom,
} from './common.js';

/**
 * Cut one sheet and write the derivatives.
 *
 * This is the command for a curator with one plate and no workspace. It writes
 * the image files and nothing else: no sidecar, no ledger, no folder
 * structure. A run that has to be traceable goes through the batch command,
 * and the difference is deliberate rather than a gap.
 */

export const usage = 'deckle crop <capture> [options]';
export const describe = 'cut one sheet and write its derivatives';

export const options = Object.freeze({
  ...CAPTURE_OPTIONS,
  out: { short: 'o', type: 'string', fallback: '.', describe: 'where to put the files' },
  name: { short: 'n', type: 'string', describe: 'the accession, if not the file name' },
  only: { type: 'string', repeat: true, describe: 'write only this derivative, may repeat' },
  force: { short: 'f', describe: 'replace files that are already there' },
  'dry-run': { describe: 'say what would be written and write nothing' },
});

/**
 * @param {{options: Record<string, any>, positional: string[]}} args
 * @param {import('./common.js').Io} sink
 * @returns {Promise<number>}
 */
export async function run({ options: given, positional }, sink) {
  const [path] = positional;
  if (!path) {
    sink.err('which capture? try: deckle crop g4181.png --out delivered');
    return 2;
  }

  try {
    const profile = await profileNamed(given.profile);
    const capture = await captureAt(path);
    const accession = given.name ?? capture.accession;
    const surface = readImage(capture.bytes, { path });

    const derived = deriveOne({
      surface,
      profile,
      accession,
      sampling: samplingFrom(given.ppi, profile),
    });

    const wanted = new Set(given.only ?? []);
    const files =
      wanted.size > 0 ? derived.files.filter((one) => wanted.has(one.name)) : derived.files;

    if (files.length === 0) {
      sink.err(
        `nothing to write, this profile makes ${derived.files.map((one) => one.name).join(', ')}`,
      );
      return 1;
    }

    sink.out(`${accession}: ${summarisePlan(derived.plan)}`);
    for (const file of files) {
      sink.out(`  ${file.fileName}, ${humanBytes(file.bytes.length)}, ${file.sampling}`);
    }
    for (const warning of derived.warnings) sink.err(`warning: ${warning}`);

    if (given['dry-run']) {
      sink.out(`would write ${files.length} files into ${given.out}`);
      return 0;
    }

    const written = await writeSetAtomic(
      join(sink.cwd, given.out),
      files.map((file) => ({ name: file.fileName, bytes: file.bytes })),
      { overwrite: Boolean(given.force) },
    );
    sink.out(`wrote ${written.written.length} files, ${humanBytes(written.bytes)}`);
    return 0;
  } catch (caught) {
    return report(sink, caught);
  }
}
