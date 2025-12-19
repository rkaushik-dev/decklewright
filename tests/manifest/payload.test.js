import { describe, expect, it } from 'vitest';

import * as manifest from '../../src/manifest/index.js';
import { worked } from '../helpers/accession.js';

const AT = new Date('2025-11-18T10:30:00Z');

/**
 * @param {object} [options]
 * @returns {Parameters<typeof manifest.deliveryFor>[0]}
 */
function run(options = {}) {
  return { ...worked(options), version: '1.4.0', at: AT };
}

/** @returns {import('../../src/manifest/payload.js').Payload} */
function delivery() {
  return manifest.deliveryFor(run());
}

describe('payloadFor', () => {
  it('puts every derivative in the folder', () => {
    const names = delivery().entries.map((entry) => entry.name);
    expect(names).toContain('g4181-1907_master.png');
    expect(names).toContain('g4181-1907_thumb.png');
  });

  it('adds the sidecar, the provenance and the sums', () => {
    const names = delivery().entries.map((entry) => entry.name);
    expect(names).toContain(manifest.SIDECAR_NAME);
    expect(names).toContain(manifest.PROVENANCE_NAME);
    expect(names).toContain(manifest.SUMS_NAME);
  });

  it('leaves the checksum file out of the checksum file', () => {
    const text = new TextDecoder().decode(
      /** @type {Uint8Array} */ (manifest.entryNamed(delivery(), manifest.SUMS_NAME)),
    );
    expect(text).not.toMatch(manifest.SUMS_NAME);
    expect(text).not.toMatch(manifest.SIDECAR_NAME);
  });

  it('gives the whole delivery one number', () => {
    expect(delivery().digest).toHaveLength(64);
  });

  it('gives the same number for the same run twice', () => {
    expect(manifest.deliveryFor(run()).digest).toBe(manifest.deliveryFor(run()).digest);
  });

  it('gives a different number for a different accession', () => {
    expect(manifest.deliveryFor(run({ id: 'g4182' })).digest).not.toBe(delivery().digest);
  });

  it('refuses a delivery with no files in it', () => {
    expect(() =>
      manifest.payloadFor({ accession: 'a', files: [], sidecar: { version: 2, accession: 'a' } }),
    ).toThrow(/no files in it/);
  });

  it('leaves the provenance out when there is none', () => {
    const out = manifest.payloadFor({
      accession: 'a',
      files: run().files,
      sidecar: { version: 2, accession: 'a' },
    });
    expect(manifest.entryNamed(out, manifest.PROVENANCE_NAME)).toBeNull();
  });
});

describe('payloadSize', () => {
  it('adds every entry up', () => {
    const out = delivery();
    expect(manifest.payloadSize(out)).toBe(
      out.entries.reduce((sum, entry) => sum + entry.bytes.length, 0),
    );
  });
});

describe('objectionsToPayload', () => {
  it('has nothing to say about one it built itself', () => {
    expect(manifest.objectionsToPayload(delivery())).toEqual([]);
  });

  it('catches a file that was swapped after the sidecar was written', () => {
    const out = delivery();
    const at = out.entries.findIndex((entry) => entry.name.endsWith('_master.png'));
    out.entries[at] = { name: out.entries[at].name, bytes: new Uint8Array([1, 2, 3]) };
    const said = manifest.objectionsToPayload(out).join(' ');
    expect(said).toMatch(/bytes and the sidecar says/);
  });

  it('catches a file the sidecar lists and the folder does not have', () => {
    const out = delivery();
    out.entries = out.entries.filter((entry) => !entry.name.endsWith('_thumb.png'));
    expect(manifest.objectionsToPayload(out).join(' ')).toMatch(/lists .* and it is not here/);
  });

  it('catches a file in the folder that the sums file does not list', () => {
    const out = delivery();
    const sums = out.entries.find((entry) => entry.name === manifest.SUMS_NAME);
    if (sums) {
      const kept = new TextDecoder()
        .decode(sums.bytes)
        .split('\n')
        .filter((line) => !line.includes('_thumb'))
        .join('\n');
      sums.bytes = new TextEncoder().encode(kept);
    }
    expect(manifest.objectionsToPayload(out).join(' ')).toMatch(/missing from sha256sums/);
  });

  it('catches a sidecar for another accession', () => {
    const out = delivery();
    out.accession = 'somebody-elses';
    expect(manifest.objectionsToPayload(out).join(' ')).toMatch(/and this delivery is/);
  });

  it('says so when the sidecar is missing altogether', () => {
    const out = delivery();
    out.entries = out.entries.filter((entry) => entry.name !== manifest.SIDECAR_NAME);
    expect(manifest.objectionsToPayload(out)).toEqual([`there is no ${manifest.SIDECAR_NAME}`]);
  });

  it('says so when the sidecar will not read back', () => {
    const out = delivery();
    const sidecar = out.entries.find((entry) => entry.name === manifest.SIDECAR_NAME);
    if (sidecar) sidecar.bytes = new TextEncoder().encode('{ not json');
    expect(manifest.objectionsToPayload(out)[0]).toMatch(/will not read back/);
  });
});

describe('deliveryFor', () => {
  it('builds a delivery that checks out', () => {
    expect(manifest.objectionsToPayload(delivery())).toEqual([]);
  });

  it('records the agent version in the provenance', () => {
    const chain = JSON.parse(
      new TextDecoder().decode(
        /** @type {Uint8Array} */ (manifest.entryNamed(delivery(), manifest.PROVENANCE_NAME)),
      ),
    );
    expect(chain[0].agent).toBe('decklewright 1.4.0');
  });

  it('uses one timestamp for the sidecar and the chain', () => {
    const out = delivery();
    const sidecar = manifest.parseSidecar(
      new TextDecoder().decode(
        /** @type {Uint8Array} */ (manifest.entryNamed(out, manifest.SIDECAR_NAME)),
      ),
    );
    const chain = JSON.parse(
      new TextDecoder().decode(
        /** @type {Uint8Array} */ (manifest.entryNamed(out, manifest.PROVENANCE_NAME)),
      ),
    );
    expect(sidecar.created).toBe(chain[0].at);
  });

  it('carries a run that was never graded', () => {
    const out = manifest.deliveryFor(run({ graded: false }));
    expect(manifest.objectionsToPayload(out)).toEqual([]);
  });
});

describe('describePayload', () => {
  it('opens with the accession and the size', () => {
    expect(manifest.describePayload(delivery())[0]).toMatch(/^g4181-1907, \d+ files, \d+ bytes$/);
  });

  it('shows the events under the files', () => {
    expect(manifest.describePayload(delivery()).join('\n')).toMatch(/capture-read:/);
  });

  it('says so rather than throwing when the sidecar is unreadable', () => {
    const out = delivery();
    const sidecar = out.entries.find((entry) => entry.name === manifest.SIDECAR_NAME);
    if (sidecar) sidecar.bytes = new TextEncoder().encode('nonsense');
    expect(manifest.describePayload(out)).toContain('the sidecar will not read back');
  });
});
