import { describe, expect, it } from 'vitest';
import type { Stub } from '../api/types';
import { applySoapDefaults, emptyForm, fromStub, soapFault, toStub, validate } from './stubForm';

describe('stub form conversion', () => {
  it('reads a REST stub into the form', () => {
    // Given
    const stub: Stub = {
      name: 'Get user',
      priority: 2,
      request: {
        method: 'GET',
        urlPathTemplate: '/users/{id}',
        queryParameters: { verbose: { equalTo: 'true' }, debug: { absent: true } },
        headers: { 'X-Tenant': { equalTo: 'acme', caseInsensitive: true } },
      },
      response: {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
        jsonBody: { id: '{{request.path.id}}' },
        transformers: ['response-template'],
        fixedDelayMilliseconds: 250,
      },
      metadata: { mockhub: { type: 'rest', description: 'Fetch a user', folder: 'Users' } },
    };

    // When
    const { form, unsupported } = fromStub(stub);

    // Then
    expect(unsupported).toEqual([]);
    expect(form.name).toBe('Get user');
    expect(form.priority).toBe('2');
    expect(form.urlMatch).toBe('urlPathTemplate');
    expect(form.url).toBe('/users/{id}');
    expect(form.query).toEqual([
      { key: 'verbose', operator: 'equalTo', value: 'true', caseInsensitive: false },
      { key: 'debug', operator: 'absent', value: '', caseInsensitive: false },
    ]);
    expect(form.headers[0].caseInsensitive).toBe(true);
    expect(form.bodyType).toBe('json');
    expect(JSON.parse(form.responseBody)).toEqual({ id: '{{request.path.id}}' });
    expect(form.templating).toBe(true);
    expect(form.delayType).toBe('fixed');
    expect(form.fixedDelay).toBe('250');
    expect(form.description).toBe('Fetch a user');
    expect(form.folder).toBe('Users');
  });

  it('round-trips a stub without losing fields the form does not edit', () => {
    // Given
    const stub: Stub = {
      id: 'abc',
      request: { method: 'POST', urlPath: '/orders', basicAuthCredentials: { username: 'u', password: 'p' } },
      response: { status: 201, additionalProxyRequestHeaders: { X: '1' } },
      persistent: true,
      metadata: { team: 'payments', mockhub: { projectId: 'p1' } },
    };

    // When
    const { form, unsupported } = fromStub(stub);
    const result = toStub(form, stub);

    // Then
    expect(unsupported).toContain('Request basicAuthCredentials');
    expect(result.id).toBe('abc');
    expect(result.persistent).toBe(true);
    expect((result.request as Record<string, unknown>).basicAuthCredentials).toEqual({ username: 'u', password: 'p' });
    expect(result.metadata).toEqual({ team: 'payments', mockhub: { projectId: 'p1', type: 'rest' } });
  });

  it('writes JSON bodies as jsonBody unless templating is on', () => {
    // Given
    const form = { ...emptyForm(), responseBody: '{"a":1}' };

    // When / Then
    expect((toStub(form).response as Record<string, unknown>).jsonBody).toEqual({ a: 1 });
    const templated = toStub({ ...form, templating: true }).response as Record<string, unknown>;
    expect(templated.body).toBe('{"a":1}');
    expect(templated.transformers).toEqual(['response-template']);
  });

  it('writes body matchers including XPath with an expected value', () => {
    // Given
    const form = {
      ...emptyForm(),
      method: 'POST',
      body: [
        { operator: 'equalToJson' as const, value: '{"a":1}', ignoreArrayOrder: true, ignoreExtraElements: true, expectedValue: '' },
        { operator: 'matchesXPath' as const, value: '//a/text()', ignoreArrayOrder: false, ignoreExtraElements: false, expectedValue: '2' },
      ],
    };

    // When
    const request = toStub(form).request as Record<string, unknown>;

    // Then
    expect(request.bodyPatterns).toEqual([
      { equalToJson: '{"a":1}', ignoreArrayOrder: true, ignoreExtraElements: true },
      { matchesXPath: { expression: '//a/text()', equalTo: '2' } },
    ]);
  });

  it('reads XPath matchers with an expected value back into the form', () => {
    // When
    const { form } = fromStub({ request: { bodyPatterns: [{ matchesXPath: { expression: '//a', equalTo: '5' } }] }, response: {} });

    // Then
    expect(form.body[0]).toMatchObject({ operator: 'matchesXPath', value: '//a', expectedValue: '5' });
  });

  it('writes faults, proxies and delay distributions', () => {
    expect(toStub({ ...emptyForm(), responseMode: 'fault', fault: 'EMPTY_RESPONSE' }).response).toEqual({ fault: 'EMPTY_RESPONSE' });
    expect(toStub({ ...emptyForm(), responseMode: 'proxy', proxyBaseUrl: 'https://api.example.com', delayType: 'uniform' }).response).toEqual({
      proxyBaseUrl: 'https://api.example.com',
      delayDistribution: { type: 'uniform', lower: 100, upper: 800 },
    });
  });

  it('only sets scenario states when a scenario is named', () => {
    expect(toStub({ ...emptyForm(), requiredState: 'x' }).requiredScenarioState).toBeUndefined();
    const stub = toStub({ ...emptyForm(), scenarioName: 'Cart', newState: 'Added' });
    expect(stub.requiredScenarioState).toBe('Started');
    expect(stub.newScenarioState).toBe('Added');
  });

  it('marks any-URL stubs and missing urls', () => {
    const request = toStub({ ...emptyForm(), urlMatch: 'any' }).request as Record<string, unknown>;
    expect(Object.keys(request)).toEqual(['method']);
  });

  it('reports unsupported constructs so the UI can suggest the JSON editor', () => {
    // When
    const { unsupported } = fromStub({
      request: { headers: { A: { and: [{ contains: 'x' }] } } },
      response: { transformers: ['custom'] },
      serveEventListeners: [],
    });

    // Then
    expect(unsupported).toEqual(['Header "A" uses and', 'Transformers custom', 'Webhooks / serve event listeners']);
  });
});

