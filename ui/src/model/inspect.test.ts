import type { Stub } from '../api/types';
import { inspectSteps, queryOf, testPayload, testTarget } from './inspect';

describe('the Inspect tab model', () => {
  it('marks the steps the user has done', () => {
    // When
    const steps = inspectSteps({ requests: 2, looked: true, answered: false, shared: false });

    // Then
    expect(steps.map((s) => [s.id, s.done])).toEqual([['catch', true], ['look', true], ['answer', false], ['share', false]]);
  });

  it('reads the query of a logged URL', () => {
    // When / Then
    expect(queryOf('/hooks/stripe?id=1&tag=a%20b')).toEqual([{ name: 'id', value: '1' }, { name: 'tag', value: 'a b' }]);
    expect(queryOf('/hooks')).toEqual([]);
  });

  it('sends the test to the first stub that takes a POST', () => {
    // Given
    const stubs = [
      { request: { method: 'GET', urlPath: '/health' } },
      { request: { method: 'ANY', urlPathPattern: '/hooks(/.*)?' } },
    ] as unknown as Stub[];

    // When / Then
    expect(testTarget(stubs, 'https://m.example.com/shop')).toBe('https://m.example.com/shop/hooks/test');
    expect(testTarget([], 'https://m.example.com/shop')).toBe('https://m.example.com/shop/test');
  });

  it('builds a recognisable test event', () => {
    // When
    const body = JSON.parse(testPayload(new Date('2026-11-09T10:00:00Z')));

    // Then
    expect(body.event).toBe('mockhub.test');
    expect(body.sentAt).toBe('2026-11-09T10:00:00.000Z');
  });
});
