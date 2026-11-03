import type { HistoryEntry } from '../api/types';
import { NO_FILTERS, activeFilters, historyQuery, isValidHeader, isValidStatus, loggedRequestOf, replayRequest, stubFromRequest } from './history';

const request = (method: string, url: string, body = '') => ({ method, url, body, headers: [] });

const entry: HistoryEntry = {
  id: 'e1', at: 1_793_700_000_000, projectId: 'p1', method: 'POST', url: '/pay/orders', absoluteUrl: 'http://h/pay/orders', clientIp: '10.0.0.1',
  requestHeaders: [{ name: 'Accept', value: 'a' }, { name: 'Accept', value: 'b' }, { name: 'X-Id', value: '7' }], requestBody: '{}', requestBodyTruncated: false,
  status: 201, responseHeaders: [], responseBody: '', responseBodyTruncated: false, matched: true, stubId: 's1', stubName: 'Create order', durationMs: 4,
};

describe('historyQuery', () => {
  it('sends only the filters that are set, with the time range as a start time', () => {
    // Given
    const filters = { ...NO_FILTERS, text: ' orders ', method: 'POST', status: '4xx', matched: 'unmatched' as const, header: 'X-Tenant:acme', range: '1h' as const };

    // When
    const query = new URLSearchParams(historyQuery(filters, 10_000_000, 100));

    // Then
    expect(Object.fromEntries(query)).toEqual({ q: 'orders', method: 'POST', status: '4xx', matched: 'false', header: 'X-Tenant:acme', from: String(10_000_000 - 3_600_000), limit: '100', offset: '100' });
  });

  it('prefers a span picked on the chart over the relative time range', () => {
    // Given
    const filters = { ...NO_FILTERS, range: '24h' as const, from: '1000', to: '5000' };

    // When
    const query = Object.fromEntries(new URLSearchParams(historyQuery(filters, 10_000_000)));

    // Then
    expect(query).toEqual({ from: '1000', to: '5000', limit: '100' });
  });

  it('asks for the first page when nothing is filtered', () => {
    // When
    const query = historyQuery(NO_FILTERS, 0);

    // Then
    expect(query).toBe('limit=100');
    expect(activeFilters(NO_FILTERS)).toBe(0);
  });
});

describe('filter validation', () => {
  it('accepts the status and header shapes the server understands', () => {
    // When
    const statuses = ['', '404', '4xx', '400-499', 'abc', '40'].map(isValidStatus);
    const headers = ['', 'X-Tenant', 'X-Tenant:acme', ':x', 'a b:c'].map(isValidHeader);

    // Then
    expect(statuses).toEqual([true, true, true, true, false, false]);
    expect(headers).toEqual([true, true, true, false, false]);
  });
});

describe('loggedRequestOf', () => {
  it('turns a history entry into the shape WireMock near-miss matching expects', () => {
    // When
    const logged = loggedRequestOf(entry);

    // Then
    expect(logged).toMatchObject({ url: '/pay/orders', method: 'POST', body: '{}', clientIp: '10.0.0.1', loggedDate: entry.at, headers: { Accept: ['a', 'b'], 'X-Id': '7' } });
  });
});

describe('stubFromRequest', () => {
  it('builds a relative stub with query matchers', () => {
    // When
    const stub = stubFromRequest(request('GET', '/pay/orders?status=open'), '/pay');

    // Then
    expect(stub.request).toEqual({ method: 'GET', urlPath: '/orders', queryParameters: { status: { equalTo: 'open' } } });
    expect(stub.name).toBe('GET /orders');
  });

  it('matches JSON bodies loosely and answers with JSON', () => {
    // When
    const stub = stubFromRequest(request('POST', '/pay/orders', '{"a":1}'), '/pay');

    // Then
    expect((stub.request as Record<string, unknown>).bodyPatterns).toEqual([{ equalToJson: '{"a":1}', ignoreExtraElements: true }]);
    expect((stub.response as Record<string, unknown>).jsonBody).toEqual({});
  });

  it('recognises SOAP operations', () => {
    // Given
    const envelope = '<s:Envelope xmlns:s="x"><s:Body><m:GetQuote xmlns:m="y"/></s:Body></s:Envelope>';

    // When
    const stub = stubFromRequest(request('POST', '/pay/ws', envelope), '/pay');

    // Then
    expect((stub.request as Record<string, unknown>).bodyPatterns).toEqual([{ matchesXPath: "//*[local-name()='Body']/*[local-name()='GetQuote']" }]);
    expect(stub.metadata).toEqual({ mockhub: { type: 'soap', operation: 'GetQuote', sampleRequest: { body: envelope } } });
  });
});

describe('replayRequest', () => {
  it('keeps meaningful headers, joins repeated ones and makes the path relative', () => {
    // Given
    const logged = { method: 'POST', url: '/pay/orders?x=1', body: '{}', headers: [{ name: 'Host', value: 'h' }, { name: 'Content-Type', value: 'application/json' }, { name: 'X-Tenant', value: 'a' }, { name: 'X-Tenant', value: 'b' }] };

    // When
    const replay = replayRequest(logged, '/pay');

    // Then
    expect(replay).toEqual({ method: 'POST', path: '/orders?x=1', headers: [['Content-Type', 'application/json'], ['X-Tenant', 'a, b']], body: '{}' });
  });
});
