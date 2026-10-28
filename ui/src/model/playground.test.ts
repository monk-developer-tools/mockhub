import type { PlaygroundEntry, Project } from '../api/types';
import { replayOf, curlOf, joinUrl, prettyBody, projectFor, relativePath, splitUrl, statusTone, stubFromEntry } from './playground';

const shop = { id: 'p1', basePath: '/shop', mockUrl: 'https://mocks.acme.dev/shop' } as Project;

const entry: PlaygroundEntry = {
  id: 'e1',
  at: '2026-10-17T10:00:00Z',
  request: { method: 'GET', url: 'https://mocks.acme.dev/shop/orders/7?x=1', headers: [], body: '' },
  response: { status: 200, headers: [{ name: 'content-type', value: 'application/json' }], body: '{"id":7}', size: 8, truncated: false, durationMs: 4 },
  error: null,
  local: true,
};

describe('playground model', () => {
  it('splits and joins query parameters', () => {
    // When
    const parts = splitUrl('https://a.example/x?q=hello%20world&flag');

    // Then
    expect(parts).toEqual({ base: 'https://a.example/x', params: [{ name: 'q', value: 'hello world' }, { name: 'flag', value: '' }] });
    expect(joinUrl(parts.base, [{ name: 'q', value: 'a b' }, { name: '', value: 'ignored' }])).toBe('https://a.example/x?q=a%20b');
  });

  it('colours statuses by class', () => {
    expect([statusTone(204), statusTone(302), statusTone(404), statusTone(503), statusTone(undefined)]).toEqual(['ok', 'redirect', 'client', 'server', 'none']);
  });

  it('pretty-prints JSON and leaves other bodies alone', () => {
    expect(prettyBody('{"a":1}', 'application/json')).toEqual({ text: '{\n  "a": 1\n}', language: 'json' });
    expect(prettyBody('<a/>', 'text/xml').language).toBe('xml');
    expect(prettyBody('{broken', '').language).toBe('text');
  });

  it('finds the project a URL belongs to and the path inside it', () => {
    // When
    const project = projectFor(entry.request.url, 'https://mocks.acme.dev', [shop]);

    // Then
    expect(project?.id).toBe('p1');
    expect(relativePath(entry.request.url, project)).toBe('/orders/7');
    expect(relativePath('/other/x', undefined)).toBe('/other/x');
  });

  it('turns a response into a stub', () => {
    // When
    const stub = stubFromEntry(entry, '/orders/7', 'Order 7');

    // Then
    expect(stub).toEqual({
      name: 'Order 7',
      request: { method: 'GET', urlPath: '/orders/7' },
      response: { status: 200, headers: { 'Content-Type': 'application/json' }, jsonBody: { id: 7 } },
    });
  });

  it('builds a cURL command, resolving relative URLs', () => {
    // When
    const curl = curlOf({ method: 'POST', url: '/shop/orders', headers: [{ name: 'Content-Type', value: 'application/json' }], body: '{"a":1}' }, 'http://localhost:8080');

    // Then
    expect(curl).toBe("curl -X POST 'http://localhost:8080/shop/orders' \\\n  -H 'Content-Type: application/json' \\\n  --data-raw '{\"a\":1}'");
  });

  it('replays a logged request without transport headers', () => {
    // When
    const replay = replayOf({
      url: '/shop/orders?x=1', absoluteUrl: '', method: 'POST', body: '{"a":1}', loggedDate: 0, loggedDateString: '', clientIp: '',
      headers: { Host: 'localhost', 'Content-Length': '7', 'Content-Type': 'application/json', Accept: ['a', 'b'] },
    });

    // Then
    expect(replay).toEqual({
      method: 'POST',
      url: '/shop/orders?x=1',
      headers: [{ name: 'Content-Type', value: 'application/json' }, { name: 'Accept', value: 'a' }, { name: 'Accept', value: 'b' }],
      body: '{"a":1}',
    });
  });
});

describe('projectFor with a project at the root', () => {
  it('prefers the project whose base path matches over the root project', () => {
    // Given
    const root = { id: 'r', basePath: '', mockUrl: 'http://hub' } as Project;
    const orders = { id: 'o', basePath: '/orders', mockUrl: 'http://hub/orders' } as Project;

    // When / Then
    expect(projectFor('http://hub/orders/1', 'http://hub', [root, orders])?.id).toBe('o');
    expect(projectFor('http://hub/customers/1', 'http://hub', [root, orders])?.id).toBe('r');
    expect(relativePath('http://hub/customers/1', root)).toBe('/customers/1');
  });
});
