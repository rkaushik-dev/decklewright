import { describeProblems, faultsOnly, inspectProfile } from '../../profile/validate.js';
import { describeProfile } from '../../profile/diagnostics.js';
import { migrationNotes, needsMigration, versionOf } from '../../profile/migrate.js';
import { ProfileRegistry } from '../../profile/registry.js';
import { settingAt, settingPaths } from '../../profile/fields.js';
import { profileNamed, report } from './common.js';

/**
 * Look at a profile without running anything through it.
 *
 * Three questions get asked about a profile and they want different answers.
 * What profiles are there. What does this one actually say once its parents
 * are folded in. And is this one somebody wrote by hand going to work. The
 * third is the one that matters, because a profile with a typo in a field name
 * does not fail, it silently keeps the inherited value.
 */

export const usage = 'deckle profile [name] [options]';
export const describe = 'list the profiles, or show and check one';

export const options = Object.freeze({
  json: { short: 'j', describe: 'write it as json' },
  setting: { short: 's', type: 'string', describe: 'show one setting rather than all of them' },
  paths: { describe: 'list every setting a profile can carry' },
});

/**
 * @param {{options: Record<string, any>, positional: string[]}} args
 * @param {import('./common.js').Io} sink
 * @returns {Promise<number>}
 */
export async function run({ options: given, positional }, sink) {
  const [name] = positional;

  try {
    if (given.paths) {
      for (const path of settingPaths()) sink.out(path);
      return 0;
    }

    if (!name) {
      const registry = new ProfileRegistry();
      if (given.json) {
        sink.out(JSON.stringify(registry.names(), null, 2));
        return 0;
      }
      for (const one of registry.names()) {
        sink.out(`${one}  ${registry.get(one).title ?? ''}`.trimEnd());
      }
      return 0;
    }

    const profile = await profileNamed(name);

    if (given.setting) {
      const value = settingAt(profile, given.setting);
      if (value === undefined) {
        sink.err(`this profile carries no setting called ${given.setting}`);
        return 1;
      }
      sink.out(typeof value === 'object' ? JSON.stringify(value, null, 2) : String(value));
      return 0;
    }

    if (given.json) {
      sink.out(JSON.stringify(profile, null, 2));
    } else {
      for (const line of describeProfile(profile)) sink.out(line);
    }

    if (needsMigration(profile)) {
      sink.err(`this profile is version ${versionOf(profile)} and would be brought forward:`);
      for (const note of migrationNotes(profile)) sink.err(`  ${note}`);
    }

    const problems = inspectProfile(profile);
    if (problems.length > 0) {
      for (const line of describeProblems(problems)) sink.err(line);
      return faultsOnly(problems).length > 0 ? 1 : 0;
    }
    return 0;
  } catch (caught) {
    return report(sink, caught);
  }
}
