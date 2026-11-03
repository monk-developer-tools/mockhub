import { describe, expect, it } from 'vitest';
import type { RuntimeSettings } from '../api/types';
import { EMPTY_RUNTIME, parseHeaders, runtimeSummary, toForm, toSettings } from './runtime';

describe('runtime form', () => {
  it('round-trips every setting through the form', () => {
    // Given
    const settings: RuntimeSettings = {
      defaultResponse: { status: 503, headers: { 'Content-Type': 'text/plain' }, body: 'down', templated: false },
      rateLimit: { requests: 5, per: 'second' },
      latency: { mode: 'uniform', minMs: 100, maxMs: 900, load: { afterRps: 4, msPerRequest: 25 } },
      cors: { origins: ['https://app.example'], methods: ['GET', 'POST'], headers: ['X-Trace'], credentials: true, simulateFailure: false },
      ipAllowList: ['10.0.0.0/8'],
    };

    // When
    const back = toSettings(toForm(settings));

    // Then
    expect(back).toEqual(settings);
  });

  it('sends nothing for settings that are switched off', () => {
    // Given
    const form = toForm(EMPTY_RUNTIME);

    // When
    const settings = toSettings(form);

    // Then
    expect(settings).toEqual(EMPTY_RUNTIME);
  });

  it('turns header lines into a map and ignores blank lines', () => {
    // Given
    const value = 'Content-Type: application/json\n\n X-Why :  no match \nX-Empty';

    // When
    const headers = parseHeaders(value);

    // Then
    expect(headers).toEqual({ 'Content-Type': 'application/json', 'X-Why': 'no match', 'X-Empty': '' });
  });

  it('describes the active settings in short phrases', () => {
    // Given
    const settings: RuntimeSettings = { ...EMPTY_RUNTIME, rateLimit: { requests: 2, per: 'minute' }, cors: { origins: [], methods: [], headers: [], credentials: false, simulateFailure: true } };

    // When
    const summary = runtimeSummary(settings);

    // Then
    expect(summary).toEqual(['2 requests per minute', 'CORS fails on purpose']);
  });
});
