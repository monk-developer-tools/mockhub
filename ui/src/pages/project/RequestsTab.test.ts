import type { ServeEvent } from '../../api/types';
import { replayRequest, stubFromRequest } from './RequestsTab';

const event = (method: string, url: string, body = ''): ServeEvent => ({
  id: '1',
  wasMatched: false,
  request: { method, url, absoluteUrl: url, headers: {}, body, loggedDate: 0, loggedDateString: '', clientIp: '' },
});

describe('stubFromRequest', () => {
  it('builds a relative stub with query matchers', () => {
    // When
    const stub = stubFromRequest(event('GET', '/pay/orders?status=open'), '/pay');

    // Then
    expect(stub.request).toEqual({ method: 'GET', urlPath: '/orders', queryParameters: { status: { equalTo: 'open' } } });
    expect(stub.name).toBe('GET /orders');
  });

  it('matches JSON bodies loosely and answers with JSON', () => {
    // When
    const stub = stubFromRequest(event('POST', '/pay/orders', '{"a":1}'), '/pay');

    // Then
    expect((stub.request as Record<string, unknown>).bodyPatterns).toEqual([{ equalToJson: '{"a":1}', ignoreExtraElements: true }]);
    expect((stub.response as Record<string, unknown>).jsonBody).toEqual({});
  });

  it('recognises SOAP operations', () => {
    // Given
    const envelope = '<s:Envelope xmlns:s="x"><s:Body><m:GetQuote xmlns:m="y"/></s:Body></s:Envelope>';

    // When
    const stub = stubFromRequest(event('POST', '/pay/ws', envelope), '/pay');

    // Then
    expect((stub.request as Record<string, unknown>).bodyPatterns).toEqual([{ matchesXPath: "//*[local-name()='Body']/*[local-name()='GetQuote']" }]);
    expect(stub.metadata).toEqual({ mockhub: { type: 'soap', operation: 'GetQuote', sampleRequest: { body: envelope } } });
  });
});

describe('replayRequest', () => {
  it('keeps meaningful headers and makes the path relative', () => {
    // Given
    const logged = event('POST', '/pay/orders?x=1', '{}');
    logged.request.headers = { Host: 'h', 'Content-Type': 'application/json', 'X-Tenant': ['a', 'b'] };

    // When
    const replay = replayRequest(logged, '/pay');

    // Then
    expect(replay).toEqual({
      method: 'POST',
      path: '/orders?x=1',
      headers: [['Content-Type', 'application/json'], ['X-Tenant', 'a, b']],
      body: '{}',
    });
  });
});
