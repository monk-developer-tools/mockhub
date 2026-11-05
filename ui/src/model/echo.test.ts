import { describe, expect, it } from 'vitest';
import type { Stub } from '../api/types';
import { ECHO_TRANSFORMER, emptyForm, fromStub, toStub } from './stubForm';

describe('echo response mode', () => {
  it('reads an echo stub as the echo mode', () => {
    // Given
    const stub: Stub = {
      request: { method: 'ANY', urlPattern: '/.*' },
      response: { status: 201, headers: { 'X-Echo': 'yes' }, transformers: [ECHO_TRANSFORMER] },
    };

    // When
    const { form, unsupported } = fromStub(stub);

    // Then
    expect(form.responseMode).toBe('echo');
    expect(form.status).toBe('201');
    expect(form.templating).toBe(false);
    expect(unsupported).toEqual([]);
  });

  it('writes the echo transformer with status and headers but no body', () => {
    // Given
    const form = { ...emptyForm(), url: '/echo', responseMode: 'echo' as const, status: '202', templating: true, responseBody: '{"x":1}',
      responseHeaders: [{ key: 'X-Echo', value: 'yes' }, { key: 'Content-Type', value: 'text/plain' }] };

    // When
    const stub = toStub(form);

    // Then
    expect(stub.response).toEqual({ status: 202, headers: { 'X-Echo': 'yes' }, transformers: [ECHO_TRANSFORMER] });
  });

  it('survives a round trip', () => {
    // Given
    const stub = toStub({ ...emptyForm(), url: '/echo', responseMode: 'echo' });

    // When
    const again = toStub(fromStub(stub).form);

    // Then
    expect(again.response).toEqual(stub.response);
  });
});
