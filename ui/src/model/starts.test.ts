import { describe, expect, it } from 'vitest';
import { aiPrompt, callbackStub, crudRequest, detectContentType, echoStub, isStartKind, nameFromSource, payloadStub, proxyStub, receiverStub, SAMPLES, START_GROUPS, tunnelCommand } from './starts';

describe('start cards', () => {
  it('cover the three website menus and every card leads somewhere', () => {
    // Given
    const groups = START_GROUPS;

    // When
    const cards = groups.flatMap((g) => g.cards);

    // Then
    expect(groups.map((g) => g.title)).toEqual(['Mock servers', 'Tools', 'Service virtualization']);
    expect(cards.every((c) => c.to || c.href || c.createsProject)).toBe(true);
    expect(cards.filter((c) => c.kind).every((c) => isStartKind(c.kind))).toBe(true);
    expect(new Set(cards.map((c) => c.id)).size).toBe(cards.length);
    expect(isStartKind('soap')).toBe(false);
  });
});

describe('names from the source', () => {
  it('reads the title from OpenAPI, WSDL, proto and Postman, and falls back to the file name', () => {
    // When
    const names = [
      nameFromSource('openapi', SAMPLES.openapi.text),
      nameFromSource('openapi', '{"openapi":"3.0.0","info":{"title":"Pets"}}'),
      nameFromSource('wsdl', SAMPLES.wsdl.text),
      nameFromSource('grpc', SAMPLES.grpc.text),
      nameFromSource('postman', '{"info":{"name":"Shop"},"item":[]}'),
      nameFromSource('graphql', SAMPLES.graphql.text, 'shop-api.graphql'),
      nameFromSource('openapi', 'not a spec', ''),
    ];

    // Then
    expect(names).toEqual(['Orders API', 'Pets', 'Calculator', 'Greeter', 'Shop', 'Shop Api', '']);
  });
});

describe('stubs built from a form', () => {
  it('makes a stub from a pasted answer and refuses broken JSON or a bad status', () => {
    // When
    const stub = payloadStub({ method: 'GET', path: 'users/1', status: 200, contentType: 'application/json', body: '{"id":1}' });

    // Then
    expect(stub).toMatchObject({ request: { method: 'GET', urlPath: '/users/1' }, response: { status: 200, headers: { 'Content-Type': 'application/json' }, body: '{"id":1}' } });
    expect(() => payloadStub({ method: 'GET', path: '/x', status: 200, contentType: 'application/json', body: '{oops' })).toThrow(/valid JSON/);
    expect(() => payloadStub({ method: 'GET', path: '/x', status: 99, contentType: 'text/plain', body: '' })).toThrow(/100 and 599/);
    expect(() => payloadStub({ method: 'GET', path: '  ', status: 200, contentType: 'text/plain', body: '' })).toThrow(/Choose the path/);
    expect(detectContentType('<soap:Envelope/>')).toBe('text/xml');
    expect(detectContentType('[1]')).toBe('application/json');
    expect(detectContentType('<a/>')).toBe('application/xml');
  });

  it('catches every webhook under a path and calls a service back after a delay', () => {
    // When
    const receiver = receiverStub('/hooks/stripe.v1/');
    const callback = callbackStub({ path: '/payments', status: 202, callbackUrl: 'https://my.app/hooks', delaySeconds: 2 });

    // Then
    expect(receiver.request).toEqual({ method: 'ANY', urlPathPattern: '/hooks/stripe\\.v1(/.*)?' });
    expect(receiverStub('/').request).toEqual({ method: 'ANY', urlPathPattern: '(/.*)?' });
    expect(callback).toMatchObject({ request: { method: 'POST', urlPath: '/payments' }, response: { status: 202 } });
    expect(callback.serveEventListeners).toEqual([expect.objectContaining({ name: 'mockhub-callout', parameters: expect.objectContaining({ url: 'https://my.app/hooks', delay: { type: 'fixed', milliseconds: 2000 } }) })]);
    expect(() => callbackStub({ path: '/p', status: 200, callbackUrl: 'ftp://x', delaySeconds: 1 })).toThrow(/http/);
    expect(() => callbackStub({ path: '/p', status: 200, callbackUrl: 'https://x', delaySeconds: 301 })).toThrow(/300 seconds/);
  });

  it('forwards everything to a service or a tunnel, and echoes requests', () => {
    // When
    const proxy = proxyStub('https://api.example.com/');
    const tunnel = proxyStub('tunnel://my-laptop');
    const echo = echoStub('/echo');

    // Then
    expect(proxy).toMatchObject({ request: { method: 'ANY' }, response: { proxyBaseUrl: 'https://api.example.com' } });
    expect(tunnel.response).toEqual({ proxyBaseUrl: 'tunnel://my-laptop' });
    expect(() => proxyStub('api.example.com')).toThrow(/address of the service/);
    expect(echo).toMatchObject({ request: { method: 'ANY', urlPathPattern: '/echo(/.*)?' }, response: { status: 200, transformers: ['mockhub-echo'] } });
    expect(tunnelCommand('https://mocks.example.com', 'my-laptop')).toBe('MOCKHUB_TOKEN=mh_… java -jar mockhub.jar tunnel --server https://mocks.example.com --name my-laptop --to http://localhost:3000');
  });
});

describe('CRUD and AI requests', () => {
  it('reads records from CSV or JSON and picks the id type', () => {
    // When
    const fromCsv = crudRequest('users', SAMPLES.crud.text);
    const fromJson = crudRequest('orders', '[{"id":"a1","total":5}]');
    const empty = crudRequest('items', '');

    // Then
    expect(fromCsv).toEqual({ resource: 'users', idField: 'id', idType: 'number', seed: [{ id: '1', name: 'Ada Lovelace', email: 'ada@example.com' }, { id: '2', name: 'Alan Turing', email: 'alan@example.com' }] });
    expect(fromJson.idType).toBe('uuid');
    expect(empty).toEqual({ resource: 'items', idField: 'id', idType: 'number' });
    expect(() => crudRequest('Users', '')).toThrow(/lowercase/);
    expect(() => crudRequest('users', '[1,2]')).toThrow(/JSON object/);
    expect(() => crudRequest('users', 'only,a,header')).toThrow(/header row/);
  });

  it('turns a short description into a prompt for the assistant', () => {
    // When
    const prompt = aiPrompt('An orders API with list and get');

    // Then
    expect(prompt).toContain('An orders API with list and get');
    expect(() => aiPrompt('orders')).toThrow(/sentence or two/);
  });
});
