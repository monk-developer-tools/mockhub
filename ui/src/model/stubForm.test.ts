import { describe, expect, it } from 'vitest';
import type { Stub } from '../api/types';
import { applySoapDefaults, emptyAlternative, emptyForm, errorPercentOf, fromStub, isDisabled, soapFault, toStub, validate, weightShares } from './stubForm';

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
      serveEventListeners: [{ name: 'recordState', parameters: {} }],
    });

    // Then
    expect(unsupported).toEqual(['Header "A" uses and', 'Transformers custom', 'Serve event listener recordState']);
  });

  it('reads WireMock webhooks and writes them as logged MockHub callouts without touching other listeners', () => {
    // Given
    const original: Stub = {
      request: { method: 'POST', urlPath: '/payments' },
      response: { status: 202 },
      serveEventListeners: [
        { name: 'recordState', parameters: { context: 'x' } },
        { name: 'webhook', parameters: { method: 'POST', url: 'https://partner/cb', headers: { 'Content-Type': 'application/json' }, body: '{"ok":true}', delay: { type: 'fixed', milliseconds: 250 } } },
        { name: 'mockhub-callout', parameters: { url: 'https://partner/fwd', forward: true } },
      ],
    };

    // When
    const { form, unsupported } = fromStub(original);
    const changed = { ...form, webhooks: [...form.webhooks, { method: 'GET', url: 'https://partner/ping', headers: [], body: 'ignored', delay: '', forward: false }] };
    const written = toStub(changed, original);

    // Then
    expect(unsupported).toEqual(['Serve event listener recordState']);
    expect(form.webhooks).toEqual([
      { method: 'POST', url: 'https://partner/cb', headers: [{ key: 'Content-Type', value: 'application/json' }], body: '{"ok":true}', delay: '250', forward: false },
      { method: 'POST', url: 'https://partner/fwd', headers: [], body: '', delay: '', forward: true },
    ]);
    expect(written.serveEventListeners).toEqual([
      { name: 'recordState', parameters: { context: 'x' } },
      { name: 'mockhub-callout', parameters: { method: 'POST', url: 'https://partner/cb', headers: { 'Content-Type': 'application/json' }, body: '{"ok":true}', delay: { type: 'fixed', milliseconds: 250 } } },
      { name: 'mockhub-callout', parameters: { url: 'https://partner/fwd', forward: true } },
      { name: 'mockhub-callout', parameters: { method: 'GET', url: 'https://partner/ping' } },
    ]);
    expect(toStub({ ...form, webhooks: [] }, { request: {}, response: {} }).serveEventListeners).toBeUndefined();
  });

  it('round-trips a stub that waits for a callout and answers with its response', () => {
    // Given
    const original: Stub = {
      request: { method: 'POST', urlPath: '/authorize' },
      response: {
        status: 200,
        fixedDelayMilliseconds: 100,
        transformers: ['mockhub-sync-callout'],
        transformerParameters: { callout: { method: 'PUT', url: 'https://issuer/auth', headers: { 'X-Key': 'k' }, body: '{"card":"{{jsonPath originalRequest.body \'$.card\'}}"}' } },
      },
    };

    // When
    const { form, unsupported } = fromStub(original);
    const written = toStub(form, original);
    const backToDirect = toStub({ ...form, responseMode: 'direct', bodyType: 'json', responseBody: '{}' }, original);

    // Then
    expect(unsupported).toEqual([]);
    expect(form.responseMode).toBe('callout');
    expect(form.syncCallout).toMatchObject({ method: 'PUT', url: 'https://issuer/auth', forward: false, headers: [{ key: 'X-Key', value: 'k' }] });
    expect(written.response).toEqual(original.response);
    expect(backToDirect.response).toEqual({ status: 200, jsonBody: {}, fixedDelayMilliseconds: 100 });
  });

  it('keeps other transformer parameters but still flags them for the JSON editor', () => {
    // Given
    const original: Stub = { request: {}, response: { status: 200, transformers: ['mockhub-crud'], transformerParameters: { resource: 'orders' } } };

    // When
    const { form, unsupported } = fromStub(original);
    const written = toStub(form, original);

    // Then
    expect(unsupported).toContain('Response transformerParameters resource');
    expect((written.response as { transformerParameters: unknown }).transformerParameters).toEqual({ resource: 'orders' });
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

  it('round-trips a failure rate through stub metadata', () => {
    // Given
    const form = { ...emptyForm('rest'), errorPercent: '10', errorStatus: '503', errorBody: '{"error":"busy"}' };

    // When
    const stub = toStub(form);
    const back = fromStub(stub);

    // Then
    expect((stub.metadata as { mockhub: { errorRate: unknown } }).mockhub.errorRate).toEqual({ percent: 10, status: 503, body: '{"error":"busy"}' });
    expect(errorPercentOf(stub)).toBe(10);
    expect(back.form.errorPercent).toBe('10');
    expect(back.form.errorBody).toBe('{"error":"busy"}');
    expect(back.unsupported).toEqual([]);
  });

  it('stores a fault instead of a status and removes the rate when cleared', () => {
    // Given
    const withFault = toStub({ ...emptyForm('rest'), errorPercent: '25', errorMode: 'fault', errorFault: 'EMPTY_RESPONSE' });

    // When
    const cleared = toStub({ ...fromStub(withFault).form, errorPercent: '' }, withFault);

    // Then
    expect((withFault.metadata as { mockhub: { errorRate: unknown } }).mockhub.errorRate).toEqual({ percent: 25, fault: 'EMPTY_RESPONSE' });
    expect((cleared.metadata as { mockhub: Record<string, unknown> }).mockhub.errorRate).toBeUndefined();
  });

  it('round-trips a stub rate limit and drops it when cleared', () => {
    // Given
    const stub = toStub({ ...emptyForm('rest'), rateRequests: '3', ratePer: 'second' });

    // When
    const back = fromStub(stub).form;
    const cleared = toStub({ ...back, rateRequests: '' }, stub);

    // Then
    expect((stub.metadata as { mockhub: { rateLimit: unknown } }).mockhub.rateLimit).toEqual({ requests: 3, per: 'second' });
    expect(back.rateRequests).toBe('3');
    expect(back.ratePer).toBe('second');
    expect((cleared.metadata as { mockhub: Record<string, unknown> }).mockhub.rateLimit).toBeUndefined();
  });

  it('rejects a rate limit that is not a whole number', () => {
    // When / Then
    expect(validate({ ...emptyForm('rest'), rateRequests: '2.5' })).toContain('The rate limit must be a whole number of requests up to 1,000,000.');
  });

  it('rejects failure rates outside 0 to 100', () => {
    // When / Then
    expect(validate({ ...emptyForm('rest'), errorPercent: '120' })).toContain('The failure rate must be between 0 and 100 percent.');
  });

  it('keeps the disabled flag when a disabled stub is saved from the form', () => {
    // Given
    const stub = { request: { method: 'GET', urlPath: '/a' }, response: { status: 200 }, metadata: { mockhub: { disabled: true } } };

    // When
    const saved = toStub({ ...fromStub(stub).form, name: 'A' }, stub);

    // Then
    expect(isDisabled(saved)).toBe(true);
  });

  it('asks for a URL when the stub waits for a callout', () => {
    // Given
    const form = { ...emptyForm(), responseMode: 'callout' as const };

    // When
    const errors = validate(form);

    // Then
    expect(errors).toContain('Enter the URL to call, e.g. https://partner.example.com/authorize');
  });

  it('writes other responses by weight after the normal one and reads them back', () => {
    // Given
    const form = { ...emptyForm(), url: '/orders', templating: true, mainWeight: '9' };
    form.alternatives = [{ weight: '1', status: '503', headers: [{ key: 'Retry-After', value: '5' }], body: '{"error":"busy"}' }];

    // When
    const stub = toStub(form);
    const back = fromStub(stub);

    // Then
    expect(stub.response).toMatchObject({
      status: 200,
      transformers: ['response-template', 'mockhub-weighted'],
      transformerParameters: { responses: [{ weight: 9, status: 200 }, { weight: 1, status: 503, headers: { 'Retry-After': '5' }, body: '{"error":"busy"}' }] },
    });
    expect(back.unsupported).toEqual([]);
    expect(back.form.mainWeight).toBe('9');
    expect(back.form.alternatives).toEqual(form.alternatives);
    expect(back.form.templating).toBe(true);
    expect(weightShares(back.form)).toEqual([90, 10]);
  });

  it('reads weighted responses written by hand, where every entry has its own body', () => {
    // Given
    const stub: Stub = {
      request: { method: 'GET', urlPath: '/coin' },
      response: { status: 200, transformers: ['mockhub-weighted'], transformerParameters: { responses: [{ weight: 1, status: 200, jsonBody: { side: 'heads' } }, { weight: 1, status: 200, body: 'tails' }] } },
    };

    // When
    const { form, unsupported } = fromStub(stub);

    // Then
    expect(unsupported).toEqual([]);
    expect(form.mainWeight).toBe('0');
    expect(form.alternatives.map((a) => a.body)).toEqual(['{\n  "side": "heads"\n}', 'tails']);
  });

  it('drops the weighted transformer when the other responses are removed', () => {
    // Given
    const stub = toStub({ ...emptyForm(), alternatives: [emptyAlternative()] });

    // When
    const plain = toStub({ ...fromStub(stub).form, alternatives: [] }, stub);

    // Then
    expect(plain.response).not.toHaveProperty('transformers');
    expect(plain.response).not.toHaveProperty('transformerParameters');
  });

  it('asks for usable weights and statuses', () => {
    // Given
    const allZero = { ...emptyForm(), mainWeight: '0', alternatives: [{ ...emptyAlternative(), weight: '0' }] };
    const badStatus = { ...emptyForm(), alternatives: [{ ...emptyAlternative(), status: '42' }] };

    // When / Then
    expect(validate(allZero)).toContain('At least one response needs a weight above zero.');
    expect(validate(badStatus)).toContain('Each other response needs a status between 100 and 599.');
  });

  it('round-trips a webhook signed with a connection that retries', () => {
    // Given
    const original: Stub = {
      request: { method: 'POST', urlPath: '/orders' },
      response: { status: 202 },
      serveEventListeners: [
        { name: 'mockhub-callout', parameters: { method: 'POST', url: 'https://partner/cb', connection: 'bank', retry: { count: 3, backoffMs: 500, statuses: '503' } } },
      ],
    };

    // When
    const { form, unsupported } = fromStub(original);
    const written = toStub(form, original);
    const withoutRetry = toStub({ ...form, webhooks: [{ ...form.webhooks[0], retries: '0', connection: '' }] }, original);

    // Then
    expect(unsupported).toEqual([]);
    expect(form.webhooks[0]).toMatchObject({ connection: 'bank', retries: '3', retryBackoff: '500', retryStatuses: '503' });
    expect(written.serveEventListeners).toEqual(original.serveEventListeners);
    expect(withoutRetry.serveEventListeners).toEqual([{ name: 'mockhub-callout', parameters: { method: 'POST', url: 'https://partner/cb' } }]);
  });

  it('signs a proxy with a connection and drops it when the connection is cleared', () => {
    // Given
    const original: Stub = {
      request: { method: 'ANY', urlPathPattern: '/ledger/.*' },
      response: { proxyBaseUrl: 'https://bank.test', transformers: ['mockhub-connection'], transformerParameters: { connection: 'bank' } },
    };

    // When
    const { form, unsupported } = fromStub(original);
    const written = toStub(form, original);
    const cleared = toStub({ ...form, proxyConnection: '' }, original);

    // Then
    expect(unsupported).toEqual([]);
    expect(form.proxyConnection).toBe('bank');
    expect(written.response).toEqual(original.response);
    expect(cleared.response).toEqual({ proxyBaseUrl: 'https://bank.test' });
  });
});