describe('SOAP helpers', () => {
  it('applies SOAP defaults: POST, operation XPath and content type', () => {
    // Given
    const form = { ...emptyForm('soap'), method: 'GET', soapOperation: 'GetQuote', soapVersion: '1.2' as const };

    // When
    const soap = applySoapDefaults(form);

    // Then
    expect(soap.method).toBe('POST');
    expect(soap.body[0].value).toBe("//*[local-name()='Body']/*[local-name()='GetQuote']");
    expect(soap.responseHeaders[0]).toEqual({ key: 'Content-Type', value: 'application/soap+xml; charset=utf-8' });
  });

  it('round-trips SOAP metadata', () => {
    // Given
    const form = { ...emptyForm('soap'), soapAction: 'urn:Get', soapOperation: 'Get', sampleBody: '<x/>' };

    // When
    const { form: back } = fromStub(toStub(applySoapDefaults(form)));

    // Then
    expect(back.kind).toBe('soap');
    expect(back.soapAction).toBe('urn:Get');
    expect(back.soapOperation).toBe('Get');
    expect(back.sampleBody).toBe('<x/>');
  });

  it('builds version-specific faults', () => {
    expect(soapFault('1.1', 'Boom')).toContain('<faultstring>Boom</faultstring>');
    expect(soapFault('1.2', 'Boom')).toContain('<soap:Text xml:lang="en">Boom</soap:Text>');
  });
});

describe('validation', () => {
  it('accepts the default form', () => {
    expect(validate(emptyForm())).toEqual([]);
  });

  it('rejects bad status, json, regex, proxy and priority', () => {
    const errors = validate({
      ...emptyForm(),
      status: '42',
      responseBody: '{oops',
      urlMatch: 'urlPattern',
      url: '([',
      priority: 'high',
    });
    expect(errors).toHaveLength(4);
    expect(validate({ ...emptyForm(), responseMode: 'proxy', proxyBaseUrl: 'ftp://x' })).toEqual([
      'Proxy target must start with http:// or https://',
    ]);
  });
});
