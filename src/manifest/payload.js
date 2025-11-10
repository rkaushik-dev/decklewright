import { ManifestError } from '../errors.js';
import { digestOfSet, digests, fromSumFile, sameDigest, toSumFile } from './checksum.js';
import { describeChain } from './provenance.js';
import { describeSidecar, parseSidecar, writeSidecar } from './sidecar.js';

/**
 * A delivery, as a set of named blobs.
 *
 * Everything that goes into one folder and travels together: the derivative
 * files, the sidecar that describes them, the provenance chain, and a plain
 * checksum file that a tape robot can read without knowing anything about any
 * of the rest of it.
 *
 * The payload is bytes and names and nothing else. It does not know where it
 * will be written, which is what makes it possible to build one in memory,
 * check it, and only then let the store put it somewhere. A delivery that is
 * checked after it is written is a delivery that gets half written.
 */

/** What the fixed files are called inside a delivery. */
export const SIDECAR_NAME = 'sidecar.json';
export const PROVENANCE_NAME = 'provenance.json';
export const SUMS_NAME = 'sha256sums.txt';

/**
 * @typedef {object} Payload
 * @property {string} accession
 * @property {{name: string, bytes: Uint8Array}[]} entries every file in the folder
 * @property {string} digest one number for the whole delivery
 */

/**
 * @param {string} text
 * @returns {Uint8Array}
 */
function utf8(text) {
  return new TextEncoder().encode(text);
}

/**
 * Assemble a delivery.
 *
 * The checksum file lists the image files and not itself, which is the only
 * arrangement that terminates. It also does not list the sidecar, because the
 * sidecar carries its own digests for the images and a second copy of them in
 * a third place is a third thing to keep in step.
 *
 * @param {object} args
 * @param {string} args.accession
 * @param {readonly import('../derive/oneshot.js').DerivedFile[]} args.files
 * @param {Record<string, any>} args.sidecar
 * @param {readonly import('./provenance.js').Event[]} [args.provenance]
 * @returns {Payload}
 */
export function payloadFor({ accession, files, sidecar, provenance }) {
  if (files.length === 0) throw new ManifestError('a delivery with no files in it', { accession });

  /** @type {{name: string, bytes: Uint8Array}[]} */
  const entries = files.map((file) => ({ name: file.fileName, bytes: file.bytes }));

  const sums = toSumFile(
    entries.map((entry) => ({ name: entry.name, digests: digests(entry.bytes) })),
  );

  entries.push({ name: SIDECAR_NAME, bytes: utf8(writeSidecar(sidecar)) });
  if (provenance) {
    entries.push({
      name: PROVENANCE_NAME,
      bytes: utf8(`${JSON.stringify(provenance, null, 2)}\n`),
    });
  }
  entries.push({ name: SUMS_NAME, bytes: utf8(`${sums}\n`) });

  return { accession, entries, digest: digestOfSet(entries) };
}

/**
 * @param {Payload} payload
 * @param {string} name
 * @returns {Uint8Array|null}
 */
export function entryNamed(payload, name) {
  return payload.entries.find((entry) => entry.name === name)?.bytes ?? null;
}

/**
 * @param {Payload} payload
 * @returns {number} how large the whole delivery is
 */
export function payloadSize(payload) {
  return payload.entries.reduce((sum, entry) => sum + entry.bytes.length, 0);
}

/**
 * Check a delivery against itself, before anything is written.
 *
 * Three things have to agree and they are written by three different pieces of
 * code: the files that are actually here, the files the sidecar says are here,
 * and the files the checksum file lists. Any two of them agreeing is not
 * enough, which is why this compares all three rather than the pair that is
 * convenient.
 *
 * @param {Payload} payload
 * @returns {string[]} empty when the delivery is consistent
 */
export function objectionsToPayload(payload) {
  /** @type {string[]} */
  const out = [];

  const present = new Set(payload.entries.map((entry) => entry.name));
  for (const name of [SIDECAR_NAME, SUMS_NAME]) {
    if (!present.has(name)) out.push(`there is no ${name}`);
  }

  const sidecarBytes = entryNamed(payload, SIDECAR_NAME);
  if (!sidecarBytes) return out;

  /** @type {Record<string, any>} */
  let sidecar;
  try {
    sidecar = parseSidecar(new TextDecoder().decode(sidecarBytes));
  } catch (cause) {
    out.push(`the sidecar will not read back: ${/** @type {Error} */ (cause).message}`);
    return out;
  }

  if (sidecar.accession !== payload.accession) {
    out.push(`the sidecar is for ${sidecar.accession} and this delivery is ${payload.accession}`);
  }

  for (const listed of sidecar.derivatives ?? []) {
    const bytes = entryNamed(payload, listed.file);
    if (!bytes) {
      out.push(`the sidecar lists ${listed.file} and it is not here`);
      continue;
    }
    if (bytes.length !== listed.bytes) {
      out.push(`${listed.file} is ${bytes.length} bytes and the sidecar says ${listed.bytes}`);
    }
    const found = digests(bytes).sha256;
    if (!sameDigest(found, listed.digests?.sha256)) {
      out.push(`${listed.file} does not match the digest in the sidecar`);
    }
  }

  const sumsBytes = entryNamed(payload, SUMS_NAME);
  if (sumsBytes) {
    const listed = fromSumFile(new TextDecoder().decode(sumsBytes));
    const named = new Set(listed.map((line) => line.name));
    for (const item of sidecar.derivatives ?? []) {
      if (!named.has(item.file)) out.push(`${item.file} is missing from ${SUMS_NAME}`);
    }
    for (const line of listed) {
      const bytes = entryNamed(payload, line.name);
      if (!bytes) out.push(`${SUMS_NAME} lists ${line.name} and it is not here`);
      else if (!sameDigest(digests(bytes).sha256, line.digest)) {
        out.push(`${line.name} does not match the digest in ${SUMS_NAME}`);
      }
    }
  }

  return out;
}

/**
 * What a delivery looks like to a person, without unpacking it.
 *
 * @param {Payload} payload
 * @returns {string[]}
 */
export function describePayload(payload) {
  const sidecarBytes = entryNamed(payload, SIDECAR_NAME);
  const provenanceBytes = entryNamed(payload, PROVENANCE_NAME);

  /** @type {string[]} */
  const lines = [
    `${payload.accession}, ${payload.entries.length} files, ${payloadSize(payload)} bytes`,
  ];

  if (sidecarBytes) {
    try {
      lines.push(...describeSidecar(parseSidecar(new TextDecoder().decode(sidecarBytes))));
    } catch {
      lines.push('the sidecar will not read back');
    }
  }
  if (provenanceBytes) {
    try {
      const chain = JSON.parse(new TextDecoder().decode(provenanceBytes));
      lines.push(...describeChain(chain).map((line) => `  ${line}`));
    } catch {
      lines.push('the provenance chain will not read back');
    }
  }
  return lines;
}
