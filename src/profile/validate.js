import { ProfileError } from '../errors.js';

/**
 * Checks that need more than one setting to make.
 *
 * The schema can say a number is a number. It cannot say that a crop anchored
 * to a neatline is useless when the capture stage was never told to look for
 * one, and that combination has shipped twice.
 *
 * Faults stop a run. Notes do not, but they go in the run log, because most of
 * them are a profile that will surprise somebody later.
 */

/** @typedef {{path: string, message: string, severity: 'fault'|'note'}} Problem */

/**
 * @param {string} path
 * @param {string} message
 * @returns {Problem}
 */
export function fault(path, message) {
  return { path, message, severity: 'fault' };
}

/**
 * @param {string} path
 * @param {string} message
 * @returns {Problem}
 */
export function note(path, message) {
  return { path, message, severity: 'note' };
}

/**
 * @param {Record<string, any>} profile already parsed
 * @returns {Problem[]}
 */
export function inspectProfile(profile) {
  /** @type {Problem[]} */
  const problems = [];

  if (profile.crop.anchor === 'neatline' && !profile.capture.expectNeatline) {
    problems.push(
      fault('crop.anchor', 'anchors to the neatline but the capture stage never looks for one'),
    );
  }
  if (profile.capture.expectNeatline && profile.crop.anchor !== 'neatline') {
    problems.push(note('capture.expectNeatline', 'looks for a neatline that the crop ignores'));
  }

  if (profile.crop.minimumPpi > profile.qc.samplingPpi) {
    problems.push(
      fault(
        'crop.minimumPpi',
        `demands ${profile.crop.minimumPpi} ppi from a capture taken at ${profile.qc.samplingPpi}`,
      ),
    );
  }

  for (const side of ['top', 'right', 'bottom', 'left']) {
    if (profile.crop.bleed[side].isNegative) {
      problems.push(fault(`crop.bleed.${side}`, 'is negative, which would cut into the sheet'));
    }
    if (profile.crop.safeArea[side].isNegative) {
      problems.push(fault(`crop.safeArea.${side}`, 'is negative'));
    }
    if (!profile.crop.bleed[side].isZero && !profile.crop.safeArea[side].isZero) {
      problems.push(
        note(`crop.safeArea.${side}`, 'is set alongside a bleed, so the two will fight'),
      );
    }
  }

  problems.push(...inspectDerivatives(profile.derivatives));

  if (profile.derivatives.length === 0) {
    problems.push(note('derivatives', 'is empty, so a run will plan a crop and write nothing'));
  }

  if (profile.qc.minimumStars > 3 && profile.qc.maximumClipping > 0.001) {
    problems.push(
      note('qc.maximumClipping', 'is looser than four stars allows, so the level is unreachable'),
    );
  }

  return problems;
}

/**
 * @param {readonly Record<string, any>[]} derivatives
 * @returns {Problem[]}
 */
export function inspectDerivatives(derivatives) {
  /** @type {Problem[]} */
  const problems = [];
  const seen = new Set();
  let fullSize = 0;

  derivatives.forEach((derivative, i) => {
    const path = `derivatives[${i}]`;
    if (seen.has(derivative.name)) {
      problems.push(fault(`${path}.name`, `repeats the name ${derivative.name}`));
    }
    seen.add(derivative.name);

    const hasSide = derivative.longestSide !== undefined;
    const hasScale = derivative.scale !== undefined;
    if (hasSide && hasScale) {
      problems.push(fault(path, 'sets both a longest side and a scale, which cannot both hold'));
    }
    if (!hasSide && !hasScale) fullSize += 1;

    if (derivative.format === 'pnm' && derivative.keepMetadata) {
      problems.push(note(`${path}.keepMetadata`, 'has no effect, netpbm carries no metadata'));
    }
    if (derivative.format === 'pnm' && derivative.layout !== 'gray' && derivative.depth === '16') {
      problems.push(note(`${path}.format`, 'writes a very large file at sixteen bit colour'));
    }
  });

  if (fullSize > 1) {
    problems.push(note('derivatives', 'has more than one derivative at full size'));
  }
  return problems;
}

/**
 * @param {readonly Problem[]} problems
 * @returns {Problem[]}
 */
export function faultsOnly(problems) {
  return problems.filter((problem) => problem.severity === 'fault');
}

/**
 * @param {Record<string, any>} profile
 * @returns {Record<string, any>} the same profile, when it is usable
 */
export function assertUsable(profile) {
  const faults = faultsOnly(inspectProfile(profile));
  if (faults.length > 0) {
    throw new ProfileError(`the ${profile.name} profile cannot be used: ${faults[0].message}`, {
      name: profile.name,
      faults: faults.map((problem) => `${problem.path}: ${problem.message}`),
    });
  }
  return profile;
}

/**
 * @param {readonly Problem[]} problems
 * @returns {string[]}
 */
export function describeProblems(problems) {
  return problems.map((problem) => `${problem.severity}: ${problem.path}: ${problem.message}`);
}
