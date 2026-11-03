import { describe, expect, it } from 'vitest';
import type { CalloutEntry } from '../api/types';
import { askAssistantAbout, calloutCounts, catchAllStub, describeOutcome, hasCatchAll, outcomeOf } from './callouts';

function entry(patch: Partial<CalloutEntry> = {}): CalloutEntry {
  return {
    id: '1',
    at: '2026-11-03T10:00:00Z',
    projectId: 'p',
    stubId: 's',
    stubName: 'Take payment',
    mode: 'async',
    trigger: { method: 'POST', url: '/pay/payments' },
    request: { method: 'POST', url: 'https://partner.test/cb', headers: [], body: '{}' },
    response: { status: 204, headers: [], body: '', truncated: false },
    error: null,
    durationMs: 12, requestId: null,
    ...patch,
  };
}

describe('callout outcomes', () => {
  it('classifies answers and failures', () => {
    // Given
    const ok = entry();
    const rejected = entry({ response: { status: 404, headers: [], body: '', truncated: false } });
    const broken = entry({ response: { status: 503, headers: [], body: '', truncated: false } });
    const failed = entry({ response: null, error: 'Couldn\'t connect' });

    // When
    const outcomes = [ok, rejected, broken, failed].map(outcomeOf);

    // Then
    expect(outcomes).toEqual(['ok', 'client-error', 'server-error', 'failed']);
    expect(describeOutcome(failed)).toBe("Couldn't connect");
    expect(describeOutcome(rejected)).toBe('404');
    expect(calloutCounts([ok, rejected, broken, failed])).toEqual({ total: 4, failed: 3 });
  });

  it('writes an assistant prompt that names the stub, target and error', () => {
    // Given
    const failed = entry({ mode: 'sync', response: null, error: '10.0.0.5 is a private or local address' });

    // When
    const prompt = askAssistantAbout(failed);

    // Then
    expect(prompt).toContain('wait-for-callout');
    expect(prompt).toContain('"Take payment"');
    expect(prompt).toContain('POST https://partner.test/cb');
    expect(prompt).toContain('private or local address');
    expect(prompt).toContain('stage a fix');
  });
});

describe('catch-all receiver', () => {
  it('answers anything with a templated 200 at a low priority', () => {
    // When
    const stub = catchAllStub();

    // Then
    expect(stub.request).toEqual({ method: 'ANY' });
    expect(stub.priority).toBe(10);
    expect(stub.response).toMatchObject({ status: 200, transformers: ['response-template'] });
    expect(hasCatchAll([stub])).toBe(true);
    expect(hasCatchAll([{ name: 'Other' }])).toBe(false);
  });
});
