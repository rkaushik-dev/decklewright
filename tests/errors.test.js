import { describe, expect, it } from 'vitest';

import {
  BadArgumentError,
  CodecError,
  DeckleError,
  DetectionError,
  ERROR_CODES,
  GeometryError,
  PipelineError,
  PlanError,
  ProfileError,
  QcError,
  StoreError,
  UnsupportedError,
  isDeckleError,
  wrapError,
} from '../src/errors.js';

describe('DeckleError', () => {
  it('carries a default code and an empty detail bag', () => {
    const err = new DeckleError('bed is empty');
    expect(err.code).toBe(ERROR_CODES.ASSERTION);
    expect(err.details).toEqual({});
    expect(err.name).toBe('DeckleError');
    expect(err instanceof Error).toBe(true);
  });

  it('copies the details rather than aliasing them', () => {
    const details = { sheet: 'q17' };
    const err = new DeckleError('nope', { details });
    details.sheet = 'q18';
    expect(err.details.sheet).toBe('q17');
  });

  it('keeps the cause when one is given', () => {
    const cause = new Error('EACCES');
    const err = new DeckleError('cannot read capture', { cause });
    expect(err.cause).toBe(cause);
  });

  it('annotates in place and returns itself', () => {
    const err = new DeckleError('bad crop');
    const same = err.annotate({ stage: 'plan' }).annotate({ job: 7 });
    expect(same).toBe(err);
    expect(err.details).toEqual({ stage: 'plan', job: 7 });
  });

  it('serialises to something a batch report can print', () => {
    const err = new BadArgumentError('width must be positive', { width: -3 });
    expect(err.toJSON()).toEqual({
      name: 'BadArgumentError',
      code: ERROR_CODES.BAD_ARGUMENT,
      message: 'width must be positive',
      details: { width: -3 },
    });
  });

  it('does not leak the internal detail object through toJSON', () => {
    const err = new DeckleError('x', { details: { a: 1 } });
    const json = err.toJSON();
    json.details.a = 2;
    expect(err.details.a).toBe(1);
  });
});

describe('the subclasses', () => {
  const cases = [
    [BadArgumentError, ERROR_CODES.BAD_ARGUMENT],
    [CodecError, ERROR_CODES.CODEC],
    [GeometryError, ERROR_CODES.GEOMETRY],
    [DetectionError, ERROR_CODES.DETECTION],
    [ProfileError, ERROR_CODES.PROFILE],
    [PlanError, ERROR_CODES.PLAN],
    [QcError, ERROR_CODES.QC],
    [PipelineError, ERROR_CODES.PIPELINE],
    [StoreError, ERROR_CODES.STORE],
    [UnsupportedError, ERROR_CODES.UNSUPPORTED],
  ];

  it.each(cases)('%s reports its own code and name', (Ctor, code) => {
    const err = new Ctor('boom', { where: 'here' });
    expect(err.code).toBe(code);
    expect(err.name).toBe(Ctor.name);
    expect(err.details).toEqual({ where: 'here' });
    expect(isDeckleError(err)).toBe(true);
  });

  it('every code is distinct', () => {
    const codes = Object.values(ERROR_CODES);
    expect(new Set(codes).size).toBe(codes.length);
  });

  it('the code table is frozen', () => {
    expect(Object.isFrozen(ERROR_CODES)).toBe(true);
  });
});

describe('isDeckleError', () => {
  it('rejects foreign errors and plain values', () => {
    expect(isDeckleError(new Error('plain'))).toBe(false);
    expect(isDeckleError({ code: 'DW_CODEC' })).toBe(false);
    expect(isDeckleError(null)).toBe(false);
    expect(isDeckleError('DW_CODEC')).toBe(false);
  });
});

describe('wrapError', () => {
  it('annotates an error that is already ours instead of nesting it', () => {
    const inner = new CodecError('bad png header');
    const out = wrapError(inner, 'reading q17.png');
    expect(out).toBe(inner);
    expect(out.code).toBe(ERROR_CODES.CODEC);
    expect(out.details.context).toBe('reading q17.png');
  });

  it('wraps a foreign error and keeps it as the cause', () => {
    const inner = new Error('ENOENT');
    const out = wrapError(inner, 'reading q17.png', ERROR_CODES.STORE);
    expect(out).toBeInstanceOf(DeckleError);
    expect(out.code).toBe(ERROR_CODES.STORE);
    expect(out.message).toBe('reading q17.png: ENOENT');
    expect(out.cause).toBe(inner);
  });

  it('stringifies a thrown non error', () => {
    const out = wrapError('just a string', 'reading q17.png');
    expect(out.message).toBe('reading q17.png: just a string');
    expect(out.code).toBe(ERROR_CODES.ASSERTION);
  });
});
